package dev.denver.dressroomadvanced;

import android.graphics.Path;
import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Method;
import java.util.List;

final class OneUi85AodBlur {
    static final String STRATEGY = "aod.Lv",
            OPTIONS = "aod.dl",
            CLIP_HELPER = "aod.rF",
            SUBMIT_HELPER = "aod.yi";

    private OneUi85AodBlur() {}

    static ClockBlurBinding resolve(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> weather =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.WeatherInfo", false, loader);
        Class<?> aod8936 =
                NativeContract.knownClass(
                        loader,
                        type ->
                                NativeContract.hasMethod(type, PointF.class, ViewGroup.class)
                                        && NativeContract.hasMethod(
                                                type, void.class, int.class, ImageView.class),
                        "aod.Nv");
        if (aod8936 != null) {
            Class<?> options = OneUi9AodBlur.options(loader, aod8936, weather);
            Class<?> helper = clipHelper(loader, aod8936, weather, options, "aod.i20");
            // Nv.b -> Cy0.a is the clock; Nv.b() submits its path through i20.c().
            return new ClockBlurBinding(aod8936, options, weather, helper, null, "a");
        }
        Class<?> strategy =
                NativeContract.findClass(
                        loader,
                        type ->
                                NativeContract.hasMethod(type, PointF.class, ViewGroup.class)
                                        && NativeContract.hasMethod(
                                                type, void.class, int.class, ImageView.class),
                        STRATEGY);
        Class<?> options = OneUi9AodBlur.options(loader, strategy, weather);
        Class<?> helper = clipHelper(loader, strategy, weather, options, CLIP_HELPER);
        // Lv.b points at a synthetic holder; that holder's b is the AbsClockView.
        return new ClockBlurBinding(strategy, options, weather, helper, null, "b");
    }

    private static Class<?> clipHelper(
            ClassLoader loader, Class<?> strategy, Class<?> weather, Class<?> options, String hint)
            throws ReflectiveOperationException {
        Class<?> builder = Class.forName("android.view.SemBlurInfo$Builder");
        Class<?> known =
                NativeContract.knownClass(
                        loader,
                        type ->
                                NativeContract.hasStaticMethod(
                                        type, void.class, builder, Path.class),
                        hint);
        if (known != null) return known;
        Method render =
                NativeContract.findMethod(strategy, null, null, ImageView.class, weather, options);
        java.util.Set<Class<?>> matches = new java.util.LinkedHashSet<>();
        try {
            for (DexSignatures.Call call : DexSignatures.body(render).calls())
                if (call.isStatic()
                        && call.result().equals("V")
                        && call.parameters()
                                .equals(
                                        List.of(
                                                "Landroid/view/SemBlurInfo$Builder;",
                                                "Landroid/graphics/Path;")))
                    matches.add(call.method(loader).getDeclaringClass());
        } catch (java.io.IOException invalidDex) {
            throw new ReflectiveOperationException(
                    "Could not read clock blur call site", invalidDex);
        }
        if (matches.size() != 1)
            throw new ClassNotFoundException(
                    "Ambiguous clock blur clip call site: " + matches.size());
        return matches.iterator().next();
    }

    static List<Method> submissions(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> info = Class.forName("android.view.SemBlurInfo");
        Class<?> clipHelper = resolve(loader).clip.getDeclaringClass();
        Class<?> helper =
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
                        SUBMIT_HELPER);
        return List.of(
                NativeContract.findStaticMethod(
                        clipHelper, null, void.class, View.class, info, String.class),
                NativeContract.findStaticMethod(
                        helper, "b", void.class, View.class, info, String.class),
                NativeContract.findStaticMethod(
                        helper,
                        "d",
                        void.class,
                        View.class,
                        float.class,
                        float[].class,
                        Integer.class),
                NativeContract.findStaticMethod(
                        helper,
                        "e",
                        void.class,
                        View.class,
                        int.class,
                        float.class,
                        Integer.class,
                        String.class));
    }
}
