// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.view.View;

import java.util.Map;
import java.util.WeakHashMap;

final class MediaBlurHost {
    private static final class Opacity {
        float nativeAlpha, factor, applied;
        android.view.ViewTreeObserver.OnPreDrawListener observer;
        java.lang.ref.WeakReference<android.view.ViewTreeObserver> tree;

        Opacity(float alpha) {
            nativeAlpha = applied = alpha;
        }
    }

    private static final Map<View, Opacity> held = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> writing = new ThreadLocal<>();
    private static boolean installed;



    static float nativeAlpha(View view, float value) {
        Opacity state = held.get(view);
        if (!Boolean.TRUE.equals(writing.get()) && state != null) {
            state.nativeAlpha = value;
            state.applied=value*state.factor;return state.applied;
        }
        return value;
    }

    static float requestedAlpha(View view) {
        Opacity state = held.get(view);
        return state == null ? view.getAlpha() : state.nativeAlpha;
    }

    static void hold(View view) {
        fade(view, 0f);
    }

    static void fade(View view, float factor) {
        Opacity state = held.get(view);
        if (state == null) {
            state = new Opacity(view.getAlpha());
            held.put(view, state);
            Opacity target=state;
            java.lang.ref.WeakReference<View> reference=new java.lang.ref.WeakReference<>(view);
            target.observer=()->{
                View current=reference.get();if(current==null||held.get(current)!=target)return true;
                float actual=current.getAlpha();
                if(Math.abs(actual-target.applied)>.0001f){target.nativeAlpha=actual;write(current,actual*target.factor);}
                return true;
            };
            android.view.ViewTreeObserver tree=view.getViewTreeObserver();target.tree=new java.lang.ref.WeakReference<>(tree);tree.addOnPreDrawListener(target.observer);
        }
        state.factor = Math.max(0f, Math.min(1f, factor));
        float alpha = state.nativeAlpha * state.factor;
        if (view.getAlpha() != alpha) write(view, alpha);
    }

    static void release(View view) {
        Opacity state = held.remove(view);
        if (state != null) {
            android.view.ViewTreeObserver tree=state.tree==null?null:state.tree.get();if(tree!=null&&tree.isAlive())tree.removeOnPreDrawListener(state.observer);
            write(view,state.nativeAlpha);
        }
    }

    private static void write(View view, float alpha) {
        Boolean previous = writing.get();
        writing.set(true);
        try {
            Opacity state=held.get(view);if(state!=null)state.applied=alpha;
            view.setAlpha(alpha);
        } finally {
            if (previous == null) writing.remove();
            else writing.set(previous);
        }
    }
}
