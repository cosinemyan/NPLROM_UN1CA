// Native adaptation of BlueOutside / Dressroom Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Native glass owners are found by contract; scalar hints remain type checked. */
final class ClockGlassBinding {
    final Field manager, mode, opacity, fixedTone, dozing;
    final Method collectGlyphs, refresh;
    private static final ClassValue<ClockGlassBinding> bindings = new ClassValue<>() {
        @Override protected ClockGlassBinding computeValue(Class<?> type) {
            try { return new ClockGlassBinding(type); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException("Clock glass ABI unavailable", error); }
        }
    };
    static ClockGlassBinding of(Object clock) { return bindings.get(clock.getClass()); }
    static ClockGlassBinding of(Class<?> clock) { return bindings.get(clock); }
    private ClockGlassBinding(Class<?> base) throws ReflectiveOperationException {
        while (!base.getName().equals("com.samsung.android.uniform.widget.clock.AbsClockView")) {
            base = base.getSuperclass();
            if (base == null) throw new ClassNotFoundException("Clock glass owner");
        }
        Class<?> owner = OneUi9AodBlur.manager(base.getClassLoader());
        manager = NativeContract.findField(base, "h1", owner);
        Field colorMode = null;
        for (Field field : base.getDeclaredFields()) {
            Class<?> type = field.getType();
            if (!type.isEnum()) continue;
            boolean custom = false, adaptive = false;
            for (Object value : type.getEnumConstants()) {
                custom |= ((Enum<?>) value).name().equals("Custom");
                adaptive |= ((Enum<?>) value).name().equals("Adaptive");
            }
            if (!custom || !adaptive) continue;
            if (colorMode != null) throw new NoSuchFieldException("Ambiguous glass color mode");
            colorMode = field;
        }
        if (colorMode == null) throw new NoSuchFieldException("Glass color mode unavailable");
        colorMode.setAccessible(true);
        mode = colorMode;
        boolean reference = manager.getName().equals("h1");
        if (!reference && !manager.getName().equals("f1")) throw new NoSuchFieldException("Unknown glass scalar layout");
        opacity = NativeContract.field(base, reference ? "l0" : "k0", int.class);
        fixedTone = NativeContract.field(base, reference ? "L0" : "J0", boolean.class);
        dozing = NativeContract.field(base, reference ? "s0" : "r0", boolean.class);
        collectGlyphs = NativeContract.findMethod(base, "x0", void.class, int.class, android.widget.TextView[].class);
        Method invalidate;
        try { invalidate = NativeContract.method(base, "E0", void.class); }
        catch (NoSuchMethodException renamed) { invalidate = NativeContract.method(base, "C0", void.class); }
        refresh = invalidate;
    }
}
