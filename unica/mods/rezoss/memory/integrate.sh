# SPDX-License-Identifier: GPL-3.0-or-later
# Sourced by mods/rezoss; patch the exact Memory .48 baseline for this ROM.
# shellcheck disable=SC2154
if [[ -f "$MODPATH/memory/disable" ]]; then
    LOG "- Samsung Memory integration is disabled"
    return 0
fi
if [[ "$TARGET_CODENAME" != "dm3q" ]]; then
    LOG "- Samsung Memory: skipping unsupported target $TARGET_CODENAME"
    return 0
fi

REZOSS_MEMORY_ROOT="$MODPATH/memory"
REZOSS_MEMORY_LAUNCHER="system/priv-app/TouchWizHome_2017/TouchWizHome_2017.apk"
if [[ "$(sha256sum "$WORK_DIR/system/$REZOSS_MEMORY_LAUNCHER" | cut -d ' ' -f 1)" != \
        "4630968289288d355aa3f8884ef45679ced56c0b3c0925d96a9a77ca6fcf47cf" ]]; then
    LOGE "Memory gestures require the validated One UI Home 18.0.07.53; revalidate the patch for this launcher"
    return 1
fi

# The required Java shared library must be registered as well as present.
if [[ ! -f "$WORK_DIR/system/system/framework/samsungkeystoreutils.jar" ]] ||
        [[ ! -f "$WORK_DIR/system/system/etc/permissions/samsungkeystoreutils.xml" ]]; then
    LOGE "Samsung Memory requires samsungkeystoreutils.jar and its library XML"
    return 1
fi

# Samsung skips system APKs absent from this list, even with valid permissions.
REZOSS_MEMORY_PRELOAD_CONFIG="$WORK_DIR/system/system/etc/sysconfig/allowed-system-preload-apps.xml"
if [[ ! -f "$REZOSS_MEMORY_PRELOAD_CONFIG" ]] ||
        [[ "$(grep -c '^[[:space:]]*</config>[[:space:]]*$' "$REZOSS_MEMORY_PRELOAD_CONFIG")" != 1 ]]; then
    LOGE "Samsung Memory requires a valid system-preload allowlist"
    return 1
fi

# Check the untouched .48 baseline before applying the isolated AI adapter.
MEMORY_SHA256="ea088fe8329289b9b1494d805ac0555eea9f888757cba394c99b429985f3939a"
if [[ "$(cat "$REZOSS_MEMORY_ROOT/system/system/priv-app/Memory/Memory.apk.00" \
        "$REZOSS_MEMORY_ROOT/system/system/priv-app/Memory/Memory.apk.01" | sha256sum | cut -d ' ' -f 1)" != "$MEMORY_SHA256" ]]; then
    LOGE "Samsung Memory APK chunks are missing or do not match the investigated APK"
    return 1
fi

if [[ -d "$APKTOOL_DIR/system/${REZOSS_MEMORY_LAUNCHER//system\//}" ]]; then
    LOGE "Launcher is already decoded by another mod; review patch order before Memory integration"
    return 1
fi
# Reuse Samsung's existing Volume Up + Power rule; configured accessibility
# targets and regional shutdown retain priority. Standard services.jar patching
# keeps this composable with the other framework mods in the shared decode.
LOG "- Adding Memory Volume Up + Side voice capture (accessibility has priority)"
DECODE_APK "system" "system/framework/services.jar" || return 1
python3 "$REZOSS_MEMORY_ROOT/sidekey/check-target.py" \
    "$APKTOOL_DIR/system/framework/services.jar" || return 1
LC_ALL=C patch --dry-run --fuzz=0 -p1 -N --forward \
    -d "$APKTOOL_DIR/system/framework/services.jar" \
    < "$REZOSS_MEMORY_ROOT/sidekey/0001-Add-Memory-volume-up-side-key.patch" || return 1
APPLY_PATCH "system" "system/framework/services.jar" \
    "$REZOSS_MEMORY_ROOT/sidekey/0001-Add-Memory-volume-up-side-key.patch" || return 1
REZOSS_MEMORY_STAGE=$(mktemp -d /tmp/rezoss-memory-stage.XXXXXX) || return 1
mkdir -p "$REZOSS_MEMORY_STAGE/system/system/priv-app/TouchWizHome_2017" || return 1
mkdir -p "$REZOSS_MEMORY_STAGE/system/system/priv-app/Memory" || return 1
cat "$REZOSS_MEMORY_ROOT/system/system/priv-app/Memory/Memory.apk.00" \
    "$REZOSS_MEMORY_ROOT/system/system/priv-app/Memory/Memory.apk.01" \
    > "$REZOSS_MEMORY_STAGE/Memory-v48-original.apk" || return 1
LOG "- Adding Memory .48 UN1CA AI backend and isolated failure fallback"
bash "$REZOSS_MEMORY_ROOT/prepare-memory.sh" "$REZOSS_MEMORY_STAGE/Memory-v48-original.apk" \
    "$REZOSS_MEMORY_STAGE/system/system/priv-app/Memory/Memory.apk" || return 1
bash "$REZOSS_MEMORY_ROOT/prepare-launcher.sh" "$WORK_DIR/system/$REZOSS_MEMORY_LAUNCHER" \
    "$REZOSS_MEMORY_STAGE/system/$REZOSS_MEMORY_LAUNCHER" || return 1

SET_METADATA "system" "system/priv-app/Memory" 0 0 755 "u:object_r:system_file:s0" || return 1
ADD_TO_WORK_DIR "$REZOSS_MEMORY_STAGE" "system" "system/priv-app/Memory/Memory.apk" 0 0 644 "u:object_r:system_file:s0" || return 1
ADD_TO_WORK_DIR "$REZOSS_MEMORY_ROOT" "system" "system/etc/permissions/privapp-permissions-com.samsung.android.app.memory.xml" 0 0 644 "u:object_r:system_file:s0" || return 1
ADD_TO_WORK_DIR "$REZOSS_MEMORY_ROOT" "system" "system/etc/permissions/signature-permissions-com.samsung.android.app.memory.xml" 0 0 644 "u:object_r:system_file:s0" || return 1
if ! grep -q 'package="com.samsung.android.app.memory"' "$REZOSS_MEMORY_PRELOAD_CONFIG"; then
    LOG "- Allowing Samsung Memory system preload"
    sed -i '/^[[:space:]]*<\/config>[[:space:]]*$/i\    <allowed-system-preload package="com.samsung.android.app.memory"/>' "$REZOSS_MEMORY_PRELOAD_CONFIG" || return 1
fi

LOG "- Adding Memory three-finger down/screen, right/voice, left/camera gestures"
ADD_TO_WORK_DIR "$REZOSS_MEMORY_STAGE" "system" "$REZOSS_MEMORY_LAUNCHER" 0 0 644 "u:object_r:system_file:s0" || return 1
# Compiled profiles no longer match the patched gesture DEX.
if [[ -d "$WORK_DIR/system/${REZOSS_MEMORY_LAUNCHER%/*}/oat" ]]; then
    DELETE_FROM_WORK_DIR "system" "${REZOSS_MEMORY_LAUNCHER%/*}/oat" || return 1
fi
for REZOSS_MEMORY_PROFILE in "$REZOSS_MEMORY_LAUNCHER.prof" "$REZOSS_MEMORY_LAUNCHER.bprof"; do
    if [[ -f "$WORK_DIR/system/$REZOSS_MEMORY_PROFILE" ]]; then
        DELETE_FROM_WORK_DIR "system" "$REZOSS_MEMORY_PROFILE" || return 1
    fi
done

unset MEMORY_SHA256 REZOSS_MEMORY_ROOT REZOSS_MEMORY_LAUNCHER REZOSS_MEMORY_STAGE REZOSS_MEMORY_PROFILE REZOSS_MEMORY_PRELOAD_CONFIG
