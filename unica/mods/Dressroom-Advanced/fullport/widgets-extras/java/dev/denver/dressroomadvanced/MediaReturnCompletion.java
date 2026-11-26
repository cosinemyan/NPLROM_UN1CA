// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

final class MediaReturnCompletion {
    private static final Map<View, Wait> waiting = new WeakHashMap<>();

    private static final class Wait extends AnimatorListenerAdapter {
        final WeakReference<View> clock;
        final Animator animator;
        final Runnable finished;

        Wait(View clock, Animator animator, Runnable finished) {
            this.clock = new WeakReference<>(clock);
            this.animator = animator;
            this.finished = finished;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            View view = clock.get();
            animator.removeListener(this);
            if (view != null && waiting.get(view) == this) {
                waiting.remove(view);
                finished.run();
            }
        }
    }

    static boolean await(View clock, Animator animator, Runnable finished) {
        if (animator == null || !animator.isStarted()) return false;
        Wait old = waiting.get(clock);
        if (old != null && old.animator == animator) return true;
        cancel(clock);
        Wait next = new Wait(clock, animator, finished);
        waiting.put(clock, next);
        animator.addListener(next);
        return true;
    }

    static void cancel(View clock) {
        Wait old = waiting.remove(clock);
        if (old != null) old.animator.removeListener(old);
    }
}
