package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

final class LayoutOrientation {
    static final String FIELD = "layoutOrientation";

    private LayoutOrientation() {}

    static int current(Context context) {
        return SettingsScope.orientation(context.getResources().getConfiguration().orientation);
    }

    static void stamp(Context context, Bundle data) {
        if (!data.containsKey(FIELD)) data.putInt(FIELD, current(context));
        ScreenIdentity.stamp(data);
    }

    static void copy(Bundle source, Bundle target) {
        ScreenIdentity.copy(source, target);
        if (source.containsKey(FIELD))
            target.putInt(FIELD, SettingsScope.orientation(source.getInt(FIELD)));
    }

    static String key(String base, Bundle data) {
        return ScreenIdentity.key(SettingsScope.oriented(base, data.getInt(FIELD)), data);
    }

    static String read(SharedPreferences prefs, String base, Bundle data) {
        if (ScreenIdentity.value(data) == 0) return readLegacy(prefs, base, data);
        String key = key(base, data);
        String saved = prefs.getString(key, null);
        if (saved != null) return saved;
        String legacy = readLegacy(prefs, base, data);
        if (legacy == null) return null;
        // Snapshot both siblings together so a later edit cannot become the other's seed.
        SharedPreferences.Editor edit = prefs.edit();
        for (int screen : new int[] {ScreenIdentity.MAIN, ScreenIdentity.SUB}) {
            Bundle scope = new Bundle(data);
            scope.putInt(ScreenIdentity.FIELD, screen);
            String sibling = key(base, scope);
            if (prefs.getString(sibling, null) != null) continue;
            try {
                org.json.JSONObject inherited = new org.json.JSONObject(legacy);
                inherited.put(ScreenIdentity.FIELD, screen);
                if (screen == ScreenIdentity.SUB) {
                    inherited.remove("depthMask");
                    inherited.remove("depthMaskRef");
                }
                edit.putString(sibling, inherited.toString());
            } catch (org.json.JSONException error) {
                throw new IllegalArgumentException("Invalid inherited screen profile", error);
            }
        }
        if (!edit.commit()) throw new IllegalStateException("Could not separate screen profiles");
        return prefs.getString(key, null);
    }

    static String readWallpaper(SharedPreferences prefs, String base, String legacyBase, Bundle data) {
        String saved = read(prefs, base, data);
        if (saved != null || base.equals(legacyBase) || ScreenIdentity.value(data) == 0) return saved;
        String marker = "display_wallpaper_imported_" + key(legacyBase, data);
        if (prefs.getBoolean(marker, false)) return null;
        String inherited = read(prefs, legacyBase, data);
        SharedPreferences.Editor edit = prefs.edit().putBoolean(marker, true);
        if (inherited != null) edit.putString(key(base, data), inherited);
        if (!edit.commit()) throw new IllegalStateException("Could not migrate display wallpaper settings");
        return inherited;
    }

    private static String readLegacy(SharedPreferences prefs, String base, Bundle data) {
        int orientation = SettingsScope.orientation(data.getInt(FIELD));
        String key = SettingsScope.oriented(base, orientation);
        String saved = prefs.getString(key, null);
        if (saved != null || orientation == 0) return saved;
        String old = prefs.getString(base, null);
        String owner = "layout_owner_" + base;
        if (old == null) return null;
        int originalOrientation = prefs.getInt(owner, orientation);
        if (originalOrientation != orientation) {
            try {
                org.json.JSONObject inherited = new org.json.JSONObject(old);
                inherited.remove("depthMask");
                inherited.remove("depthMaskRef");
                inherited.put(FIELD, orientation);
                old = inherited.toString();
            } catch (org.json.JSONException error) {
                throw new IllegalArgumentException("Invalid legacy layout profile", error);
            }
        }
        if (!prefs.edit().putString(key, old).putInt(owner, originalOrientation).commit())
            throw new IllegalStateException("Could not migrate layout settings");
        return old;
    }
}
