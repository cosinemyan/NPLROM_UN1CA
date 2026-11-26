package dev.denver.dressroomadvanced;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class ClockBinding {
    final boolean legacy;
    final boolean clockStyle;
    final Method balance, render;
    final int balanceHeights;
    private final Field font, nativeFontId;
    private final Method fontType;
    private final Method refreshAppearance;
    private static final ClassValue<ClockBinding> bindings =
            new ClassValue<>() {
                @Override
                protected ClockBinding computeValue(Class<?> clock) {
                    Class<?> base = clock;
                    while (base != null
                            && !base.getName()
                                    .equals(
                                            "com.samsung.android.uniform.widget.clock.AbsClockView"))
                        base = base.getSuperclass();
                    if (base == null)
                        throw new IllegalStateException(
                                "Unknown clock provider: " + clock.getName());
                    try {
                        return clock == base ? new ClockBinding(base) : bindings.get(base);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Unsupported clock provider", error);
                    }
                }
            };

    static ClockBinding of(Object clock) {
        return bindings.get(clock instanceof Class<?> ? (Class<?>) clock : clock.getClass());
    }

    private ClockBinding(Class<?> base) throws ReflectiveOperationException {
        ClassLoader loader = base.getClassLoader();
        Class<?> type =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ClockFontData", false, loader);

        // Provider-owned settings classes survive R8 renaming and also distinguish plugin
        // loaders inside Dressroom/SystemUI, where the process package is not the clock provider.
        boolean aod =
                present(
                        loader,
                        "com.samsung.android.app.aodservice.settings.provider.SettingsSearchProvider")
                        || present(loader, "com.samsung.android.app.aodservice.AlwaysOnDisplayEx")
                        || present(
                                loader,
                                "com.samsung.android.app.aodservice.provider.LockScreenDataContent");
        boolean pack =
                !aod
                        && (present(
                                        loader,
                                        "com.samsung.android.app.clockpack.settings.provider.SettingsSearchProvider")
                                || present(
                                        loader,
                                        "com.samsung.android.app.clockpack.provider.LockSettingsProvider")
                                || present(
                                        loader,
                                        "com.samsung.android.app.clockpack.db.entity.ProfileState")
                                || present(
                                        loader,
                                        "com.samsung.android.app.facewidget.utils.KeyguardClockProvider")
                                || present(
                                        loader,
                                        "com.samsung.android.clockpack.plugins.clock.ClockView")
                                || present(
                                        loader,
                                        "com.samsung.android.clockpack.plugins.clock.ClockFontData")
                                || present(
                                        loader,
                                        "com.samsung.android.uniform.widget.clock.AbsClockView"));
        if (!aod && !pack)
            throw new ClassNotFoundException("Clock provider identity is missing or ambiguous");
        clockStyle = pack;
        font = NativeContract.findField(base, null, type);

        balance = ClockSourceBinding.normalizer(loader, type, clockStyle);
        balanceHeights = balance.getParameterCount() == 1 ? 0 : 2;
        legacy = balanceHeights == 0;

        fontType = NativeContract.method(type, "getFontType", int.class);
        // Requested font identity can differ from the temporary FontData used by a transition.
        // Pair verified storage layouts; never pick an arbitrary int field on a new provider.
        String idName =
                switch (font.getName()) {
                    case "T0" -> "D0";
                    case "S0" -> "C0";
                    case "L0" -> "w0";
                    case "M0" -> "x0";
                    default -> null;
                };
        Field id = null;
        if (idName != null) {
            try {
                id = NativeContract.field(base, idName, int.class);
            } catch (NoSuchFieldException changedLayout) {
            }
        }
        nativeFontId = id;

        Method renderer = null;
        for (Method method : base.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    || method.isBridge() || method.getName().startsWith("nativeAdvancedOriginal")
                    || method.getReturnType() != void.class
                    || !java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[] {type})
                    || method.getName().equals("setFontType")
                    || method.getName().equals("startSpringAnimation")) continue;
            if (renderer != null)
                throw new NoSuchMethodException(
                        "Ambiguous clock renderers: " + renderer + " and " + method);
            renderer = method;
        }
        if (renderer == null)
            throw new NoSuchMethodException("Clock renderer not found in " + base);
        renderer.setAccessible(true);
        render = renderer;

        Class<?> shadow =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ShadowStyle", false, loader);
        Method appearance = null;
        for (Method method : base.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())
                    || method.isBridge() || method.getName().startsWith("nativeAdvancedOriginal")
                    || method.getReturnType() != void.class
                    || !java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[] {shadow})
                    || method.getName().equals("setShadowStyle")) continue;
            if (appearance != null)
                throw new NoSuchMethodException(
                        "Ambiguous shadow refresh: " + appearance + " and " + method);
            appearance = method;
        }
        if (appearance == null) throw new NoSuchMethodException("Clock shadow refresh not found");
        appearance.setAccessible(true);
        refreshAppearance = appearance;
    }

    private static boolean present(ClassLoader loader, String name) {
        try {
            // Count only classes owned by this plugin, not ones visible through a parent loader.
            return Class.forName(name, false, loader).getClassLoader() == loader;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    int fontId(Object clock) throws IllegalAccessException {
        if (nativeFontId != null) return nativeFontId.getInt(clock);
        Object value = font(clock);
        if (value == null) return 0;
        try {
            return ((Number) fontType.invoke(value)).intValue();
        } catch (java.lang.reflect.InvocationTargetException error) {
            return 0;
        }
    }

    Object font(Object clock) throws IllegalAccessException {
        return font.get(clock);
    }

    void render(Object clock, Object value) throws ReflectiveOperationException {
        render.invoke(clock, value);
    }

    void refreshAppearance(Object clock) throws ReflectiveOperationException {
        refreshAppearance.invoke(clock, new Object[] {null});
    }
}
