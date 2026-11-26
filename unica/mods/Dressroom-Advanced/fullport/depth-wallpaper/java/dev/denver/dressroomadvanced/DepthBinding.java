package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import java.lang.reflect.Modifier;

final class DepthBinding {
    private static final String SDK = "com.samsung.android.app.sdk.deepsky.objectcapture.";

    private DepthBinding() {}

    static void validateEditor(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> capture = Class.forName(SDK + "base.ObjectCapture", false, loader);
        Class<?> result = Class.forName(SDK + "base.ObjectResult", false, loader);
        Class<?> info = Class.forName(SDK + "base.ObjectInfo", false, loader);
        var factory =
                Class.forName(SDK + "ObjectCaptureProvider", false, loader)
                        .getDeclaredMethod("getInstance", Context.class);
        if (!Modifier.isStatic(factory.getModifiers()) || factory.getReturnType() != capture)
            throw new NoSuchMethodException("Object Capture factory changed");
        NativeContract.method(capture, "isObjectCaptureSupported", boolean.class);
        NativeContract.method(capture, "init", void.class);
        NativeContract.method(capture, "release", void.class);
        NativeContract.method(capture, "capture", result, Bitmap.class);
        NativeContract.method(result, "isCaptured", boolean.class);
        NativeContract.method(result, "getOutput", info);
        NativeContract.method(result, "getObjects", java.util.List.class);
        NativeContract.method(info, "getObjBitmap", Bitmap.class);
        NativeContract.method(info, "getBoundRect", Rect.class);
        Class<?> crop =
                Class.forName(
                        "com.samsung.android.app.dressroom.presentation.preview.widget.CropImageView",
                        false,
                        loader);
        NativeContract.method(crop, "getDrawableInner", Drawable.class);
        NativeContract.method(crop, "onDraw", void.class, android.graphics.Canvas.class);
    }

    static boolean provider(Object clock) {
        return providerIssue(clock) == null;
    }

    static String providerIssue(Object clock) {
        if (clock == null) return "No clock renderer is available";
        try {
            ClockBinding.of(clock);
            return null;
        } catch (Exception | LinkageError error) {
            return failureReason(error);
        }
    }

    static String failureReason(Throwable error) {
        for (int i = 0; i < 8 && error.getCause() != null; i++) error = error.getCause();
        String reason = error.getMessage();
        if (reason == null || reason.isBlank()) reason = error.getClass().getSimpleName();
        return reason.length() <= 220 ? reason : reason.substring(0, 220) + "...";
    }

    static Boolean isObjectCaptureSupported(Context context) {
        if (context == null) return null;
        try {
            ClassLoader loader = context.getClassLoader();
            // Factory support flags are hardware policy, not a probe of the fallback backend.
            return ObjectCaptureBackend.available(loader);
        } catch (Throwable error) {
            return false;
        }
    }
}
