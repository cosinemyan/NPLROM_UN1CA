package dev.denver.dressroomadvanced;

import android.graphics.Color;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class WallpaperBlurTone {
    private static final ClassValue<Native> readers =
            new ClassValue<>() {
                @Override
                protected Native computeValue(Class<?> manager) {
                    try {
                        return new Native(manager.getClassLoader());
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException(error);
                    }
                }
            };

    private record Native(Method singleton, Method hsv, Method brightness, Constructor<?> info) {
        Native(ClassLoader loader) throws ReflectiveOperationException {
            this(
                    OneUi9AodBlur.toneSampler(loader),
                    OneUi9AodBlur.brightness(loader),
                    OneUi9AodBlur.toneInfo(loader));
        }

        private Native(Class<?> sampler, Class<?> brightnessType, Class<?> infoType)
                throws ReflectiveOperationException {
            this(
                    NativeContract.findStaticMethod(sampler, "A", sampler),
                    NativeContract.findMethod(sampler, "C", float[].class, Object.class),
                    NativeContract.findStaticMethod(
                            brightnessType, "w", double.class, int.class),
                    infoType.getConstructor(double.class, float.class));
        }
    }

    static Object read(Class<?> managerType, View clock) throws ReflectiveOperationException {
        Native nativeApi = readers.get(managerType);
        int color;
        float saturation;
        if (Boolean.TRUE.equals(ClockGlassBinding.of(clock).fixedTone.get(clock))) {
            color = ((Number) Reflect.field(clock, "t")).intValue();
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            saturation = hsv[1];
        } else {
            float[] hsv =
                    (float[])
                            nativeApi.hsv.invoke(
                                    nativeApi.singleton.invoke(null), Reflect.field(clock, "s"));
            if (hsv == null) return null; // No sample yet: retain Samsung's original decision.
            color = Color.HSVToColor(hsv);
            saturation = hsv[1];
        }
        return nativeApi.info.newInstance(
                ((Number) nativeApi.brightness.invoke(null, color)).doubleValue(), saturation);
    }
}
