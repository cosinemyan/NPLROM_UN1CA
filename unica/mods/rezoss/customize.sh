SKIPUNZIP=1

LOG_STEP_IN "- Rezoss experimental mods"

source "$MODPATH/scheduled-power-on/integrate.sh" || return 1

# This POC is restricted to the tested S23U target and retains Qwen fallback.
source "$MODPATH/aioskernel/native-now-nudge/config.sh" || return 1
if [[ "$UNICA_ENABLE_NATIVE_NOW_NUDGE" == "true" ]]; then
    [[ "$TARGET_CODENAME" == "dm3q" ]] || ABORT "Native Now Nudge is tested only on dm3q"
    [[ "${UNICA_ENABLE_SAMSUNG_AICORE:-true}" == "true" ]] || ABORT "Native Now Nudge requires SamsungAiCore"
    [[ "${UNICA_ENABLE_UNSAFE_S26U_QNN_EXPERIMENT:-false}" != "true" ]] || ABORT "Do not combine native Now Nudge with the donor QNN experiment"
fi

# =============================================================================
# Base Overlay
# =============================================================================
ADD_TO_WORK_DIR "$MODPATH" "system" "." 0 0 755 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "$MODPATH" "system_ext" "." 0 0 755 "u:object_r:system_file:s0"
source "$MODPATH/memory/integrate.sh" || return 1
LOG "- Adding mountless AOSP zip boot animation support"
ADD_TO_WORK_DIR "$MODPATH/bootanimation_zip" "system" \
    "system/bin/bootanimation_zip" 0 2000 755 "u:object_r:bootanim_exec:s0"
# The branded payload retains the stock SONAME through its loader alias.
ADD_TO_WORK_DIR "$MODPATH/bootanimation_zip" "system" \
    "system/lib64/libbootanimation_rezoss_custom.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "$MODPATH/bootanimation_zip" "system" \
    "system/lib64/libbootanimation.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" \
    "system/etc/default-permissions/default-permissions-com.samsung.android.smartsuggestions.xml" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" \
    "system/etc/permissions/privapp-permissions-com.samsung.android.smartsuggestions.xml" 0 0 644 "u:object_r:system_file:s0"
# Restore the stock S23U One UI 9 packages after debloat and local overlays.
# Samsung Messages already advertises nownudge.inappnudge.revision=1.
LOG "- Using S23U One UI 9 sharing, keyboard and messaging apps"
for f in "app/AllShareAware" "app/HoneyBoard" "priv-app/ShareLive" "priv-app/SamsungMessages"; do
    ADD_TO_WORK_DIR "$SOURCE_FIRMWARE" "system" "system/$f" 0 0 755 "u:object_r:system_file:s0"
done
# Debloat also removes Messages' permission XMLs; restore them with the APK.
# The privileged allowlist grants MANAGE_USERS for its startup user query.
for f in \
    "default-permissions/default-permissions-com.samsung.android.messaging.xml" \
    "permissions/privapp-permissions-com.samsung.android.messaging.xml"; do
    ADD_TO_WORK_DIR "$SOURCE_FIRMWARE" "system" "system/etc/$f" 0 0 644 "u:object_r:system_file:s0"
done
LOG "- Patching SmartSuggestions Developer Mode access"
REZOSS_SMARTSUGGESTIONS_APK="$WORK_DIR/system/system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk"
REZOSS_SMARTSUGGESTIONS_TMP="$TMP_DIR/rezoss_smartsuggestions_dev_mode"
REZOSS_SMARTSUGGESTIONS_CERT_PREFIX="aosp"
$ROM_IS_OFFICIAL && REZOSS_SMARTSUGGESTIONS_CERT_PREFIX="unica"
EVAL "rm -rf \"$REZOSS_SMARTSUGGESTIONS_TMP\" && mkdir -p \"$REZOSS_SMARTSUGGESTIONS_TMP\""
EVAL "unzip -q -p \"$REZOSS_SMARTSUGGESTIONS_APK\" classes11.dex > \"$REZOSS_SMARTSUGGESTIONS_TMP/classes11.dex\""
EVAL "unzip -q -p \"$REZOSS_SMARTSUGGESTIONS_APK\" classes13.dex > \"$REZOSS_SMARTSUGGESTIONS_TMP/classes13.dex\""
EVAL "unzip -q -p \"$REZOSS_SMARTSUGGESTIONS_APK\" classes14.dex > \"$REZOSS_SMARTSUGGESTIONS_TMP/classes14.dex\""
EVAL "unzip -q -p \"$REZOSS_SMARTSUGGESTIONS_APK\" classes15.dex > \"$REZOSS_SMARTSUGGESTIONS_TMP/classes15.dex\""
EVAL "unzip -q -p \"$REZOSS_SMARTSUGGESTIONS_APK\" AndroidManifest.xml > \"$REZOSS_SMARTSUGGESTIONS_TMP/AndroidManifest.xml\""
EVAL "python3 \"$MODPATH/smartsuggestions/patch_contents_visibility_dex.py\" \"$REZOSS_SMARTSUGGESTIONS_TMP/classes14.dex\""
EVAL "python3 \"$MODPATH/smartsuggestions/patch_now_nudge_dex.py\" \"$REZOSS_SMARTSUGGESTIONS_TMP/classes15.dex\""
EVAL "python3 \"$MODPATH/smartsuggestions/patch_smartsuggestions_dev_mode.py\" \"$REZOSS_SMARTSUGGESTIONS_TMP/classes11.dex\" \"$REZOSS_SMARTSUGGESTIONS_TMP/classes13.dex\" \"$REZOSS_SMARTSUGGESTIONS_TMP/classes15.dex\" \"$REZOSS_SMARTSUGGESTIONS_TMP/AndroidManifest.xml\""
EVAL "cp -a \"$REZOSS_SMARTSUGGESTIONS_APK\" \"$REZOSS_SMARTSUGGESTIONS_TMP/SamsungSmartSuggestions.apk\""
EVAL "cd \"$REZOSS_SMARTSUGGESTIONS_TMP\" && zip -q -0 \"SamsungSmartSuggestions.apk\" classes11.dex classes13.dex classes14.dex classes15.dex AndroidManifest.xml"
EVAL "signapk \"$SRC_DIR/security/${REZOSS_SMARTSUGGESTIONS_CERT_PREFIX}_platform.x509.pem\" \"$SRC_DIR/security/${REZOSS_SMARTSUGGESTIONS_CERT_PREFIX}_platform.pk8\" \"$REZOSS_SMARTSUGGESTIONS_TMP/SamsungSmartSuggestions.apk\" \"$REZOSS_SMARTSUGGESTIONS_TMP/SamsungSmartSuggestions.signed.apk\""
EVAL "mv -f \"$REZOSS_SMARTSUGGESTIONS_TMP/SamsungSmartSuggestions.signed.apk\" \"$REZOSS_SMARTSUGGESTIONS_APK\""
SET_METADATA "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" 0 0 644 "u:object_r:system_file:s0"
EVAL "rm -rf \"$REZOSS_SMARTSUGGESTIONS_TMP\""
LOG "- Patching SmartSuggestions native library extraction"
REZOSS_SMARTSUGGESTIONS_DECODED="$APKTOOL_DIR/system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk"
EVAL "rm -rf \"$REZOSS_SMARTSUGGESTIONS_DECODED\""
DECODE_APK "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk"
EVAL "sed -i 's/android:extractNativeLibs=\"false\"/android:extractNativeLibs=\"true\"/g' \"$REZOSS_SMARTSUGGESTIONS_DECODED/AndroidManifest.xml\""
if ! grep -q 'android:extractNativeLibs="true"' "$REZOSS_SMARTSUGGESTIONS_DECODED/AndroidManifest.xml"; then
    LOGE "Failed to enable native library extraction for SamsungSmartSuggestions.apk"
    return 1
fi
unset REZOSS_SMARTSUGGESTIONS_DECODED
LOG "- Applying SmartSuggestions China compatibility patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0010-China-SmartSuggestions-compatibility.patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0011-Enable-Netflix-custom-card-helpers-and-forced-skills.patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0012-Bind-event-travel-custom-card-sources.patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0013-Allow-calendar-travel-custom-card-prompts.patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0025-Remove-Custom-Card-category-limit.patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0014-Allow-Now-Brief-recall-calendar-reminder-fallback.patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0015-Force-Now-Brief-non-empty-fallback.patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0016-Allow-more-Now-Nudge-messenger-packages.patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0017-Preserve-Now-Nudge-pass-package-message.patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0018-Route-notification-assistant-requests-to-Now-Nudge.patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0019-Log-Now-Nudge-assistant-request-extra-keys.patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0020-Route-Now-Nudge-replies-through-UN1CA-Qwen-provider.patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0026-Connect-in-app-and-notification-replies-to-RezossAI.patch"
APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "$MODPATH/smartsuggestions/SamsungSmartSuggestions.apk/0027-Preserve-WhatsApp-conversation-nudge-on-unknown-autofill.patch"
if [[ "$UNICA_ENABLE_NATIVE_NOW_NUDGE" == "true" ]]; then
    LOG "- Integrating native V73 Now Nudge after Rezoss patch 0026"
    for REZOSS_NUDGE_PATCH in "$MODPATH/aioskernel/native-now-nudge/"000[123456789]-*.patch; do
        APPLY_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" "$REZOSS_NUDGE_PATCH"
    done
    EVAL "bash \"$MODPATH/aioskernel/native-now-nudge/build_client_helper.sh\" \"$APKTOOL_DIR/system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk\"" || return 1
    # Paradigm applied its incompatible native-model bypass before this mod.
    # Supply recall-specific inference while keeping Samsung's parser/validators.
    APPLY_PATCH "system" "system/priv-app/SemanticSearchCore/SemanticSearchCore.apk" \
        "$MODPATH/aioskernel/native-now-nudge/ssco-patches/0001-Use-compatible-recall-inference.patch"
    EVAL "bash \"$MODPATH/aioskernel/native-now-nudge/build_ssco_helper.sh\" \"$APKTOOL_DIR/system/priv-app/SemanticSearchCore/SemanticSearchCore.apk\"" || return 1
    DECODE_APK "system_ext" "priv-app/SystemUI/SystemUI.apk" || return 1
    APPLY_PATCH "system_ext" "priv-app/SystemUI/SystemUI.apk" \
        "$MODPATH/aioskernel/native-now-nudge/shade-reply/0001-Stamp-active-RemoteInput-editor-identity.patch"
    EVAL "bash \"$MODPATH/aioskernel/native-now-nudge/shade-reply/build_systemui_helper.sh\" \"$APKTOOL_DIR/system_ext/priv-app/SystemUI/SystemUI.apk\"" || return 1
    unset REZOSS_NUDGE_PATCH
fi
# Temporarily disabled for CHN SmartSuggestions testing.
# LOG "- Patching SmartSuggestions China custom-card feature gates"
# SMALI_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
#     "smali_classes15/com/samsung/android/smartsuggestions/featureconfig/rune/Rune.smali" "return" \
#     'getSUPPORT_AI_SUGGESTION_CUSTOM_CARD_CHN()Z' \
#     'true' \
#     > /dev/null
# SMALI_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
#     "smali_classes15/com/samsung/android/smartsuggestions/featureconfig/rune/Rune.smali" "return" \
#     'getSUPPORT_AI_SUGGESTION_EDITABLE_CARD_CHN()Z' \
#     'true' \
#     > /dev/null
LOG "- Patching SmartSuggestions Now Nudge experimental runtime gates"
SMALI_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "smali_classes15/com/samsung/android/smartsuggestions/service/screenintelligence/setting/NowNudgesSettingHelper.smali" "return" \
    'isAvailableAccount(Landroid/content/Context;)Z' \
    'true' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "smali_classes15/com/samsung/android/smartsuggestions/service/screenintelligence/setting/NowNudgesSettingHelper.smali" "return" \
    'isOnNowNudgesInApp(Landroid/content/Context;)Z' \
    'true' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "smali_classes15/com/samsung/android/smartsuggestions/service/screenintelligence/setting/NowNudgesSettingHelper.smali" "return" \
    'isNowNudgesSwitchOn(Landroid/content/Context;)Z' \
    'true' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "smali_classes15/com/samsung/android/smartsuggestions/service/autofill/AutofillNudgeProvider.smali" "return" \
    'checkAutofillSupported()Z' \
    'true' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "smali_classes15/com/samsung/android/smartsuggestions/service/autofill/AutofillNudgeProvider.smali" "return" \
    'checkKeyboardLocale()Z' \
    'true' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" \
    "smali_classes15/com/samsung/android/smartsuggestions/service/policy/AutofillPolicyManager.smali" "return" \
    'isNudgeAllowed(Landroid/content/ComponentName;)Z' \
    'true' \
    > /dev/null
# S23U One UI 9 Messages declares nownudge.inappnudge.revision=1.
# Keep the stock metadata lookup and its missing-package fallback.
unset REZOSS_SMARTSUGGESTIONS_APK REZOSS_SMARTSUGGESTIONS_TMP REZOSS_SMARTSUGGESTIONS_CERT_PREFIX

# =============================================================================
# Device Care / Smart Manager CN
# =============================================================================
DELETE_FROM_WORK_DIR "system" "system/priv-app/SmartManager_v5"
DELETE_FROM_WORK_DIR "system" "system/app/SmartManager_v6_DeviceSecurity"
DELETE_FROM_WORK_DIR "system" "system/etc/permissions/privapp-permissions-com.samsung.android.lool.xml"
DELETE_FROM_WORK_DIR "system" "system/etc/permissions/signature-permissions-com.samsung.android.lool.xml"
DELETE_FROM_WORK_DIR "system" "system/etc/permissions/privapp-permissions-com.samsung.android.sm.devicesecurity_v6.xml"

SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_SECURITY_CONFIG_DEVICEMONITOR_PACKAGE_NAME" "com.samsung.android.sm.devicesecurity.tcm"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_SMARTMANAGER_CONFIG_PACKAGE_NAME" "com.samsung.android.sm_cn"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_COMMON_SUPPORT_NAL_PRELOADAPP_REGULATION" "TRUE"

# =============================================================================
# Floating Features - Camera
# =============================================================================
# Stock S23U does not define CAMERA_CONFIG_AUTOFRAMING; forcing "uhd" crashes smooth_transition on dm3q.
# SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_CONFIG_AUTOFRAMING" "uhd"
# SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_CONFIG_VENDOR_LIB_INFO" "de_flicker.arcsoft.v1,de_flicker_hdr.arcsoft.v1,food.samsung.v1,face_landmark.arcsoft.v2_1,beauty.samsung.v4,facial_restoration.arcsoft.v1,facial_attribute.samsung.v1,human_tracking_hand.arcsoft.v4,fr_tracking.arcsoft.v1,smart_scan.samsung.v2,aimode.samsung.v2,aimfisp.samsung.v1,ai_clear_zoom.arcsoft.v1,macro_raw_sr.arcsoft.v1,super_resolution_raw.arcsoft.v2,aebhdr.arcsoft.v1,hybridhdr.arcsoft.v1,single_bokeh.samsung.v2,super_night.mpi.v2,swuwdc.arcsoft.v1,event_detection.samsung.v2,selfie_correction.samsung.v1,dual_bokeh.samsung.v1_1,image_codec.samsung.v2,pro_single_rgb.mpi.v1,image_enhance.arcsoft.v1,localtm.samsung.v1_1,stereo_photo.samsung.v1,compressed_raw_decoder.samsung.v1"
# Keep S26U autofocus/fusion/SW-binning paths disabled on dm3q; they crash the S23U vendor camera stack.
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_AFSENSITIVITY" "FALSE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_AFSMARTTRACKING" "FALSE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_AFSPEED" "FALSE"
# Experimental cinematic/natural-blur controls; retain the stock S23U portrait backend.
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_CINEMATIC_PORTRAITVIDEO" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_FUSION_HIGH_RESOLUTION" "FALSE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_HIGH_RESOLUTION_SWBINNING" "FALSE"
# These S26U capture pipelines are not exposed by the stock dm3q camera stack.
# Advertising them makes SamsungCamera submit an unsupported night request and
# triggers CHI recovery (Usecase::DumpSystemEvent) in the camera provider.
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_NIGHT_INTEGRATED_PHOTO_MODE" "FALSE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_SUPER_NIGHT_DRAFT_RAW" "FALSE"
# WARNING: Might Cause Crash
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_VDIS_ON_MOTIONPHOTO" "FALSE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_VIDEO_SOFTENING" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_DUAL_PORTRAITVIDEO" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_EDITABLE_PORTRAITVIDEO" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_PORTRAIT_INTELLIGENT_OPTIMIZATION_AI_ISP" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_SEAMLESS_PORTRAITVIDEO" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_AUTO_EXPOSURE_LIMIT" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_MOTIONPHOTO_AUTO_TRIM_MODE" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_MOTIONPHOTO_SOUND_TIMING" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_MOTIONPHOTO_SOUND_TYPE" "TRUE"

# =============================================================================
# Floating Features - Display / Gallery / Media
# =============================================================================
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_LCD_SUPPORT_DOZE_AP_SLEEP" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_MMFW_SUPPORT_CFA_CODEC" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_MMFW_SUPPORT_VIDEO_SEARCH" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_MMFW_SUPPORT_VIDEOSERACH" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_GALLERY_CONFIG_LIVEFOCUS_EFFECT_DUAL_BOKEH" "BLUR,EFFECT,BIGBOKEH,PORTRAIT,RELIGHT,REFOCUS,LIGHT360,GENERIC_REFOCUS,GENERIC_REEDIT"
#SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_GALLERY_CONFIG_AI_EXPANSION" "AI_Timelapse,singletake.hidt.support.on,singletake.capture.support.off,singletake.video_res.config.fhd,singletake.video.previous_record"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_GRAPHICS_SUPPORT_REDUCE_FLASH_LIGHT" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_GRAPHICS_SUPPORT_TOUCH_FAST_RESPONSE" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_LCD_CONFIG_AOD_BRIGHTNESS_ANIMATION" "1"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_LCD_CONFIG_AOD_FULLSCREEN" "1"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_MMFW_SUPPORT_HDR2SDR_MAX_8K" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_MMFW_SUPPORT_HIERARCHICAL_B_ENCODING" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_MMFW_SUPPORT_LONGEXPOSURE_EFFECT_10BIT" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_MMFW_SUPPORT_PHOTOHDR" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_MMFW_SUPPORT_TARGET_TRACKING" "TRUE"
# WARNING: The S26U FM/unified clipper route previously crashed PhotoEditor_AIFull
# on the S23U SNAP/vendor stack during contour/lasso execution.
# SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_VIDEO_CONFIG_VIDEO_CLIPPING_MODE" "NPU,unifiedclipper,foundational_segmentation"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_VIDEO_CONFIG_VIDEO_CLIPPING_MODE" "NPU"

# =============================================================================
# Floating Features - Galaxy AI / Framework / Search
# =============================================================================
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_ACCESSIBILITY_SUPPORT_AI_CORE_IMAGE_DESCRIPTION" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_COMMON_CONFIG_WAKEUP_MULTIAGENT_VERSION" "1"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_FRAMEWORK_CONFIG_APPFUNCTION_AGENT_APPLIST" "ai.perplexity.app.android"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_FRAMEWORK_SUPPORT_COMPUTER_CONTROL" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_FRAMEWORK_SUPPORT_PROVIDE_TSP_RAWDATA" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_SAMSUNG_SEARCH_SEMANTIC_SEARCH_VERSION" "510"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_GENAI_CONFIG_FOUNDATION_MODEL" "3B"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_GENAI_SUPPORT_TIME_WEATHER_WALLPAPER" "V7"

# =============================================================================
# Helpers
# =============================================================================
_REZOSS_SET_VENDOR_FLOATING_FEATURE_CONFIG()
{
    local CONFIG="$1"
    local VALUE="$2"
    local FILE="$WORK_DIR/vendor/etc/floating_feature.xml"

    if [ ! -f "$FILE" ]; then
        LOGW "File not found: ${FILE//$WORK_DIR/}"
        return 0
    fi

    if grep -q "$CONFIG" "$FILE"; then
        LOG "- Replacing \"$CONFIG\" config with \"$VALUE\" in /vendor/etc/floating_feature.xml"
        sed -i "$(sed -n "/<${CONFIG}>/=" "$FILE") c\ \ \ \ <${CONFIG}>${VALUE}</${CONFIG}>" "$FILE"
    else
        LOG "- Adding \"$CONFIG\" config with \"$VALUE\" in /vendor/etc/floating_feature.xml"
        sed -i "/<\/SecFloatingFeatureSet>/d" "$FILE"
        if ! grep -q "Added by unica/mods/rezoss" "$FILE"; then
            echo "    <!-- Added by unica/mods/rezoss/customize.sh -->" >> "$FILE"
        fi
        echo "    <${CONFIG}>${VALUE}</${CONFIG}>" >> "$FILE"
        echo "</SecFloatingFeatureSet>" >> "$FILE"
    fi
}

_REZOSS_APPEND_UNIQUE_LINE()
{
    local FILE="$1"
    local LINE="$2"

    if ! grep -q -F "$LINE" "$FILE"; then
        echo "$LINE" >> "$FILE"
    fi
}

_REZOSS_ENSURE_FOUNDATIONAL_SEGMENTATION_SYSTEM_CONFIGS()
{
    local PUBLIC_LIBS_FILE="$WORK_DIR/system/system/etc/public.libraries-camera.samsung.txt"
    local IRREMOVABLE_FILE="$WORK_DIR/system/system/etc/irremovable_list.txt"

    if [ -f "$PUBLIC_LIBS_FILE" ]; then
        LOG "- Ensuring foundational segmentation camera public library"
        _REZOSS_APPEND_UNIQUE_LINE "$PUBLIC_LIBS_FILE" "libfoundational_segmentation.camera.samsung.so"
    else
        LOGW "File not found: ${PUBLIC_LIBS_FILE//$WORK_DIR/}"
    fi

    if [ -f "$IRREMOVABLE_FILE" ]; then
        LOG "- Ensuring foundational segmentation irremovable entry"
        _REZOSS_APPEND_UNIQUE_LINE "$IRREMOVABLE_FILE" "/system/lib64/libfoundational_segmentation.camera.samsung.so"
    else
        LOGW "File not found: ${IRREMOVABLE_FILE//$WORK_DIR/}"
    fi
}

_REZOSS_ENSURE_DVS_SYSTEM_CONFIGS()
{
    local PUBLIC_LIBS_FILE="$WORK_DIR/system/system/etc/public.libraries-camera.samsung.txt"
    local IRREMOVABLE_FILE="$WORK_DIR/system/system/etc/irremovable_list.txt"

    if [ -f "$PUBLIC_LIBS_FILE" ]; then
        LOG "- Ensuring DVS camera public library"
        _REZOSS_APPEND_UNIQUE_LINE "$PUBLIC_LIBS_FILE" "libdvs.camera.samsung.so"
    else
        LOGW "File not found: ${PUBLIC_LIBS_FILE//$WORK_DIR/}"
    fi

    if [ -f "$IRREMOVABLE_FILE" ]; then
        LOG "- Ensuring DVS irremovable entry"
        _REZOSS_APPEND_UNIQUE_LINE "$IRREMOVABLE_FILE" "/system/lib64/libdvs.camera.samsung.so"
    else
        LOGW "File not found: ${IRREMOVABLE_FILE//$WORK_DIR/}"
    fi
}

_REZOSS_ENSURE_VENDOR_CONFIG_FILE_CONTEXTS()
{
    local FC_FILE="$WORK_DIR/vendor/etc/selinux/vendor_file_contexts"

    if [ ! -f "$FC_FILE" ]; then
        LOGW "File not found: ${FC_FILE//$WORK_DIR/}"
        return 0
    fi

    LOG "- Ensuring S26U vendor config/model file contexts"
    _REZOSS_APPEND_UNIQUE_LINE "$FC_FILE" "/vendor/etc/saiv/image_understanding/db/doc_rectifier(/.*)? u:object_r:vendor_configs_file:s0"
    _REZOSS_APPEND_UNIQUE_LINE "$FC_FILE" "/vendor/etc/saiv/image_understanding/db/dvs(/.*)? u:object_r:vendor_configs_file:s0"
    _REZOSS_APPEND_UNIQUE_LINE "$FC_FILE" "/vendor/etc/saiv/image_understanding/db/fm(/.*)? u:object_r:vendor_configs_file:s0"
    _REZOSS_APPEND_UNIQUE_LINE "$FC_FILE" "/vendor/etc/saiv/image_understanding/db/ss_magnet(/.*)? u:object_r:vendor_configs_file:s0"
    _REZOSS_APPEND_UNIQUE_LINE "$FC_FILE" "/vendor/etc/midas_enhancedocumentscan(/.*)? u:object_r:vendor_configs_file:s0"
}

_REZOSS_SET_VENDOR_CONFIG_DIR_METADATA()
{
    local ENTRY="$1"
    local BASE="$WORK_DIR/vendor/$ENTRY"
    local FILE
    local REL

    if [ ! -d "$BASE" ]; then
        LOGW "Directory not found: ${BASE//$WORK_DIR/}"
        return 0
    fi

    while IFS= read -r FILE; do
        REL="${FILE#$WORK_DIR/vendor/}"
        if [ -d "$FILE" ]; then
            SET_METADATA "vendor" "$REL" 0 2000 755 "u:object_r:vendor_configs_file:s0"
        else
            SET_METADATA "vendor" "$REL" 0 0 644 "u:object_r:vendor_configs_file:s0"
        fi
    done < <(find "$BASE")
}

_REZOSS_SET_SYSTEM_LIB64_METADATA()
{
    local FILE="$1"

    SET_METADATA "system" "system/lib64/$FILE" 0 0 644 "u:object_r:system_lib_file:s0"
}

_REZOSS_GET_SEPOLICY_API_SUFFIX()
{
    local CIL_FILE="$1"

    if grep -q "(type init_34_0)" "$CIL_FILE"; then
        echo "34_0"
    else
        echo "33_0"
    fi
}

_REZOSS_CIL_HAS_SYMBOL()
{
    local SYMBOL="$1"
    shift

    local FILE
    for FILE in "$@"; do
        [ -f "$FILE" ] || continue
        if grep -q " ${SYMBOL}[ )]" "$FILE" || grep -q "(${SYMBOL}[ )]" "$FILE"; then
            return 0
        fi
    done

    return 1
}

_REZOSS_ENSURE_LOG_VIDEO_FILTER_SELINUX()
{
    local FC_FILE="$WORK_DIR/vendor/etc/selinux/vendor_file_contexts"
    local CIL_FILE="$WORK_DIR/vendor/etc/selinux/vendor_sepolicy.cil"

    if [ -f "$FC_FILE" ]; then
        if ! grep -q -F "/data/vendor/LogVideofilters" "$FC_FILE"; then
            LOG "- Adding LogVideofilters data file context"
            echo "/data/vendor/LogVideofilters(/.*)? u:object_r:LogVideofilters_data_file:s0" >> "$FC_FILE"
        fi
    else
        LOGW "File not found: ${FC_FILE//$WORK_DIR/}"
    fi

    if [ -f "$CIL_FILE" ]; then
        LOG "- Ensuring LogVideofilters SELinux access"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(type LogVideofilters_data_file)"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(roletype object_r LogVideofilters_data_file)"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(typeattributeset file_type (LogVideofilters_data_file))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(typeattributeset data_file_type (LogVideofilters_data_file))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow hal_camera_default LogVideofilters_data_file (dir (ioctl read getattr lock open watch watch_reads search)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow hal_camera_default LogVideofilters_data_file (file (ioctl read getattr lock map open watch watch_reads)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow platform_app_33_0 LogVideofilters_data_file (dir (ioctl read getattr lock open watch watch_reads search)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow platform_app_33_0 LogVideofilters_data_file (file (ioctl read getattr lock map open watch watch_reads)))"
    else
        LOGW "File not found: ${CIL_FILE//$WORK_DIR/}"
    fi
}

_REZOSS_ENSURE_BOOTANIMATION_SELINUX()
{
    local SYSTEM_EXT_SELINUX
    local CIL_FILE

    if $TARGET_OS_BUILD_SYSTEM_EXT_PARTITION; then
        SYSTEM_EXT_SELINUX="$WORK_DIR/system_ext/etc/selinux"
    else
        SYSTEM_EXT_SELINUX="$WORK_DIR/system/system/system_ext/etc/selinux"
    fi

    CIL_FILE="$SYSTEM_EXT_SELINUX/system_ext_sepolicy.cil"

    if [ -f "$CIL_FILE" ]; then
        LOG "- Ensuring mountless AOSP bootanimation SELinux access"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "; Added by unica/mods/rezoss/customize.sh for mountless AOSP zip boot animation"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell self (capability (dac_override dac_read_search)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell bootanim_exec (file (ioctl read getattr lock map open watch watch_reads)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell bootanim_oem_file (file (ioctl read getattr lock map open watch watch_reads)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell system_data_root_file (dir (ioctl read getattr lock open watch watch_reads search)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell adb_data_file (dir (ioctl read getattr lock open watch watch_reads search)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell adb_data_file (file (ioctl read getattr lock map open watch watch_reads)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell bootanim_data_file (dir (ioctl read write create getattr setattr lock rename open watch watch_reads add_name remove_name search rmdir)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell bootanim_data_file (file (ioctl read write create getattr setattr lock map open watch watch_reads rename unlink)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell system_data_file (dir (ioctl read getattr lock open watch watch_reads search)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell system_data_file (file (ioctl read getattr lock map open watch watch_reads)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow bootanim bootanim_data_file (dir (ioctl read getattr lock open watch watch_reads search)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow bootanim bootanim_data_file (file (ioctl read getattr lock map open watch watch_reads)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell ctl_start_prop (property_service (set)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell ctl_start_prop (file (read getattr map open)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell ctl_stop_prop (property_service (set)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell ctl_stop_prop (file (read getattr map open)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell ctl_restart_prop (property_service (set)))"
        _REZOSS_APPEND_UNIQUE_LINE "$CIL_FILE" "(allow sec_system_init_shell ctl_restart_prop (file (read getattr map open)))"
    else
        LOGW "File not found: ${CIL_FILE//$WORK_DIR/}"
    fi
}

_REZOSS_ENSURE_BOOTANIMATION_SELINUX

# =============================================================================
# S26U One UI 9 Quick Share Apple Devices Extension
# =============================================================================
REZOSS_ENABLE_MOSEY_APPLE_SHARING="${REZOSS_ENABLE_MOSEY_APPLE_SHARING:-true}"
if [[ "$REZOSS_ENABLE_MOSEY_APPLE_SHARING" == "true" ]]; then
    LOG "- Adding S26U One UI 9 Mosey system_ext service"
    # S23U One UI 9 already provides mosey_app/server policy and file contexts.
    ADD_TO_WORK_DIR "m3qxxx" "system_ext" "priv-app/MoseyApp" 0 0 755 "u:object_r:system_file:s0"
    ADD_TO_WORK_DIR "m3qxxx" "system_ext" "bin/mosey_server" 0 1000 755 "u:object_r:mosey_server_exec:s0"
    ADD_TO_WORK_DIR "m3qxxx" "system_ext" "lib64/libmosey_daemon_ffi.so" 0 0 644 "u:object_r:system_lib_file:s0"
    for f in "init/mosey.rc" "vintf/manifest/manifest_mosey.xml" \
        "default-permissions/default-permissions-com.google.android.mosey.xml" \
        "permissions/privapp-permissions-com.google.android.mosey.xml" \
        "sysconfig/preinstalled-packages-com.google.android.mosey.xml"; do
        ADD_TO_WORK_DIR "m3qxxx" "system_ext" "etc/$f" 0 0 644 "u:object_r:system_file:s0"
    done
else
    LOG "- Skipping Mosey Quick Share extension; REZOSS_ENABLE_MOSEY_APPLE_SHARING=false"
    DELETE_FROM_WORK_DIR "system" "system/etc/default-permissions/default-permissions-com.google.android.mosey.xml"
    DELETE_FROM_WORK_DIR "system" "system/etc/init/rezoss_mosey_permissions.rc"
    DELETE_FROM_WORK_DIR "system" "system/etc/unica/rezoss_mosey_permissions.sh"
    DELETE_FROM_WORK_DIR "system_ext" "priv-app/MoseyApp"
fi

# =============================================================================
# S26U Prebuilts - Gallery LOG / Camera LOG-LUT / Privacy Display
# =============================================================================
LOG "- Adding S26U Privacy Display, Gallery LOG, Camera LOG/LUT, and Horizon Lock support files"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/cameradata/logCubefiles" 0 0 755 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libcontextanalyzer_jni.media.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libauto-reframing-arm64-v8a.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libmediacontextanalyzer.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libmpp.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libmpp_common.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libmppclient.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libmppfilter.so" 0 0 644 "u:object_r:system_lib_file:s0"

# =============================================================================
# S26U Prebuilts - Video Clipping / Preview Dependencies
# =============================================================================
# Required by S26U Camera LOG/LUT preview.
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libppvdis_core.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libppvdis_interface.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libppvdis_wrapper.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libveframework.videoeditor.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libvideo-highlight-arm64-v8a.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/vendor.samsung.hardware.media.mpp-V5-ndk.so" 0 0 644 "u:object_r:system_lib_file:s0"

# =============================================================================
# S26U Prebuilts - Dual Recording Split View
# =============================================================================
# Disabled after S26U vendor nodes crashed on the dm3q/S23U camera provider stack.
# Keep the existing dm3q/S23U SamsungCamera, recording node, sensor bridge,
# dual calibration bins, and QTI/CHI provider stack active.
# LOG "- Adding S26U Dual Recording Split View vendor nodes"
# ADD_TO_WORK_DIR "m3qxxx" "system" "system/priv-app/SamsungCamera/SamsungCamera.apk" 0 0 644 "u:object_r:system_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "system" "system/priv-app/SamsungCamera/SamsungCamera.apk.prof" 0 0 644 "u:object_r:system_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/libsecsettingsmanager.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/libsensorndkbridge.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/libunidatamanager.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/camera/components/com.samsung.node.uniplugin_recording.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/camera/components/com.samsung.node.uniplugin_meta_surface.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/camera/components/com.samsung.node.uniplugin_record_portrait_depth_extract.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/camera/components/com.samsung.node.uniplugin_record_portrait_render.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/camera/multical_param.bin" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/camera/t2_w_dual_calibration.bin" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/camera/t2_t1_dual_calibration.bin" 0 0 644 "u:object_r:vendor_file:s0"

# =============================================================================
# S26U Prebuilts - Camera Core / Photo Processing Libraries
# =============================================================================
LOG "- Adding S26U camera node libraries for partial vendor-lib feature set"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libAIDeflicker.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libDeflickerHDR.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libsnapshotdebanding.arcsoft.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libStereoSolution.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libSR_StereoCapture.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libfoundational_segmentation.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libdvs.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
_REZOSS_ENSURE_FOUNDATIONAL_SEGMENTATION_SYSTEM_CONFIGS
_REZOSS_ENSURE_DVS_SYSTEM_CONFIGS
# S26U replacements previously carried by the rezoss static lib64 overlay.
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/DualOutFocusViewer_B.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libArtifactDetector_v1.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libC2paDps.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libGenSR_saicc_core.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libSR_DynamicRectifier.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libSR_NearDetector.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libStereoWarp.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libTextEnhancementV2.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libVirtualApertureCapture.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libframebooster.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libphotohdr.so" 0 0 644 "u:object_r:system_lib_file:s0"

# =============================================================================
# S26U Prebuilts - PhotoHDR Simba / HEIF Stack
# =============================================================================
# Use the One UI 9 system stack with the stock One UI 9 imagecodec APEX.
LOG "- Adding S26U PhotoHDR Simba/HEIF system stack"
for f in \
    "libsimba.media.samsung.so" \
    "libsimba.decoder.media.samsung.so" \
    "libsimba.cfa.media.samsung.so" \
    "libheifcapture.so" \
    "libheifcapture_jni.media.samsung.so" \
    "libheifcodec_jni.so" \
    "libheifregiondec_jni.so" \
    "libsheif.so" \
    "libsecultrahdr.so" \
    "libultrahdr.so" \
    "libjpegsq.media.samsung.so"; do
    ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/$f" 0 0 644 "u:object_r:system_lib_file:s0"
    _REZOSS_SET_SYSTEM_LIB64_METADATA "$f"
done

# =============================================================================
# S26U Prebuilts - Enhanced Document Scan
# =============================================================================
# S26U enhanced document-scan native libs.
# WARNING: Might cause crash
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libDocColorEnhance.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libDocColorEnhance_Auto.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libDocMagnetEngine.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
if [ -e "$WORK_DIR/system/system/lib64/libDocObjectRemovalV2.camera.samsung.so" ] || \
        [ -L "$WORK_DIR/system/system/lib64/libDocObjectRemovalV2.camera.samsung.so" ]; then
    DELETE_FROM_WORK_DIR "system" "system/lib64/libDocObjectRemovalV2.camera.samsung.so"
fi
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libDocScannerFilterV2.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libDocShadowRemoval.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libMoireFilterV2.camera.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
for f in \
    "libauto-reframing-arm64-v8a.so" \
    "libcontextanalyzer_jni.media.samsung.so" \
    "libmediacontextanalyzer.so" \
    "libmpp.so" \
    "libmpp_common.so" \
    "libmppclient.so" \
    "libmppfilter.so" \
    "libppvdis_core.so" \
    "libppvdis_interface.so" \
    "libppvdis_wrapper.so" \
    "libveframework.videoeditor.samsung.so" \
    "libvideo-highlight-arm64-v8a.so" \
    "vendor.samsung.hardware.media.mpp-V5-ndk.so" \
    "libAIDeflicker.camera.samsung.so" \
    "DualOutFocusViewer_B.so" \
    "libArtifactDetector_v1.camera.samsung.so" \
    "libC2paDps.camera.samsung.so" \
    "libDeflickerHDR.camera.samsung.so" \
    "libDocColorEnhance.camera.samsung.so" \
    "libDocColorEnhance_Auto.camera.samsung.so" \
    "libDocMagnetEngine.camera.samsung.so" \
    "libDocScannerFilterV2.camera.samsung.so" \
    "libDocShadowRemoval.camera.samsung.so" \
    "libdvs.camera.samsung.so" \
    "libfoundational_segmentation.camera.samsung.so" \
    "libGenSR_saicc_core.camera.samsung.so" \
    "libMoireFilterV2.camera.samsung.so" \
    "libSR_DynamicRectifier.camera.samsung.so" \
    "libSR_NearDetector.camera.samsung.so" \
    "libsnapshotdebanding.arcsoft.so" \
    "libStereoSolution.camera.samsung.so" \
    "libSR_StereoCapture.camera.samsung.so" \
    "libStereoWarp.camera.samsung.so" \
    "libTextEnhancementV2.camera.samsung.so" \
    "libVirtualApertureCapture.camera.samsung.so" \
    "libframebooster.so" \
    "libphotohdr.so" \
    "libsamsungSoundbooster_plus_legacy.so" \
    "libsoundboostereq_legacy.so"; do
    _REZOSS_SET_SYSTEM_LIB64_METADATA "$f"
done

# =============================================================================
# S26U Prebuilts - Video Clipping / Document Scan Models
# =============================================================================
LOG "- Adding S26U video clipping and document-scan model files"
_REZOSS_ENSURE_VENDOR_CONFIG_FILE_CONTEXTS
LOG "- Adding S26U MediaContextAnalyzer My FanCam model files"
for f in \
    "07-03_Video_HumanPetDetection_v2.0.1_SM8850_SNPE238.dlc" \
    "07-04_Video_HumanPetPose_v3.1.1_SM8850-SNPE238.dlc" \
    "07-05_Video_KeywordClassification_v1.1.0_SM8850_SNPE238.dlc" \
    "Detection.dlc" \
    "Keyword.dlc" \
    "Pose.dlc"; do
    ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/mediacontextanalyzer/$f" 0 0 644 "u:object_r:system_file:s0"
done
if [[ "$TARGET_CODENAME" == "dm3q" ]]; then
    # EXPERIMENTAL Human/Pet Detection: rebuilt from saved YOLOv7-tiny ONNX
    # with SNPE 2.10 and a fresh SM8550/V73 cache; not Samsung donor weights.
    # Ten direct stock-SNPE samples and Samsung native decoder checks passed.
    # WARNING: stock Detection SNAP/MCA integration and Camera use are UNTESTED.
    # Only dog/cat/human classes exist; faces/gestures are unsupported and one
    # white-dog sample is misclassified as cat. Installed by experimental opt-in.
    LOG "- Adding EXPERIMENTAL SM8550 Human/Pet Detection SNPE 2.10 DLC"
    LOG "- WARNING: Human/Pet Detection is partial; stock SNAP/Camera integration is unverified"
    # EXPERIMENTAL My FanCam DLCs: learned UN1CA replacements, not S26U weights.
    # Custom payloads use the _rezoss_custom suffix; loader aliases stay stable.
    # Locator v4 SNPE 2.10: trained head improves sampled person recall;
    # stock SNAP numerical/native ABI checks cover 102 inputs. Strict ONNX
    # box parity still fails 55 inputs; background false-positives remain.
    # ReID v22 SNPE 2.10: crowded appearance conv5/FC adaptation with training-
    # only distillation; combined repaired pose and integer RGB resize policy.
    # Stock SNAP numerical checks cover 549 inputs; 533 host pose outputs
    # remain bit-identical to the preceding combined experimental candidate.
    # WARNING: 95% identity/recall and spectator-separation gates still FAIL.
    # Gallery identity continuity, crop/zoom, preview/export are NOT validated
    # for v4/v22. Prior v21 single-clip Gallery evidence does not validate v22.
    # ReID measured ~40% slower than v21 on one repeated input; facial
    # mapping remains provisional and this is not end-to-end Gallery timing.
    # Explicit experimental user opt-in on 2026-10-07 despite these failures.
    # Rollback: archived UN1CA-FanCam-Crowded-Rollout-20261007-210113/
    # rom-source-backup/ retains v3/v21 DLCs, aliases and this script.
    LOG "- Adding EXPERIMENTAL SM8550 My FanCam Locator/ReID DLCs"
    LOG "- WARNING: EXPERIMENTAL Locator v4/ReID v22: accuracy gates FAIL; known false-positives/box drift; Gallery cropping is unverified"
    for f in \
        "07-03_Video_HumanPetDetection_v2.0.1_SM8850_SNPE238.dlc" \
        "07-03_Video_HumanPetDetection_UN1CA_Experimental_v1_SM8550-SNPE210.dlc" \
        "Detection.dlc" \
        "07-21_Video_ReframingTargetLocator_v0.1.0_SM8850-SNPE239.dlc" \
        "07-22_Video_ReframingReID_v0.2.3_SM8850-SNPE244.dlc" \
        "07-21_Video_ReframingTargetLocator_UN1CA_Experimental_v1_SM8550-SNPE244.dlc" \
        "07-21_Video_ReframingTargetLocator_UN1CA_Experimental_v2_SM8550-SNPE210.dlc" \
        "07-21_Video_ReframingTargetLocator_UN1CA_Experimental_v3_SM8550-SNPE210_rezoss_custom.dlc" \
        "07-22_Video_ReframingReID_UN1CA_Experimental_v1_SM8550-SNPE244.dlc" \
        "07-22_Video_ReframingReID_UN1CA_Experimental_v2_SM8550-SNPE210.dlc" \
        "07-22_Video_ReframingReID_UN1CA_Experimental_v21_SM8550-SNPE210_rezoss_custom.dlc" \
        "07-21_Video_ReframingTargetLocator_UN1CA_Experimental_v3_SM8550-SNPE210.dlc" \
        "07-21_Video_ReframingTargetLocator_UN1CA_Experimental_v4_SM8550-SNPE210.dlc" \
        "07-22_Video_ReframingReID_UN1CA_Experimental_v21_SM8550-SNPE210.dlc" \
        "07-22_Video_ReframingReID_UN1CA_Experimental_v22_SM8550-SNPE210.dlc" \
        "Locator.dlc" \
        "Reid.dlc"; do
        DELETE_FROM_WORK_DIR "system" "system/etc/mediacontextanalyzer/$f"
    done
    # Stable loader aliases point to explicitly named SM8550 experimental files.
    for f in \
        "07-03_Video_HumanPetDetection_UN1CA_Experimental_v1_SM8550-SNPE210_rezoss_custom.dlc" \
        "Detection.dlc" \
        "07-21_Video_ReframingTargetLocator_UN1CA_Experimental_v4_SM8550-SNPE210_rezoss_custom.dlc" \
        "07-22_Video_ReframingReID_UN1CA_Experimental_v22_SM8550-SNPE210_rezoss_custom.dlc" \
        "Locator.dlc" \
        "Reid.dlc"; do
        ADD_TO_WORK_DIR "$MODPATH/fancam/experimental" "system" "system/etc/mediacontextanalyzer/$f" 0 0 644 "u:object_r:system_file:s0"
    done
else
    for f in \
        "07-21_Video_ReframingTargetLocator_v0.1.0_SM8850-SNPE239.dlc" \
        "07-22_Video_ReframingReID_v0.2.3_SM8850-SNPE244.dlc" \
        "Locator.dlc" \
        "Reid.dlc"; do
        ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/mediacontextanalyzer/$f" 0 0 644 "u:object_r:system_file:s0"
    done
fi
ADD_TO_WORK_DIR "m3qxxx" "vendor" "etc/saiv/image_understanding/db/fm" 0 2000 755 "u:object_r:vendor_configs_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "vendor" "etc/saiv/image_understanding/db/dvs" 0 2000 755 "u:object_r:vendor_configs_file:s0"
# Keep the target firmware's GPU/TFLite document rectifier. The S26U DLC
# contains a V81/SM8850 cache that cannot run on the S23U's V73 DSP.
ADD_TO_WORK_DIR "$TARGET_FIRMWARE" "vendor" "etc/saiv/image_understanding/db/doc_rectifier" 0 2000 755 "u:object_r:vendor_configs_file:s0"
# S26U enhanced document-scan configs/models.
# WARNING: Might cause crash.
# SNPE 2.10 audit: no validated Document Scan conversion is available.
# DocSegment retains weights but needs newer-container/graph translation;
# dewarp/deblur/inpaint/shadow/line models lack portable donor weights.
# Keep these existing donor assets distinct from the experimental MCA DLCs.
# No camera .so, SNAP/SNPE runtime or unvalidated 2.44 DocSegment is added here.
ADD_TO_WORK_DIR "m3qxxx" "vendor" "etc/saiv/image_understanding/db/ss_magnet" 0 2000 755 "u:object_r:vendor_configs_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "vendor" "etc/midas_enhancedocumentscan" 0 2000 755 "u:object_r:vendor_configs_file:s0"
for f in \
    "etc/saiv/image_understanding/db/dvs" \
    "etc/saiv/image_understanding/db/fm"; do
    _REZOSS_SET_VENDOR_CONFIG_DIR_METADATA "$f"
done

# Do not install the S26U compressed-RAW stack on dm3q. These libraries execute
# inside the S23U camera-provider process and can stall its Night capture graph.
# Keep the matching libraries from the untouched target firmware instead.

# =============================================================================
# S26U Prebuilts - Video Editor / LOG Transitive Dependencies
# =============================================================================
# Transitive dependencies of the S26U video editor / LOG correction stack.
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libmultisourceseparator.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libsbs.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libtensorflowlite_gpu_delegate.so" 0 0 644 "u:object_r:system_lib_file:s0"
for f in \
    "libmultisourceseparator.so" \
    "libsbs.so" \
    "libtensorflowlite_gpu_delegate.so"; do
    _REZOSS_SET_SYSTEM_LIB64_METADATA "$f"
done

# =============================================================================
# Experimental S23U Horizontal Lock - Native EIS Adapter
# =============================================================================
# Experimental dm3q-only horizon correction, linked into the stock S23U EIS node.
# Exact-firmware checks and stock backup are handled by the installer.
. "$MODPATH/horizontal-lock/install.sh"

# =============================================================================
# Disabled Experiments - Camera Vendor Stack
# =============================================================================
# Rezoss S26U Horizon Lock vendor VDIS experiment.
# WARNING: Disabled after confirmed camera crash.
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/camera/components/com.samsung.node.uniplugin_vdis.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/libvdis_interface.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/libvdis_core.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/libsecsettingsmanager.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/libIMUSensor.so" 0 0 644 "u:object_r:vendor_file:s0"

# Optional deeper S26U sensor/OIS test.
# WARNING: Disabled after crash.
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/libsensorndkbridge.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/libprotobuf-cpp-lite-21.12.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/liboischannel.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/libois_channel_stub.so" 0 0 644 "u:object_r:vendor_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/libois_channel_factory_test_stub.so" 0 0 644 "u:object_r:vendor_file:s0"

# =============================================================================
# S26U Prebuilts - PhotoHDR Vendor Encoder Plugin
# =============================================================================
# Keep dm3q UniHAL support libraries from the S23U vendor stack. Importing the
# S26U copies here changes shared dependencies used by object-tracking AF.
LOG "- Adding S26U PhotoHDR vendor encoder plugin"
for f in \
    "libSecPhotoHdrEncoder.uniplugin@1.0.so"; do
    ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/$f" 0 0 644 "u:object_r:vendor_file:s0"
done

# =============================================================================
# Vendor Floating Feature Mirror / SELinux Fixups
# =============================================================================
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_LCD_CONFIG_PRIVACY_DISPLAY" "1"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_GALLERY_SUPPORT_LOG_CORRECT_COLOR" "TRUE"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_CONFIG_LOG_VIDEO" "V1.0"
_REZOSS_SET_VENDOR_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_LCD_CONFIG_PRIVACY_DISPLAY" "1"
_REZOSS_SET_VENDOR_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_CONFIG_VENDOR_LIB_INFO" "single_bokeh.samsung.v2,smart_scan.samsung.v2,beauty.samsung.v4"
_REZOSS_SET_VENDOR_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_FUSION_HIGH_RESOLUTION" "FALSE"
_REZOSS_SET_VENDOR_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_CAMERA_SUPPORT_HIGH_RESOLUTION_SWBINNING" "FALSE"
_REZOSS_ENSURE_LOG_VIDEO_FILTER_SELINUX

unset -f _REZOSS_SET_VENDOR_FLOATING_FEATURE_CONFIG _REZOSS_APPEND_UNIQUE_LINE
unset -f _REZOSS_ENSURE_FOUNDATIONAL_SEGMENTATION_SYSTEM_CONFIGS _REZOSS_ENSURE_DVS_SYSTEM_CONFIGS
unset -f _REZOSS_ENSURE_MOSEY_VENDOR_SELINUX _REZOSS_GET_MOSEY_APP_DOMAIN
unset -f _REZOSS_DROP_MOSEY_APP_VENDOR_RULES _REZOSS_CIL_HAS_SYMBOL _REZOSS_GET_SEPOLICY_API_SUFFIX
unset -f _REZOSS_ENSURE_LOG_VIDEO_FILTER_SELINUX _REZOSS_ENSURE_BOOTANIMATION_SELINUX


# =============================================================================
# Build Display Branding
# =============================================================================
NOW_BUILD="UN1CA-ROM built by Rezoss on $(GET_PROP "system" "ro.build.PDA")"
SET_PROP "system" "ro.build.display.id" "${NOW_BUILD}"
SET_PROP "product" "ro.build.display.id" "${NOW_BUILD}"

# =============================================================================
# Local System App Overlays
# =============================================================================
# The legacy SamsungSans overlay bundles over 1k asset fonts. One UI 9 Studio
# loads installed decoration fonts into a 512 MB heap and can OOM on this APK.
# Keep the stock Monotype preload set and expose SamsungSans only as a source
# bank for the UN1CA Font Selector.
DELETE_FROM_WORK_DIR "system" "system/app/SamsungSans"
SAMSUNGSANS_SOURCE_APK="$SRC_DIR/prebuilts/samsung/dm3qxxx/system/app/SamsungSans/SamsungSans.apk"
SAMSUNGSANS_BANK_APK="$WORK_DIR/system/system/etc/unica/font_selector/SamsungSans.apk"
if [ -f "$SAMSUNGSANS_SOURCE_APK" ] || [ -f "$SAMSUNGSANS_SOURCE_APK.00" ]; then
    LOG "- Adding SamsungSans source bank for UN1CA Font Selector"
    EVAL "mkdir -p \"$(dirname "$SAMSUNGSANS_BANK_APK")\""
    if [ -f "$SAMSUNGSANS_SOURCE_APK" ]; then
        EVAL "cp -a \"$SAMSUNGSANS_SOURCE_APK\" \"$SAMSUNGSANS_BANK_APK\""
    else
        EVAL "cat \"$SAMSUNGSANS_SOURCE_APK.\"[0-9][0-9] > \"$SAMSUNGSANS_BANK_APK\""
    fi
    SET_METADATA "system" "system/etc/unica" 0 0 755 "u:object_r:system_file:s0"
    SET_METADATA "system" "system/etc/unica/font_selector" 0 0 755 "u:object_r:system_file:s0"
    SET_METADATA "system" "system/etc/unica/font_selector/SamsungSans.apk" 0 0 644 "u:object_r:system_file:s0"
else
    LOGW "SamsungSans source bank not found; UN1CA Font Selector will show a missing-source message"
fi
unset SAMSUNGSANS_SOURCE_APK SAMSUNGSANS_BANK_APK
ADD_TO_WORK_DIR "dm3qxxx" "system" "system/app/VisionModel-Stub/VisionModel-Stub.apk" 0 0 644 "u:object_r:system_file:s0"

# =============================================================================
# Ambient Weather Wallpaper / VisualCloudCore Patches
# =============================================================================
LOG "- Patch stock DressRoom Ambient Weather feature gate"
APPLY_PATCH "system" "system/priv-app/DressRoom/DressRoom.apk" \
    "$MODPATH/dressroom/DressRoom.apk/0001-Bypass-AICore-weather-feature-check.patch"

LOG "- Patch DressRoom UN1CA lockscreen font picker integration"
# Firmware updates can rename the obfuscated callers. Resolve exact instruction
# sequences and dry-run the whole patch before modifying the decoded APK.
REZOSS_DRESSROOM_PATCH=$(mktemp /tmp/rezoss-dressroom-fonts.XXXXXX.patch) || return 1
if ! EVAL "python3 \"$MODPATH/dressroom/resolve_font_patch.py\" \
    \"$APKTOOL_DIR/system/priv-app/DressRoom/DressRoom.apk\" \
    \"$MODPATH/dressroom/DressRoom.apk/0002-Expose-UN1CA-selected-fonts-to-lockscreen-picker.patch\" \
    > \"$REZOSS_DRESSROOM_PATCH\""; then
    rm -- "$REZOSS_DRESSROOM_PATCH"
    unset REZOSS_DRESSROOM_PATCH
    return 1
fi
if ! APPLY_PATCH "system" "system/priv-app/DressRoom/DressRoom.apk" \
    "$REZOSS_DRESSROOM_PATCH"; then
    rm -- "$REZOSS_DRESSROOM_PATCH"
    unset REZOSS_DRESSROOM_PATCH
    return 1
fi
rm -- "$REZOSS_DRESSROOM_PATCH"
unset REZOSS_DRESSROOM_PATCH

# The full native port owns editor controls and AOD normalization on dm3q/SDK37.
REZOSS_DRESSROOM_NATIVE_FULL=false
if [[ "$TARGET_CODENAME" == "dm3q" && "$TARGET_PLATFORM_SDK_VERSION" == "37" &&
        ! -f "$SRC_DIR/unica/mods/Dressroom-Advanced/disable" ]]; then
    REZOSS_DRESSROOM_NATIVE_FULL=true
fi
if ! "$REZOSS_DRESSROOM_NATIVE_FULL"; then
LOG "- Add lockscreen clock height and widget size controls"
APPLY_PATCH "system" "system/priv-app/DressRoom/DressRoom.apk" \
    "$MODPATH/dressroom/DressRoom.apk/0003-Add-clock-height-and-widget-size-controls.patch"
fi
LOG "- Remove canceled lockscreen Depth Effect integration"
DELETE_FROM_WORK_DIR "system" "system/lib64/libunica_depth_effect.so"
DELETE_FROM_WORK_DIR "system" "system/etc/unica-depth-effect/pidnet_s.tflite"
REZOSS_DEPTH_DRESSROOM_DIR="$APKTOOL_DIR/system/priv-app/DressRoom/DressRoom.apk"
REZOSS_DEPTH_DRESSROOM_HOOK="smali_classes2/vo/x.smali"
REZOSS_DEPTH_DRESSROOM_LAYOUT="res/layout/clock_panel_layout.xml"
DECODE_APK "system" "system/priv-app/DressRoom/DressRoom.apk" || return 1
if grep -q -F 'DepthEffectToggle;->bind' "$REZOSS_DEPTH_DRESSROOM_DIR/$REZOSS_DEPTH_DRESSROOM_HOOK" || \
        grep -q -F 'unica_depth_effect' "$REZOSS_DEPTH_DRESSROOM_DIR/$REZOSS_DEPTH_DRESSROOM_LAYOUT"; then
    APPLY_PATCH "system" "system/priv-app/DressRoom/DressRoom.apk" \
        "$MODPATH/dressroom/DressRoom.apk/0004-Remove-Depth-Effect-toggle.patch"
fi
unset REZOSS_DEPTH_DRESSROOM_DIR REZOSS_DEPTH_DRESSROOM_HOOK REZOSS_DEPTH_DRESSROOM_LAYOUT

# Full menu replaces old editor controls; font-bank and Ambient Weather remain.
source "$SRC_DIR/unica/mods/Dressroom-Advanced/integrate.sh" dressroom || return 1

REZOSS_DEPTH_SYSTEMUI_DIR="$APKTOOL_DIR/system_ext/priv-app/SystemUI/SystemUI.apk"
REZOSS_DEPTH_SYSTEMUI_HOOK="smali_classes2/com/android/systemui/keyguard/ui/binder/KeyguardClockViewBinder.smali"
DECODE_APK "system_ext" "priv-app/SystemUI/SystemUI.apk" || return 1
if grep -q -F 'DepthEffectController;->attach' "$REZOSS_DEPTH_SYSTEMUI_DIR/$REZOSS_DEPTH_SYSTEMUI_HOOK"; then
    APPLY_PATCH "system_ext" "priv-app/SystemUI/SystemUI.apk" \
        "$MODPATH/systemui/SystemUI.apk/0008-Remove-lockscreen-Depth-Effect-compositor-hook.patch"
fi
unset REZOSS_DEPTH_SYSTEMUI_DIR REZOSS_DEPTH_SYSTEMUI_HOOK

LOG "- Downloading latest Samsung Always On Display app"
DOWNLOAD_FILE "$(GET_GALAXY_STORE_DOWNLOAD_URL "com.samsung.android.app.aodservice")" \
    "$WORK_DIR/system/system/priv-app/AODService_v80/AODService_v80.apk" || return 1

# The Galaxy Store APK may change obfuscated class names. Preserve any cached
# decode (including partial failed applies) and decode the downloaded APK fresh.
REZOSS_AOD_TEMP="$(mktemp -d /tmp/unica-aod.XXXXXX)" || return 1
REZOSS_AOD_DECODE="$APKTOOL_DIR/system/priv-app/AODService_v80/AODService_v80.apk"
REZOSS_AOD_PATCH_DIR="$REZOSS_AOD_TEMP/patches"
if [[ -d "$REZOSS_AOD_DECODE" ]]; then
    LOG "- Preserving previous AOD decode at $REZOSS_AOD_TEMP/previous-decode"
    mv "$REZOSS_AOD_DECODE" "$REZOSS_AOD_TEMP/previous-decode" || return 1
fi
DECODE_APK "system" "system/priv-app/AODService_v80/AODService_v80.apk" || return 1
LOG "- Resolve AOD font integration and preflight the selected patch stack"
REZOSS_AOD_PROFILE_ARGS=()
if "$REZOSS_DRESSROOM_NATIVE_FULL"; then
    REZOSS_AOD_PROFILE_ARGS+=(--native-full)
fi
python3 "$MODPATH/aodservice/prepare_patches.py" "$REZOSS_AOD_DECODE" \
    "$MODPATH/aodservice/AODService_v80.apk" "$REZOSS_AOD_PATCH_DIR" "${REZOSS_AOD_PROFILE_ARGS[@]}" || return 1

LOG "- Patch AODService UN1CA clock font list integration"
APPLY_PATCH "system" "system/priv-app/AODService_v80/AODService_v80.apk" \
    "$REZOSS_AOD_PATCH_DIR/0001-Expose-UN1CA-Font-Selector-fonts-to-clock-picker.patch" || return 1

LOG "- Patch AODService stretch clock font type sync"
APPLY_PATCH "system" "system/priv-app/AODService_v80/AODService_v80.apk" \
    "$REZOSS_AOD_PATCH_DIR/0002-Mirror-stretch-clock-font-type-to-AOD.patch" || return 1

LOG "- Patch AODService stretch clock font type startup backfill"
APPLY_PATCH "system" "system/priv-app/AODService_v80/AODService_v80.apk" \
    "$REZOSS_AOD_PATCH_DIR/0003-Backfill-AOD-stretch-font-type-on-startup.patch" || return 1

if ! "$REZOSS_DRESSROOM_NATIVE_FULL"; then
LOG "- Patch AODService stretch clock font render normalization"
APPLY_PATCH "system" "system/priv-app/AODService_v80/AODService_v80.apk" \
    "$REZOSS_AOD_PATCH_DIR/0004-Normalize-stretch-font-data-before-AOD-render.patch" || return 1

LOG "- Patch AODService AOD stretch font heights"
APPLY_PATCH "system" "system/priv-app/AODService_v80/AODService_v80.apk" \
    "$REZOSS_AOD_PATCH_DIR/0005-Preserve-AOD-stretch-font-heights.patch" || return 1

LOG "- Patch AODService AOD stretch renderer gate"
APPLY_PATCH "system" "system/priv-app/AODService_v80/AODService_v80.apk" \
    "$REZOSS_AOD_PATCH_DIR/0006-Allow-AOD-stretch-render-when-font-heights-exist.patch" || return 1

fi
LOG "- Patch AODService to use lockscreen clock style for AOD rendering"
APPLY_PATCH "system" "system/priv-app/AODService_v80/AODService_v80.apk" \
    "$REZOSS_AOD_PATCH_DIR/0007-Use-lockscreen-clock-style-for-AOD-render.patch" || return 1

if ! "$REZOSS_DRESSROOM_NATIVE_FULL"; then
LOG "- Expand lockscreen widget grid and preserve widget proportions when resizing"
APPLY_PATCH "system" "system/priv-app/AODService_v80/AODService_v80.apk" \
    "$REZOSS_AOD_PATCH_DIR/0008-Expand-and-resize-lockscreen-widget-container.patch" || return 1

LOG "- Patch AODService SDK clock item stretch font data"
APPLY_PATCH "system" "system/priv-app/AODService_v80/AODService_v80.apk" \
    "$REZOSS_AOD_PATCH_DIR/0009-Normalize-AOD-SDK-clock-item-stretch-font-data.patch" || return 1

fi
# The port replaces old normalization and widget overrides with one native owner.
source "$SRC_DIR/unica/mods/Dressroom-Advanced/integrate.sh" aod || return 1
source "$SRC_DIR/unica/mods/Dressroom-Advanced/integrate.sh" systemui || return 1
source "$SRC_DIR/unica/mods/Dressroom-Advanced/integrate.sh" wallpaper || return 1

unset REZOSS_AOD_TEMP REZOSS_AOD_DECODE REZOSS_AOD_PATCH_DIR REZOSS_AOD_PROFILE_ARGS REZOSS_DRESSROOM_NATIVE_FULL

LOG "- Patch VisualCloudCore Galaxy Store model check"
APPLY_PATCH "system" "system/app/VisualCloudCore/VisualCloudCore.apk" \
    "$MODPATH/visualcloudcore/VisualCloudCore.apk/0001-Use-S25-Ultra-model-for-stub-update-check.patch"

LOG "- Patch product framework overlay doze auto-brightness"
APPLY_PATCH "product" "overlay/framework-res__dm3qxxx__auto_generated_rro_product.apk" \
    "$MODPATH/rro/framework-res__dm3qxxx__auto_generated_rro_product.apk/0001-Add-doze-auto-brightness-arrays.patch"

LOG "- Patch product framework overlay Now Nudge notification allowlist"
APPLY_PATCH "product" "overlay/framework-res__dm3qxxx__auto_generated_rro_product.apk" \
    "$MODPATH/rro/framework-res__dm3qxxx__auto_generated_rro_product.apk/0002-Add-Now-Nudge-notification-allow-packages.patch"

# =============================================================================
# Notification Highlights / Galaxy AI Stack
# =============================================================================
# S26U Notification highlights requirements: expose the Galaxy AI common-AI gate, keep LLM/offline model metadata enabled for the backend, ensure the offline language model stub is present, remove the extra SecSettings LLM-version UI gate, allow dmxq devices in NmRune, and enable priority/summary defaults.
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_COMMON_CONFIG_AI_VERSION" "20263"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_FRAMEWORK_CONFIG_NOW_NUDGE_VERSION" "2"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_GENAI_CONFIG_LLM_VERSION" "0.81"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_GENAI_SUPPORT_OFFLINE_LANGUAGEMODEL" "TRUE"
LOG "- Adding S26U SmartSuggestions and SCS policy XMLs"
for REZOSS_SCS_POLICY_XML in \
    "default-permissions/default-permissions-com.samsung.android.scs.xml" \
    "permissions/privapp-permissions-com.samsung.android.scs.xml" \
    "permissions/signature-permissions-com.samsung.android.scs.xml" \
    "sysconfig/preinstalled-packages-com.samsung.android.scs.xml" \
    "sysconfig/samsungintellivoiceservice.xml" \
    "sysconfig/samsungsmartsuggestions.xml"; do
    ADD_TO_WORK_DIR "$MODPATH" "system" "system/etc/$REZOSS_SCS_POLICY_XML" 0 0 644 "u:object_r:system_file:s0"
done
unset REZOSS_SCS_POLICY_XML
LOG "- Build opt-in UN1CA local notification-summary provider"
LOCAL_SUMMARY_SERVICE_DIR="$MODPATH/local-summary-service"
LOCAL_SUMMARY_SERVICE_APK="$LOCAL_SUMMARY_SERVICE_DIR/system/priv-app/RezossAI/RezossAI.apk"
EVAL "bash \"$LOCAL_SUMMARY_SERVICE_DIR/build.sh\" \"$LOCAL_SUMMARY_SERVICE_APK\""
ADD_TO_WORK_DIR "$LOCAL_SUMMARY_SERVICE_DIR" "system" "system/priv-app/RezossAI/RezossAI.apk" 0 0 644 "u:object_r:system_file:s0"
SET_METADATA "system" "system/priv-app/RezossAI" 0 0 755 "u:object_r:system_file:s0"
SET_METADATA "system" "system/priv-app/RezossAI/RezossAI.apk" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "$LOCAL_SUMMARY_SERVICE_DIR" "system" "system/etc/default-permissions/default-permissions-unica-local-summary.xml" 0 0 644 "u:object_r:system_file:s0"
# Ship the lightweight API/settings app; users install model assets as an update.
# Build in a temporary directory so a previous combined APK cannot be reused.
LOG "- Build small Qwen settings stub (offline model APK installed separately)"
QWEN_STUB_BUILD=$(mktemp -d "$TMP_DIR/qwen-stub.XXXXXX") || return 1
EVAL "bash \"$MODPATH/qwen-llm/system-stub/build.sh\" \"$QWEN_STUB_BUILD/system/priv-app/QWEN_LLM_rezoss_stub/QWEN_LLM_rezoss_stub.apk\"" || return 1
ADD_TO_WORK_DIR "$QWEN_STUB_BUILD" "system" "system/priv-app/QWEN_LLM_rezoss_stub/QWEN_LLM_rezoss_stub.apk" 0 0 644 "u:object_r:system_file:s0" || return 1
SET_METADATA "system" "system/priv-app/QWEN_LLM_rezoss_stub" 0 0 755 "u:object_r:system_file:s0"
SET_METADATA "system" "system/priv-app/QWEN_LLM_rezoss_stub/QWEN_LLM_rezoss_stub.apk" 0 0 644 "u:object_r:system_file:s0"
unset QWEN_STUB_BUILD
# The separately installed app holds local model assets and private API settings.
# Remove legacy payloads from incremental builds; keep only the bridge.
DELETE_FROM_WORK_DIR "system" "system/bin/unica-summary-llama"
DELETE_FROM_WORK_DIR "system" "system/bin/unica-summary-llama-server"
DELETE_FROM_WORK_DIR "system" "system/etc/unica/local-summary/qwen2.5-0.5b-instruct-q4_k_m.gguf"
ADD_TO_WORK_DIR "$MODPATH" "system" "system/etc/sysconfig/rezoss-ai-associations.xml" 0 0 644 "u:object_r:system_file:s0"
LOG "- Native dm3q local backend: $UNICA_LOCAL_LLM_BACKEND; separate model and optional APIs in UN1CA Extra Settings > AI backend settings"
LOCAL_SUMMARY_ALLOWLIST="$WORK_DIR/system/system/etc/sysconfig/allowed-system-preload-apps.xml"
if ! grep -q -F 'package="io.mesalabs.unica.localsummary"' "$LOCAL_SUMMARY_ALLOWLIST"; then
    EVAL "LC_ALL=C patch --batch --fuzz=0 -p1 -d \"$WORK_DIR/system/system/etc/sysconfig\" < \"$LOCAL_SUMMARY_SERVICE_DIR/allowed-system-preload-apps.xml/0001-Allow-UN1CA-local-summary-provider.patch\"" \
        || ABORT "Failed to allow the UN1CA local summary provider"
fi
if ! grep -q -F 'package="io.mesalabs.rezoss.qwen"' "$LOCAL_SUMMARY_ALLOWLIST"; then
    EVAL "LC_ALL=C patch --batch --fuzz=0 -p1 -d \"$WORK_DIR/system/system/etc/sysconfig\" < \"$LOCAL_SUMMARY_SERVICE_DIR/allowed-system-preload-apps.xml/0002-Allow-Rezoss-Qwen-engine.patch\"" \
        || ABORT "Failed to allow the Rezoss Qwen engine"
fi
EVAL "rm -f \"$LOCAL_SUMMARY_ALLOWLIST.orig\" \"$LOCAL_SUMMARY_ALLOWLIST.rej\"" \
    || ABORT "Failed to remove stale local summary allowlist patch artifacts"
unset LOCAL_SUMMARY_ALLOWLIST
unset LOCAL_SUMMARY_SERVICE_APK LOCAL_SUMMARY_SERVICE_DIR
ADD_TO_WORK_DIR "m3qxxx" "system" "system/app/SketchBook/SketchBook.apk" 0 0 644 "u:object_r:system_file:s0"
LOG "- Overlay S26U Notification highlights AI APKs"
LOG "- Adding S26U One UI 9 Samsung Intelligence Voice Service"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/priv-app/SamsungIntelliVoiceServices/SamsungIntelliVoiceServices.apk" 0 0 644 "u:object_r:system_file:s0"
SET_METADATA "system" "system/priv-app/SamsungIntelliVoiceServices" 0 0 755 "u:object_r:system_file:s0"
SET_METADATA "system" "system/priv-app/SamsungIntelliVoiceServices/SamsungIntelliVoiceServices.apk" 0 0 644 "u:object_r:system_file:s0"
DELETE_FROM_WORK_DIR "system" "system/priv-app/SamsungAiCore"
if [[ "${UNICA_ENABLE_SAMSUNG_AICORE:-true}" == "true" ]]; then
    ADD_TO_WORK_DIR "m3qxxx" "system" "system/priv-app/SamsungAiCore/SamsungAiCore.apk" 0 0 644 "u:object_r:system_file:s0"
    ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/permissions/privapp-permissions-com.samsung.android.aicore.xml" 0 0 644 "u:object_r:system_file:s0"
    LOG "- Disabling SamsungAiCore neural-wallpaper pipeline"
    APPLY_PATCH "system" "system/priv-app/SamsungAiCore/SamsungAiCore.apk" \
        "$MODPATH/aicore/SamsungAiCore.apk/0003-Disable-crashing-wallpaper-pipeline.patch"
else
    LOG "- Removing SamsungAiCore; UN1CA Qwen provider handles notification AI paths"
fi
DELETE_FROM_WORK_DIR "system" "system/app/AIOSKernelService"
DELETE_FROM_WORK_DIR "system" "system/priv-app/AIOSKernelService"
if [[ "$UNICA_ENABLE_NATIVE_NOW_NUDGE" == "true" ]]; then
    source "$MODPATH/aioskernel/native-now-nudge/integrate_rom.sh" || return 1
elif [[ "${UNICA_ENABLE_AIOSKERNELSERVICE:-false}" == "true" ]]; then
    ADD_TO_WORK_DIR "m3qxxx" "system" "system/priv-app/AIOSKernelService/AIOSKernelService.apk" 0 0 644 "u:object_r:system_file:s0"
    ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/permissions/privapp-permissions-com.samsung.android.aioskernelservice.xml" 0 0 644 "u:object_r:system_file:s0"
    ADD_TO_WORK_DIR "pa2qxxx" "system" "system/etc/sysconfig/aioskernelservice.xml" 0 0 644 "u:object_r:system_file:s0"
else
    LOG "- Disabling AIOSKernelService; UN1CA Qwen provider handles notification AI replies"
fi
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/permissions/signature-permissions-com.samsung.android.offline.languagemodel.xml" 0 0 644 "u:object_r:system_file:s0"
# Keep the stub; the full OfflineLanguageModel is installed later as an update.
ADD_TO_WORK_DIR "m3qxxx" "system" "system/priv-app/OfflineLanguageModel_stub/OfflineLanguageModel_stub.apk" 0 0 644 "u:object_r:system_file:s0"

LOG "- Adding S26U NMT languagepack preload metadata"
S26U_NMT_TARGET_PRELOAD="$WORK_DIR/system/system/etc/removable_preload.txt"
if [ ! -f "$S26U_NMT_TARGET_PRELOAD" ]; then
    LOGE "File not found: ${S26U_NMT_TARGET_PRELOAD//$SRC_DIR\//}"
    return 1
fi
ADD_S26U_NMT_PRELOAD()
{
    local NMT_LANG="$1"
    local PKG="com.samsung.android.nmt.apps.t2t.languagepack.$NMT_LANG"
    local PRELOAD_BLOCK="$MODPATH/nmt-preload/$NMT_LANG.txt"

    if grep -Fq "name='$PKG'" "$S26U_NMT_TARGET_PRELOAD"; then
        return 0
    fi

    if [ ! -s "$PRELOAD_BLOCK" ]; then
        LOGE "Missing S26U preload metadata for $PKG"
        return 1
    fi

    LOG "- Adding $PKG to removable_preload.txt"
    {
        echo ""
        cat "$PRELOAD_BLOCK"
    } >> "$S26U_NMT_TARGET_PRELOAD"
}
for NMT_LANG in \
    enar \
    enesus \
    enfil \
    enid \
    enko \
    ennl \
    enpt \
    enro \
    enru \
    ensv \
    entr \
    enzh; do
    ADD_S26U_NMT_PRELOAD "$NMT_LANG" || return 1
done
unset -f ADD_S26U_NMT_PRELOAD
unset NMT_LANG S26U_NMT_TARGET_PRELOAD
#ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/vendor.qti.hardware.dsp-V1-ndk.so" 0 0 644 "u:object_r:vendor_file:s0"
# Causing bootloop
# ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/android.hardware.common-V2-ndk.so" 0 0 644 "u:object_r:vendor_file:s0"
# DOWNLOAD_FILE "$(GET_GALAXY_STORE_DOWNLOAD_URL "com.samsung.android.aicore")" \
    # "$WORK_DIR/system/system/priv-app/SamsungAiCore/SamsungAiCore.apk"

UNICA_ENABLE_UNSAFE_S26U_QNN_EXPERIMENT="${UNICA_ENABLE_UNSAFE_S26U_QNN_EXPERIMENT:-false}"
if [[ "$UNICA_ENABLE_UNSAFE_S26U_QNN_EXPERIMENT" == "true" && "${UNICA_ENABLE_SAMSUNG_AICORE:-true}" == "true" && "${UNICA_ENABLE_AIOSKERNELSERVICE:-false}" == "true" ]]; then
REZOSS_SIVS_MODEL_PATCHED_FILE="$APKTOOL_DIR/system/priv-app/SamsungIntelliVoiceServices/SamsungIntelliVoiceServices.apk/smali/com/samsung/android/intellivoiceservice/common/connection/server/LlmClientInterceptor.smali"
REZOSS_SIVS_MODEL_PATCH_MARKER="$APKTOOL_DIR/system/priv-app/SamsungIntelliVoiceServices/SamsungIntelliVoiceServices.apk/.rezoss_s26u_device_model_patch_applied"
if [ -f "$REZOSS_SIVS_MODEL_PATCHED_FILE" ] && grep -qF 'const-string v4, "SM-S948B"' "$REZOSS_SIVS_MODEL_PATCHED_FILE"; then
    touch "$REZOSS_SIVS_MODEL_PATCH_MARKER"
fi
if [ ! -f "$REZOSS_SIVS_MODEL_PATCH_MARKER" ]; then
    LOG "- Patch SamsungIntelliVoiceServices SCS device-model header to SM-S948B"
    APPLY_PATCH "system" "system/priv-app/SamsungIntelliVoiceServices/SamsungIntelliVoiceServices.apk" \
        "$MODPATH/sivs/SamsungIntelliVoiceServices.apk/0001-Use-S26U-device-model-for-SCS-requests.patch"
    touch "$REZOSS_SIVS_MODEL_PATCH_MARKER"
fi
unset REZOSS_SIVS_MODEL_PATCHED_FILE REZOSS_SIVS_MODEL_PATCH_MARKER

LOG "- Patch SamsungAiCore Vision Model device mapping"
APPLY_PATCH "system" "system/priv-app/SamsungAiCore/SamsungAiCore.apk" \
    "$MODPATH/aicore/SamsungAiCore.apk/0001-Map-dm-series-to-m3q-for-vision-model.patch"

LOG "- Experimentally patch SamsungAiCore SM8550/V73 QNN profile"
APPLY_PATCH "system" "system/priv-app/SamsungAiCore/SamsungAiCore.apk" \
    "$MODPATH/aicore/SamsungAiCore.apk/0002-Experiment-enable-SM8550-V73-QNN-profile.patch"
# Experimental: replace the S26U V81 HTP binaries inside SamsungAiCore.apk with
# S23U Hexagon V73 binaries and patch AiCore's own QNN wrapper crash paths.
local AICORE_DECODED_APK="$APKTOOL_DIR/system/priv-app/SamsungAiCore/SamsungAiCore.apk"
local AICORE_DECODED_LIB="$AICORE_DECODED_APK/lib/arm64-v8a"
local AICORE_DECODED_SSGEN_LIB="$AICORE_DECODED_APK/assets/ssgen/libs"
local AICORE_SNAP_QNN_LIB="$AICORE_DECODED_LIB/libsnap_qnn.so"
local AICORE_SNAP_QNN_PATCHED_LIB="$TMP_DIR/aicore_libsnap_qnn.so"
local AICORE_S23U_FW_DIR="$FW_DIR/SM-S918B_EUX"
local AICORE_QNN_MISSING=0
if [ ! -d "$AICORE_DECODED_LIB" ] || [ ! -d "$AICORE_DECODED_SSGEN_LIB" ]; then
    LOGE "SamsungAiCore.apk decoded QNN directories are missing"
    return 1
fi
if [ ! -f "$AICORE_SNAP_QNN_LIB" ]; then
    LOGE "SamsungAiCore.apk libsnap_qnn.so is missing"
    return 1
fi
for f in \
    "$AICORE_S23U_FW_DIR/vendor/lib64/snap/libQnnHtp.so" \
    "$AICORE_S23U_FW_DIR/vendor/lib64/snap/libQnnSystem.so" \
    "$AICORE_S23U_FW_DIR/vendor/lib64/snap/libQnnHtpV73Stub.so" \
    "$AICORE_S23U_FW_DIR/vendor/lib/rfsa/adsp/snap/libQnnHtpV73Skel.so"; do
    if [ ! -f "$f" ]; then
        LOGE "File not found: ${f//$SRC_DIR\//}"
        AICORE_QNN_MISSING=1
    fi
done
if [ "$AICORE_QNN_MISSING" != "0" ]; then
    return 1
fi
LOG "- Replacing SamsungAiCore.apk QNN HTP V81 binaries with S23U Hexagon V73 binaries"
cp -f "$AICORE_S23U_FW_DIR/vendor/lib64/snap/libQnnHtp.so" "$AICORE_DECODED_LIB/libQnnHtp.so"
cp -f "$AICORE_S23U_FW_DIR/vendor/lib64/snap/libQnnSystem.so" "$AICORE_DECODED_LIB/libQnnSystem.so"
cp -f "$AICORE_S23U_FW_DIR/vendor/lib64/snap/libQnnHtpV73Stub.so" "$AICORE_DECODED_LIB/libQnnHtpV73Stub.so"
cp -f "$AICORE_S23U_FW_DIR/vendor/lib64/snap/libQnnHtpV73Stub.so" "$AICORE_DECODED_LIB/libQnnHtpV81Stub.so"
cp -f "$AICORE_S23U_FW_DIR/vendor/lib/rfsa/adsp/snap/libQnnHtpV73Skel.so" "$AICORE_DECODED_SSGEN_LIB/libQnnHtpV73Skel.so"
cp -f "$AICORE_S23U_FW_DIR/vendor/lib/rfsa/adsp/snap/libQnnHtpV73Skel.so" "$AICORE_DECODED_SSGEN_LIB/libQnnHtpV81Skel.so"
if [ -f "$AICORE_S23U_FW_DIR/vendor/lib64/libqnnengine.so" ]; then
    cp -f "$AICORE_S23U_FW_DIR/vendor/lib64/libqnnengine.so" "$AICORE_DECODED_LIB/libqnnengine.so"
fi
LOG "- Patching SamsungAiCore QNN wrapper for SM8550/V73 experiment"
EVAL "python3 \"$MODPATH/aicore/patch_samsung_aicore_qnn_v73_experiment.py\" \"$AICORE_SNAP_QNN_LIB\" \"$AICORE_SNAP_QNN_PATCHED_LIB\""
EVAL "mv -f \"$AICORE_SNAP_QNN_PATCHED_LIB\" \"$AICORE_SNAP_QNN_LIB\""
unset AICORE_DECODED_APK AICORE_DECODED_LIB AICORE_DECODED_SSGEN_LIB AICORE_SNAP_QNN_LIB AICORE_SNAP_QNN_PATCHED_LIB AICORE_S23U_FW_DIR AICORE_QNN_MISSING

LOG "- Patching AIOSKernelService service config for SM8550"
APPLY_PATCH "system" "system/priv-app/AIOSKernelService/AIOSKernelService.apk" \
    "$MODPATH/aioskernel/AIOSKernelService.apk/0001-Allow-SM8550-service-config.patch"
LOG "- Patching AIOSKernelService QNN Skel name for Hexagon V73"
APPLY_PATCH "system" "system/priv-app/AIOSKernelService/AIOSKernelService.apk" \
    "$MODPATH/aioskernel/AIOSKernelService.apk/0002-Use-Hexagon-V73-QNN-skel.patch"
LOG "- Spoofing AIOSKernelService build flavor for SM8550"
APPLY_PATCH "system" "system/priv-app/AIOSKernelService/AIOSKernelService.apk" \
    "$MODPATH/aioskernel/AIOSKernelService.apk/0003-Spoof-SM8550-build-flavor.patch"
LOG "- Refusing AIOSKernelService LLM/LLMV before QNN execution on SM8550"
APPLY_PATCH "system" "system/priv-app/AIOSKernelService/AIOSKernelService.apk" \
    "$MODPATH/aioskernel/AIOSKernelService.apk/0004-Refuse-LLM-LLMV-before-QNN-execution.patch"
# Replace the S26U V81 HTP binaries inside AIOSKernelService.apk with the S23U Hexagon V73 pair.
local AIOS_DECODED_APK="$APKTOOL_DIR/system/priv-app/AIOSKernelService/AIOSKernelService.apk"
local AIOS_DECODED_LIB="$AIOS_DECODED_APK/lib/arm64-v8a"
local AIOS_DECODED_SSGEN_LIB="$AIOS_DECODED_APK/assets/ssgen/libs"
local AIOS_SSN_LIB="$AIOS_DECODED_LIB/libssneural_vndk.so"
local AIOS_SSN_PATCHED_LIB="$TMP_DIR/aios_libssneural_vndk.so"
local AIOS_SNAP_QNN_LIB="$AIOS_DECODED_LIB/libsnap_qnn.so"
local AIOS_SNAP_QNN_PATCHED_LIB="$TMP_DIR/aios_libsnap_qnn.so"
local S23U_FW_DIR="$FW_DIR/SM-S918B_EUX"
local AIOS_QNN_MISSING=0
if [ ! -d "$AIOS_DECODED_LIB" ] || [ ! -d "$AIOS_DECODED_SSGEN_LIB" ]; then
    LOGE "AIOSKernelService.apk decoded QNN directories are missing"
    return 1
fi
if [ ! -f "$AIOS_SSN_LIB" ]; then
    LOGE "AIOSKernelService.apk libssneural_vndk.so is missing"
    return 1
fi
if [ ! -f "$AIOS_SNAP_QNN_LIB" ]; then
    LOGE "AIOSKernelService.apk libsnap_qnn.so is missing"
    return 1
fi
for f in \
    "$S23U_FW_DIR/vendor/lib64/snap/libQnnHtp.so" \
    "$S23U_FW_DIR/vendor/lib64/snap/libQnnSystem.so" \
    "$S23U_FW_DIR/vendor/lib64/snap/libQnnHtpV73Stub.so" \
    "$S23U_FW_DIR/vendor/lib/rfsa/adsp/snap/libQnnHtpV73Skel.so"; do
    if [ ! -f "$f" ]; then
        LOGE "File not found: ${f//$SRC_DIR\//}"
        AIOS_QNN_MISSING=1
    fi
done
if [ "$AIOS_QNN_MISSING" != "0" ]; then
    return 1
fi
LOG "- Replacing AIOSKernelService.apk QNN HTP V81 binaries with S23U Hexagon V73 binaries"
cp -f "$S23U_FW_DIR/vendor/lib64/snap/libQnnHtp.so" "$AIOS_DECODED_LIB/libQnnHtp.so"
cp -f "$S23U_FW_DIR/vendor/lib64/snap/libQnnSystem.so" "$AIOS_DECODED_LIB/libQnnSystem.so"
cp -f "$S23U_FW_DIR/vendor/lib64/snap/libQnnHtpV73Stub.so" "$AIOS_DECODED_LIB/libQnnHtpV73Stub.so"
cp -f "$S23U_FW_DIR/vendor/lib64/snap/libQnnHtpV73Stub.so" "$AIOS_DECODED_LIB/libQnnHtpV81Stub.so"
cp -f "$S23U_FW_DIR/vendor/lib/rfsa/adsp/snap/libQnnHtpV73Skel.so" "$AIOS_DECODED_SSGEN_LIB/libQnnHtpV73Skel.so"
cp -f "$S23U_FW_DIR/vendor/lib/rfsa/adsp/snap/libQnnHtpV73Skel.so" "$AIOS_DECODED_SSGEN_LIB/libQnnHtpV81Skel.so"
if [ -f "$S23U_FW_DIR/vendor/lib64/libqnnengine.so" ]; then
    cp -f "$S23U_FW_DIR/vendor/lib64/libqnnengine.so" "$AIOS_DECODED_LIB/libqnnengine.so"
fi
LOG "- Patching AIOSKernelService native SSNeural SM8550 support gate"
EVAL "python3 \"$MODPATH/aioskernel/patch_sm8550_chipset.py\" \"$AIOS_SSN_LIB\" \"$AIOS_SSN_PATCHED_LIB\""
EVAL "mv -f \"$AIOS_SSN_PATCHED_LIB\" \"$AIOS_SSN_LIB\""
LOG "- Patching AIOSKernelService QNN logging null guard"
EVAL "python3 \"$MODPATH/aioskernel/patch_qnn_logging_nullguard.py\" \"$AIOS_SNAP_QNN_LIB\" \"$AIOS_SNAP_QNN_PATCHED_LIB\""
EVAL "mv -f \"$AIOS_SNAP_QNN_PATCHED_LIB\" \"$AIOS_SNAP_QNN_LIB\""
LOG "- Patching AIOSKernelService QNN backend V73 fallback"
EVAL "python3 \"$MODPATH/aioskernel/patch_qnn_backend_v73_fallback.py\" \"$AIOS_SNAP_QNN_LIB\" \"$AIOS_SNAP_QNN_PATCHED_LIB\""
EVAL "mv -f \"$AIOS_SNAP_QNN_PATCHED_LIB\" \"$AIOS_SNAP_QNN_LIB\""
unset AIOS_DECODED_APK AIOS_DECODED_LIB AIOS_DECODED_SSGEN_LIB AIOS_SSN_LIB AIOS_SSN_PATCHED_LIB AIOS_SNAP_QNN_LIB AIOS_SNAP_QNN_PATCHED_LIB S23U_FW_DIR AIOS_QNN_MISSING
SET_METADATA "system" "system/priv-app/AIOSKernelService" 0 0 755 "u:object_r:system_file:s0"
SET_METADATA "system" "system/priv-app/AIOSKernelService/AIOSKernelService.apk" 0 0 644 "u:object_r:system_file:s0"

LOG "- Re-signing AIOSKernelService.apk for SamsungAiCore signature-permission access"
AIOS_KERNEL_APK="$WORK_DIR/system/system/priv-app/AIOSKernelService/AIOSKernelService.apk"
AIOS_KERNEL_TMP="$TMP_DIR/aios_kernel_service"
AIOS_KERNEL_CERT_PREFIX="aosp"
$ROM_IS_OFFICIAL && AIOS_KERNEL_CERT_PREFIX="unica"
EVAL "rm -rf \"$AIOS_KERNEL_TMP\" && mkdir -p \"$AIOS_KERNEL_TMP\""
EVAL "signapk \"$SRC_DIR/security/${AIOS_KERNEL_CERT_PREFIX}_platform.x509.pem\" \"$SRC_DIR/security/${AIOS_KERNEL_CERT_PREFIX}_platform.pk8\" \"$AIOS_KERNEL_APK\" \"$AIOS_KERNEL_TMP/AIOSKernelService.signed.apk\""
EVAL "zipalign -c -p 4 \"$AIOS_KERNEL_TMP/AIOSKernelService.signed.apk\""
EVAL "mv -f \"$AIOS_KERNEL_TMP/AIOSKernelService.signed.apk\" \"$AIOS_KERNEL_APK\""
SET_METADATA "system" "system/priv-app/AIOSKernelService/AIOSKernelService.apk" 0 0 644 "u:object_r:system_file:s0"
EVAL "rm -rf \"$AIOS_KERNEL_TMP\""
unset AIOS_KERNEL_APK AIOS_KERNEL_TMP AIOS_KERNEL_CERT_PREFIX
else
    LOG "- Skip unsupported S26U QNN V81-to-V73 experiment on SM8550"
fi
unset UNICA_ENABLE_UNSAFE_S26U_QNN_EXPERIMENT

for REZOSS_NOTI_AI_REQ in \
    "system/priv-app/SecSettings/SecSettings.apk" \
    "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" \
    "system/priv-app/SettingsProvider/SettingsProvider.apk" \
    "system/priv-app/SamsungIntelliVoiceServices/SamsungIntelliVoiceServices.apk" \
    "system/etc/permissions/privapp-permissions-com.samsung.android.intellivoiceservice.xml" \
    "system/etc/permissions/signature-permissions-com.samsung.android.offline.languagemodel.xml" \
    "system/etc/default-permissions/default-permissions-com.samsung.android.scs.xml" \
    "system/etc/permissions/privapp-permissions-com.samsung.android.scs.xml" \
    "system/etc/permissions/signature-permissions-com.samsung.android.scs.xml" \
    "system/etc/permissions/signature-permissions-downloadable.xml" \
    "system/etc/sysconfig/preinstalled-packages-com.samsung.android.scs.xml" \
    "system/etc/sysconfig/samsungintellivoiceservice.xml" \
    "system/etc/sysconfig/samsungsmartsuggestions.xml" \
    "system/etc/sysconfig/allowed-system-preload-apps.xml"; do
    if [ ! -f "$WORK_DIR/system/$REZOSS_NOTI_AI_REQ" ]; then
        LOGW "Notification highlights requirement missing: /system/$REZOSS_NOTI_AI_REQ"
    fi
done
unset REZOSS_NOTI_AI_REQ

LOG "- Patch SecSettings Notification highlights S26U gate"
APPLY_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "$MODPATH/notification-priority/SecSettings.apk/0001-Match-S26U-notification-highlights-gate.patch"

LOG "- Patch services.jar AI notification priority/summary model gate"
APPLY_PATCH "system" "system/framework/services.jar" \
    "$MODPATH/notification-priority/services.jar/0001-Allow-dm1q-dm2q-dm3q-AI-notification-priority.patch"
LOG "- Add property-gated local notification-summary test bridge"
APPLY_PATCH "system" "system/framework/services.jar" \
    "$MODPATH/notification-priority/services.jar/0002-Add-property-gated-local-summary-test-bridge.patch"
LOG "- Route opt-in local notification summaries through the provider"
APPLY_PATCH "system" "system/framework/services.jar" \
    "$MODPATH/notification-priority/services.jar/0003-Call-opt-in-local-summary-provider.patch"
LOG "- Log Now Nudge notification request gates"
APPLY_PATCH "system" "system/framework/services.jar" \
    "$MODPATH/notification-priority/services.jar/0004-Log-Now-Nudge-notification-request-gates.patch"
LOG "- Keeping bound Qwen inference on foreground CPUs"
APPLY_PATCH "system" "system/framework/services.jar" \
    "$MODPATH/notification-priority/services.jar/0005-Keep-bound-Qwen-on-foreground-CPUs.patch"

LOG "- Patch SettingsProvider notification priority/summary defaults"
APPLY_PATCH "system" "system/priv-app/SettingsProvider/SettingsProvider.apk" \
    "$MODPATH/notification-priority/SettingsProvider.apk/0001-Enable-notification-priority-default.patch"

# =============================================================================
# Firewall Region String Cleanup
# =============================================================================
LOG "- Sanitize Firewall province/country strings"
DECODE_APK "system" "system/priv-app/Firewall/Firewall.apk"
python3 "$MODPATH/firewall/patch_region_strings.py" \
    "$APKTOOL_DIR/system/priv-app/Firewall/Firewall.apk" \
    "$MODPATH/firewall/region_names.tsv" \
    || ABORT "Failed to sanitize Firewall province/country strings"

LOG_STEP_OUT

# =============================================================================
# Kernel fallback helpers
# =============================================================================
REZOSS_ARCHIVED_KERNEL_DIR="$SRC_DIR/out/archived/kernel"

_REZOSS_ARCHIVE_KERNEL_IMAGE()
{
  local IMAGE_NAME="$1"
  local SOURCE_IMAGE="$WORK_DIR/kernel/$IMAGE_NAME"
  local ARCHIVED_IMAGE="$REZOSS_ARCHIVED_KERNEL_DIR/$IMAGE_NAME"

  if [ ! -f "$SOURCE_IMAGE" ]; then
    LOGE "Kernel image not found: ${SOURCE_IMAGE//$SRC_DIR\//}"
    return 1
  fi

  mkdir -p "$REZOSS_ARCHIVED_KERNEL_DIR" || return 1
  LOG "- Archiving $IMAGE_NAME to ${ARCHIVED_IMAGE//$SRC_DIR\//}"
  cp -f "$SOURCE_IMAGE" "$ARCHIVED_IMAGE" || return 1
}

_REZOSS_ARCHIVE_KERNEL()
{
  _REZOSS_ARCHIVE_KERNEL_IMAGE "boot.img" || return 1
  _REZOSS_ARCHIVE_KERNEL_IMAGE "init_boot.img" || return 1
}

_REZOSS_RESTORE_ARCHIVED_KERNEL_IMAGE()
{
  local IMAGE_NAME="$1"
  local ARCHIVED_IMAGE="$REZOSS_ARCHIVED_KERNEL_DIR/$IMAGE_NAME"
  local TARGET_IMAGE="$WORK_DIR/kernel/$IMAGE_NAME"

  if [ ! -f "$ARCHIVED_IMAGE" ]; then
    ABORT "Archived kernel image not found: ${ARCHIVED_IMAGE//$SRC_DIR\//}"
    return 1
  fi

  LOG "- Restoring $IMAGE_NAME from ${ARCHIVED_IMAGE//$SRC_DIR\//}"
  cp -f "$ARCHIVED_IMAGE" "$TARGET_IMAGE" \
    || {
      ABORT "Failed to restore $IMAGE_NAME from archived kernel"
      return 1
    }
}

_REZOSS_RESTORE_ARCHIVED_KERNEL()
{
  local RESTORE_BOOT="$1"
  local RESTORE_INIT_BOOT="$2"

  if [ "$RESTORE_BOOT" = "true" ]; then
    _REZOSS_RESTORE_ARCHIVED_KERNEL_IMAGE "boot.img" || return 1
  fi
  if [ "$RESTORE_INIT_BOOT" = "true" ]; then
    _REZOSS_RESTORE_ARCHIVED_KERNEL_IMAGE "init_boot.img" || return 1
  fi
}

# Local bundles are durable inputs; all image work stays in a unique /tmp directory.
_REZOSS_KERNEL_FROM_CACHE()
(
  local CACHE_DIR="$MODPATH/ksu-next"
  local TMP_DIR KERNEL_VERSION KERNEL_ZIP
  local -a KERNEL_ZIPS
  local KSU_KSUD KSU_MODULE
  python3 "$CACHE_DIR/update.py" "$CACHE_DIR" || {
    LOGE "Invalid KernelSU-Next cache/version configuration"
    return 1
  }
  KERNEL_VERSION="$(sed -n 's/^kernel=//p' "$CACHE_DIR/version" | tr -d '\r')"
  KERNEL_VERSION="${KERNEL_VERSION#5.15.}"
  if [[ ! "$KERNEL_VERSION" =~ ^[0-9]+$ ]]; then
    LOGE "Invalid cached kernel version: $KERNEL_VERSION"
    return 1
  fi
  mapfile -t KERNEL_ZIPS < <(find "$CACHE_DIR" -maxdepth 1 -type f -name "common-5.15.${KERNEL_VERSION}-*.zip" | sort)
  if [ "${#KERNEL_ZIPS[@]}" -ne 1 ]; then
    LOGE "Expected exactly one cached kernel ZIP for 5.15.$KERNEL_VERSION"
    return 1
  fi
  KERNEL_ZIP="${KERNEL_ZIPS[0]}"
  if [ "$(uname -m)" != "x86_64" ]; then
    LOGE "The cached ksud and magiskboot require an x86_64 build host"
    return 1
  fi

  TMP_DIR="$(mktemp -d /tmp/rezoss-kernel.XXXXXX)" || return 1
  trap 'rm -rf -- "$TMP_DIR"' EXIT
  # Validate cached ZIPs too, including when every update check failed offline.
  python3 - "$CACHE_DIR" "$KERNEL_ZIP" <<'PY_CACHE'
import importlib.util
from pathlib import Path
import sys
sys.dont_write_bytecode = True
cache = Path(sys.argv[1])
spec = importlib.util.spec_from_file_location('ksu_update', cache / 'update.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
module.validate(Path(sys.argv[2]), 'Image.gz')
for name, member in module.ARTIFACTS.items():
    module.validate(cache / (name + '.zip'), member)
PY_CACHE
  [ "$?" -eq 0 ] || return 1
  cp -p "$CACHE_DIR/magiskboot" "$TMP_DIR/magiskboot" || return 1
  chmod +x "$TMP_DIR/magiskboot" || return 1
  unzip -p "$KERNEL_ZIP" Image.gz > "$TMP_DIR/replacement-kernel" || return 1
  KSU_KSUD="$TMP_DIR/ksud"
  KSU_MODULE="$TMP_DIR/android13-5.15_kernelsu.ko"
  unzip -p "$CACHE_DIR/ksud-x86_64-unknown-linux-musl.zip" x86_64-unknown-linux-musl/release/ksud > "$KSU_KSUD" || return 1
  unzip -p "$CACHE_DIR/aarch64-android13-5.15-ksu-lkm.zip" android13-5.15_kernelsu.ko > "$KSU_MODULE" || return 1
  chmod +x "$KSU_KSUD" || return 1
  "$KSU_KSUD" --help >/dev/null || return 1

  LOG "- Replacing boot kernel with cached $(basename "$KERNEL_ZIP")"
  cp -p "$WORK_DIR/kernel/boot.img" "$TMP_DIR/boot.img" || return 1
  cd "$TMP_DIR" || return 1
  ./magiskboot unpack boot.img || return 1
  cp replacement-kernel kernel || return 1
  ./magiskboot repack boot.img || return 1
  [ -s new-boot.img ] || return 1
  cp new-boot.img "$WORK_DIR/kernel/boot.img" || return 1

  # Separate KSU outputs from magiskboot's new-boot.img to prevent stale selection.
  mkdir "$TMP_DIR/ksu" || return 1
  cp "$WORK_DIR/kernel/init_boot.img" "$TMP_DIR/ksu/init_boot.img" || return 1
  cd "$TMP_DIR/ksu" || return 1
  LOG "- Patching init_boot.img with cached $(sed -n 's/^ksu-next-CI=//p' "$CACHE_DIR/version" | tr -d '\r')"
  "$KSU_KSUD" boot-patch -b init_boot.img --module "$KSU_MODULE" --kmi android13-5.15 || return 1
  local -a PATCHED_IMAGES
  mapfile -t PATCHED_IMAGES < <(find . -maxdepth 1 -type f -name '*.img' ! -name init_boot.img)
  if [ "${#PATCHED_IMAGES[@]}" -ne 1 ] || [ ! -s "${PATCHED_IMAGES[0]}" ]; then
    LOGE "Expected exactly one newly patched init_boot image"
    return 1
  fi
  cp "${PATCHED_IMAGES[0]}" "$WORK_DIR/kernel/init_boot.img" || return 1
  cmp -s "${PATCHED_IMAGES[0]}" "$WORK_DIR/kernel/init_boot.img" || return 1
  LOG "- Verified cached KernelSU-Next patched init_boot.img"
  LOG "  SHA-256: $(sha256sum "$WORK_DIR/kernel/init_boot.img" | cut -d ' ' -f 1)"
)

# =============================================================================
# Kernel - NPL keeps S23 Edgar + KSU via kernel.sh (not tip ksu-next cache primary)
# Tip helpers (_REZOSS_KERNEL_FROM_CACHE / ksu-next) remain available for fallback tooling.
# =============================================================================
# shellcheck source=unica/mods/rezoss/kernel.sh
source "$MODPATH/kernel.sh"
REZOSS_APPLY_KERNEL_PATCHES "edgars+ksu"

# Experimental Wi-Fi modules must match the final selected kernel.
source "$MODPATH/wondertap/integrate.sh" || return 1
