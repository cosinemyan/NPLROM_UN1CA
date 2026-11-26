// Native adaptation of BlueOutside / Dressroom-Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;

/** One active native settings sheet; child windows retain their own dismissal. */
final class AdvancedPanelDismiss implements View.OnAttachStateChangeListener {
    interface Close { void run() throws Exception; }
    private static WeakReference<AdvancedPanelDismiss> current = new WeakReference<>(null);
    private static long sessionEpoch;
    private final long epoch=sessionEpoch;
    private final Activity activity;
    private final View content, header;
    private final BooleanSupplier visible;
    private final Close close;
    private final OutsidePanelTap gesture = new OutsidePanelTap();
    private final ArrayList<WeakReference<Dialog>> children = new ArrayList<>();
    private View watched;
    private int addedFlags;

    AdvancedPanelDismiss(Activity activity, View content, View header, BooleanSupplier visible, Close close) {
        this.activity=activity; this.content=content; this.header=header;
        this.visible=visible; this.close=close;
        content.addOnAttachStateChangeListener(this);
    }
    private boolean showing() {
        return epoch==sessionEpoch && content.isAttachedToWindow() && content.isShown() && visible.getAsBoolean();
    }
    void activate() {
        if (!showing()) return;
        AdvancedPanelDismiss old=current.get();
        if (old!=null && old!=this) old.deactivate();
        current=new WeakReference<>(this);
        View window=content.getRootView();
        if (watched==window || !(window.getLayoutParams() instanceof WindowManager.LayoutParams params)
                || params.type<WindowManager.LayoutParams.FIRST_SUB_WINDOW
                || params.type>WindowManager.LayoutParams.LAST_SUB_WINDOW) return;
        int flags=WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        addedFlags=flags & ~params.flags;
        watched=window;
        params.flags|=flags;
        try { activity.getSystemService(WindowManager.class).updateViewLayout(window,params); }
        catch (RuntimeException failure) { DressroomModule.note("Advanced outside events",failure); }
    }
    void deactivate() {
        gesture.reset();
        if (current.get()==this) current.clear();
        View window=watched; watched=null;
        if (window!=null && window.getLayoutParams() instanceof WindowManager.LayoutParams params) {
            params.flags&=~addedFlags;
            if (window.isAttachedToWindow()) try {
                activity.getSystemService(WindowManager.class).updateViewLayout(window,params);
            } catch (RuntimeException failure) { DressroomModule.note("Advanced outside cleanup",failure); }
        }
        addedFlags=0;
    }
    static void clear() {
        // Invalidate queued attach/activate callbacks from the closing editor session.
        sessionEpoch++;
        AdvancedPanelDismiss panel=current.get(); if(panel!=null) panel.deactivate();
    }
    static boolean shownFor(Activity activity) {
        AdvancedPanelDismiss panel=current.get();
        return panel!=null && panel.activity==activity && panel.showing();
    }
    static void protectCurrent(Dialog dialog) {
        AdvancedPanelDismiss panel=current.get();
        if(panel!=null) panel.children.add(new WeakReference<>(dialog));
    }
    private boolean childOwnsTouch(View window, MotionEvent event) {
        children.removeIf(reference -> reference.get()==null);
        for (WeakReference<Dialog> reference:children) {
            Dialog dialog=reference.get(); if(dialog!=null && dialog.isShowing()) return true;
        }
        // A spinner/dropdown or another dialog owns focus. A tap delivered to the
        // underlying Activity is still a valid outside tap when its decor has focus.
        View decor=activity.getWindow().getDecorView();
        if(!window.hasWindowFocus() && !decor.hasWindowFocus()) return true;
        WindowInsets insets=window.getRootWindowInsets();
        if(insets!=null && insets.isVisible(WindowInsets.Type.ime())) {
            // Android may redact ACTION_OUTSIDE coordinates to (0,0). Never close
            // the parent sheet for such a keyboard interaction.
            if(event.getActionMasked()==MotionEvent.ACTION_OUTSIDE && event.getRawX()==0 && event.getRawY()==0) return true;
            WindowManager manager=activity.getSystemService(WindowManager.class);
            if(manager!=null) {
                var metrics=manager.getCurrentWindowMetrics();
                int ime=metrics.getWindowInsets().getInsets(WindowInsets.Type.ime()).bottom;
                if(ime>0 && event.getRawY()>=metrics.getBounds().bottom-ime) return true;
            }
        }
        return false;
    }
    static boolean touch(Object source, MotionEvent event) {
        AdvancedPanelDismiss panel=current.get();
        return panel!=null && panel.observe(source,event);
    }
    private boolean observe(Object source, MotionEvent event) {
        if(!showing()) { deactivate(); return false; }
        View window=content.getRootView();
        if(source!=activity && source!=window) return false;
        Rect area=new Rect();
        if(window!=activity.getWindow().getDecorView()) {
            if(!ScreenBounds.visible(window,area)) return false;
        } else {
            if(!ScreenBounds.visible(content,area)) return false;
            Rect extra=new Rect();
            if(header!=null && ScreenBounds.visible(header,extra)) area.union(extra);
        }
        boolean inside=area.contains(Math.round(event.getRawX()),Math.round(event.getRawY()));
        if(!gesture.event(event.getActionMasked(),event.getRawX(),event.getRawY(),inside,
                childOwnsTouch(window,event),ViewConfiguration.get(activity).getScaledTouchSlop())) return false;
        try { close.run(); deactivate(); }
        catch(Exception failure) {
            DressroomModule.note("Advanced outside dismissal",failure);
            if(!showing()) deactivate();
        }
        return event.getActionMasked()==MotionEvent.ACTION_OUTSIDE;
    }
    @Override public void onViewAttachedToWindow(View view) { content.post(this::activate); }
    @Override public void onViewDetachedFromWindow(View view) { deactivate(); }
}
