#!/usr/bin/env bash

# Load NPL version config
source "$SRC_DIR/unica/configs/version.sh" || true

# Set NPL ROM identity properties (shown in NPL Settings menu)
SET_PROP "system" "ro.npl.version"    "${NPL_VERSION:-1.0-STABLE}"
SET_PROP "system" "ro.npl.maintainer" "${NPL_MAINTAINER:-Cosine}"
SET_PROP "system" "ro.npl.maintainers" "${NPL_MAINTAINERS:-${NPL_MAINTAINER:-Cosine}}"
SET_PROP "system" "ro.npl.build.date" "$(date +%Y-%m-%d)"

# Keep ro.unica.version compatible so the existing smali reads it
SET_PROP "system" "ro.unica.version" "${ROM_VERSION:-${NPL_VERSION:-1.0-STABLE}}"
SET_PROP "system" "ro.unica.codename" "${NPL_CODENAME:-Sagarmatha}"

# Instrumentation.smali 패치 (One UI 8.x / 최신 안드로이드 시그니처 고려)
SMALI_PATCH "system" "system/framework/framework.jar" \
    "smali/android/app/Instrumentation.smali" "replace" \
    'newApplication(Ljava/lang/Class;Landroid/content/Context;)Landroid/app/Application;' \
    'invoke-virtual {p0, p1}, Landroid/app/Application;->attach(Landroid/content/Context;)V' \
    '    invoke-virtual {p0, p1}, Landroid/app/Application;->attach(Landroid/content/Context;)V\n\n    invoke-static {p1}, Lio/mesalabs/unica/SamsungPropsHooks;->init(Landroid/content/Context;)V' \
    > /dev/null

SMALI_PATCH "system" "system/framework/framework.jar" \
    "smali/android/app/Instrumentation.smali" "replace" \
    'newApplication(Ljava/lang/ClassLoader;Ljava/lang/String;Landroid/content/Context;)Landroid/app/Application;' \
    'invoke-virtual {p0, p3}, Landroid/app/Application;->attach(Landroid/content/Context;)V' \
    '    invoke-virtual {p0, p3}, Landroid/app/Application;->attach(Landroid/content/Context;)V\n\n    invoke-static {p3}, Lio/mesalabs/unica/SamsungPropsHooks;->init(Landroid/content/Context;)V' \
    > /dev/null

DECODE_APK "system" "system/priv-app/SecSettings/SecSettings.apk"
# shellcheck source=/dev/null
. "$MODPATH/apply_to_decoded.sh" || return 1

DECODE_APK "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk"
# shellcheck source=/dev/null
. "$MODPATH/apply_intelligence_decoded.sh" || return 1

# Show Vulkan renderer toggle if required
if [[ "$(GET_PROP "ro.hwui.use_vulkan")" != "true" ]]; then
    SET_PROP "system" "persist.sys.unica.vulkan" "false"
fi