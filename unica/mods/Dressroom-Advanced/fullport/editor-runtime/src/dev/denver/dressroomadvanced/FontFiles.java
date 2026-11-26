package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.UUID;

final class FontFiles {
    static final int LIMIT = 32 * 1024 * 1024;

    private FontFiles() {}

    private static SharedPreferences tickets(Context context) {
        return context.getSharedPreferences("font_picker", Context.MODE_PRIVATE);
    }

    static synchronized Bundle begin(Context context) {
        String token = UUID.randomUUID().toString();
        if (!tickets(context)
                .edit()
                .clear()
                .putString("token", token)
                .putLong("expires", System.currentTimeMillis() + 30 * 60_000L)
                .commit()) throw new IllegalStateException("Could not open font picker");
        Bundle result = new Bundle();
        result.putString("token", token);
        return result;
    }

    static synchronized boolean valid(Context context, String token) {
        SharedPreferences prefs = tickets(context);
        return token != null
                && token.equals(prefs.getString("token", null))
                && System.currentTimeMillis() < prefs.getLong("expires", 0)
                && !prefs.getBoolean("done", false);
    }

    static Uri resultUri(String token) {
        return SettingsProvider.URI.buildUpon().appendPath("font-pick").appendPath(token).build();
    }

    static synchronized void complete(Context context, String token, Bundle result) {
        if (!valid(context, token)) return;
        SharedPreferences.Editor edit = tickets(context).edit().putBoolean("done", true);
        for (String key : new String[] {"fontFile", "fontFileName", "error"})
            edit.putString(key, result.getString(key, ""));
        if (!edit.commit()) return;
        context.getContentResolver().notifyChange(resultUri(token), null);
    }

    static synchronized Bundle result(Context context, String token) {
        SharedPreferences prefs = tickets(context);
        Bundle result = new Bundle();
        if (token == null || !token.equals(prefs.getString("token", null))) return result;
        result.putBoolean("done", prefs.getBoolean("done", false));
        for (String key : new String[] {"fontFile", "fontFileName", "error"})
            result.putString(key, prefs.getString(key, ""));
        return result;
    }

    static Uri uri(String id) {
        if (id == null || !id.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid font ID");
        return SettingsProvider.URI.buildUpon().appendPath("fonts").appendPath(id).build();
    }

    static File file(Context context, String id) {
        uri(id);
        return new File(new File(context.getFilesDir(), "clock-fonts"), id);
    }

    static Bundle importFont(Context context, Uri uri) throws Exception {
        File directory = new File(context.getFilesDir(), "clock-fonts");
        if (!directory.isDirectory() && !directory.mkdirs())
            throw new IOException("Could not store font");
        File temporary = File.createTempFile("import-", ".tmp", directory);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int size = 0;
            try (InputStream input = context.getContentResolver().openInputStream(uri);
                    FileOutputStream output = new FileOutputStream(temporary)) {
                if (input == null) throw new IOException("Could not read font");
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (size > LIMIT - count) throw new IOException("Font exceeds 32 MB");
                    size += count;
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            if (size < 12) throw new IOException("Not a font file");
            try (java.io.DataInputStream input =
                    new java.io.DataInputStream(new java.io.FileInputStream(temporary))) {
                int magic = input.readInt();
                if (magic != 0x00010000
                        && magic != 0x4f54544f
                        && magic != 0x74746366
                        && magic != 0x74727565)
                    throw new IOException("Choose a TTF, OTF or TTC font");
            }
            if (new Typeface.Builder(temporary).build() == null)
                throw new IOException("Android could not load this font");
            String id = java.util.HexFormat.of().formatHex(digest.digest());
            File destination = file(context, id);
            if (!destination.isFile() && !temporary.renameTo(destination))
                throw new IOException("Could not import font");
            String name = "Custom font";
            try (Cursor cursor =
                    context.getContentResolver()
                            .query(
                                    uri,
                                    new String[] {OpenableColumns.DISPLAY_NAME},
                                    null,
                                    null,
                                    null)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0))
                    name = cursor.getString(0);
            } catch (RuntimeException ignored) {
            }
            name = name.replaceAll("[\\p{Cntrl}]", " ").strip();
            if (name.length() > 100) name = name.substring(0, 100);
            Bundle result = new Bundle();
            result.putString("fontFile", id);
            result.putString("fontFileName", name);
            return result;
        } finally {
            temporary.delete();
        }
    }
}
