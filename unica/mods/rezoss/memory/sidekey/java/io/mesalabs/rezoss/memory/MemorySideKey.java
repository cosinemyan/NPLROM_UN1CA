// SPDX-License-Identifier: GPL-3.0-or-later
package io.mesalabs.rezoss.memory;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.hardware.input.KeyGestureEvent;
import android.os.PowerManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import com.android.server.input.KeyGestureController;
import com.samsung.android.rune.InputRune;

/** Called only for Samsung's existing Volume Up + Power gesture (type 1001). */
public final class MemorySideKey {
    private static final String TAG = "RezossMemorySideKey";
    private static final SideKeyPolicy POLICY = new SideKeyPolicy();

    private static Intent voiceIntent() {
        return new Intent("com.samsung.android.app.memory.action.ADD_MEMORY_VOICE")
                .setClassName("com.samsung.android.app.memory",
                        "com.samsung.android.app.memory.feature.main.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static boolean eligible(KeyGestureController controller, int user) {
        if (InputRune.PWM_DEVICE_SHUTDOWN_VOLUP_POWER_CHN) return false;
        if (controller.mSecureLockDeviceService != null
                && controller.mSecureLockDeviceService.isSecureLockDeviceEnabled()) return false;
        Context context = controller.mContext;
        if (Settings.Secure.getIntForUser(context.getContentResolver(),
                "memory_volume_up_side_key", 1, user) != 1) return false;
        // Volume Up + Side is Samsung Direct access, distinct from Vol Up + Down.
        // Any assigned target reserves the entire chord for the stock handler.
        if (!TextUtils.isEmpty(Settings.Secure.getStringForUser(context.getContentResolver(),
                "accessibility_direct_access_target_service", user))) return false;
        if (Settings.Global.getInt(context.getContentResolver(), "device_provisioned", 0) != 1
                || Settings.Secure.getIntForUser(context.getContentResolver(),
                        "user_setup_complete", 0, user) != 1) return false;
        PowerManager power = context.getSystemService(PowerManager.class);
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        UserManager users = context.getSystemService(UserManager.class);
        if (power == null || !power.isInteractive() || keyguard == null
                || keyguard.isKeyguardLocked() || keyguard.isDeviceLocked()
                || users == null || !users.isUserUnlocked(UserHandle.of(user))) return false;
        Context userContext = context.createContextAsUser(UserHandle.of(user), 0);
        ResolveInfo resolved = userContext.getPackageManager().resolveActivity(voiceIntent(), 0);
        return resolved != null && resolved.activityInfo != null
                && resolved.activityInfo.enabled && resolved.activityInfo.exported
                && resolved.activityInfo.applicationInfo.enabled;
    }

    public static synchronized boolean handle(final KeyGestureController controller,
                                               KeyGestureEvent event) {
        // The native manager emits START once; key release is a cancelled COMPLETE.
        // Launch only on START, and swallow the matching cleanup without launching again.
        final int user;
        boolean allowed = false;
        try {
            user = ActivityManager.getCurrentUser();
            if (event.getAction() == 1 && !event.isCancelled() && event.isInteractive()) {
                allowed = eligible(controller, user);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot evaluate Memory side-key eligibility", error);
            return POLICY.event(event.getAction(), false) != SideKeyPolicy.STOCK;
        }
        int decision = POLICY.event(event.getAction(), allowed);
        if (decision == SideKeyPolicy.LAUNCH) {
            try {
                controller.mHandler.post(new Runnable() {
                @Override public void run() {
                    try {
                        // Do not carry a request across a user switch, lock or policy change.
                        if (ActivityManager.getCurrentUser() != user || !eligible(controller, user)) return;
                        controller.mContext.startActivityAsUser(voiceIntent(), UserHandle.of(user));
                        Log.i(TAG, "Started Memory voice capture for user " + user);
                    } catch (RuntimeException error) {
                        Log.w(TAG, "Cannot start Memory voice capture", error);
                    }
                }
                });
            } catch (RuntimeException error) {
                Log.w(TAG, "Cannot enqueue Memory voice capture", error);
            }
        }
        return decision != SideKeyPolicy.STOCK;
    }
}
