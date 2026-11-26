#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Sourced by Rezoss customize.sh only for the opt-in dm3q configuration.
NUDGE_ROOT="$MODPATH/aioskernel/native-now-nudge"
LOG "- Build AIOS local backend: ${UNICA_LOCAL_LLM_BACKEND:-gemma-gpu}; retain optional APIs"
NUDGE_BUILD=$(mktemp -d "$TMP_DIR/native-nudge.XXXXXX") || return 1
EVAL "bash \"$NUDGE_ROOT/build_rom_backend.sh\" \"$NUDGE_BUILD/AIOSKernelService.apk\"" || return 1
mkdir -p "$NUDGE_BUILD/system/priv-app/AIOSKernelService" || return 1
mv "$NUDGE_BUILD/AIOSKernelService.apk" "$NUDGE_BUILD/system/priv-app/AIOSKernelService/AIOSKernelService.apk" || return 1
# Bundled system APKs need their native library directory populated at build
# time. PackageManager extraction used by the data-APK trial is insufficient.
unzip -q "$NUDGE_BUILD/system/priv-app/AIOSKernelService/AIOSKernelService.apk" 'lib/arm64-v8a/*' -d "$NUDGE_BUILD/native" || return 1
mkdir -p "$NUDGE_BUILD/system/priv-app/AIOSKernelService/lib" || return 1
mv "$NUDGE_BUILD/native/lib/arm64-v8a" "$NUDGE_BUILD/system/priv-app/AIOSKernelService/lib/arm64" || return 1
ADD_TO_WORK_DIR "$NUDGE_BUILD" "system" "system/priv-app/AIOSKernelService/AIOSKernelService.apk" 0 0 644 "u:object_r:system_file:s0" || return 1
for NUDGE_DIR in AIOSKernelService AIOSKernelService/lib AIOSKernelService/lib/arm64; do
    SET_METADATA "system" "system/priv-app/$NUDGE_DIR" 0 0 755 "u:object_r:system_file:s0" || return 1
done
for NUDGE_LIB in "$NUDGE_BUILD/system/priv-app/AIOSKernelService/lib/arm64/"*.so; do
    ADD_TO_WORK_DIR "$NUDGE_BUILD" "system" "system/priv-app/AIOSKernelService/lib/arm64/${NUDGE_LIB##*/}" 0 0 644 "u:object_r:system_lib_file:s0" || return 1
done
# This ELF is executed as a subprocess. platform_app_36 may map shared libs
# but cannot execute_no_trans a system_lib_file; use the executable's label.
SET_METADATA "system" "system/priv-app/AIOSKernelService/lib/arm64/libnudge_runner.so" 0 0 755 "u:object_r:system_file:s0" || return 1
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/permissions/privapp-permissions-com.samsung.android.aioskernelservice.xml" 0 0 644 "u:object_r:system_file:s0" || return 1
ADD_TO_WORK_DIR "pa2qxxx" "system" "system/etc/sysconfig/aioskernelservice.xml" 0 0 644 "u:object_r:system_file:s0" || return 1
# Preserve the ROM stub. The full Samsung language package is updated later.
LOG "- Native Now Nudge requires the later OfflineLanguageModel 4.0.01.24 update"
# Remove the previous bundled weights when rebuilding an existing work tree.
DELETE_FROM_WORK_DIR "system" "system/etc/unica/now-nudge-v73" || return 1
if [[ "${UNICA_LOCAL_LLM_BACKEND:-gemma-gpu}" == "gemma-gpu" ]]; then
    LOG "- Import the separate Gemma 4 E2B model in AI backend settings > Local model setup"
else
    LOG "- Separate QWEN model APK supplies verified local assets for the rollback backend"
fi
SET_PROP "system" "ro.unica.local_llm" "${UNICA_LOCAL_LLM_BACKEND:-gemma-gpu}" || return 1
SET_PROP "system" "ro.unica.now_nudge_v73" "1" || return 1
SET_PROP "system" "persist.sys.unica.enable_aioskernelservice" "1" || return 1
unset NUDGE_ROOT NUDGE_BUILD NUDGE_LIB NUDGE_DIR
