// Native port of Dressroom-Advanced by @BlueOutside. See upstream LICENSE.
package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

final class DepthViewMotion {
    private static final Map<View, State> states = new WeakHashMap<>();

    private static final class State implements ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> root;
        final ViewGeometry geometry = new ViewGeometry();
        WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);

        State(View view) {
            root = new WeakReference<>(view);
        }

        @Override
        public boolean onPreDraw() {
            View view = root.get();
            if (view != null && geometry.update(view, DepthEffects.subjectIdentity(view), null))
                DepthEffects.viewMotionChanged(view);
            return true;
        }
    }

    static void watch(View root, boolean enabled) {
        if (!enabled) {
            State state = states.remove(root);
            if (state == null) return;
            ViewTreeObserver observer = state.observer.get();
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(state);
            // Child display lists may still contain an earlier subject cut.
            invalidate(root);
            return;
        }
        State state = states.computeIfAbsent(root, State::new);
        ViewTreeObserver observer = root.getViewTreeObserver(), previous = state.observer.get();
        if (previous == observer) return;
        if (previous != null && previous.isAlive()) previous.removeOnPreDrawListener(state);
        state.observer = new WeakReference<>(observer);
        // An observer from an unattached view may already have merged into this tree.
        observer.removeOnPreDrawListener(state);
        observer.addOnPreDrawListener(state);
        invalidate(root);
    }

    private static void invalidate(View view) {
        // Parent RenderNode movement does not rerecord the children's display lists.
        view.invalidate();
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) invalidate(group.getChildAt(i));
    }
}
