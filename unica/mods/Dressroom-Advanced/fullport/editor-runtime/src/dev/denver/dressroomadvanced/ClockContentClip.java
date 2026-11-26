package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

final class ClockContentClip {
    private static final Map<View, State> states = new WeakHashMap<>();

    static boolean needed(View clock) {
        String name = clock.getClass().getName();
        return WidgetContentClip.supported()
                && ((name.startsWith("com.samsung.android.uniform.widget.clock.")
                                && ClockBinding.of(clock).clockStyle)
                        || !name.equals(
                                        "com.samsung.android.uniform.widget.clock.DigitalHorizontalClockView")
                                && !name.equals(
                                        "com.samsung.android.uniform.widget.clock.DigitalVerticalClockView"));
    }

    private static final class State implements ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> clock;
        WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);
        final ViewGeometry geometry = new ViewGeometry();

        State(View view) {
            clock = new WeakReference<>(view);
        }

        @Override
        public boolean onPreDraw() {
            View view = clock.get();
            if (view == null) {
                ViewTreeObserver tree = observer.get();
                if (tree != null && tree.isAlive()) tree.removeOnPreDrawListener(this);
                return true;
            }
            boolean suspended = DepthEffects.mediaActive(view);
            boolean changed = WidgetContentClip.apply(view, !suspended);
            boolean moved =
                    geometry.update(
                            view,
                            DepthEffects.subjectIdentity(view),
                            suspended ? Boolean.TRUE : Boolean.FALSE);
            if (changed || moved) {
                WidgetContentClip.refresh(view);
            }
            return true;
        }
    }

    static void watch(View view, boolean enabled) {
        if (!enabled) {
            State old = states.remove(view);
            if (old != null) {
                ViewTreeObserver observer = old.observer.get();
                if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(old);
                WidgetContentClip.apply(view, false);
                WidgetContentClip.refresh(view);
            }
            return;
        }
        State state = states.computeIfAbsent(view, State::new);
        ViewTreeObserver observer = view.getViewTreeObserver(), old = state.observer.get();
        if (old != observer) {
            if (old != null && old.isAlive()) old.removeOnPreDrawListener(state);
            observer.addOnPreDrawListener(state);
            state.observer = new WeakReference<>(observer);
        }
        WidgetContentClip.apply(view, !DepthEffects.mediaActive(view));
        view.invalidate();
    }
}
