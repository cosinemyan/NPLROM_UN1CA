package dev.denver.dressroomadvanced;

import android.graphics.Typeface;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.List;

/** Provider font operations used by the shared file/axis renderer. */
final class FontBinding {
    final Method fixed, variable;
    private final Method copy, refresh;
    private final ClockBinding clock;
    private static final ClassValue<FontBinding> bindings =
            new ClassValue<>() {
                @Override
                protected FontBinding computeValue(Class<?> owner) {
                    Class<?> base = owner;
                    while (base != null
                            && !base.getName()
                                    .equals(
                                            "com.samsung.android.uniform.widget.clock.AbsClockView"))
                        base = base.getSuperclass();
                    if (base == null) throw new IllegalStateException("Unknown clock font owner");
                    try {
                        if (owner != base) return bindings.get(base);
                        ClockBinding clock = ClockBinding.of(base);
                        return resolve(base, clock);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Unsupported clock font contract", error);
                    }
                }
            };

    static FontBinding of(Object owner) {
        return bindings.get(owner instanceof Class<?> type ? type : owner.getClass());
    }

    private static FontBinding resolve(Class<?> base, ClockBinding clock)
            throws ReflectiveOperationException {
        if (!clock.legacy) {
            return clock.clockStyle ? OneUi9ClockStyleFonts.bind(base) : OneUi9Fonts.bind(base);
        }
        return clock.clockStyle ? OneUi85ClockStyleFonts.bind(base) : OneUi85AodFonts.bind(base);
    }

    FontBinding(Class<?> base, String fixedName, String variableName)
            throws ReflectiveOperationException {
        clock = ClockBinding.of(base);
        fixed =
                NativeContract.findMethod(
                        base, fixedName, void.class, Typeface.class, TextView[].class);
        variable =
                NativeContract.findMethod(
                        base, variableName, void.class, int.class, String.class, TextView[].class);
        Class<?> data = clock.render.getParameterTypes()[0];
        copy =
                NativeContract.method(
                        data,
                        "copy",
                        data,
                        int.class,
                        String.class,
                        Integer.class,
                        Integer.class,
                        List.class);
        refresh = NativeContract.method(base, "refresh", void.class);
    }

    Method renderer(Class<?> owner) throws NoSuchMethodException {
        return owner.getMethod(clock.render.getName(), clock.render.getParameterTypes());
    }

    Object plain(Object original) throws ReflectiveOperationException {
        // Only the draw request loses Samsung's HEGT data; saved font identity stays intact.
        return copy.invoke(original, 0, "", null, null, null);
    }

    void apply(Object owner, Typeface typeface, TextView[] views)
            throws ReflectiveOperationException {
        fixed.invoke(owner, typeface, views);
    }

    void refresh(Object owner) throws ReflectiveOperationException {
        refresh.invoke(owner);
    }
}
