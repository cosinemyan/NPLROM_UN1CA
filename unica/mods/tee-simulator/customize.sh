#!/usr/bin/env bash
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later

SKIPUNZIP=1

if [[ "$TARGET_CODENAME" != "dm3q" || "$SOURCE_PLATFORM_SDK_VERSION" != "37" ]]; then
    LOG "- Skipping TEE Simulator: requires dm3q with SDK 37"
    return 0
fi
if [ ! -f "$SRC_DIR/unica/mods/keybox/disable" ]; then
    ABORT "Disable the old keybox mod before enabling TEE Simulator" || return 1
fi
if ! python3 "$MODPATH/check_sources.py"; then
    LOG "- TEE Simulator sources changed; rebuilding backend"
    local_teesim_build="$(mktemp -d /tmp/unica-teesim-rom-build.XXXXXX)" || return 1
    python3 "$MODPATH/build_backend.py" --work-dir "$local_teesim_build" || {
        ABORT "TEE Simulator sources changed; backend rebuild failed" || return 1
    }
    python3 "$MODPATH/check_sources.py" || {
        ABORT "TEE Simulator sources changed during rebuild" || return 1
    }
fi
if [ ! -f "$MODPATH/payload.sha256" ] || \
    ! (cd "$MODPATH/payload" && sha256sum --check --status "$MODPATH/payload.sha256"); then
    ABORT "Missing or modified TEE Simulator payload; run $MODPATH/build_backend.py" || return 1
fi
DECODE_APK "system" "system/framework/framework.jar" || return 1
if [ -f "$APKTOOL_DIR/system/framework/framework.jar/smali_classes6/io/mesalabs/unica/KeyboxDbHelper.smali" ]; then
    ABORT "Old Java keybox hooks remain in the decoded framework; start a clean ROM build" || return 1
fi

LOG "- Installing native TEE Simulator with the existing Settings keybox controls"
ADD_TO_WORK_DIR "$MODPATH/payload" "system" "system/bin/unica-teesim-inject" 0 0 755 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "$MODPATH/payload" "system" "system/lib64/libunica_teesim.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "$MODPATH/payload" "system" "system/framework/unica-teesim.jar" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "$MODPATH" "system" "system/etc/init/unica-teesim.rc" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "$MODPATH" "system" "system/etc/unica/tee-simulator/daemon.sh" 0 0 755 "u:object_r:system_file:s0"
python3 "$MODPATH/install_policy.py" "$WORK_DIR" "$MODPATH" || return 1
APPLY_PATCH "system" "system/framework/framework.jar" \
    "$MODPATH/patches/framework.jar/0001-Keep-keybox-Settings-and-PIF-compatibility.patch"

# Normal-UID WebView frontend; same ROM signing key as SecSettings.
local_teesim_cert=aosp
$ROM_IS_OFFICIAL && local_teesim_cert=unica
python3 "$MODPATH/settings-app/build.py" --check --cert-prefix "$local_teesim_cert" || {
    python3 "$MODPATH/settings-app/build.py" --cert-prefix "$local_teesim_cert" || return 1
}
ADD_TO_WORK_DIR "$MODPATH/settings-app/payload" "system" "system/priv-app/TeeSimulatorSettings/TeeSimulatorSettings.apk" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "$MODPATH/settings-app/payload" "system" "system/etc/permissions/privapp-permissions-io.mesalabs.unica.teesettings.xml" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "$MODPATH/settings-app/payload" "system" "system/etc/sysconfig/preinstalled-packages-io.mesalabs.unica.teesettings.xml" 0 0 644 "u:object_r:system_file:s0"
# Samsung rejects privileged APKs absent from its separate preload allowlist.
python3 "$MODPATH/settings-app/preload.py" "$WORK_DIR" || return 1
DECODE_APK "system" "system/priv-app/SecSettings/SecSettings.apk" || return 1
python3 "$MODPATH/settings-app/route_settings.py" \
    "$APKTOOL_DIR/system/priv-app/SecSettings/SecSettings.apk/res/xml/unica_spoof_settings.xml" "$WORK_DIR" || return 1
unset local_teesim_cert
