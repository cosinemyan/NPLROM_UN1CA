#!/usr/bin/env bash
# Native port of BlueOutside / Dressroom-Advanced.
# Integration copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later

DRESSROOM_ADVANCED_APPLY_STAGE()
{
    local STAGE="${1:-}"
    local PORT_DIR="$SRC_DIR/unica/mods/Dressroom-Advanced"
    local PARTITION="system" APK_FILE DECODED PREPARED PATCH_FILE
    [[ ! -f "$PORT_DIR/disable" ]] || return 0
    [[ "$TARGET_CODENAME" == "dm3q" && "$TARGET_PLATFORM_SDK_VERSION" == "37" ]] || return 0
    case "$STAGE" in
        dressroom) APK_FILE="system/priv-app/DressRoom/DressRoom.apk" ;;
        aod) APK_FILE="system/priv-app/AODService_v80/AODService_v80.apk" ;;
        systemui) PARTITION="system_ext"; APK_FILE="priv-app/SystemUI/SystemUI.apk" ;;
        wallpaper) APK_FILE="system/priv-app/SpriteWallpaper/SpriteWallpaper.apk" ;;
        *) ABORT "Unknown Dressroom-Advanced stage: $STAGE"; return 1 ;;
    esac
    DECODE_APK "$PARTITION" "$APK_FILE" || return 1
    DECODED="$APKTOOL_DIR/$PARTITION/${APK_FILE//system\//}"
    PREPARED=$(mktemp -d /tmp/dressroom-native-full.XXXXXX) || return 1
    LOG "- Dressroom-Advanced by @BlueOutside: full native $STAGE integration"
    python3 "$PORT_DIR/fullport/prepare.py" "$STAGE" "$DECODED" "$PREPARED/patches" --implementation || return 1
    for PATCH_FILE in "$PREPARED/patches/"*.patch; do
        APPLY_PATCH "$PARTITION" "$APK_FILE" "$PATCH_FILE" || return 1
    done
}
DRESSROOM_ADVANCED_APPLY_STAGE "${1:-}" || return 1
