/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.mesalabs.unica;

import android.os.Process;
import android.os.SystemProperties;
import android.util.Log;

/** Child-only startup entry. Never loads native code in the parent zygote. */
public final class PifProcessHooks {
    private static volatile boolean active;
    private PifProcessHooks() {}

    public static void initialize() {
        String name = Process.myProcessName();
        if (!"com.google.android.gms.unstable".equals(name)
                && !"com.android.vending".equals(name)) return;
        if (!SystemProperties.getBoolean("persist.sys.unica.pif", true)) return;
        if ("com.android.vending".equals(name)
                && !SystemProperties.getBoolean("persist.sys.pif.vending_build", false)) return;
        try {
            System.loadLibrary("unica_pif");
            active = nativeInitialize(name);
        } catch (LinkageError | RuntimeException e) {
            Log.e("UnicaPIF", "Native startup failed; retaining legacy Java hooks", e);
        }
    }

    public static boolean isActive() { return active; }
    private static native boolean nativeInitialize(String name);
}
