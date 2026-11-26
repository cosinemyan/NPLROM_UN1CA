#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

# System preloads with extractNativeLibs=true need their libraries staged by
# the ROM builder. Flutter's fallback extracts libflutter.so only, leaving the
# Dart VM snapshot in libapp.so unavailable. Preserve the signed APK unchanged.
_PRIVATE_LM_APK="$WORK_DIR/system/system/app/PrivateLM/PrivateLM.apk"
[[ -f "$_PRIVATE_LM_APK" ]] || ABORT "PrivateLM APK is missing"
_PRIVATE_LM_STAGE=$(mktemp -d /tmp/unica-privatelm-libs.XXXXXX) || return 1
unzip -q "$_PRIVATE_LM_APK" 'lib/arm64-v8a/*.so' -d "$_PRIVATE_LM_STAGE/native" || return 1
for _PRIVATE_LM_REQUIRED in libflutter.so libapp.so; do
    [[ -s "$_PRIVATE_LM_STAGE/native/lib/arm64-v8a/$_PRIVATE_LM_REQUIRED" ]] \
        || ABORT "PrivateLM is missing $_PRIVATE_LM_REQUIRED"
done
mkdir -p "$_PRIVATE_LM_STAGE/system/system/app/PrivateLM/lib" || return 1
mv "$_PRIVATE_LM_STAGE/native/lib/arm64-v8a" \
    "$_PRIVATE_LM_STAGE/system/system/app/PrivateLM/lib/arm64" || return 1
LOG "- Staging PrivateLM ARM64 native libraries"
for _PRIVATE_LM_DIR in PrivateLM PrivateLM/lib PrivateLM/lib/arm64; do
    SET_METADATA "system" "system/app/$_PRIVATE_LM_DIR" 0 0 755 "u:object_r:system_file:s0" || return 1
done
for _PRIVATE_LM_LIB in "$_PRIVATE_LM_STAGE/system/system/app/PrivateLM/lib/arm64/"*.so; do
    ADD_TO_WORK_DIR "$_PRIVATE_LM_STAGE" "system" \
        "system/app/PrivateLM/lib/arm64/${_PRIVATE_LM_LIB##*/}" \
        0 0 644 "u:object_r:system_lib_file:s0" || return 1
done

# Samsung filters preinstalled packages through this allowlist, independently
# of APK placement and runtime permission grants.
_PRIVATE_LM_ALLOWLIST="$WORK_DIR/system/system/etc/sysconfig/allowed-system-preload-apps.xml"
[[ -f "$_PRIVATE_LM_ALLOWLIST" ]] || ABORT "Samsung system preload allowlist is missing"
if ! grep -q -F 'package="com.orailnoor.privatelm"' "$_PRIVATE_LM_ALLOWLIST"; then
    LOG "- Allow PrivateLM as a Samsung system preload"
    EVAL "LC_ALL=C patch --batch --fuzz=0 --no-backup-if-mismatch -p1 -d \"$WORK_DIR/system/system/etc/sysconfig\" < \"$MODPATH/patches/0001-Allow-PrivateLM-system-preload.patch\"" \
        || ABORT "Failed to allow PrivateLM system preload"
fi
unset _PRIVATE_LM_ALLOWLIST _PRIVATE_LM_APK _PRIVATE_LM_STAGE _PRIVATE_LM_REQUIRED _PRIVATE_LM_DIR _PRIVATE_LM_LIB
