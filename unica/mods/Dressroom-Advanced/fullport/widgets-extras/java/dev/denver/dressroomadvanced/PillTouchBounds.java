package dev.denver.dressroomadvanced;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

final class PillTouchBounds {

    static Boolean contains(ViewGroup root, int x, int y) throws ReflectiveOperationException {
        if (Boolean.TRUE.equals(Reflect.call(root, "k"))
                || Boolean.TRUE.equals(Reflect.call(root, "n"))
                || Boolean.TRUE.equals(Reflect.field(root, "U"))) return null;
        int slop = ViewConfiguration.get(root.getContext()).getScaledTouchSlop();
        boolean measured = false;
        for (int i = 0; i < root.getChildCount(); i++) {
            View card = root.getChildAt(i);
            if (card.getVisibility() != View.VISIBLE || card.getAlpha() < .01f) continue;
            Object content = Reflect.call(card, "getNormalContentRootView");
            Rect bounds = new Rect();
            if (content instanceof View view
                    && view.isShown()
                    && view.getGlobalVisibleRect(bounds)) {
                measured = true;
                bounds.inset(-slop, -slop);
                if (bounds.contains(x, y)) return true;
            }
        }
        return measured ? false : null;
    }
}
