#!/usr/bin/env bash
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later

SKIPUNZIP=1

if [[ "$TARGET_CODENAME" != "dm3q" || "$SOURCE_PLATFORM_SDK_VERSION" != "37" ]]; then
    LOG "- Skipping FZH3 Hermes compatibility: requires dm3q SDK 37"
    return 0
fi

LOG "- Applying experimental FZH3 Hermes backend; credential verification remains enabled"
ADD_TO_WORK_DIR "$MODPATH" "vendor" "bin/hermesd" 0 2000 755 "u:object_r:hermesd_exec:s0"
ADD_TO_WORK_DIR "$MODPATH" "vendor" "lib64/libhermes_bdbridge.so" 0 0 644 "u:object_r:vendor_file:s0"
ADD_TO_WORK_DIR "$MODPATH" "vendor" "lib64/libhwvault.so" 0 0 644 "u:object_r:vendor_file:s0"
