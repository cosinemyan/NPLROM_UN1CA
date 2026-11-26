package dev.denver.dressroomadvanced;

import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

final class EditorWallpaperSource {
    private static final Map<View, WeakReference<View>> weather = new WeakHashMap<>();

    static void bind(View crop, View surface) {
        if (crop != null && surface != null) weather.put(crop, new WeakReference<>(surface));
    }

    static boolean available(View crop) {
        if (crop == null) return false;
        if (crop.isShown()) return true;
        WeakReference<View> reference = weather.get(crop);
        View surface = reference == null ? null : reference.get();
        return surface != null
                && surface.isShown()
                && crop.isAttachedToWindow()
                && crop.getWindowToken() == surface.getWindowToken();
    }

    static void reset() {
        weather.clear();
    }
}
