/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.rezoss.memory;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Compatibility for Memory 1.0.00.48 and SCS versions without its v1 collector.
 * Stores full documents in caller-owned SCS v2 schemas; never claims that the
 * absent Samsung Memory semantic collector or global Finder integration exists.
 */
public final class MemoryIndexAdapter {
    private static final String TAG = "UN1CAMemoryIndex";
    private static final String AUTHORITY = "com.samsung.android.smartsuggestions.search";
    private static final String OWNER = "com.samsung.android.app.memory";
    private static final int BATCH = 64;
    private static final int SEARCH_LIMIT = 200;
    private static final Set<String> UNSUPPORTED = new HashSet<>();
    private static final Set<String> READY = new HashSet<>();
    private static final Object LOCK = new Object();
    private static final String[] MEMORY_COLUMNS = {"_id", "title", "body", "source_text",
        "user_note", "memo", "representative_tag", "category", "deep_link",
        "created_time", "modified_time", "is_locked"};
    private static final String[] COLLECTION_COLUMNS = {"_id", "name", "searchable_keywords"};

    private MemoryIndexAdapter() {}

    private static String kind(Uri uri, boolean search) {
        if (uri == null || !AUTHORITY.equals(uri.getAuthority())) return null;
        List<String> parts = uri.getPathSegments();
        if (parts.size() != (search ? 3 : 2) || !"v1".equals(parts.get(0))) return null;
        String result = parts.get(1);
        if (!"memory".equals(result) && !"memory_collection".equals(result)) return null;
        return !search || "search".equals(parts.get(2)) ? result : null;
    }

    private static Uri source(String kind) {
        return Uri.parse("content://" + OWNER + ".provider.memory/v1/"
            + ("memory".equals(kind) ? "memory" : "collections"));
    }

    private static String schemaName(String kind) { return OWNER + ".unica_" + kind; }
    private static Uri index(String kind) {
        return Uri.parse("content://" + AUTHORITY + "/v2/" + schemaName(kind));
    }

    static String schema(String kind) {
        return "<schema name=\"" + schemaName(kind) + "\" version=\"1\""
            + " keyFieldName=\"_id\" useUpsert=\"true\">"
            + "<defaultAnalyzer class=\"bs.LowerCaseSpaceAnalyzer\"/>"
            + "<fieldType name=\"string\" dataType=\"String\" class=\"StringField\"/>"
            + "<fieldType name=\"text\" dataType=\"String\" class=\"TextField\"/>"
            + "<field name=\"_id\" type=\"string\" isNonNull=\"true\"/>"
            + "<field name=\"title\" type=\"text\"/>"
            + "<field name=\"content\" type=\"text\"/>"
            + "<field name=\"deep_link\" type=\"string\"/>"
            + "<field name=\"created_time\" type=\"string\"/>"
            + "<field name=\"modified_time\" type=\"string\"/>"
            + "</schema>";
    }

    private static boolean supported(String route) {
        synchronized (LOCK) { return !UNSUPPORTED.contains(route); }
    }

    private static void unavailable(String route) {
        synchronized (LOCK) {
            if (UNSUPPORTED.add(route)) Log.i(TAG, "Native Memory route absent; using caller-owned v2 index: " + route);
        }
    }

    public static int update(ContentResolver resolver, Uri uri, ContentValues values,
            String selection, String[] ids) {
        String kind = kind(uri, false);
        if (kind == null) return resolver.update(uri, values, selection, ids);
        if (supported(kind)) {
            try { return resolver.update(uri, values, selection, ids); }
            catch (UnsupportedOperationException absent) { unavailable(kind); }
        }
        try {
            synchronized (LOCK) {
                ensure(resolver, kind);
                requireIds(selection, ids);
                removeIds(resolver, kind, ids);
                return mirror(resolver, kind, ids);
            }
        } catch (RuntimeException | JSONException error) {
            Log.w(TAG, "Index update unavailable: " + error.getClass().getSimpleName());
            return -1;
        } finally { invalidateLocal(resolver, kind); }
    }

    public static int delete(ContentResolver resolver, Uri uri, String selection, String[] ids) {
        String kind = kind(uri, false);
        if (kind == null) return resolver.delete(uri, selection, ids);
        if (supported(kind)) {
            try { return resolver.delete(uri, selection, ids); }
            catch (UnsupportedOperationException absent) { unavailable(kind); }
        }
        try {
            synchronized (LOCK) {
                ensure(resolver, kind);
                requireIds(selection, ids);
                return removeIds(resolver, kind, ids);
            }
        } catch (RuntimeException | JSONException error) {
            Log.w(TAG, "Index deletion unavailable: " + error.getClass().getSimpleName());
            return -1;
        } finally { invalidateLocal(resolver, kind); }
    }

    private static void requireIds(String selection, String[] ids) {
        if (!"_id".equals(selection) || ids == null || ids.length == 0)
            throw new IllegalArgumentException("Expected explicit Memory ids");
        for (String id : ids) {
            if (id == null || !id.matches("[0-9]+")) throw new IllegalArgumentException("Invalid Memory id");
        }
    }

    private static void ensure(ContentResolver resolver, String kind) {
        if (READY.contains(kind)) return;
        Bundle request = new Bundle();
        request.putString("name", schemaName(kind));
        Bundle previous = resolver.call(AUTHORITY, "GET_SCHEMA_VERSION", null, request);
        boolean existed = previous != null && previous.containsKey("schema-version");
        request.putByteArray("schema-content", schema(kind).getBytes(StandardCharsets.UTF_8));
        Bundle response = resolver.call(AUTHORITY, "REGISTER_SCHEMA", null, request);
        if (response == null || response.getInt("status", -1) != 0)
            throw new IllegalStateException("SCS schema registration unavailable");
        // Rebuild only our own schema once per process. Removes stale/deleted/locked
        // records surviving process death or notifications missed by an older APK.
        if (existed && resolver.delete(index(kind), null, null) < 0) {
            // A registered schema can have no index files after an interrupted
            // first write. Seed it from live rows before retrying the reset.
            if (mirror(resolver, kind, null) == 0 || resolver.delete(index(kind), null, null) < 0)
                throw new IllegalStateException("SCS schema reset unavailable");
        }
        mirror(resolver, kind, null);
        READY.add(kind);
        Log.i(TAG, "Caller-owned v2 index synchronized: " + kind);
    }

    private static int removeIds(ContentResolver resolver, String kind, String[] ids) throws JSONException {
        JSONObject term = new JSONObject().put("fields", new JSONArray().put("_id"))
            .put("texts", new JSONArray(java.util.Arrays.asList(ids)));
        String query = new JSONObject().put("query", new JSONObject().put("term", term)).toString();
        int count = resolver.delete(index(kind), query, null);
        if (count < 0) throw new IllegalStateException("SCS deletion failed");
        return count;
    }

    private static int mirror(ContentResolver resolver, String kind, String[] ids) {
        boolean memory = "memory".equals(kind);
        String selection = memory ? "is_locked = 0" : null;
        if (ids != null) {
            StringBuilder where = new StringBuilder("_id IN (");
            for (int i = 0; i < ids.length; i++) { if (i > 0) where.append(','); where.append('?'); }
            where.append(')');
            selection = (selection == null ? "" : selection + " AND ") + where;
        }
        int total = 0;
        ArrayList<ContentValues> batch = new ArrayList<>();
        try (Cursor cursor = resolver.query(source(kind), memory ? MEMORY_COLUMNS : COLLECTION_COLUMNS,
                selection, ids, null)) {
            if (cursor == null) throw new IllegalStateException("Memory source cursor unavailable");
            while (cursor.moveToNext()) {
                ContentValues document = new ContentValues();
                document.put("_id", value(cursor, "_id"));
                document.put("title", value(cursor, memory ? "title" : "name"));
                StringBuilder content = new StringBuilder();
                String[] fields = memory ? new String[]{"body", "source_text", "user_note", "memo",
                    "representative_tag", "category"} : new String[]{"searchable_keywords"};
                for (String field : fields) content.append(value(cursor, field)).append('\n');
                document.put("content", content.toString());
                if (memory) for (String field : new String[]{"deep_link", "created_time", "modified_time"})
                    document.put(field, value(cursor, field));
                batch.add(document);
                if (batch.size() == BATCH) { total += insert(resolver, kind, batch); batch.clear(); }
            }
            if (!batch.isEmpty()) total += insert(resolver, kind, batch);
        }
        return total;
    }

    private static int insert(ContentResolver resolver, String kind, ArrayList<ContentValues> batch) {
        int count = resolver.bulkInsert(index(kind), batch.toArray(new ContentValues[0]));
        if (count != batch.size()) throw new IllegalStateException("SCS document write incomplete");
        return count;
    }

    private static String value(Cursor cursor, String field) {
        int at = cursor.getColumnIndex(field);
        return at < 0 || cursor.isNull(at) ? "" : cursor.getString(at);
    }

    /** SearchCraft bulk-import uses REINDEX rather than the ordinary id notifier. */
    public static Bundle call(ContentResolver resolver, String authority, String method,
            String argument, Bundle extras) {
        if (!AUTHORITY.equals(authority) || !"REINDEX".equals(method) || !OWNER.equals(argument))
            return resolver.call(authority, method, argument, extras);
        Bundle response = null;
        RuntimeException nativeFailure = null;
        try { response = resolver.call(authority, method, argument, extras); }
        catch (RuntimeException error) { nativeFailure = error; }
        synchronized (LOCK) {
            for (String kind : new String[]{"memory", "memory_collection"}) {
                READY.remove(kind);
                try { ensure(resolver, kind); }
                catch (RuntimeException error) {
                    Log.w(TAG, "Imported data index unavailable: " + error.getClass().getSimpleName());
                } finally { invalidateLocal(resolver, kind); }
            }
        }
        // Preserve the native call contract, including failures; a mirror rebuild
        // is not represented as a successful Samsung collector REINDEX response.
        if (nativeFailure != null) throw nativeFailure;
        return response;
    }

    public static Cursor query(ContentResolver resolver, Uri uri, String[] projection,
            Bundle args, CancellationSignal cancellation) {
        String kind = kind(uri, true);
        if (kind == null) return resolver.query(uri, projection, args, cancellation);
        if (cancellation != null) cancellation.throwIfCanceled();
        if (supported(kind + "/search")) {
            try { return resolver.query(uri, projection, args, cancellation); }
            catch (UnsupportedOperationException absent) { unavailable(kind + "/search"); }
        }
        String text = args == null ? "" : args.getString(ContentResolver.QUERY_ARG_SQL_SELECTION, "");
        if (text.trim().isEmpty()) return new MatrixCursor(new String[]{"_id"});
        try {
            synchronized (LOCK) { ensure(resolver, kind); }
            JSONObject match = new JSONObject().put("fields", new JSONArray().put("title").put("content"))
                .put("text", text).put("field_operator", "or");
            Bundle query = new Bundle();
            query.putString("query-json", new JSONObject().put("query", new JSONObject().put("match", match))
                .put("limit", SEARCH_LIMIT).toString());
            // Only id is requested: Memory's exact cursor consumer already supplies
            // its keyword default when semantic similarity/matched_source are absent.
            Cursor cursor = resolver.query(index(kind), new String[]{"_id"}, query, cancellation);
            if (cursor == null) throw new IllegalStateException("SCS v2 search cursor unavailable");
            return cursor;
        } catch (OperationCanceledException cancelled) { throw cancelled; }
        catch (RuntimeException | JSONException error) {
            Log.w(TAG, "SCS v2 search unavailable; using Memory local text search: " + error.getClass().getSimpleName());
            return localSearch(resolver, kind, text, cancellation);
        }
    }

    private static Cursor localSearch(ContentResolver resolver, String kind, String text,
            CancellationSignal cancellation) {
        IndexTextQuery plan = IndexTextQuery.build("memory".equals(kind), text);
        Bundle query = new Bundle();
        query.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, plan.selection);
        query.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, plan.arguments);
        MatrixCursor result = new MatrixCursor(new String[]{"_id"});
        try (Cursor cursor = resolver.query(source(kind), new String[]{"_id"}, query, cancellation)) {
            if (cursor == null) throw new IllegalStateException("Memory local search cursor unavailable");
            while (result.getCount() < SEARCH_LIMIT && cursor.moveToNext()) {
                if (cancellation != null) cancellation.throwIfCanceled();
                result.addRow(new Object[]{cursor.getLong(0)});
            }
        }
        return result;
    }

    private static void invalidateLocal(ContentResolver resolver, String kind) {
        try {
            resolver.notifyChange(source(kind), null, false);
            resolver.notifyChange(Uri.parse("content://" + OWNER + ".provider.search/view_search"), null, false);
        } catch (RuntimeException error) {
            Log.w(TAG, "Local search notification failed: " + error.getClass().getSimpleName());
        }
    }
}
