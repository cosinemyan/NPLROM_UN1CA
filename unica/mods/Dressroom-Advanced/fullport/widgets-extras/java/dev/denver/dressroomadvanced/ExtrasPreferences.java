// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;

import org.json.JSONObject;

final class ExtrasPreferences {
    static final String[] FLAGS = {"keepCharging", "fullscreenArt", "keepStretch", "textCard"};

    private ExtrasPreferences() {}

    static Bundle clean(Bundle input) {
        Bundle b = new Bundle();
        if (input != null) ScreenIdentity.copy(input, b);
        for (String flag : FLAGS) b.putBoolean(flag, input != null && input.getBoolean(flag));
        b.putInt(
                "stackCards",
                Math.max(3, Math.min(8, input == null ? 3 : input.getInt("stackCards", 3))));
        b.putString("title", text(input == null ? null : input.getString("title"), 80));
        b.putString("message", text(input == null ? null : input.getString("message"), 180));
        return b;
    }

    static String text(String text, int limit) {
        if (text == null) return "";
        text = text.replace('\u0000', ' ').strip();
        return text.codePointCount(0, text.length()) <= limit
                ? text
                : text.substring(0, text.offsetByCodePoints(0, limit));
    }

    static Bundle read(Context context) {
        Bundle query = new Bundle();
        ScreenIdentity.stamp(query);
        Bundle result =
                NativeSettings.read(context, "extras", query);
        return clean(result);
    }

    static void commit(Context context, Bundle settings) {
        java.util.ArrayList<Bundle> screens = settings.getParcelableArrayList("screenProfiles", Bundle.class);
        if (screens != null) { for (Bundle screen : screens) commit(context, screen); return; }
        Bundle snapshot = clean(settings);
        ScreenIdentity.stamp(snapshot);
        if (!NativeSettings.write(context, "extras", snapshot)) throw new IllegalStateException("Extra settings were not saved");
    }

    static Bundle store(Context context, String method, Bundle input)
            throws org.json.JSONException {
        Bundle result = store(context.getSharedPreferences("committed_lockscreen_extras", 0), method, input);
        if (method.equals("commitExtras")) context.getContentResolver().notifyChange(SettingsProvider.URI, null);
        return result;
    }

    static Bundle store(android.content.SharedPreferences prefs, String method, Bundle input)
            throws org.json.JSONException {
        String key = ScreenIdentity.key("options", input == null ? new Bundle() : input);
        if (input != null && ScreenIdentity.value(input) != 0) {
            String old = prefs.getString("options", null);
            if (old != null) {
                android.content.SharedPreferences.Editor seed = prefs.edit();
                for (int screen : new int[] {ScreenIdentity.MAIN, ScreenIdentity.SUB}) {
                    Bundle scope = new Bundle();
                    scope.putInt(ScreenIdentity.FIELD, screen);
                    String sibling = ScreenIdentity.key("options", scope);
                    if (!prefs.contains(sibling)) seed.putString(sibling, old);
                }
                if (!seed.commit()) throw new IllegalStateException("Could not separate Extras settings");
            }
        }
        if (method.equals("commitExtras")) {
            Bundle b = clean(input);
            JSONObject json = new JSONObject();
            for (String flag : FLAGS) json.put(flag, b.getBoolean(flag));
            json.put("stackCards", b.getInt("stackCards", 3));
            json.put("title", b.getString("title"));
            json.put("message", b.getString("message"));
            if (!prefs.edit().putString(key, json.toString()).commit())
                throw new IllegalStateException("Could not save lockscreen extras");
            Bundle result = new Bundle();
            result.putBoolean("saved", true);
            return result;
        }
        JSONObject json = new JSONObject(prefs.getString(key, "{}"));
        Bundle b = new Bundle();
        if (input != null) ScreenIdentity.copy(input, b);
        for (String flag : FLAGS) b.putBoolean(flag, json.optBoolean(flag));
        b.putInt("stackCards", json.optInt("stackCards", 3));
        b.putString("title", json.optString("title"));
        b.putString("message", json.optString("message"));
        return clean(b);
    }
}
