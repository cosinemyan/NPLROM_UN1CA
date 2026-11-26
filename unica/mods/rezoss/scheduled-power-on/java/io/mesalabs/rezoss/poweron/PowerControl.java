// SPDX-License-Identifier: GPL-3.0-or-later
package io.mesalabs.rezoss.poweron;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import java.lang.reflect.Method;

final class PowerControl {
    static Method method(Context context) throws Exception {
        if (context.checkSelfPermission("android.permission.REBOOT") != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("The ROM has not granted permission to shut down.");
        // Hidden platform API. This package is platform signed, uses the system
        // UID, and requests REBOOT. No shell, root manager or sys.powerctl grant.
        return PowerManager.class.getMethod("shutdown",boolean.class,String.class,boolean.class);
    }
    static void shutdown(Context context) throws Exception {
        method(context).invoke(context.getSystemService(PowerManager.class),false,"userrequested",false);
    }
    private PowerControl() {}
}
