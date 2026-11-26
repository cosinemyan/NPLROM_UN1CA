// SPDX-License-Identifier: GPL-3.0-or-later
package io.mesalabs.rezoss.poweron;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

public final class PowerScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String action=intent.getAction();
        if (!PowerScheduler.FIRE.equals(action) && !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        PendingResult result=goAsync();
        new Thread(() -> {
            PowerManager.WakeLock wake=null;
            try {
                PowerManager pm=context.getSystemService(PowerManager.class);
                wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"rezoss:power-schedule");
                wake.acquire(15000);
                PowerScheduler.receive(context,intent);
            } catch (Exception error) {
                // No retry/catch-up shutdown: a failed event leaves the device running.
                Log.e("RezossPowerSchedule","Power schedule could not be applied",error);
            } finally {
                if (wake!=null && wake.isHeld()) wake.release();
                result.finish();
            }
        },"RezossPowerSchedule").start();
    }
}
