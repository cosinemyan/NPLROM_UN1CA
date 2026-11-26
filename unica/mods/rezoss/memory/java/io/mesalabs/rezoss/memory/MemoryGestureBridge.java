package io.mesalabs.rezoss.memory;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import com.honeyspace.gesture.inputproxy.InputMonitorProxy;
import com.honeyspace.gesture.multifinger.MultiFingerGestureEvent;
import com.honeyspace.gesture.multifinger.MultiFingerGestureTrailTouchRelay;

/** Called only from the display-0 NO_BUTTON launcher input path. */
public final class MemoryGestureBridge {
    private static final String TAG = "RezossMemoryGesture";
    private static final String PACKAGE = "com.samsung.android.app.memory";
    private static final String MEMORY = "memory_three_finger_gesture";
    private static final String HOME_UP = "home_up_multi_finger_gesture_enabled";
    private static final GesturePolicy.Session SESSION = new GesturePolicy.Session();
    private static volatile boolean memoryEnabled, secureHomeUp, homeUpEnabled = true;
    private static Context context;
    private static Handler handler;
    private static ContentObserver observer;
    private static boolean initializationFailed;

    private MemoryGestureBridge() {}

    private static void refresh() {
        // Read through this launcher's user context. Never write either app's settings.
        try {
            memoryEnabled = Settings.Secure.getInt(context.getContentResolver(), MEMORY, 0) == 1;
            secureHomeUp = Settings.Secure.getInt(context.getContentResolver(), HOME_UP, 0) == 1;
        } catch (RuntimeException e) {
            memoryEnabled = false;
            Log.w(TAG, "Cannot read gesture settings; Memory disabled", e);
        }
    }

    private static void initialize(Context source) {
        if (context != null || initializationFailed) return;
        try {
            context = source.getApplicationContext();
            if (context == null) context = source;
            handler = new Handler(Looper.getMainLooper());
            observer = new ContentObserver(handler) {
                @Override public void onChange(boolean selfChange) { refresh(); }
            };
            context.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(MEMORY), false, observer);
            context.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(HOME_UP), false, observer);
            refresh();
        } catch (RuntimeException e) {
            initializationFailed = true;
            memoryEnabled = false;
            Log.w(TAG, "Cannot observe gesture settings; Memory disabled", e);
        }
    }

    /** Keep the launcher's effective Home Up master state authoritative. */
    public static boolean gate(Context source, boolean originalEnabled) {
        homeUpEnabled = originalEnabled;
        initialize(source);
        return originalEnabled || enabled(source);
    }

    public static boolean enabled(Context source) {
        initialize(source);
        return !initializationFailed && GesturePolicy.enabled(
                memoryEnabled, homeUpEnabled, secureHomeUp);
    }

    public static void observe(Context source, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) SESSION.begin(enabled(source));
        SESSION.observe(event.getPointerCount(), event.getActionMasked() == MotionEvent.ACTION_CANCEL);
    }

    private static boolean unlocked() {
        UserManager user = context.getSystemService(UserManager.class);
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        return user != null && user.isUserUnlocked() && keyguard != null && !keyguard.isKeyguardLocked();
    }

    /** True means this stream belongs to Memory; never run stale Home Up actions. */
    public static boolean handle(Context source, MultiFingerGestureEvent event,
            InputMonitorProxy monitor, MultiFingerGestureTrailTouchRelay relay) {
        if (!SESSION.owns()) return false;
        int action = SESSION.claim(enabled(source),
                event instanceof MultiFingerGestureEvent.ThreeFingerGesture,
                event.getType().name(), event.isSwipeRepeat());
        if (action == GesturePolicy.NONE) return true;
        try {
            if (!unlocked()) return true;
            final Intent intent = new Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (action == GesturePolicy.SCREEN) {
                intent.setClassName(PACKAGE, PACKAGE + ".feature.capture.ScreenCaptureViewerActivity");
            } else if (action == GesturePolicy.VOICE) {
                // Use Memory's published shortcut; external voice capture is permission-protected.
                intent.setClassName(PACKAGE, PACKAGE + ".feature.main.MainActivity");
                intent.setAction(PACKAGE + ".action.ADD_MEMORY_VOICE");
            } else {
                intent.setClassName(PACKAGE, PACKAGE + ".feature.main.MainActivity");
                intent.setAction(PACKAGE + ".action.ADD_MEMORY_CAMERA");
            }
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                Log.w(TAG, "Memory capture entry is unavailable");
                return true;
            }
            relay.suppressHoldRippleBecauseGestureActionWillRun();
            relay.setAllowMoveTrailAfterResolvedGesture(false);
            monitor.pilferPointers();
            handler.post(new Runnable() {
                @Override public void run() {
                    // Recheck settings and lock state after releasing the input callback.
                    try {
                        if (!enabled(context) || !unlocked()) return;
                        context.startActivity(intent);
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Memory capture launch failed", e);
                    }
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Memory capture dispatch failed", e);
        }
        return true;
    }
}
