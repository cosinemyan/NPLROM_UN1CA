package dev.denver.dressroomadvanced;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.WindowManager;
import android.widget.EditText;

import java.util.List;

final class PanelKeyboard
        implements View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
    private final View panel;
    private final Rect visible = new Rect();
    private final int[] location = new int[2];
    private Window activityWindow;
    private View popup;
    private WindowManager manager;
    private ViewTreeObserver observer;
    private int previousMode, appliedMode, lastBottom, baseY, baseHeight, basePixels, baseTop;
    private boolean popupAdjusted, activityAdjusted;
    private View lastFocus;

    private PanelKeyboard(View panel) {
        this.panel = panel;
    }

    static void attach(View panel) {
        PanelKeyboard keyboard = new PanelKeyboard(panel);
        panel.addOnAttachStateChangeListener(keyboard);
        panel.setOnApplyWindowInsetsListener(
                (view, insets) -> {
                    keyboard.update(insets);
                    return insets;
                });
        panel.setWindowInsetsAnimationCallback(
                new WindowInsetsAnimation.Callback(
                        WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    @Override
                    public WindowInsets onProgress(
                            WindowInsets insets, List<WindowInsetsAnimation> running) {
                        keyboard.update(insets);
                        return insets;
                    }
                });
        if (panel.isAttachedToWindow()) keyboard.onViewAttachedToWindow(panel);
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        Context context = panel.getContext();
        manager = context.getSystemService(WindowManager.class);
        for (int depth = 0; depth < 16 && context != null; depth++) {
            if (context instanceof Activity activity) {
                activityWindow = activity.getWindow();
                break;
            }
            if (!(context instanceof ContextWrapper wrapper) || wrapper.getBaseContext() == context)
                break;
            context = wrapper.getBaseContext();
        }
        boolean floating =
                panel.getRootView().getLayoutParams() instanceof WindowManager.LayoutParams params
                        && params.type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW;
        if (activityWindow != null && !floating) {
            activityAdjusted = true;
            previousMode = activityWindow.getAttributes().softInputMode;
            appliedMode =
                    (previousMode & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
            activityWindow.setSoftInputMode(appliedMode);
        }
        observer = panel.getViewTreeObserver();
        observer.addOnGlobalLayoutListener(this);
        panel.requestApplyInsets();
    }

    @Override
    public void onGlobalLayout() {
        update(panel.getRootWindowInsets());
    }

    private void update(WindowInsets insets) {
        if (!panel.isAttachedToWindow()) return;
        panel.getWindowVisibleDisplayFrame(visible);
        // Popup insets change when the popup moves. Use the containing window's IME state.
        android.view.WindowMetrics metrics =
                manager == null ? null : manager.getCurrentWindowMetrics();
        int ime =
                metrics == null
                        ? 0
                        : metrics.getWindowInsets().getInsets(WindowInsets.Type.ime()).bottom;
        if (activityWindow != null) {
            WindowInsets activityInsets = activityWindow.getDecorView().getRootWindowInsets();
            if (activityInsets != null)
                ime = Math.max(ime, activityInsets.getInsets(WindowInsets.Type.ime()).bottom);
        }
        int bottom = metrics == null ? visible.bottom : metrics.getBounds().bottom - ime;
        int topLimit =
                metrics == null
                        ? visible.top
                        : metrics.getBounds().top
                                + metrics.getWindowInsets()
                                        .getInsets(WindowInsets.Type.systemBars())
                                        .top;
        View focused = panel.findFocus();
        View root = panel.getRootView();
        // Samsung's settings sheet is a PopupWindow, outside the activity's resize layout.
        if (manager != null
                && root.getHeight() > 0
                && root.getLayoutParams() instanceof WindowManager.LayoutParams params
                && params.type >= WindowManager.LayoutParams.FIRST_SUB_WINDOW
                && params.type <= WindowManager.LayoutParams.LAST_SUB_WINDOW) {
            if (popup != root) {
                popup = root;
                baseY = params.y;
                baseHeight = params.height;
                basePixels = root.getHeight();
                root.getLocationOnScreen(location);
                baseTop = location[1];
            }
            if (ime > 0 && focused instanceof EditText) {
                int gap = Math.round(12 * root.getResources().getDisplayMetrics().density);
                int maximum = Math.max(1, bottom - Math.max(topLimit, gap) - gap);
                int height = Math.min(basePixels, maximum);
                int nextLift = Math.max(0, baseTop + height + gap - bottom);
                int nextY =
                        baseY
                                + (((params.gravity & Gravity.VERTICAL_GRAVITY_MASK)
                                                == Gravity.BOTTOM)
                                        ? nextLift
                                        : -nextLift);
                int nextHeight = height < basePixels ? height : baseHeight;
                if (params.y != nextY || params.height != nextHeight) {
                    params.y = nextY;
                    params.height = nextHeight;
                    popupAdjusted = true;
                    manager.updateViewLayout(root, params);
                }
            } else restorePopup();
        }
        if (bottom == lastBottom && focused == lastFocus) return;
        lastBottom = bottom;
        lastFocus = focused;
        if (focused instanceof EditText)
            focused.post(
                    () -> {
                        if (!focused.isAttachedToWindow() || !focused.hasFocus()) return;
                        int gap =
                                Math.round(12 * focused.getResources().getDisplayMetrics().density);
                        focused.requestRectangleOnScreen(
                                new Rect(0, 0, focused.getWidth(), focused.getHeight() + gap),
                                false);
                    });
    }

    private void restorePopup() {
        if (popupAdjusted
                && popup != null
                && popup.isAttachedToWindow()
                && popup.getLayoutParams() instanceof WindowManager.LayoutParams params) {
            popupAdjusted = false;
            params.y = baseY;
            params.height = baseHeight;
            manager.updateViewLayout(popup, params);
        }
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
        observer = null;
        lastFocus = null;
        lastBottom = 0;
        restorePopup();
        popup = null;
        popupAdjusted = false;
        if (activityAdjusted
                && activityWindow != null
                && activityWindow.getAttributes().softInputMode == appliedMode)
            activityWindow.setSoftInputMode(previousMode);
        activityWindow = null;
        activityAdjusted = false;
    }
}
