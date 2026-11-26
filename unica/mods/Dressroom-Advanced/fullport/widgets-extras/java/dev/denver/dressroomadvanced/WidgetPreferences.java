// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;

import org.json.JSONObject;

final class WidgetPreferences {
    private WidgetPreferences() {}

    static String layoutKey(Bundle value) {
        return value.getBoolean("layout")
                + ":"
                + value.getInt("columns")
                + ":"
                + value.getInt("rows")
                + ":"
                + value.getBoolean("vertical")
                + ":"
                + value.getFloat("scale", 1);
    }

    static Bundle defaults() {
        Bundle b = new Bundle();
        b.putInt("schema", 1);
        b.putInt("columns", 4);
        b.putInt("rows", 1);
        b.putFloat("scale", 1);
        b.putFloat("freeX", .5f);
        b.putFloat("freeY", .4f);
        b.putFloat("gap", 8);
        return b;
    }

    static Bundle clean(Bundle input) {
        Bundle b = defaults();
        LayoutOrientation.copy(input, b);
        if (input.containsKey("wallpaperId")) b.putInt("wallpaperId", input.getInt("wallpaperId"));
        b.putBoolean("layout", input.getBoolean("layout"));
        b.putInt("columns", WidgetLayout.clamp(input.getInt("columns", 4), 1, 8));
        b.putInt(
                "rows",
                WidgetLayout.clamp(
                        input.getInt("rows", 1), 1, Math.min(8, 32 / b.getInt("columns"))));
        b.putFloat("scale", WidgetLayout.finite(input.getFloat("scale", 1), 1, .5f, 2));
        b.putInt("anchor", WidgetLayout.clamp(input.getInt("anchor"), 0, 5));
        b.putFloat("freeX", WidgetLayout.finite(input.getFloat("freeX", .5f), .5f, 0, 1));
        b.putFloat("freeY", WidgetLayout.finite(input.getFloat("freeY", .4f), .4f, 0, 1));
        b.putFloat("gap", WidgetLayout.finite(input.getFloat("gap", 8), 8, 0, 64));
        b.putFloat("dockOffset", WidgetLayout.finite(input.getFloat("dockOffset"), 0, -2000, 2000));
        b.putInt("depth", WidgetLayout.clamp(input.getInt("depth"), 0, 2)); // inherit / on / off
        b.putInt("depthLayouts", WidgetLayout.clamp(input.getInt("depthLayouts"), 0, 3));
        b.putBoolean("blurOff", input.getBoolean("blurOff"));
        b.putBoolean("vertical", input.getBoolean("vertical"));
        b.putString("scene", input.getString("scene", ""));
        byte[] mask = input.getByteArray("depthMask");
        if (mask != null && mask.length > 0) b.putByteArray("depthMask", mask.clone());
        if (input.containsKey("depthMaskRef"))
            b.putString("depthMaskRef", input.getString("depthMaskRef"));
        DepthMetadata.copy(input, b);
        return b;
    }

    static Bundle read(Context context, String scene) {
        return read(context, scene, LayoutOrientation.current(context));
    }

    static Bundle read(Context context, String scene, int orientation) {
        Bundle query = new Bundle();
        query.putString("scene", scene);
        query.putInt(LayoutOrientation.FIELD, orientation);
        LayoutOrientation.stamp(context, query);
        query.putInt("wallpaperId", SettingsBridge.wallpaperId(context, ScreenIdentity.value(query)));
        query.putInt("legacyWallpaperId", SettingsBridge.wallpaperId(context, 0));
        if (query.getInt("wallpaperId", -1) < 0) return defaults();
        Bundle saved = NativeSettings.read(context, "widgets", query);
        Bundle result = saved == null ? defaults() : clean(DepthStorage.unpack(context, saved));
        LayoutOrientation.copy(query, result);
        result.putInt("wallpaperId", query.getInt("wallpaperId", -1));
        return result;
    }

    static void commit(Context context, Bundle snapshot) {
        java.util.ArrayList<Bundle> layouts =
                snapshot.getParcelableArrayList("layoutProfiles", Bundle.class);
        if (layouts != null) {
            for (Bundle layout : layouts) commit(context, layout);
            return;
        }
        Bundle b = clean(snapshot);
        LayoutOrientation.stamp(context, b);
        b = SettingsBridge.committedIdentity(b, screen -> SettingsBridge.wallpaperId(context, screen));
        if (!NativeSettings.write(context, "widgets", DepthStorage.pack(context, b))) throw new IllegalStateException("Widget settings were not saved");
    }

    private static void seedDepthPolicies(android.content.SharedPreferences prefs, String base, Bundle args) {
        if (ScreenIdentity.value(args) != 0 && !prefs.getBoolean(base + "_screen_depth_seeded", false)) {
            String inherited = prefs.getString(base + "_depth_policy", null);
            android.content.SharedPreferences.Editor seed = prefs.edit();
            for (int screen : new int[] {ScreenIdentity.MAIN, ScreenIdentity.SUB}) {
                Bundle scope = new Bundle();
                scope.putInt(ScreenIdentity.FIELD, screen);
                String sibling = ScreenIdentity.key(base, scope) + "_depth_policy";
                if (inherited != null && prefs.getString(sibling, null) == null)
                    seed.putString(sibling, inherited);
            }
            if (!seed.putBoolean(base + "_screen_depth_seeded", true).commit())
                throw new IllegalStateException("Could not separate widget depth policies");
        }
    }

    static Bundle store(Context context, String method, Bundle args) throws org.json.JSONException {
        return store(context.getSharedPreferences("committed_widget_settings", 0), method, args,
                () -> context.getContentResolver().notifyChange(SettingsProvider.URI, null));
    }

    static Bundle store(android.content.SharedPreferences prefs, String method, Bundle args,
            Runnable changed) throws org.json.JSONException {
        int wallpaper = args == null ? -1 : args.getInt("wallpaperId", -1);
        if (wallpaper < 0) throw new IllegalArgumentException("Missing wallpaper identity");
        String base = "wallpaper_" + wallpaper + "_main";
        String key = LayoutOrientation.key(base, args);
        String policyKey = ScreenIdentity.key(base, args) + "_depth_policy";
        seedDepthPolicies(prefs, base, args);
        if (method.equals("commitWidgets")) {
            Bundle b = clean(args);
            JSONObject json = new JSONObject();
            for (String field : b.keySet()) {
                Object value = b.get(field);
                if (value instanceof byte[] bytes)
                    value = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                json.put(field, value);
            }
            android.content.SharedPreferences.Editor edit =
                    prefs.edit().putString(key, json.toString()).putBoolean(policyKey + "_imported", true);
            if (b.getInt("depthLayouts") == 0) edit.remove(policyKey);
            else
                edit.putString(
                        policyKey,
                        new JSONObject()
                                .put("depth", b.getInt("depth"))
                                .put("depthLayouts", b.getInt("depthLayouts"))
                                .toString());
            if (!edit.commit()) throw new IllegalStateException("Widget persistence failed");
            changed.run();
            Bundle result = new Bundle();
            result.putBoolean("saved", true);
            return result;
        }
        int oldWallpaper = args.getInt("legacyWallpaperId", wallpaper);
        String legacyBase = oldWallpaper >= 0 ? "wallpaper_" + oldWallpaper + "_main" : base;
        String saved = LayoutOrientation.readWallpaper(prefs, base, legacyBase, args);
        if (saved != null && oldWallpaper != wallpaper && !prefs.getBoolean(policyKey + "_imported", false) && prefs.getString(policyKey, null) == null) {
            seedDepthPolicies(prefs, legacyBase, args);
            String oldPolicy = prefs.getString(ScreenIdentity.key(legacyBase, args) + "_depth_policy", null);
            if (!prefs.edit().putBoolean(policyKey + "_imported", true).putString(policyKey, oldPolicy).commit())
                throw new IllegalStateException("Could not migrate display wallpaper depth policy");
        }
        String policy = prefs.getString(policyKey, null);
        if (saved == null && policy == null) return null;
        JSONObject json = saved == null ? new JSONObject() : new JSONObject(saved);
        if (!SettingsScope.sceneMatches(args.getString("scene", ""), json.optString("scene", "")))
            return null;
        Bundle b = defaults();
        LayoutOrientation.copy(args, b);
        for (String field :
                new String[] {"schema", "columns", "rows", "anchor", "depth", "depthLayouts"})
            b.putInt(field, json.optInt(field, b.getInt(field)));
        if (policy != null) {
            JSONObject shared = new JSONObject(policy);
            b.putInt("depth", shared.getInt("depth"));
            b.putInt("depthLayouts", shared.getInt("depthLayouts"));
        } else b.putInt("depthLayouts", 0);
        for (String field : new String[] {"scale", "freeX", "freeY", "gap", "dockOffset"})
            b.putFloat(field, (float) json.optDouble(field, b.getFloat(field)));
        for (String field : new String[] {"layout", "vertical", "blurOff"})
            b.putBoolean(field, json.optBoolean(field));
        b.putString("scene", json.optString("scene", ""));
        DepthMetadata.read(json, b);
        return clean(b);
    }

    static boolean depth(Bundle widgets, Bundle clock) {
        int mode = widgets.getInt("depth");
        return covers(widgets)
                && (mode == 1 || (mode == 0 && clock != null && clock.getBoolean("depthEnabled")));
    }

    static boolean covers(Bundle widgets) {
        int layouts = widgets.getInt("depthLayouts"),
                orientation = widgets.getInt(LayoutOrientation.FIELD);
        return layouts == 0 || (orientation != 0 && (layouts & orientation) != 0);
    }

    static byte[] mask(Bundle widgets, Bundle clock) {
        if (widgets.getInt("depth") == 0
                && clock != null
                && clock.getByteArray("depthMask") != null) return clock.getByteArray("depthMask");
        byte[] mask = widgets.getByteArray("depthMask");
        return mask != null ? mask : clock == null ? null : clock.getByteArray("depthMask");
    }
}
