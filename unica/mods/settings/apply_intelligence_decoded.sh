# Patch decoded SecSettingsIntelligence.apk so UN1CA appears in Settings search.
# Sourced after DECODE_APK of SecSettingsIntelligence.

LOG "- Patching Settings Intelligence top-level keys in /system/system/priv-app/SecSettingsIntelligence.apk"
TOP_LEVEL_KEYS_COLLECTOR="$(
    find "$APKTOOL_DIR/system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" \
        -path '*/com/samsung/android/settings/intelligence/search/categorizing/TopLevelKeysCollector.smali' \
        -print -quit
)"
if [ ! "$TOP_LEVEL_KEYS_COLLECTOR" ]; then
    LOGE "TopLevelKeysCollector smali not found in /system/system/priv-app/SecSettingsIntelligence.apk"
    return 1
fi
TOP_LEVEL_KEYS_COLLECTOR_SMALI="${TOP_LEVEL_KEYS_COLLECTOR#"$APKTOOL_DIR"/system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk/}"

if ! grep -q '"top_level_unica"' "$TOP_LEVEL_KEYS_COLLECTOR"; then
    SMALI_PATCH "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" \
        "$TOP_LEVEL_KEYS_COLLECTOR_SMALI" "replace" \
        '<init>(Landroid/content/Context;)V' \
        '.locals 36' \
        '.locals 37' \
        > /dev/null
    SMALI_PATCH "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" \
        "$TOP_LEVEL_KEYS_COLLECTOR_SMALI" "replace" \
        '<init>(Landroid/content/Context;)V' \
        'filled-new-array/range {v1 .. v35}, [Ljava/lang/String;' \
        '    const-string v36, "top_level_unica"\n\n    filled-new-array/range {v1 .. v36}, [Ljava/lang/String;' \
        > /dev/null
fi
unset TOP_LEVEL_KEYS_COLLECTOR TOP_LEVEL_KEYS_COLLECTOR_SMALI
