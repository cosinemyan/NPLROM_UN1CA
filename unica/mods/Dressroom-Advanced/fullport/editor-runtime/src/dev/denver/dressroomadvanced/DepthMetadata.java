package dev.denver.dressroomadvanced;

import android.os.Bundle;

import org.json.JSONObject;

final class DepthMetadata {
    static final String SOURCE = "depthSourceMask", FINGERPRINT = "depthSourceFingerprint";
    static final String[] BLOBS = {"depthMask", SOURCE};

    static void copy(Bundle from, Bundle to) {
        if (from == null) return;
        for (String field :
                new String[] {
                    SOURCE,
                    SOURCE + "Ref",
                    "depthSourceFingerprint",
                    "depthSourceVersion",
                    "depthImageKey"
                }) {
            Object value = from.get(field);
            if (value instanceof byte[] bytes) to.putByteArray(field, bytes.clone());
            else if (value instanceof String text) to.putString(field, text);
            else if (value instanceof Integer number) to.putInt(field, number);
        }
    }

    static void write(JSONObject json, Bundle data) throws org.json.JSONException {
        if (data.containsKey("depthImageKey"))
            json.put("depthImageKey", data.getString("depthImageKey", ""));
        for (String field : BLOBS) {
            String reference = data.getString(field + "Ref");
            byte[] bytes = data.getByteArray(field);
            if (reference != null) json.put(field + "Ref", reference);
            else if (bytes != null && bytes.length > 0)
                json.put(
                        field,
                        android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP));
        }
        if (data.getInt("depthSourceVersion") == 2) {
            json.put("depthSourceVersion", 2);
            json.put(FINGERPRINT, data.getString(FINGERPRINT, ""));
        }
    }

    static void read(JSONObject json, Bundle data) throws org.json.JSONException {
        if (json.has("depthImageKey"))
            data.putString("depthImageKey", json.getString("depthImageKey"));
        for (String field : BLOBS) {
            String reference = json.optString(field + "Ref", "");
            if (!reference.isEmpty()) data.putString(field + "Ref", reference);
            else {
                String encoded = json.optString(field, "");
                if (!encoded.isEmpty())
                    data.putByteArray(
                            field,
                            android.util.Base64.decode(encoded, android.util.Base64.DEFAULT));
            }
        }
        if (json.optInt("depthSourceVersion") == 2) {
            data.putInt("depthSourceVersion", 2);
            data.putString(FINGERPRINT, json.optString(FINGERPRINT, ""));
        }
    }
}
