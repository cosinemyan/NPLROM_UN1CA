// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;

/** Refresh fixes for the supported native stacks. Samsung still owns card order and animations. */
final class NowBarStackRepair {
    private final Method data, item, key, blur, expanded, normalContent;
    private final Field state, animator, dragging, reordering, dismissing, expanding;

    NowBarStackRepair(NowBarStackBinding binding) {
        data = binding.data;
        item = binding.item;
        key = binding.key;
        blur = binding.blur;
        expanded = binding.expanded;
        normalContent = binding.content;
        state = binding.state;
        animator = binding.animator;
        dragging = binding.dragging;
        reordering = binding.reordering;
        dismissing = binding.dismissing;
        expanding = binding.expanding;
    }



    void updatePivotsSafely(DressroomModule module, ViewGroup stack) {
        try {
            if (expanding.getBoolean(stack) || Boolean.TRUE.equals(expanded.invoke(stack))) return;
            for (int i = 0; i < stack.getChildCount(); i++) {
                View child = stack.getChildAt(i);
                View content = (View) normalContent.invoke(child);
                if (content == null
                        || content.getParent() != child
                        || content.getWidth() <= 0
                        || content.getHeight() <= 0) continue;
                // Some cards retain a tall, invisible expanded container. Scaling around
                // that wrapper's center pulls the actual pill into the card above it.
                float x = content.getLeft() + content.getWidth() * .5f;
                float y = content.getTop() + content.getHeight() * .5f;
                if (Math.abs(child.getPivotX() - x) > .01f) child.setPivotX(x);
                if (Math.abs(child.getPivotY() - y) > .01f) child.setPivotY(y);
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            module.report("Now Bar pill pivot unavailable", error);
        }
    }

    private String name(Object model) throws IllegalAccessException {
        return ((Enum<?>) state.get(model)).name();
    }

    private boolean isMove(Object model) throws IllegalAccessException {
        String value = name(model);
        return value.equals("MOVE_OTHER") || value.equals("MOVE_OTHER_DELAY");
    }

    void beforeRefresh(ViewGroup stack, CopyOnWriteArrayList<?> incoming)
            throws ReflectiveOperationException {
        if (dragging.getBoolean(stack)
                || reordering.getBoolean(stack)
                || dismissing.getBoolean(stack)
                || expanding.getBoolean(stack)
                || Boolean.TRUE.equals(expanded.invoke(stack))) return;
        HashSet<Object> retained = new HashSet<>();
        for (Object value : incoming) if (value != null) retained.add(key.invoke(null, value));
        for (int i = 0; i < stack.getChildCount(); i++) {
            View child = stack.getChildAt(i);
            Object previous = data.invoke(child);
            if (previous == null || !isMove(previous)) continue;
            Object value = item.invoke(child);
            if (value == null || !retained.contains(key.invoke(null, value))) continue;
            Animator active = (Animator) animator.get(previous);
            if (active == null || !active.isStarted()) continue;
            float x = child.getScaleX(), y = child.getScaleY(), offset = child.getTranslationY();
            // The same-depth refresh writes its resting pose without stopping this animator.
            // Cancel before Samsung refreshes it, preserving the start pose for a changed slot.
            active.cancel();
            child.setScaleX(x);
            child.setScaleY(y);
            child.setTranslationY(offset);
            animator.set(previous, null);
        }
    }
}
