// Native adaptation of BlueOutside / Dressroom Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class OneUi9AodBlur {
    static final String STRATEGY = "aod.tx0",
            OPTIONS = "aod.xb0",
            HELPER = "aod.e70",
            MANAGER = "aod.cq0",
            GLYPH_RUNNABLE = "aod.yp0",
            TONE_SAMPLER = "aod.n16",
            BRIGHTNESS = "aod.rv2",
            TONE_INFO = "aod.l49",
            WIDGET_SIZING = "aod.v99";

    private OneUi9AodBlur() {}

    static ClockBlurBinding resolve(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> weather =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.WeatherInfo", false, loader);
        Class<?> builder = Class.forName("android.view.SemBlurInfo$Builder");
        Class<?> strategy = strategy(loader, weather);
        Class<?> options = options(loader, strategy, weather);
        Class<?> helper =
                NativeContract.findClass(
                        loader,
                        type -> NativeContract.hasStaticMethod(type, void.class, builder, Path.class),
                        HELPER, "aod.x60");
        return new ClockBlurBinding(strategy, options, weather, helper, "a", "o");
    }

    static Class<?> strategy(ClassLoader loader, Class<?> weather)
            throws ReflectiveOperationException {
        return NativeContract.findClass(
                loader,
                type ->
                        NativeContract.hasMethod(type, PointF.class, ViewGroup.class)
                                && NativeContract.hasMethod(
                                        type,
                                        Path.class,
                                        ImageView.class,
                                        weather,
                                        float.class,
                                        ViewGroup.class),
                STRATEGY, "aod.jx0");
    }

    static Class<?> options(ClassLoader loader, Class<?> strategy, Class<?> weather)
            throws ReflectiveOperationException {
        for (Method method : strategy.getDeclaredMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 3 && params[0] == ImageView.class && params[1] == weather) {
                return params[2];
            }
        }
        return NativeContract.findClass(
                loader,
                type ->
                        NativeContract.hasConstructor(
                                type, double.class, float.class, boolean.class, float.class),
                OPTIONS);
    }

    static Class<?> manager(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> weather =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.WeatherInfo", false, loader);
        return NativeContract.findClass(
                loader,
                type ->
                        NativeContract.hasMethod(
                                        type, void.class, ArrayList.class, int.class, float.class)
                                && NativeContract.hasField(type, weather),
                MANAGER, "aod.sp0");
    }

    static Class<?> glyphRunnable(ClassLoader loader, Class<?> manager)
            throws ReflectiveOperationException {
        return NativeContract.findClass(
                loader,
                type ->
                        Runnable.class.isAssignableFrom(type)
                                && NativeContract.hasConstructor(
                                        type, manager, int.class, int.class, boolean.class),
                GLYPH_RUNNABLE, "aod.op0");
    }

    static List<Method> submissions(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> utils =
                NativeContract.findClass(
                        loader,
                        type ->
                                NativeContract.hasStaticMethod(
                                                type,
                                                void.class,
                                                View.class,
                                                float.class,
                                                float[].class,
                                                Integer.class)
                                        && NativeContract.hasStaticMethod(
                                                type,
                                                void.class,
                                                View.class,
                                                int.class,
                                                float.class,
                                                Integer.class,
                                                String.class),
                        HELPER, "aod.x60");
        return List.of(
                NativeContract.findStaticMethod(
                        utils, "c", void.class, View.class, float.class, float[].class, Integer.class),
                NativeContract.findStaticMethod(
                        utils,
                        "d",
                        void.class,
                        View.class,
                        int.class,
                        float.class,
                        Integer.class,
                        String.class));
    }

    static Class<?> toneSampler(ClassLoader loader) throws ReflectiveOperationException {
        return NativeContract.findClass(
                loader,
                type ->
                        NativeContract.hasStaticMethod(type, type)
                                && NativeContract.hasMethod(type, float[].class, Object.class)
                                && NativeContract.hasMethod(type, int.class, Object.class),
                TONE_SAMPLER, "aod.i16");
    }

    static Class<?> brightness(ClassLoader loader) throws ReflectiveOperationException {
        return NativeContract.findClass(
                loader,
                type ->
                        NativeContract.hasStaticMethod(type, double.class, int.class)
                                && NativeContract.hasStaticMethod(
                                        type, Shader.TileMode.class, int.class),
                BRIGHTNESS, "aod.ev2");
    }

    static Class<?> toneInfo(ClassLoader loader) throws ReflectiveOperationException {
        return NativeContract.findClass(
                loader,
                type ->
                        NativeContract.hasConstructor(type, double.class, float.class)
                                && NativeContract.hasField(type, double.class)
                                && NativeContract.hasField(type, float.class),
                TONE_INFO, "aod.q39");
    }

    static Method widgetSizing(ClassLoader loader, Class<?> spec)
            throws ReflectiveOperationException {
        Class<?> sizing =
                NativeContract.findClass(
                        loader,
                        type ->
                                NativeContract.hasStaticMethod(
                                        type,
                                        spec,
                                        android.content.Context.class,
                                        int.class,
                                        int.class,
                                        int.class,
                                        int.class,
                                        boolean.class),
                        WIDGET_SIZING, "aod.x89");
        return NativeContract.findStaticMethod(
                sizing,
                "a",
                spec,
                android.content.Context.class,
                int.class,
                int.class,
                int.class,
                int.class,
                boolean.class);
    }
}
