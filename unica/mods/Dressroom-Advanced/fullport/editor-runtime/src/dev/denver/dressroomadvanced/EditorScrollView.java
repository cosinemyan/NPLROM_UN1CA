package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;

final class EditorScrollView extends ScrollView {
    private final Rect visible = new Rect();
    private ViewTreeObserver observer;
    private final ViewTreeObserver.OnGlobalLayoutListener viewportChanged =
            this::updateBottomSpacing;

    EditorScrollView(Context context) {
        super(context);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateBottomSpacing();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        observer = getViewTreeObserver();
        observer.addOnGlobalLayoutListener(viewportChanged);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (observer != null && observer.isAlive())
            observer.removeOnGlobalLayoutListener(viewportChanged);
        observer = null;
        super.onDetachedFromWindow();
    }

    private void updateBottomSpacing() {
        if (getChildCount() == 0 || !getLocalVisibleRect(visible)) return;
        int padding = Math.max(0, getHeight() + getScrollY() - visible.bottom);
        if (getPaddingBottom() != padding) {
            setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), padding);
            scrollTo(getScrollX(), getScrollY());
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            updateBottomSpacing();
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        boolean result = super.dispatchTouchEvent(event);
        if ((event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                && getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        return result;
    }
}
