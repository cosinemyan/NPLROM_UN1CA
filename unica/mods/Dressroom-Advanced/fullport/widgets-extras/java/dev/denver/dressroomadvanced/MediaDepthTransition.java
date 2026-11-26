// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/** Keep the travelling clock behind the subject, but leave the settled media clock unmasked. */
final class MediaDepthTransition {
    private static final Map<View, Pending> pending = new WeakHashMap<>();

    private MediaDepthTransition() {}

    static void screenChanged() {
        for (View clock : new java.util.ArrayList<>(pending.keySet())) restore(clock);
    }

    static void restore(View clock) {
        Pending old = pending.remove(clock);
        if (old != null) old.cancel();
        DepthEffects.media(clock, false);
    }

    static void update(View clock, boolean expanded) {
        update(
                clock,
                expanded,
                view -> {
                    ClockTransitionBinding binding = ClockTransitionBinding.of(view);
                    try {
                        return (Animator) binding.animation.get(binding.controller.get(view));
                    } catch (IllegalAccessException error) {
                        throw new IllegalStateException(error);
                    }
                });
    }

    static void update(View clock, boolean expanded, Function<View, Animator> animation) {
        Pending current = pending.get(clock);
        if (expanded
                && (current instanceof Opening
                        || current == null && DepthEffects.mediaActive(clock))) return;
        if (!expanded && current instanceof Return) return;
        Pending old = pending.remove(clock);
        if (old != null) old.cancel();
        if (expanded) {
            if (!DepthEffects.hasSubject(clock)) {
                DepthEffects.media(clock, true);
                return;
            }
            DepthEffects.media(clock, false);
            Opening request = new Opening(clock, animation);
            pending.put(clock, request);
            clock.postOnAnimation(request);
        } else if (DepthEffects.mediaActive(clock)) {
            Return request = new Return(clock);
            pending.put(clock, request);
            // Let Samsung set up the return before rebuilding the native blur path.
            clock.postOnAnimation(request);
        }
    }

    private abstract static class Pending implements Runnable {
        final WeakReference<View> clock;

        Pending(View clock) {
            this.clock = new WeakReference<>(clock);
        }

        void cancel() {}
    }

    private static final class Return extends Pending {
        Return(View clock) {
            super(clock);
        }

        @Override
        public void run() {
            View view = clock.get();
            if (view != null && pending.get(view) == this) {
                pending.remove(view);
                DepthEffects.media(view, false);
            }
        }
    }

    private static final class Opening extends Pending
            implements ViewTreeObserver.OnPreDrawListener {
        final ViewGeometry geometry = new ViewGeometry();
        Function<View, Animator> animation;
        WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);
        int stable;

        Opening(View clock, Function<View, Animator> animation) {
            super(clock);
            this.animation = animation;
        }

        @Override
        public void run() {
            View view = clock.get();
            if (view == null || pending.get(view) != this) return;
            ViewTreeObserver tree = view.getViewTreeObserver();
            observer = new WeakReference<>(tree);
            tree.addOnPreDrawListener(this);
            view.postInvalidateOnAnimation();
        }

        @Override
        public boolean onPreDraw() {
            View view = clock.get();
            if (view == null || pending.get(view) != this) {
                cancel();
                return true;
            }
            if (view.isAttachedToWindow() && !view.isShown()) {
                finish(view);
                return true;
            }
            boolean running = false;
            if (animation != null) {
                try {
                    Animator current = animation.apply(view);
                    running = current != null && current.isStarted();
                } catch (RuntimeException ignored) {
                    animation = null; // Non-stretchable layouts can still move with their page.
                }
            }
            boolean moved = geometry.update(view, null, null);
            if (running || moved || view.isLayoutRequested()) stable = 0;
            else if (++stable >= 2) {
                finish(view);
                return true;
            }
            view.postInvalidateOnAnimation();
            return true;
        }

        private void finish(View view) {
            pending.remove(view);
            cancel();
            DepthEffects.media(view, true);
        }

        @Override
        void cancel() {
            ViewTreeObserver tree = observer.get();
            if (tree != null && tree.isAlive()) tree.removeOnPreDrawListener(this);
            View view = clock.get();
            if (view != null && view.getViewTreeObserver() != tree)
                view.getViewTreeObserver().removeOnPreDrawListener(this);
        }
    }
}
