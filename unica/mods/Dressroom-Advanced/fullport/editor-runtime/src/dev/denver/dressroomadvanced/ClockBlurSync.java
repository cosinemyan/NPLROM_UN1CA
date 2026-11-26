package dev.denver.dressroomadvanced;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

final class ClockBlurSync {
    private static final Map<View, Consumer<View>> pending = new WeakHashMap<>();
    private static final ThreadLocal<View> rebuilding = new ThreadLocal<>();
    private static boolean installed;



    static boolean rebuilding(View clock) {
        return rebuilding.get() == clock;
    }

    static boolean pending(View clock) {
        return pending.containsKey(clock);
    }

    static boolean defer(View clock, Consumer<View> work) {
        if (!clock.isAttachedToWindow()) {
            cancel(clock);
            return false;
        }
        pending.put(clock, work);
        clock.invalidate();
        return true;
    }

    static void drawn(View clock) {
        Consumer<View> work = pending.get(clock);
        if (work == null) return;
        if (!clock.isAttachedToWindow()) {
            cancel(clock);
            return;
        }
        if (clock.isLayoutRequested()) {
            clock.invalidate();
            return;
        }
        pending.remove(clock);
        View previous = rebuilding.get();
        rebuilding.set(clock);
        try {
            work.accept(clock);
        } catch (RuntimeException ignored) {
        } finally {
            if (previous == null) rebuilding.remove();
            else rebuilding.set(previous);
        }
    }

    static void cancel(View clock) {
        pending.remove(clock);
    }
}
