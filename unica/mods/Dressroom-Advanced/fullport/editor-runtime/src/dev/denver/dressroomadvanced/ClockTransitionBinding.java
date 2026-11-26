package dev.denver.dressroomadvanced;

import android.animation.Animator;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Resolved once per provider; transition state is not tied to a device model. */
final class ClockTransitionBinding {
    final Method transition;
    final Field controller, expanded, dozing, animation, axes, amount, mediaExpanded;
    private static final ClassValue<ClockTransitionBinding> bindings = new ClassValue<>() {
        @Override protected ClockTransitionBinding computeValue(Class<?> type) {
            try { return new ClockTransitionBinding(type); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException("Clock transition ABI unavailable", e); }
        }
    };
    static ClockTransitionBinding of(Class<?> type) { return bindings.get(type); }
    static ClockTransitionBinding of(Object clock) { return of(clock.getClass()); }
    private ClockTransitionBinding(Class<?> base) throws ReflectiveOperationException {
        while (!base.getName().equals("com.samsung.android.uniform.widget.clock.AbsClockView")) {
            base = base.getSuperclass();
            if (base == null) throw new ClassNotFoundException("Clock transition owner");
        }
        Method operation;
        try { operation = NativeContract.method(base, "K0", void.class, float.class, boolean.class); }
        catch (NoSuchMethodException renamed) { operation = NativeContract.findMethod(base, "G0", void.class, float.class, boolean.class); }
        transition = operation;
        Field resolved = null;
        for (Field field : base.getDeclaredFields()) {
            Class<?> owner = field.getType();
            if (NativeContract.hasField(owner, Animator.class) && NativeContract.hasField(owner, List.class)) {
                if (resolved != null) throw new NoSuchFieldException("Ambiguous clock transition owners");
                resolved = field;
            }
        }
        if (resolved == null) throw new NoSuchFieldException("Clock transition controller");
        resolved.setAccessible(true);
        controller = resolved;
        Class<?> owner = controller.getType();
        expanded = NativeContract.field(owner, "h", boolean.class);
        dozing = NativeContract.field(owner, "d", boolean.class);
        animation = NativeContract.findField(owner, "i", Animator.class);
        axes = NativeContract.findField(owner, "j", List.class);
        // Known state layouts are checked hints: multiple floats/booleans have different semantics.
        boolean reference = controller.getName().equals("W0");
        if (!reference && !controller.getName().equals("U0")) throw new NoSuchFieldException("Unknown transition scalar layout");
        amount = NativeContract.field(base, reference ? "t0" : "s0", float.class);
        mediaExpanded = NativeContract.field(base, reference ? "d1" : "b1", boolean.class);
    }
}
