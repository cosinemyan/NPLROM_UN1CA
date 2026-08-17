if [ ! "$(GET_PROP "system" "ro.unica.codename")" ]; then
    # Match latest Samsung's flagship device codename
    ROM_CODENAME="$(basename "$MODPATH")"
    SET_PROP "system" "ro.unica.codename" "${ROM_CODENAME^}"
    unset ROM_CODENAME
fi

_PARADIGM_SET_VENDOR_FLOATING_FEATURE_CONFIG()
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
        if ! grep -q "Added by unica/mods/paradigm" "$FILE"; then
            echo "    <!-- Added by unica/mods/paradigm/customize.sh -->" >> "$FILE"
        fi
        echo "    <${CONFIG}>${VALUE}</${CONFIG}>" >> "$FILE"
        echo "</SecFloatingFeatureSet>" >> "$FILE"
    fi
}

# 2025 Audio Pack
LOG_STEP_IN "- Adding 2025 Audio Pack"
DELETE_FROM_WORK_DIR "system" "system/hidden/INTERNAL_SDCARD/Music/Samsung/Over_the_Horizon.mp3"
ADD_TO_WORK_DIR "m3qxxx" "system" \
    "system/hidden/INTERNAL_SDCARD/Music/Samsung/Over_the_Horizon.m4a" 0 0 644 "u:object_r:system_file:s0"
DELETE_FROM_WORK_DIR "system" "system/media/audio/notifications"
DELETE_FROM_WORK_DIR "system" "system/media/audio/ringtones"
if $TARGET_AUDIO_SUPPORT_ACH_RINGTONE; then
    ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/ringtones_count_list.txt" 0 0 644 "u:object_r:system_file:s0"
    ADD_TO_WORK_DIR "m3qxxx" "system" "system/media/audio/notifications" 0 0 755 "u:object_r:system_file:s0"
    ADD_TO_WORK_DIR "m3qxxx" "system" "system/media/audio/ringtones" 0 0 755 "u:object_r:system_file:s0"
    SET_PROP "vendor" "ro.config.ringtone" "ACH_Galaxy_Bells.ogg"
    SET_PROP "vendor" "ro.config.notification_sound" "ACH_Brightline.ogg"
    SET_PROP "vendor" "ro.config.alarm_alert" "ACH_Morning_Xylophone.ogg"
    SET_PROP "vendor" "ro.config.media_sound" "Media_preview_Over_the_horizon.ogg"
    SET_PROP "vendor" "ro.config.ringtone_2" "ACH_Atomic_Bell.ogg"
    SET_PROP "vendor" "ro.config.notification_sound_2" "ACH_Three_Star.ogg"
else
    ADD_TO_WORK_DIR "a56xnaxx" "system" "system/etc/ringtones_count_list.txt" 0 0 644 "u:object_r:system_file:s0"
    ADD_TO_WORK_DIR "a56xnaxx" "system" "system/media/audio/notifications" 0 0 755 "u:object_r:system_file:s0"
    ADD_TO_WORK_DIR "a56xnaxx" "system" "system/media/audio/ringtones" 0 0 755 "u:object_r:system_file:s0"
    SET_PROP "vendor" "ro.config.ringtone" "Galaxy_Bells.ogg"
    SET_PROP "vendor" "ro.config.notification_sound" "Brightline.ogg"
    SET_PROP "vendor" "ro.config.alarm_alert" "Morning_Xylophone.ogg"
    SET_PROP "vendor" "ro.config.media_sound" "Media_preview_Over_the_horizon.ogg"
    SET_PROP "vendor" "ro.config.ringtone_2" "Atomic_Bell.ogg"
    SET_PROP "vendor" "ro.config.notification_sound_2" "Three_Star.ogg"
fi
ADD_TO_WORK_DIR "m3qxxx" "system" \
    "system/media/audio/ui/Media_preview_Over_the_horizon.ogg" 0 0 644 "u:object_r:system_file:s0"
APPLY_PATCH "system" "system/priv-app/SecSoundPicker/SecSoundPicker.apk" \
    "$MODPATH/brandsound/SecSoundPicker.apk/0001-Enable-SUPPORT_SAMSUNG_BRAND_SOUND_ONEUI_7.patch"
LOG_STEP_OUT

# Adaptive colour tone
LOG_STEP_IN "- Adding Adaptive colour tone feature"
ADD_TO_WORK_DIR "m3qxxx" "system" \
    "system/etc/permissions/privapp-permissions-com.samsung.android.sead.xml" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" \
    "system/priv-app/EnvironmentAdaptiveDisplay/EnvironmentAdaptiveDisplay.apk" 0 0 644 "u:object_r:system_file:s0"
if $TARGET_LCD_SUPPORT_MDNIE_HW; then
    APPLY_PATCH "system" "system/framework/services.jar" \
        "$MODPATH/ead/services.jar/0001-Add-Adaptive-color-tone-feature.patch"
else
    APPLY_PATCH "system" "system/framework/services.jar" \
        "$MODPATH/ead_mdnie/services.jar/0001-Add-Adaptive-color-tone-feature.patch"
fi
if $TARGET_COMMON_SUPPORT_DYN_RESOLUTION_CONTROL; then
    if [ "$TARGET_PLATFORM_SDK_VERSION" -ge "36" ]; then
        APPLY_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
            "$MODPATH/ead_resolution/SecSettings.apk/0001-Add-Adaptive-color-tone-feature.patch"
    else
        APPLY_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
            "$MODPATH/ead_resolution_legacy/SecSettings.apk/0001-Add-Adaptive-color-tone-feature.patch"
    fi
else
    APPLY_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
        "$MODPATH/ead/SecSettings.apk/0001-Add-Adaptive-color-tone-feature.patch"
fi
APPLY_PATCH "system" "system/priv-app/SettingsProvider/SettingsProvider.apk" \
    "$MODPATH/ead/SettingsProvider.apk/0001-Add-Adaptive-color-tone-feature.patch"
APPLY_PATCH "system_ext" "priv-app/SystemUI/SystemUI.apk" \
    "$MODPATH/ead/SystemUI.apk/0001-Add-Adaptive-color-tone-toggle.patch"
LOG_STEP_OUT

# Media Context Analyzer
LOG_STEP_IN "- Adding Media Context Analyzer feature"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/mediacontextanalyzer" 0 0 755 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/mediacontextanalyzer/07-03_Video_HumanPetDetection_v2.0.1_SM8850_SNPE238.dlc" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/mediacontextanalyzer/07-04_Video_HumanPetPose_v3.1.1_SM8850-SNPE238.dlc" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/mediacontextanalyzer/07-05_Video_KeywordClassification_v1.1.0_SM8850_SNPE238.dlc" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/mediacontextanalyzer/Detection.dlc" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/mediacontextanalyzer/Keyword.dlc" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/mediacontextanalyzer/Pose.dlc" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libcontextanalyzer_jni.media.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libmediacontextanalyzer.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libvideo-highlight-arm64-v8a.so" 0 0 644 "u:object_r:system_lib_file:s0"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_MMFW_CONFIG_MEDIA_CONTEXT_ANALYZER_CORE" "NPU"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_MMFW_SUPPORT_MEDIA_CONTEXT_ANALYZER" "TRUE"
LOG_STEP_OUT

# Audio eraser
# Requires SEC_PRODUCT_FEATURE_MMFW_SUPPORT_MEDIA_CONTEXT_ANALYZER
LOG_STEP_IN "- Adding Audio eraser feature"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/audio_ae_intervals.conf" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/fastScanner.tflite" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/mss_v0.23.0_VMWO_2_fp32.sorione" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/public.libraries-audio.samsung.txt" 0 0 644 "u:object_r:system_file:s0"
# Keep SoundAlive_C and its native wrappers aligned on the One UI 9 ver900 path.
DELETE_FROM_WORK_DIR "system" "system/etc/permissions/privapp-permissions-com.sec.android.app.soundalive_B2.xml"
DELETE_FROM_WORK_DIR "system" "system/priv-app/SoundAlive_B2"
DELETE_FROM_WORK_DIR "system" "system/lib/libaudiosaplus_sec_legacy.so"
DELETE_FROM_WORK_DIR "system" "system/lib/lib_SoundAlive_play_plus_ver800.so"
DELETE_FROM_WORK_DIR "system" "system/lib64/lib_SoundAlive_play_plus_ver800.so"
DELETE_FROM_WORK_DIR "vendor" "lib/soundfx/libaudiosaplus_sec.so"
DELETE_FROM_WORK_DIR "vendor" "lib/lib_SoundAlive_play_plus_ver800.so"
DELETE_FROM_WORK_DIR "vendor" "lib64/lib_SoundAlive_play_plus_ver800.so"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/permissions/privapp-permissions-com.sec.android.app.soundalive_C.xml" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/etc/sysconfig/preinstalled-packages-com.sec.android.app.soundalive_C.xml" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/priv-app/SoundAlive_C" 0 0 755 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libaudiosaplus_sec_legacy.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libSoundAlive_VSP_ver316c_ARMCpp.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/lib_SoundAlive_AlbumArt_ver105.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/lib_SoundAlive_SRC192_ver205a.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/lib_SoundAlive_SRC384_ver320.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/lib_SoundAlive_SRC384_ver330.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/lib_SoundAlive_play_plus_ver900.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/lib_soundaliveresampler.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/priv-app/AudioMirroring/AudioMirroring.apk" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/bin/audiomirroring" 0 2000 755 "u:object_r:audiomirroring_exec:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libaudiomirroring.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libaudiomirroring_jni.audiomirroring.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libaudiomirroringservice.so" 0 0 644 "u:object_r:system_lib_file:s0"
# Keep APlayer on the One UI 8.5 media stack.
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libaplayer.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/android.media.audio.common.types-V1-ndk.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/android.media.audio.common.types-V4-cpp.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/android.media.audio.common.types-V4-ndk.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/android.media.audio.eraser.types-V1-ndk.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/android.media.audio.eraser.types-V2-ndk.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libmediasndk.mediacore.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libmediasndk.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libmultisourceseparator.audio.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libmultisourceseparator.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libsbs.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libtensorflowlite.audio.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libtensorflowlite_jni.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libvoice_booster.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libsoundboostereq_legacy.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/lib_sag_ai_sound_sep_v1.00.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/lib_sag_ai_sound_sep_v2.00.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libtensorflowlite_gpu_delegate.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/lib64/libveframework.videoeditor.samsung.so" 0 0 644 "u:object_r:system_lib_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/android.media.audio.eraser.types-V1-ndk.so" 0 0 644 "u:object_r:vendor_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/libtensorflowlite.adv_audio.samsung.so" 0 0 644 "u:object_r:vendor_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/soundfx/libaudiosaplus_sec.so" 0 0 644 "u:object_r:vendor_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/lib_SoundAlive_3DPosition_ver202.so" 0 0 644 "u:object_r:vendor_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/lib_SoundAlive_AlbumArt_ver105.so" 0 0 644 "u:object_r:vendor_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "vendor" "lib64/lib_SoundAlive_play_plus_ver900.so" 0 0 644 "u:object_r:vendor_file:s0"
_AUDIO_ERASER_SOUNDALIVE_VERSION="eq_custom,uhq_onoff,karaoke,adapt,spk_stereo,dvfs_20_percent,dvfs_max_45_percent,voice_boost,dolby_game_spk_off"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_AUDIO_CONFIG_SOUNDALIVE_VERSION" "$_AUDIO_ERASER_SOUNDALIVE_VERSION"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_AUDIO_CONFIG_MULTISOURCE_SEPARATOR" "{FastScanning_6, SourceSeparator_4, Version_1.3.0}"
_PARADIGM_SET_VENDOR_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_AUDIO_CONFIG_SOUNDALIVE_VERSION" "$_AUDIO_ERASER_SOUNDALIVE_VERSION"
_PARADIGM_SET_VENDOR_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_AUDIO_CONFIG_MULTISOURCE_SEPARATOR" "{FastScanning_6, SourceSeparator_4, Version_1.3.0}"
unset _AUDIO_ERASER_SOUNDALIVE_VERSION
LOG_STEP_OUT
unset -f _PARADIGM_SET_VENDOR_FLOATING_FEATURE_CONFIG

# Now brief
# Requires SEC_FLOATING_FEATURE_COMMON_CONFIG_AI_VERSION >= 20251
# or SEC_FLOATING_FEATURE_FRAMEWORK_SUPPORT_AI_BRIEF_FOR_UT
LOG_STEP_IN "- Adding Now brief feature"
ADD_TO_WORK_DIR "m3qxxx" "system" \
    "system/etc/default-permissions/default-permissions-com.samsung.android.app.moments.xml" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" \
    "system/etc/permissions/privapp-permissions-com.samsung.android.app.moments.xml" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" \
    "system/etc/sysconfig/moments.xml" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" "system/priv-app/Moments/Moments.apk" 0 0 644 "u:object_r:system_file:s0"
# SmartSuggestions APK and permission XMLs are consolidated in unica/mods/rezoss.
# ADD_TO_WORK_DIR "$SRC_DIR/unica/mods/rezoss" "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk" 0 0 644 "u:object_r:system_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "system" \
#     "system/etc/default-permissions/default-permissions-com.samsung.android.smartsuggestions.xml" 0 0 644 "u:object_r:system_file:s0"
# ADD_TO_WORK_DIR "m3qxxx" "system" \
#     "system/etc/permissions/privapp-permissions-com.samsung.android.smartsuggestions.xml" 0 0 644 "u:object_r:system_file:s0"
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_FRAMEWORK_SUPPORT_PERSONALIZED_DATA_CORE" "TRUE"
LOG "- Forcing Now Nudge availability in SecSettings.apk"
APPLY_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "$MODPATH/now-nudge/SecSettings.apk/0001-Force-Now-Nudge-Galaxy-AI-availability.patch"
# LOG "- Downloading Smart suggestions app with full-global-release flavor"
# DOWNLOAD_FILE "$(GET_GALAXY_STORE_DOWNLOAD_URL "com.samsung.android.smartsuggestions")" \
    # "$WORK_DIR/system/system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk"
# HACK [
# Samsung has released an update for the Smart suggestions app in March 2026.
# The versioning of the "basic-global-release" flavor differs from the "full-global-release" one.
# This is done on purpose: Samsung uses a lower version number to avoid installing this variant
# on unsupported devices by triggering the downgrade check in PM. To avoid users updating to the
# "non-AI" app, let's fake the versionCode so that it matches the latest available version.
# DECODE_APK "system" "system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk"
# LOG "- Patching versionCode in SamsungSmartSuggestions.apk"
# EVAL "sed -i \"s/710500000/711100100/g\" \"$APKTOOL_DIR/system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk/apktool.yml\""
# # ]
# SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_FRAMEWORK_SUPPORT_PERSONALIZED_DATA_CORE" "TRUE"
LOG_STEP_OUT

# Semantic search
# Requires SEC_FLOATING_FEATURE_COMMON_CONFIG_AI_VERSION >= 20251
LOG_STEP_IN "- Adding Semantic search feature"
ADD_TO_WORK_DIR "m3qxxx" "system" \
    "system/etc/default-permissions/default-permissions-com.samsung.mediasearch.xml" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "pa2qxxx" "system" \
    "system/etc/mediasearch/data/dec_adaptor.tflite" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "pa2qxxx" "system" \
    "system/etc/mediasearch/data/dec_event.tflite" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "pa2qxxx" "system" \
    "system/etc/mediasearch/data/enc_image.tflite" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "pa2qxxx" "system" \
    "system/etc/mediasearch/data/enc_text.tflite" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "pa2qxxx" "system" \
    "system/etc/mediasearch/data/versioninfo.json" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" \
    "system/etc/permissions/privapp-permissions-com.samsung.mediasearch.xml" 0 0 644 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "m3qxxx" "system" \
    "system/priv-app/MediaSearch/MediaSearch.apk" 0 0 644 "u:object_r:system_file:s0"
# The S26U v5 package initializes an SM8850 V81/NPU model on dm3q. Keep the
# S26U provider surface and alias its bundled HTP payloads to the SM8550/V73
# pair so the native QNN path can be tested on dm3q.
LOG "- Adding S26U SemanticSearchCore with SM8550/V73 QNN payloads"
ADD_TO_WORK_DIR "m3qxxx" "system" \
    "system/priv-app/SemanticSearchCore/SemanticSearchCore.apk" 0 0 644 "u:object_r:system_file:s0"
DECODE_APK "system" "system/priv-app/SemanticSearchCore/SemanticSearchCore.apk"
SEMANTIC_SEARCH_CORE_DECODED="$APKTOOL_DIR/system/priv-app/SemanticSearchCore/SemanticSearchCore.apk"
SEMANTIC_SEARCH_CORE_LIB="$SEMANTIC_SEARCH_CORE_DECODED/lib/arm64-v8a"
SEMANTIC_SEARCH_CORE_SHARED="$SEMANTIC_SEARCH_CORE_DECODED/assets/shared"
SEMANTIC_SEARCH_CORE_FW="$FW_DIR/SM-S911B_EUX"
SEMANTIC_SEARCH_CORE_QNN_MISSING=0
if [ ! -d "$SEMANTIC_SEARCH_CORE_LIB" ] || [ ! -d "$SEMANTIC_SEARCH_CORE_SHARED" ]; then
    LOGE "SemanticSearchCore.apk decoded QNN directories are missing"
    return 1
fi
for f in \
    "$SEMANTIC_SEARCH_CORE_FW/vendor/lib64/snap/libQnnHtp.so" \
    "$SEMANTIC_SEARCH_CORE_FW/vendor/lib64/snap/libQnnSystem.so" \
    "$SEMANTIC_SEARCH_CORE_FW/vendor/lib64/snap/libQnnHtpV73Stub.so" \
    "$SEMANTIC_SEARCH_CORE_FW/vendor/lib/rfsa/adsp/snap/libQnnHtpV73Skel.so"; do
    if [ ! -f "$f" ]; then
        LOGE "File not found: ${f//$SRC_DIR\//}"
        SEMANTIC_SEARCH_CORE_QNN_MISSING=1
    fi
done
if [ "$SEMANTIC_SEARCH_CORE_QNN_MISSING" != "0" ]; then
    return 1
fi
LOG "- Replacing SemanticSearchCore.apk QNN HTP V81 binaries with S23U Hexagon V73 binaries"
cp -f "$SEMANTIC_SEARCH_CORE_FW/vendor/lib64/snap/libQnnHtp.so" "$SEMANTIC_SEARCH_CORE_LIB/libQnnHtp.so"
cp -f "$SEMANTIC_SEARCH_CORE_FW/vendor/lib64/snap/libQnnSystem.so" "$SEMANTIC_SEARCH_CORE_LIB/libQnnSystem.so"
cp -f "$SEMANTIC_SEARCH_CORE_FW/vendor/lib64/snap/libQnnHtpV73Stub.so" "$SEMANTIC_SEARCH_CORE_LIB/libQnnHtpV73Stub.so"
cp -f "$SEMANTIC_SEARCH_CORE_FW/vendor/lib64/snap/libQnnHtpV73Stub.so" "$SEMANTIC_SEARCH_CORE_LIB/libQnnHtpV81Stub.so"
cp -f "$SEMANTIC_SEARCH_CORE_FW/vendor/lib/rfsa/adsp/snap/libQnnHtpV73Skel.so" "$SEMANTIC_SEARCH_CORE_SHARED/libQnnHtpV73Skel.so"
cp -f "$SEMANTIC_SEARCH_CORE_FW/vendor/lib/rfsa/adsp/snap/libQnnHtpV73Skel.so" "$SEMANTIC_SEARCH_CORE_SHARED/libQnnHtpV81Skel.so"
EVAL "sed -i 's/qc-sm8850-release/qc-sm8550-release/g' \"$SEMANTIC_SEARCH_CORE_DECODED/AndroidManifest.xml\""
EVAL "sed -i 's/android:extractNativeLibs=\"false\"/android:extractNativeLibs=\"true\"/g' \"$SEMANTIC_SEARCH_CORE_DECODED/AndroidManifest.xml\""
if ! grep -q 'android:extractNativeLibs="true"' "$SEMANTIC_SEARCH_CORE_DECODED/AndroidManifest.xml"; then
    LOGE "Failed to enable native library extraction for SemanticSearchCore.apk"
    return 1
fi
SEMANTIC_SEARCH_CORE_BYPASS_PATCH="$MODPATH/semanticsearch/SemanticSearchCore.apk/0001-Bypass-QNN-HTP-neural-entrypoints.patch"
EVAL "find \"$SEMANTIC_SEARCH_CORE_DECODED\" -type f \( -name \"*.orig\" -o -name \"*.rej\" \) -delete"
if LC_ALL=C patch --dry-run -R -p1 -d "$SEMANTIC_SEARCH_CORE_DECODED" -l < "$SEMANTIC_SEARCH_CORE_BYPASS_PATCH" > /dev/null 2>&1; then
    LOG "- SemanticSearchCore QNN HTP bypass patch already applied"
elif LC_ALL=C patch --dry-run -p1 -d "$SEMANTIC_SEARCH_CORE_DECODED" -N --forward -l < "$SEMANTIC_SEARCH_CORE_BYPASS_PATCH" > /dev/null 2>&1; then
    LOG "- Bypassing SemanticSearchCore native QNN HTP neural entrypoints"
    EVAL "LC_ALL=C patch -p1 -d \"$SEMANTIC_SEARCH_CORE_DECODED\" -N --forward -l < \"$SEMANTIC_SEARCH_CORE_BYPASS_PATCH\"" || return 1
else
    LOGE "SemanticSearchCore.apk QNN HTP bypass patch state is inconsistent"
    return 1
fi
EVAL "find \"$SEMANTIC_SEARCH_CORE_DECODED\" -type f \( -name \"*.orig\" -o -name \"*.rej\" \) -delete"
unset SEMANTIC_SEARCH_CORE_DECODED SEMANTIC_SEARCH_CORE_LIB SEMANTIC_SEARCH_CORE_SHARED
unset SEMANTIC_SEARCH_CORE_FW SEMANTIC_SEARCH_CORE_QNN_MISSING SEMANTIC_SEARCH_CORE_BYPASS_PATCH
DECODE_APK "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk"
LOG "- Enabling Semantic search feature in /system/system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk"
EVAL "cp -a \"$MODPATH/semanticsearch/SecSettingsIntelligence.apk/res/raw/\"* \"$APKTOOL_DIR/system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk/res/raw\""
SMALI_PATCH "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" \
    "smali_classes2/com/samsung/android/settings/intelligence/Rune.smali" "replaceall" \
    "const-string v1, \\\"\\\"" \
    "const-string v1, \\\"400\\\"" \
    > /dev/null
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_MSCH_SUPPORT_NLSEARCH" "TRUE"
LOG_STEP_OUT

# Game Booster
LOG "- Downloading latest Game Booster app"
DOWNLOAD_FILE "$(GET_GALAXY_STORE_DOWNLOAD_URL "com.samsung.android.game.gametools")" \
    "$WORK_DIR/system/system/priv-app/GameTools_Dream/GameTools_Dream.apk"
