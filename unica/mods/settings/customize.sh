#!/usr/bin/env bash

# Load NPL version config
source "$SRC_DIR/unica/configs/version.sh" || true

# Set NPL ROM identity properties (shown in NPL Settings menu)
SET_PROP "system" "ro.npl.version"    "${NPL_VERSION:-1.0-STABLE}"
SET_PROP "system" "ro.npl.maintainer" "${NPL_MAINTAINER:-Cosine}"
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

# Disable stock OTA references
if [ ! -f "$WORK_DIR/system/system/priv-app/ChoiDujour/ChoiDujour.apk" ]; then
    SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
        "smali_classes5/com/samsung/android/settings/softwareupdate/SoftwareUpdateUtils.smali" "return" \
        'isOTAUpgradeAllowed(Landroid/content/Context;)Z' \
        'false'
fi

# Always show One UI minor version
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali_classes5/com/samsung/android/settings/deviceinfo/softwareinfo/OneUIVersionPreferenceController.smali" "replace" \
    'isDeviceWithMicroVersion()Z' \
    'move-result p0' \
    'const/4 p0, 0x1'

# Show real device model number
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali_classes5/com/samsung/android/settings/deviceinfo/aboutphone/ModelNameGetter.smali" "replace" \
    'getModelName()Ljava/lang/String;' \
    'ro.product.model' \
    'ro.boot.em.model'

LOG_STEP_IN "- Adding UN1CA Settings"

# Dynamically patch SecSettings
# - Add missing/non-xml files in place
# - Patch existing files
#   - Use the first line of the file to tell sed how to apply the rest of the content
#   - Exception made for files under *res/values* where the "resources" tag gets nuked
while IFS= read -r f; do
    f="${f//$MODPATH\/SecSettings.apk\//}"

    if [ ! -f "$APKTOOL_DIR/system/priv-app/SecSettings/SecSettings.apk/$f" ] || \
            [[ "$f" != *".xml" ]]; then
        LOG "- Adding \"$f\" to /system/system/priv-app/SecSettings.apk"
        EVAL "mkdir -p \"$(dirname "$APKTOOL_DIR/system/priv-app/SecSettings/SecSettings.apk/$f")\""
        EVAL "cp -a \"$MODPATH/SecSettings.apk/${f//\$/\\$}\" \"$APKTOOL_DIR/system/priv-app/SecSettings/SecSettings.apk/${f//\$/\\$}\""
    else
        LOG "- Patching \"$f\" in /system/system/priv-app/SecSettings.apk"
        if [[ "$f" == *"res/values"* ]]; then
            PATCH_INST="/<\/resources>/i"
            CONTENT="$(sed -e "/?xml/d" -e "/resources>/d" "$MODPATH/SecSettings.apk/$f")"
        else
            PATCH_INST="$(head -n 1 "$MODPATH/SecSettings.apk/$f")"
            CONTENT="$(tail -n +2 "$MODPATH/SecSettings.apk/$f")"
        fi
        CONTENT="$(sed -e "s/\"/\\\\\"/g" -e "s/\\$/\\\\$/g" -e "s/ /\\\ /g" -e "s/\\\\n/\\\\\\\\\n/g" <<< "$CONTENT")"
        CONTENT="$(sed -E ':a;N;$!ba;s/\r{0,1}\n/\\n/g' <<< "$CONTENT")"
        EVAL "sed -i \"$PATCH_INST $CONTENT\" \"$APKTOOL_DIR/system/priv-app/SecSettings/SecSettings.apk/$f\""
    fi
done < <(find "$MODPATH/SecSettings.apk" -type f \
    ! -name "*.bak*" \
    ! -name "*.orig" \
    ! -name "*.rej")

# Mark UN1CA Settings fragments as "valid"
LOG "- Patching \"smali/com/android/settings/core/gateway/SettingsGateway.smali\" in /system/system/priv-app/SecSettings.apk"
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v171}, [Ljava/lang/String;' \
    '    const-string v172, "io.mesalabs.unica.settings.UnicaSettingsFragment"\n\n    filled-new-array/range {v1 .. v160}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v160}, [Ljava/lang/String;' \
    '    const-string v173, "io.mesalabs.unica.settings.extra.ExtraSettingsFragment"\n\n    filled-new-array/range {v1 .. v161}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v161}, [Ljava/lang/String;' \
    '    const-string v174, "io.mesalabs.unica.settings.hma.HideMyApplistFragment"\n\n    filled-new-array/range {v1 .. v162}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v162}, [Ljava/lang/String;' \
    '    const-string v175, "io.mesalabs.unica.settings.spoof.HideDeveloperStatusFragment"\n\n    filled-new-array/range {v1 .. v163}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v163}, [Ljava/lang/String;' \
    '    const-string v176, "io.mesalabs.unica.settings.spoof.SpoofSettingsFragment"\n\n    filled-new-array/range {v1 .. v164}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v164}, [Ljava/lang/String;' \
    '    const-string v177, "io.mesalabs.unica.settings.ui.UISettingsFragment"\n\n    filled-new-array/range {v1 .. v177}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v177}, [Ljava/lang/String;' \
    '    const-string v178, "io.mesalabs.unica.settings.spoof.CameraFeatureFragment"\n\n    filled-new-array/range {v1 .. v178}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v178}, [Ljava/lang/String;' \
    '    const-string v179, "io.mesalabs.unica.settings.extra.ScpmAllowlistFragment"\n\n    filled-new-array/range {v1 .. v179}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v179}, [Ljava/lang/String;' \
    '    const-string v180, "io.mesalabs.unica.settings.extra.NowNudgeReplyFallbacksFragment"\n\n    filled-new-array/range {v1 .. v180}, [Ljava/lang/String;' \
    > /dev/null

# Mark Privacy Display custom app fragment as "valid"
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v180}, [Ljava/lang/String;' \
    '    const-string v181, "com.samsung.android.settings.bpd.PdCustomAppsSettings"\n\n    filled-new-array/range {v1 .. v181}, [Ljava/lang/String;' \
    > /dev/null
LOG "- Patching \"smali/com/android/settings/SettingsActivity.smali\" in /system/system/priv-app/SecSettings.apk"
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/SettingsActivity.smali" "replace" \
    'isValidFragment(Ljava/lang/String;)Z' \
    'const/16 v2, 0xab' \
    'const/16 v2, 0xb5' \
    > /dev/null

# Add UN1CA Settings SearchIndexDataProvider(s)
LOG "- Patching Settings search index providers in /system/system/priv-app/SecSettings.apk"
# shellcheck disable=SC2016
SEARCH_INDEX_RESOURCES="$(
    find "$APKTOOL_DIR/system/priv-app/SecSettings/SecSettings.apk" \
        -path '*/com/android/settings/search/SearchFeatureProviderImpl$$ExternalSyntheticLambda0.smali' \
        -print -quit
)"
if [ ! "$SEARCH_INDEX_RESOURCES" ]; then
    LOGE "Settings search provider registry not found in /system/system/priv-app/SecSettings.apk"
    return 1
fi
SEARCH_INDEX_RESOURCES_SMALI="${SEARCH_INDEX_RESOURCES#"$APKTOOL_DIR"/system/priv-app/SecSettings/SecSettings.apk/}"

ADD_UNICA_SETTINGS_SEARCH_INDEX_DATA_PROVIDER()
{
    local FRAGMENT="$1"

    if grep -q "L$FRAGMENT;->SEARCH_INDEX_DATA_PROVIDER" "$SEARCH_INDEX_RESOURCES"; then
        return 0
    fi

    SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
        "$SEARCH_INDEX_RESOURCES_SMALI" "replace" \
        'invoke()Ljava/lang/Object;' \
        'new-instance v0, Lcom/android/settingslib/spa/search/SearchIndexableDataConverter;' \
        "    new-instance v0, Lcom/android/settingslib/search/SearchIndexableData;\n\n    const-class v1, L$FRAGMENT;\n\n    sget-object v2, L$FRAGMENT;->SEARCH_INDEX_DATA_PROVIDER:Lcom/android/settings/search/BaseSearchIndexProvider;\n\n    invoke-direct {v0, v1, v2}, Lcom/android/settingslib/search/SearchIndexableData;-><init>(Ljava/lang/Class;Lcom/android/settingslib/search/Indexable\$SearchIndexProvider;)V\n\n    invoke-virtual {p0, v0}, Lcom/android/settingslib/search/SearchIndexableResourcesBase;->addIndex(Lcom/android/settingslib/search/SearchIndexableData;)V\n\n    new-instance v0, Lcom/android/settingslib/spa/search/SearchIndexableDataConverter;" \
        > /dev/null
}

for f in \
    "io/mesalabs/unica/settings/UnicaSettingsFragment" \
    "io/mesalabs/unica/settings/extra/ExtraSettingsFragment" \
    "io/mesalabs/unica/settings/hma/HideMyApplistFragment" \
    "io/mesalabs/unica/settings/spoof/HideDeveloperStatusFragment" \
    "io/mesalabs/unica/settings/spoof/SpoofSettingsFragment" \
    "io/mesalabs/unica/settings/ui/UISettingsFragment" \
    "io/mesalabs/unica/settings/spoof/CameraFeatureFragment" \
    "io/mesalabs/unica/settings/extra/ScpmAllowlistFragment" \
    "io/mesalabs/unica/settings/extra/NowNudgeReplyFallbacksFragment"; do
    ADD_UNICA_SETTINGS_SEARCH_INDEX_DATA_PROVIDER "$f" || return 1
done

DECODE_APK "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk"
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

# Show Vulkan renderer toggle if required
if [[ "$(GET_PROP "ro.hwui.use_vulkan")" != "true" ]]; then
    SET_PROP "system" "persist.sys.unica.vulkan" "false"
fi

unset -f ADD_UNICA_SETTINGS_SEARCH_INDEX_DATA_PROVIDER
unset PATCH_INST CONTENT SEARCH_INDEX_RESOURCES SEARCH_INDEX_RESOURCES_SMALI TOP_LEVEL_KEYS_COLLECTOR TOP_LEVEL_KEYS_COLLECTOR_SMALI

LOG_STEP_OUT