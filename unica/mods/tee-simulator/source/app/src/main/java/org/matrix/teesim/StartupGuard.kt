// Copyright (c) 2026 Rezoss
// SPDX-License-Identifier: GPL-3.0-or-later
package org.matrix.teesim

import java.io.File

/** No state writes: an enabled standalone injector owns the boot, so the ROM stays idle. */
object StartupGuard {
    fun check(data: File = File("/data")) {
        val adb = File(data, "adb")
        if (!adb.exists()) return
        val entries = requireNotNull(adb.list()) { "Cannot inspect root-manager directory" }
        for (name in listOf("modules", "modules_update")) {
            if (name !in entries) continue
            val modules = File(adb, name)
            val names = requireNotNull(modules.list()) { "Cannot inspect root-manager modules" }
            for (id in listOf("teesim", "unica_teesim_runtime_fix", "unica_teesim_runtime_hotfix")) {
                if (id !in names) continue
                val markers = requireNotNull(File(modules, id).list()) {
                    "Cannot inspect competing injector"
                }
                require("disable" in markers || "remove" in markers) {
                    "Competing TEE injector enabled; ROM injection blocked"
                }
            }
        }
    }
}
