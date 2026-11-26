package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class RuntimeClockData {
    private record Entry(int type, int font, String path, int orientation, int screen, List<Float> heights) {}

    private static final Map<Object, Entry> sources =
            java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private RuntimeClockData() {}

    public static void invalidate() {
        sources.clear();
    }

    static List<Float> manualHeights(Bundle settings) {
        return settings != null
                        && FontAxisSettings.nativeStretch(settings)
                        && (settings.getBoolean("manual") || settings.getBoolean("depthEnabled"))
                        && settings.getFloatArray("manualHeights") != null
                ? SavedStretch.heights(settings.getFloatArray("manualHeights"))
                : null;
    }

    private static List<Float> heights(Object provider, Object font)
            throws ReflectiveOperationException {
        Context context = (Context) Reflect.field(provider, "context");
        try {
            if (!ScreenIdentity.supported() && context.getDisplay() != null && context.getDisplay().getDisplayId() != 0)
                return null;
        } catch (UnsupportedOperationException ignored) {
        } // Package contexts may not carry a Display.

        int type = ((Number) Reflect.call(provider, "getConsideredThemeClockType")).intValue();
        int id = ((Number) Reflect.call(font, "getFontType")).intValue();
        if (type <= 0 || id < 0 || !NativeClockRuntime.nativeStretchFont(font, id)) return null;
        String path = "";
        try {
            Object rawPath = Reflect.call(font, "getFontPath");
            if (rawPath instanceof String) path = (String) rawPath;
        } catch (Exception ignored) {
        }
        int orientation = LayoutOrientation.current(context);
        int screen = ScreenIdentity.current();
        Entry entry = sources.get(provider);
        if (entry == null
                || entry.type != type
                || entry.font != id
                || !entry.path.equals(path)
                || entry.orientation != orientation || entry.screen != screen) {
            Bundle query = new Bundle();
            query.putInt("type", type);
            query.putInt("font", id);
            if (!path.isEmpty()) query.putString("fontPath", path);
            Bundle settings = SettingsBridge.read(context, query);
            if (settings == null) return null;
            List<Float> values = StretchRestorePolicy.eligible(true, id,
                    settings.getInt("font", -1), path, settings.getString("fontPath", ""),
                    !FontAxisSettings.nativeStretch(settings)) ? manualHeights(settings) : null;
            entry = new Entry(type, id, path, orientation, screen, values);
            sources.put(provider, entry);
        }
        return entry.heights;
    }


    public static Object source(Object provider,Object font) {
        if(font==null) return null;
        try { return SavedStretch.font(font,heights(provider,font)); } catch(Exception ignored) { return font; }
    }
    public static java.util.List<?> sourceHeights(Object provider,java.util.List<?> original) {
        try { List<Float> saved=heights(provider,Reflect.call(provider,"getClockFontData")); return saved==null?original:saved; }
        catch(Exception ignored) { return original; }
    }
}
