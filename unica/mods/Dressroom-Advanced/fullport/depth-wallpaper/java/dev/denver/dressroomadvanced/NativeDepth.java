package dev.denver.dressroomadvanced;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

/** Explicit calls from patched APKs; there is no runtime method interception. */
public final class NativeDepth {
    private NativeDepth() {}

    public static int beforeDraw(View view, Canvas canvas) {
        try { return DepthEffects.beforeDraw(view, canvas); }
        catch (RuntimeException failure) {
            android.util.Log.w("Dressroom-Advanced", "Depth draw setup unavailable", failure);
            return -1;
        }
    }

    public static void afterDraw(View view, Canvas canvas, int saved) {
        DepthEffects.afterDraw(view, canvas, saved);
    }

    public static void editorWallpaperDraw(View image) { DepthEffects.editorWallpaperDraw(image); }
    public static void weatherSource(View surface) {
        surface.post(() -> DepthEffects.weatherSource(surface));
    }

    /** Posted after Samsung's weather editor configures/replaces its surface. */
    public static void weatherOwner(Object owner) {
        try {
            Object candidate = Reflect.field(owner, "d");
            if (!(candidate instanceof View image)) return;
            image.post(() -> {
                try {
                    Object surface = Reflect.field(owner, "l");
                    if (surface instanceof View layer) {
                        EditorWallpaperSource.bind(image, layer);
                        DepthEffects.weatherSource(layer);
                    }
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    android.util.Log.w("Dressroom-Advanced", "Weather subject binding unavailable", failure);
                }
            });
        } catch (ReflectiveOperationException | RuntimeException failure) {
            android.util.Log.w("Dressroom-Advanced", "Weather source unavailable", failure);
        }
    }

    public static void apply(View clock, Bundle profile, boolean editor) {
        boolean enabled = profile != null && profile.getBoolean("depthEnabled", false);
        byte[] mask = enabled ? profile.getByteArray("depthMask") : null;
        if (editor) {
            DepthEffects.applyEditor(clock, enabled, mask);
            DepthEffects.setEditorState(clock, enabled, mask);
        } else DepthEffects.apply(clock, enabled, mask);
    }

    /** Replaces only the native combined-clock blur path before Samsung submits it. */
    public static Path blurPath(Object strategy, Path original) {
        if (original == null || strategy == null) return original;
        try {
            Object owner = Reflect.field(strategy, "b");
            Object candidate = Reflect.field(owner, "o");
            if (!(candidate instanceof View clock)) return original;
            Object host = Reflect.call(strategy, "d");
            Object point = host instanceof ViewGroup ? Reflect.call(strategy, "e", host) : null;
            if (!(point instanceof PointF origin)) return original;
            MediaFullscreenExtras.clockPath(clock, origin.x, origin.y, original);
            Path subject = DepthEffects.nativeSubjectPath(clock, origin.x, origin.y);
            return subject == null ? original : DepthGeometry.subtract(original, subject);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            android.util.Log.w("Dressroom-Advanced", "Native blur depth unavailable", failure);
            return original;
        }
    }

    /** Called at native clearBlur so media never retains a retired clock silhouette. */
    public static void clearBlur(Object strategy) {
        try {
            Object owner = Reflect.field(strategy, "b");
            Object candidate = Reflect.field(owner, "o");
            if (candidate instanceof View clock) MediaFullscreenExtras.clockPath(clock, 0, 0, null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {}
    }

    /** Typed SemBlurInfo bridge is emitted at each APK's callsite by the patch generator. */
    public static Object widgetBlur(View background, Object original) {
        return WidgetDepthBlur.submission(background, original);
    }
}
