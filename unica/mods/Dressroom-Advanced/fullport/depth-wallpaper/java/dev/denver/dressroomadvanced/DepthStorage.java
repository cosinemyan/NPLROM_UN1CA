package dev.denver.dressroomadvanced;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import java.io.InputStream;
import java.io.OutputStream;

final class DepthStorage {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final android.util.LruCache<String, byte[]> cache =
            new android.util.LruCache<>(8 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, byte[] bytes) {
                    return bytes.length;
                }
            };

    static String hash(byte[] bytes) {
        try {
            return hex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
            text.append(Character.forDigit((b >>> 4) & 15, 16))
                    .append(Character.forDigit(b & 15, 16));
        return text.toString();
    }

    static Uri uri(String key) {
        if (key == null || !key.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Invalid mask reference");
        return SettingsProvider.URI.buildUpon().appendPath("masks").appendPath(key).build();
    }

    static Bundle pack(Context context, Bundle source) {
        Bundle packed = new Bundle(source);
        for (String field : DepthMetadata.BLOBS) {
            byte[] bytes = source.getByteArray(field);
            if (bytes == null) continue;
            if (bytes.length == 0 || bytes.length > MAX_BYTES)
                throw new IllegalArgumentException("Invalid subject mask size");
            String key = hash(bytes);
            try (OutputStream output =
                    context.getContentResolver().openOutputStream(uri(key), "w")) {
                if (output == null) throw new java.io.IOException("Mask storage unavailable");
                output.write(bytes);
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Could not save subject mask", error);
            }
            cache.put(key, bytes);
            packed.remove(field);
            packed.putString(field + "Ref", key);
        }
        return packed;
    }

    static Bundle unpack(Context context, Bundle packed) {
        if (packed == null) return null;
        Bundle value = new Bundle(packed);
        for (String field : DepthMetadata.BLOBS) {
            String key = value.getString(field + "Ref");
            if (key == null) continue;
            String caller = context.getOpPackageName();
            if (field.equals(DepthMetadata.SOURCE)
                    && (caller.equals("com.android.systemui")
                            || caller.equals("com.samsung.android.app.aodservice"))) continue;
            byte[] bytes = cache.get(key);
            if (bytes == null)
                try (InputStream input = context.getContentResolver().openInputStream(uri(key))) {
                    if (input == null) throw new java.io.IOException("Mask file unavailable");
                    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                    byte[] chunk = new byte[16384];
                    int count;
                    while ((count = input.read(chunk)) != -1) {
                        if (output.size() + count > MAX_BYTES)
                            throw new java.io.IOException("Mask file too large");
                        output.write(chunk, 0, count);
                    }
                    bytes = output.toByteArray();
                    if (!hash(bytes).equals(key))
                        throw new java.io.IOException("Incomplete subject mask");
                    cache.put(key, bytes);
                } catch (java.io.IOException error) {
                    throw new IllegalStateException("Could not load subject mask", error);
                }
            value.putByteArray(field, bytes);
        }
        return value;
    }
}
