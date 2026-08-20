#!/usr/bin/env bash
# Inject custom images from assets/ into the target device's wallpaper-res.apk.
# Keeps stock S23 wallpapers; adds npl_* entries + featured JSON (no Wallpaper_001 replace).

WALLPAPER_APK="system/priv-app/wallpaper-res/wallpaper-res.apk"

if [ ! -f "$WORK_DIR/system/system/priv-app/wallpaper-res/wallpaper-res.apk" ]; then
    LOGE "Target wallpaper-res.apk missing from work dir"
    return 1
fi

LOG "- Decoding wallpaper-res.apk for NPL catalog + featured JSON"
DECODE_APK "system" "$WALLPAPER_APK" || return 1

APK_DIR="$APKTOOL_DIR/system/priv-app/wallpaper-res/wallpaper-res.apk"
"$MODPATH/apply_to_decoded.sh" "$APK_DIR" || return 1

unset APK_DIR
