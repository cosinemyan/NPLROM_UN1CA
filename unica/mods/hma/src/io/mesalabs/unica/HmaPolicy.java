/* SPDX-License-Identifier: AGPL-3.0-or-later
 * ROM adaptation of HMA-OSS feature semantics. See ../NOTICE in this mod.
 */
package io.mesalabs.unica;

import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import org.json.*;

/** Shared policy; no writes to package state, real settings, or primary process GIDs. */
public final class HmaPolicy {
    public static final String KEY = "unica_hma_policy";
    private static volatile Context context;
    private static final ThreadLocal<Boolean> busy = new ThreadLocal<Boolean>();
    private static final ConcurrentHashMap<Integer, Config> configs = new ConcurrentHashMap<Integer, Config>();
    private static final ConcurrentHashMap<String, Facts> facts = new ConcurrentHashMap<String, Facts>();
    private static final Set<String> pending = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final ExecutorService scanner = Executors.newSingleThreadExecutor();
    private static final int[] GIDS = {1015, 1023, 1032, 1077, 1078, 1079, 3003, 9997};
    private static final String PLAY_STORE = "com.android.vending";
    private static volatile boolean registered;
    private static final class Config {
        final JSONObject json;
        final long time = SystemClock.elapsedRealtime();
        Config(String value) throws JSONException { json = new JSONObject(value == null ? "{}" : value); }
    }
    static final class Facts {
        final Set<String> presets = new HashSet<String>();
        boolean risky;
    }
    private HmaPolicy() {}

    public static void init(Context ctx) {
        if (ctx == null) return;
        context = ctx;
        synchronized (HmaPolicy.class) {
            if (registered) return;
            registered = true;
        }
        scanner.execute(new Runnable() { public void run() {
            try {
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_PACKAGE_ADDED);
                filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
                filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
                filter.addDataScheme("package");
                context.registerReceiver(new BroadcastReceiver() {
                    public void onReceive(Context c, Intent i) {
                        final String name = i.getData() == null ? null : i.getData().getSchemeSpecificPart();
                        if (name == null) return;
                        for (String key : facts.keySet()) if (key.endsWith(":" + name)) facts.remove(key);
                        if (!Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction())) requestScan(name, i.getIntExtra("android.intent.extra.UID", 0) / 100000);
                    }
                }, filter);
                for (ApplicationInfo app : context.getPackageManager().getInstalledApplications(0)) requestScan(app.packageName, 0);
            } catch (RuntimeException e) { Log.w("UnicaHMA", "[other] Package scan unavailable", e); }
        }});
    }

    private static Context getContext() {
        if (context != null) return context;
        try {
            Class<?> c = Class.forName("android.app.ActivityThread");
            Object thread = c.getMethod("currentActivityThread").invoke(null);
            context = (Context)c.getMethod("getSystemContext").invoke(thread);
        } catch (Exception ignored) {}
        return context;
    }
    private static boolean appUid(int uid) { return uid >= 0 && uid % 100000 >= 10000 && uid % 100000 <= 19999; }
    private static boolean booted() {
        try { return "1".equals(Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, "sys.boot_completed")); }
        catch (Exception e) { return false; }
    }
    private static Config config(int uid) {
        int user = uid / 100000;
        Config result = configs.get(user);
        if (result != null && SystemClock.elapsedRealtime() - result.time < 1000) return result;
        Context c = getContext();
        if (c == null) return null;
        long token = Binder.clearCallingIdentity();
        try {
            Method getter = Settings.Secure.class.getMethod("getStringForUser", ContentResolver.class, String.class, int.class);
            result = new Config((String)getter.invoke(null, c.getContentResolver(), KEY, user));
            configs.put(user, result);
            return result;
        } catch (Exception e) { return null; }
        finally { Binder.restoreCallingIdentity(token); }
    }
    private static boolean contains(JSONObject json, String key, String value) {
        JSONArray array = json.optJSONArray(key);
        if (array != null) for (int i=0; i<array.length(); i++) if (value.equals(array.optString(i))) return true;
        return false;
    }
    private static String sourceName(int uid, Config cfg) {
        if (cfg == null || !cfg.json.optBoolean("enabled", false) || !appUid(uid)) return null;
        long token = Binder.clearCallingIdentity();
        try {
            String[] names = getContext().getPackageManager().getPackagesForUid(uid);
            if (names != null) for (String name : names) {
                if ("com.android.settings".equals(name) || "com.android.systemui".equals(name)) return null;
            }
            if (names != null && names.length > 0) return names[0];
        } catch (RuntimeException ignored) {}
        finally { Binder.restoreCallingIdentity(token); }
        return null;
    }
    private static String caller(int uid, Config cfg) {
        if (cfg == null || !cfg.json.optBoolean("enabled", false) || !appUid(uid)) return null;
        long token = Binder.clearCallingIdentity();
        try {
            String[] names = getContext().getPackageManager().getPackagesForUid(uid);
            if (names != null) for (String name : names) {
                if ("com.android.settings".equals(name) || "com.android.systemui".equals(name)) return null;
            }
            if (names != null) for (String name : names) if (contains(cfg.json, "targets", name)) return name;
        } catch (RuntimeException ignored) {}
        finally { Binder.restoreCallingIdentity(token); }
        return null;
    }
    private static boolean enter(int uid) {
        if (!appUid(uid) || Boolean.TRUE.equals(busy.get()) || !booted()) return false;
        busy.set(true);
        return true;
    }
    private static void event(Config c, String category, int uid, String detail) {
    }
    private static boolean hidden(Config c, int uid, String source, String target) {
        if (target == null || target.equals(source) || "android".equals(target) || "com.android.settings".equals(target) || "com.android.systemui".equals(target)) return false;
        long identity = Binder.clearCallingIdentity();
        try {
            String[] own = getContext().getPackageManager().getPackagesForUid(uid);
            if (own != null && Arrays.asList(own).contains(target)) return false;
        } finally { Binder.restoreCallingIdentity(identity); }
        String key = uid / 100000 + ":" + target;
        Facts f = facts.get(key);
        if (f == null) requestScan(target, uid / 100000);
        // Until the scan completes, fail open for Play services to avoid breaking dependencies.
        if (c.json.optBoolean("risky", true) && "com.google.android.gms".equals(source) && (f == null || f.risky)) return false;
        if (contains(c.json, "hidden", target)) return true;
        if (f != null) for (String preset : f.presets) if (contains(c.json, "apps", preset)) return true;
        return false;
    }
    private static String mapped(JSONObject json, String key, String name) {
        JSONObject map = json.optJSONObject(key);
        String value = map == null || name == null ? null : map.optString(name, null);
        return value == null || value.length() == 0 ? null : value;
    }
    private static String reflectString(Object object, String method) {
        try {
            Object value = object == null ? null : object.getClass().getMethod(method).invoke(object);
            return value instanceof String ? (String)value : null;
        } catch (Exception e) { return null; }
    }
    private static boolean unknownInstallSource(Object info) {
        return info != null && reflectString(info, "getInstallingPackageName") == null && reflectString(info, "getInitiatingPackageName") == null;
    }
    private static Object installSourceInfo(String store) {
        try {
            Class<?> info = Class.forName("android.content.pm.InstallSourceInfo");
            Class<?> signing = Class.forName("android.content.pm.SigningInfo");
            Constructor<?> ctor = info.getConstructor(String.class, signing, String.class, String.class, String.class, int.class);
            return ctor.newInstance(store, null, null, store, store, Integer.valueOf(2));
        } catch (Exception e) { return null; }
    }
    public static boolean shouldHide(Context c, String target, int uid) {
        if (c != null) context = c;
        if (!enter(uid)) return false;
        try {
            Config cfg = config(uid);
            String source = sourceName(uid, cfg);
            boolean hide = source != null && ((contains(cfg.json, "targets", source) && hidden(cfg, uid, source, target)) || (PLAY_STORE.equals(source) && contains(cfg.json, "playUpdate", target)));
            if (hide) event(cfg, "package", uid, source + " -> " + target);
            return hide;
        } catch (RuntimeException e) { return false; }
        finally { busy.remove(); }
    }
    public static boolean blockActivity(int uid, Intent intent, ActivityInfo info) {
        if (!enter(uid)) return false;
        try {
            Config cfg = config(uid);
            String source = caller(uid, cfg);
            if (source == null) return false;
            boolean protect = cfg.json.optBoolean("activity", true) ^ contains(cfg.json, "invertActivity", source);
            String target = info == null ? (intent == null || intent.getComponent() == null ? null : intent.getComponent().getPackageName()) : info.packageName;
            boolean hide = protect && hidden(cfg, uid, source, target);
            if (hide) event(cfg, "activity", uid, source + " -> " + target);
            return hide;
        } catch (RuntimeException e) { return false; }
        finally { busy.remove(); }
    }
    public static int[] restrictGids(int uid, int[] gids) {
        if (gids == null || !enter(uid)) return gids;
        try {
            Config cfg = config(uid);
            if (caller(uid, cfg) == null) return gids;
            int[] result = new int[gids.length];
            int size = 0;
            for (int gid : gids) {
                boolean remove = false;
                for (int allowed : GIDS) if (gid == allowed && contains(cfg.json, "gids", Integer.toString(gid))) remove = true;
                if (!remove) result[size++] = gid;
            }
            if (size == gids.length) return gids;
            event(cfg, "other", uid, "Restricted supplementary GIDs");
            return Arrays.copyOf(result, size);
        } catch (RuntimeException e) { return gids; }
        finally { busy.remove(); }
    }
    public static Object filterInstallSourceInfo(String target, int uid, Object result) {
        if (result == null || !enter(uid)) return result;
        try {
            Config cfg = config(uid);
            if (sourceName(uid, cfg) == null || !unknownInstallSource(result)) return result;
            String store = mapped(cfg.json, "installerSpoof", target);
            Object spoofed = store == null ? null : installSourceInfo(store);
            if (spoofed == null) return result;
            event(cfg, "package", uid, "installer " + target + " -> " + store);
            return spoofed;
        } catch (RuntimeException e) { return result; }
        finally { busy.remove(); }
    }
    public static String filterInstallerPackageName(String target, int uid, String result) {
        if (result != null || !enter(uid)) return result;
        try {
            Config cfg = config(uid);
            if (sourceName(uid, cfg) == null) return result;
            String store = mapped(cfg.json, "installerSpoof", target);
            if (store == null) return result;
            event(cfg, "package", uid, "installer " + target + " -> " + store);
            return store;
        } catch (RuntimeException e) { return result; }
        finally { busy.remove(); }
    }
    // A one-element array distinguishes a spoofed null from an unchanged setting.
    private static String[] replacement(Config cfg, String database, String name) {
        if (name == null) return null;
        if (contains(cfg.json, "settings", "dev_options")) {
            if ("global".equals(database)) {
                if (Arrays.asList("adb_enabled", "adb_wifi_enabled", "development_settings_enabled").contains(name)) return new String[]{"0"};
                if ("hidden_api_policy".equals(name)) return new String[]{null};
            }
            if ("secure".equals(database) && "mock_location".equals(name)) return new String[]{"0"};
        }
        if ("secure".equals(database)) {
            if (contains(cfg.json, "settings", "accessibility")) {
                if ("accessibility_enabled".equals(name)) return new String[]{"0"};
                if ("enabled_accessibility_services".equals(name)) return new String[]{""};
            }
            if (contains(cfg.json, "settings", "input_method")) {
                if ("default_input_method".equals(name)) return new String[]{"com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"};
                if ("tts_default_synth".equals(name)) return new String[]{"com.google.android.tts"};
            }
        }
        return null;
    }
    public static Bundle filterCall(ContentProvider provider, String method, String name, Bundle result) {
        if (provider == null || result == null || method == null || !(method.equals("GET_global") || method.equals("GET_secure"))) return result;
        if (!"com.android.providers.settings".equals(provider.getContext().getPackageName())) return result;
        context = provider.getContext();
        int uid = Binder.getCallingUid();
        if (!enter(uid)) return result;
        try {
            Config cfg = config(uid);
            if (caller(uid, cfg) == null) return result;
            String[] value = replacement(cfg, method.substring(4), name);
            if (value == null) return result;
            // Do not hand out a generation tracker: cached real settings would bypass per-caller policy.
            Bundle filtered = new Bundle();
            filtered.putString("value", value[0]);
            event(cfg, "settings", uid, name);
            return filtered;
        } catch (RuntimeException e) { return result; }
        finally { busy.remove(); }
    }
    public static Cursor filterQuery(ContentProvider provider, Uri uri, Cursor result) {
        if (result == null || uri == null || !"settings".equals(uri.getAuthority()) || uri.getPathSegments().isEmpty()) return result;
        context = provider.getContext();
        int uid = Binder.getCallingUid();
        if (!enter(uid)) return result;
        int position = result.getPosition();
        try {
            Config cfg = config(uid);
            if (caller(uid, cfg) == null) return result;
            String database = uri.getPathSegments().get(0);
            int nameCol = result.getColumnIndex("name"), valueCol = result.getColumnIndex("value");
            if (valueCol < 0) return result;
            String uriName = uri.getPathSegments().size() > 1 ? uri.getPathSegments().get(1) : null;
            if (nameCol < 0 && uriName == null) return result;
            MatrixCursor copy = new MatrixCursor(result.getColumnNames());
            result.moveToPosition(-1);
            boolean changed = false;
            while (result.moveToNext()) {
                Object[] row = new Object[result.getColumnCount()];
                for (int i=0; i<row.length; i++) row[i] = result.getString(i);
                String name = nameCol < 0 ? uriName : result.getString(nameCol);
                String[] value = replacement(cfg, database, name);
                if (value != null) { row[valueCol] = value[0]; changed = true; }
                copy.addRow(row);
            }
            if (!changed) { result.moveToPosition(position); copy.close(); return result; }
            result.close();
            event(cfg, "settings", uid, "query " + database);
            return copy;
        } catch (RuntimeException e) { result.moveToPosition(position); return result; }
        finally { busy.remove(); }
    }
    public static boolean hideServiceList(int uid, String preset) {
        if (!enter(uid)) return false;
        try {
            Config cfg = config(uid);
            boolean hide = caller(uid, cfg) != null && contains(cfg.json, "settings", preset);
            if (hide) event(cfg, "settings", uid, preset + " services");
            return hide;
        } catch (RuntimeException e) { return false; }
        finally { busy.remove(); }
    }
    public static List<?> filterAccessibility(int uid, List<?> original) {
        return hideServiceList(uid, "accessibility") ? Collections.emptyList() : original;
    }
    public static Object filterAccessibilitySlice(int uid, Object original) {
        if (!hideServiceList(uid, "accessibility") || original == null) return original;
        try { return original.getClass().getConstructor(List.class).newInstance(Collections.emptyList()); }
        catch (Exception e) { return original; }
    }
    public static List<?> filterInputMethods(int uid, List<?> original) {
        if (original == null || !hideServiceList(uid, "input_method")) return original;
        return Collections.singletonList(fakeInputMethod());
    }
    private static android.view.inputmethod.InputMethodInfo fakeInputMethod() {
        return new android.view.inputmethod.InputMethodInfo("com.google.android.inputmethod.latin", "com.android.inputmethod.latin.LatinIME", "Gboard", null);
    }
    public static android.view.inputmethod.InputMethodInfo filterCurrentInputMethod(int uid, android.view.inputmethod.InputMethodInfo original) {
        return hideServiceList(uid, "input_method") ? fakeInputMethod() : original;
    }
    public static android.view.inputmethod.InputMethodSubtype filterInputSubtype(int uid, android.view.inputmethod.InputMethodSubtype original) {
        return hideServiceList(uid, "input_method") ? null : original;
    }
    public static Object filterInputSubtypeList(int uid, Object original) {
        if (original == null || !hideServiceList(uid, "input_method")) return original;
        try { return original.getClass().getMethod("create", List.class).invoke(null, Collections.emptyList()); }
        catch (Exception e) { return original; }
    }
    private static void requestScan(final String name, final int user) {
        final String key = user + ":" + name;
        if (!pending.add(key)) return;
        scanner.execute(new Runnable() { public void run() {
            busy.set(true);
            try {
                Context c = getContext();
                if (user != 0) {
                    Object handle = Class.forName("android.os.UserHandle").getConstructor(int.class).newInstance(user);
                    c = (Context)Context.class.getMethod("createContextAsUser", handle.getClass(), int.class).invoke(c, handle, 0);
                }
                ApplicationInfo app = c.getPackageManager().getApplicationInfo(name, 0);
                facts.put(key, HmaPresets.scan(app));
            } catch (Exception ignored) { facts.remove(key); }
            finally { busy.remove(); pending.remove(key); }
        }});
    }
}
