package dev.denver.dressroomadvanced;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.List;

/** Resolve every source/measurement dependency before installing any hooks. */
final class ClockSourceBinding {
    final Method data, heights, wallpaper;
    final List<Method> callers;

    ClockSourceBinding(ClassLoader loader, ClockBinding clock) throws ReflectiveOperationException {
        Class<?> provider =
                Class.forName(
                        "com.samsung.android.app.facewidget.utils.KeyguardClockProvider",
                        false,
                        loader);
        Class<?> font =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ClockFontData", false, loader);
        NativeContract.field(provider, "context", Context.class);
        NativeContract.method(provider, "getConsideredThemeClockType", int.class);
        data = NativeContract.method(provider, "getClockFontData", font);
        heights = NativeContract.method(provider, "getClockFontHeights", List.class);
        wallpaper =
                NativeContract.method(
                        provider, "updateWallpaperColorsItem", void.class, Object.class);
        Method stretch = NativeContract.method(provider, "hasStretchFontHeights", boolean.class);
        var methods = new java.util.ArrayList<Method>();
        methods.add(stretch);
        try {
            Class<?> model =
                    Class.forName(
                            "com.samsung.android.app.facewidget.FaceWidgetViewModel", false, loader);
            Class<?> view =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.ClockView", false, loader);
            addCallers(methods, model, font, view, false);
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            Class<?> page =
                    Class.forName(
                            "com.samsung.android.app.facewidget.pages.clock.FaceWidgetClockPage",
                            false,
                            loader);
            Class<?> view =
                    Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.ClockView", false, loader);
            addCallers(methods, page, font, view, true);
        } catch (ReflectiveOperationException ignored) {
        }
        callers = List.copyOf(methods);
    }

    static Method normalizer(ClassLoader loader, Class<?> font, boolean clockStyle)
            throws ReflectiveOperationException {
        // Avoid a full modern-ABI scan on every known legacy provider.
        Class<?> known =
                NativeContract.knownClass(
                        loader,
                        type ->
                                NativeContract.hasStaticMethod(
                                        type, List.class, int.class, font, List.class),
                        clockStyle ? "aod.uq6" : "aod.qn1",
                        clockStyle ? "aod.qn1" : "aod.uq6", "aod.an1");
        if (known != null)
            return NativeContract.findStaticMethod(
                    known,
                    known.getName().equals("aod.uq6")
                            ? "T"
                            : known.getName().equals("aod.an1") ? "c0" : "X",
                    List.class,
                    int.class,
                    font,
                    List.class);
        Class<?> aod8936 =
                NativeContract.knownClass(
                        loader,
                        type -> NativeContract.hasStaticMethod(type, List.class, List.class),
                        "aod.VO");
        if (aod8936 != null)
            return NativeContract.findStaticMethod(aod8936, "d", List.class, List.class);
        known =
                NativeContract.knownClass(
                        loader,
                        type -> NativeContract.hasStaticMethod(type, List.class, List.class),
                        clockStyle ? "aod.QT" : "aod.PO",
                        clockStyle ? "aod.PO" : "aod.QT");
        if (known != null)
            return NativeContract.findStaticMethod(
                    known, clockStyle ? "g" : "k", List.class, List.class);
        Class<?> owner;
        try {
            owner =
                    NativeContract.findClass(
                            loader,
                            type ->
                                    NativeContract.hasStaticMethod(
                                            type, List.class, int.class, font, List.class),
                            clockStyle ? "aod.uq6" : "aod.qn1",
                            clockStyle ? "aod.qn1" : "aod.uq6", "aod.an1");
            return NativeContract.findStaticMethod(
                    owner,
                    owner.getName().equals("aod.uq6")
                            ? "T"
                            : owner.getName().equals("aod.an1") ? "c0" : "X",
                    List.class,
                    int.class,
                    font,
                    List.class);
        } catch (NativeContract.AmbiguousClassException ambiguous) {
            throw ambiguous;
        } catch (ClassNotFoundException missingModernAbi) {
            // The legacy List -> List contract is weaker. Only try it after ruling out
            // the modern font-data operation, not as part of the same broad scan.
            // List -> List alone also matches Kotlin collection helpers. Follow the
            // named clock API's actual call instead of scanning unrelated utilities.
            Class<?> base =
                    Class.forName(
                            "com.samsung.android.uniform.widget.clock.AbsClockView", false, loader);
            Method stretch =
                    NativeContract.method(base, "isStretchable", boolean.class, List.class);
            java.util.Set<Method> candidates = new java.util.LinkedHashSet<>();
            try {
                for (DexSignatures.Call call : DexSignatures.body(stretch).calls())
                    if (call.isStatic()
                            && (call.result().equals("Ljava/util/List;")
                                    || call.result().equals("Ljava/util/ArrayList;"))
                            && call.parameters().equals(List.of("Ljava/util/List;")))
                        candidates.add(call.method(loader));
            } catch (java.io.IOException invalidDex) {
                throw new ReflectiveOperationException(
                        "Could not read clock height call site", invalidDex);
            }
            if (candidates.size() != 1)
                throw new NoSuchMethodException(
                        "Ambiguous clock height call site: " + candidates.size());
            return candidates.iterator().next();
        }
    }

    private static void addCallers(
            List<Method> result, Class<?> owner, Class<?> font, Class<?> view, boolean page) {
        // Several zero-argument boolean methods share the stretch-measurement signature.
        // Deoptimize that small superset; never hook or invoke an arbitrary match.
        for (Method method : owner.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers()) || method.isBridge())
                continue;
            Class<?>[] args = method.getParameterTypes();
            Class<?> returns = method.getReturnType();
            if ((args.length == 0
                            && (returns == boolean.class
                                    || (page
                                            && (returns == font
                                                    || view.isAssignableFrom(returns)))))
                    || (page
                            && returns == void.class
                            && args.length == 2
                            && !args[0].isPrimitive()
                            && args[1] == Object.class)) {
                result.add(method);
            }
        }
    }
}
