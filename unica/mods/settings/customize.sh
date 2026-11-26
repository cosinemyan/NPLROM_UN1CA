# Show battery regulatory info in Settings
# Requires SEM_BATTERY_PROPERTY_IC_AUTHENTICATION_RESULT support
if [ "$(GET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_BATTERY_SUPPORT_BSOH_SETTINGS")" ]; then
    SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_BATTERY_SUPPORT_BSOH_SETTINGS" --delete
fi
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_SETTINGS_ENABLE_EU_BATTERY_REGULATORY" "TRUE"

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

# Relax SDHMS uses the inspected dm3q persistent process, with no root shell,
# kernel changes or cross-app data writes. Reject an unreviewed SDHMS ABI.
if [[ "$TARGET_CODENAME" == "dm3q" ]]; then
    python3 "$MODPATH/thermal/verify-input.py" \
        "$WORK_DIR/system/system/priv-app/SamsungDeviceHealthManagerService/SamsungDeviceHealthManagerService.apk" \
        || ABORT "Relax SDHMS firmware compatibility check failed"
    APPLY_PATCH "system" "system/priv-app/SamsungDeviceHealthManagerService/SamsungDeviceHealthManagerService.apk" \
        "$MODPATH/thermal/patches/0001-Add-protected-SDHMS-policy-switch.patch" \
        || ABORT "Failed to install Relax SDHMS policy control"
fi

# SoftwareUpdateUtils no longer exposes isOTAUpgradeAllowed(Context) on One UI 9.

# Always show One UI minor version
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali_classes3/com/samsung/android/settings/deviceinfo/softwareinfo/OneUIVersionPreferenceController.smali" "replace" \
    'isDeviceWithMicroVersion()Z' \
    'move-result p0' \
    'const/4 p0, 0x1'

# Show real device model number
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali_classes3/com/samsung/android/settings/deviceinfo/aboutphone/ModelNameGetter.smali" "replace" \
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

# The toggle controller binds the backend row after the UN1CA overlay is copied.
APPLY_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "$MODPATH/now-nudge/0001-Show-AI-backend-only-when-Now-Nudge-is-enabled.patch" \
    || ABORT "Failed to configure Now Nudge backend menu visibility"

# Keep the device-specific Power schedule menu aligned with its Rezoss backend.
python3 "$SRC_DIR/unica/mods/rezoss/scheduled-power-on/install-settings-menu.py" \
    "$APKTOOL_DIR/system/priv-app/SecSettings/SecSettings.apk/res/xml" \
    "$TARGET_CODENAME" || ABORT "Failed to configure Power schedule menu"

# Expose UN1CA-selected SamsungSans source-bank fonts through Samsung's normal
# Display > Font list and apply path.
LOG "- Patching Samsung font list integration in /system/system/priv-app/SecSettings.apk"
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali_classes3/com/samsung/android/settings/flipfont/FontListAdapter.smali" "replace" \
    'getInstanceFontListAdapter(Landroid/content/Context;)Lcom/samsung/android/settings/flipfont/FontListAdapter;' \
    'invoke-virtual/range {v3 .. v8}, Lcom/samsung/android/fontutil/TypefaceFinder;->getSansEntries(Landroid/content/Context;Landroid/content/pm/PackageManager;Ljava/util/ArrayList;Ljava/util/ArrayList;Ljava/util/ArrayList;)V' \
    '    invoke-virtual/range {v3 .. v8}, Lcom/samsung/android/fontutil/TypefaceFinder;->getSansEntries(Landroid/content/Context;Landroid/content/pm/PackageManager;Ljava/util/ArrayList;Ljava/util/ArrayList;Ljava/util/ArrayList;)V\n\n    invoke-static {v2}, Lio/mesalabs/unica/settings/font/FontListHook;->appendSelectedFonts(Lcom/samsung/android/settings/flipfont/FontListAdapter;)V' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali_classes3/com/samsung/android/settings/flipfont/FontListAdapter.smali" "replace" \
    'getFont(Ljava/lang/String;Ljava/lang/String;)Landroid/graphics/Typeface;' \
    'const-string v0, "fonts/"' \
    '    invoke-static {p0, p1, p2}, Lio/mesalabs/unica/settings/font/FontListHook;->getFont(Lcom/samsung/android/settings/flipfont/FontListAdapter;Ljava/lang/String;Ljava/lang/String;)Landroid/graphics/Typeface;\n\n    move-result-object v0\n\n    if-eqz v0, :cond_unica_font_original_getFont\n\n    return-object v0\n\n    :cond_unica_font_original_getFont\n    const-string v0, "fonts/"' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali_classes3/com/samsung/android/settings/display/SecFontStylePreferenceFragment.smali" "replace" \
    'onItemClick(ILandroid/view/View;)V' \
    'iget-object p2, p0, Lcom/samsung/android/settings/display/SecFontStylePreferenceFragment;->mContext:Landroid/content/Context;' \
    '    invoke-static {p1}, Lio/mesalabs/unica/settings/font/FontListHook;->isUnicaFontPackage(Ljava/lang/String;)Z\n\n    move-result v0\n\n    if-eqz v0, :cond_unica_font_original_apply\n\n    iget-object v0, p0, Lcom/samsung/android/settings/display/SecFontStylePreferenceFragment;->mFontListAdapter:Lcom/samsung/android/settings/flipfont/FontListAdapter;\n\n    iget-object v0, v0, Lcom/samsung/android/settings/flipfont/FontListAdapter;->mTypefaceFiles:Ljava/util/ArrayList;\n\n    iget v2, p0, Lcom/samsung/android/settings/display/SecFontStylePreferenceFragment;->mCurrentFontIndex:I\n\n    invoke-virtual {v0, v2}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;\n\n    move-result-object v0\n\n    check-cast v0, Ljava/lang/String;\n\n    invoke-virtual {v0}, Ljava/lang/String;->toString()Ljava/lang/String;\n\n    move-result-object v0\n\n    iget-object v2, p0, Lcom/samsung/android/settings/display/SecFontStylePreferenceFragment;->mFontListAdapter:Lcom/samsung/android/settings/flipfont/FontListAdapter;\n\n    iget-object v2, v2, Lcom/samsung/android/settings/flipfont/FontListAdapter;->mFontNames:Ljava/util/ArrayList;\n\n    iget v3, p0, Lcom/samsung/android/settings/display/SecFontStylePreferenceFragment;->mCurrentFontIndex:I\n\n    invoke-virtual {v2, v3}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;\n\n    move-result-object v2\n\n    check-cast v2, Ljava/lang/String;\n\n    invoke-virtual {v2}, Ljava/lang/String;->toString()Ljava/lang/String;\n\n    move-result-object v2\n\n    iget-object v3, p0, Lcom/samsung/android/settings/display/SecFontStylePreferenceFragment;->mContext:Landroid/content/Context;\n\n    invoke-static {v3, v0, v2}, Lio/mesalabs/unica/settings/font/FontListHook;->applyUnicaFont(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Z\n\n    move-result v2\n\n    if-eqz v2, :cond_unica_font_apply_failed\n\n    goto/16 :goto_6\n\n    :cond_unica_font_apply_failed\n\n    invoke-virtual {p0, p1}, Lcom/samsung/android/settings/display/SecFontStylePreferenceFragment;->showWarningDialog(Ljava/lang/String;)V\n\n    return-void\n\n    :cond_unica_font_original_apply\n    iget-object p2, p0, Lcom/samsung/android/settings/display/SecFontStylePreferenceFragment;->mContext:Landroid/content/Context;' \
    > /dev/null

# Mark UN1CA Settings fragments as "valid"
LOG "- Patching \"smali/com/android/settings/core/gateway/SettingsGateway.smali\" in /system/system/priv-app/SecSettings.apk"
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v186}, [Ljava/lang/String;' \
    '    const-string v187, "io.mesalabs.unica.settings.UnicaSettingsFragment"\n\n    filled-new-array/range {v1 .. v187}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v187}, [Ljava/lang/String;' \
    '    const-string v188, "io.mesalabs.unica.settings.extra.ExtraSettingsFragment"\n\n    filled-new-array/range {v1 .. v188}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v188}, [Ljava/lang/String;' \
    '    const-string v189, "io.mesalabs.unica.settings.hma.HideMyApplistFragment"\n\n    filled-new-array/range {v1 .. v189}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v189}, [Ljava/lang/String;' \
    '    const-string v190, "io.mesalabs.unica.settings.spoof.HideDeveloperStatusFragment"\n\n    filled-new-array/range {v1 .. v190}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v190}, [Ljava/lang/String;' \
    '    const-string v191, "io.mesalabs.unica.settings.spoof.SpoofSettingsFragment"\n\n    filled-new-array/range {v1 .. v191}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v191}, [Ljava/lang/String;' \
    '    const-string v192, "io.mesalabs.unica.settings.ui.UISettingsFragment"\n\n    filled-new-array/range {v1 .. v192}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v192}, [Ljava/lang/String;' \
    '    const-string v193, "io.mesalabs.unica.settings.spoof.CameraFeatureFragment"\n\n    filled-new-array/range {v1 .. v193}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v193}, [Ljava/lang/String;' \
    '    const-string v194, "io.mesalabs.unica.settings.font.FontSelectorFragment"\n\n    filled-new-array/range {v1 .. v194}, [Ljava/lang/String;' \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v194}, [Ljava/lang/String;' \
    '    const-string v195, "io.mesalabs.unica.settings.extra.ScpmAllowlistFragment"\n\n    filled-new-array/range {v1 .. v195}, [Ljava/lang/String;' \
    > /dev/null

# Mark Privacy Display custom app fragment as "valid"
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/core/gateway/SettingsGateway.smali" "replace" \
    '<clinit>()V' \
    'filled-new-array/range {v1 .. v195}, [Ljava/lang/String;' \
    '    const-string v196, "com.samsung.android.settings.bpd.PdCustomAppsSettings"\n\n    filled-new-array/range {v1 .. v196}, [Ljava/lang/String;' \
    > /dev/null
LOG "- Patching \"smali/com/android/settings/SettingsActivity.smali\" in /system/system/priv-app/SecSettings.apk"
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali/com/android/settings/SettingsActivity.smali" "replace" \
    'isValidFragment(Ljava/lang/String;)Z' \
    'const/16 v2, 0xba' \
    'const/16 v2, 0xc4' \
    > /dev/null

# Add UN1CA Settings SearchIndexDataProvider(s)
LOG "- Patching Settings search index providers in /system/system/priv-app/SecSettings.apk"
SEARCH_INDEX_RESOURCES="$(
    find "$APKTOOL_DIR/system/priv-app/SecSettings/SecSettings.apk" \
        -path '*/com/android/settings/search/SearchFeatureProviderImpl$$ExternalSyntheticLambda0.smali' \
        -print -quit
)"
if [ ! "$SEARCH_INDEX_RESOURCES" ]; then
    LOGE "Settings search provider registry not found in /system/system/priv-app/SecSettings.apk"
    return 1
fi
SEARCH_INDEX_RESOURCES_SMALI="${SEARCH_INDEX_RESOURCES#$APKTOOL_DIR/system/priv-app/SecSettings/SecSettings.apk/}"

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
    "io/mesalabs/unica/settings/spoof/HideDeveloperStatusFragment" \
    "io/mesalabs/unica/settings/spoof/SpoofSettingsFragment" \
    "io/mesalabs/unica/settings/ui/UISettingsFragment" \
    "io/mesalabs/unica/settings/spoof/CameraFeatureFragment" \
    "io/mesalabs/unica/settings/font/FontSelectorFragment" \
    "io/mesalabs/unica/settings/extra/ScpmAllowlistFragment"; do
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
TOP_LEVEL_KEYS_COLLECTOR_SMALI="${TOP_LEVEL_KEYS_COLLECTOR#$APKTOOL_DIR/system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk/}"

if ! grep -q '"top_level_unica"' "$TOP_LEVEL_KEYS_COLLECTOR"; then
    SMALI_PATCH "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" \
        "$TOP_LEVEL_KEYS_COLLECTOR_SMALI" "replace" \
        '<init>(Landroid/content/Context;)V' \
        '.locals 37' \
        '.locals 38' \
        > /dev/null
    SMALI_PATCH "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" \
        "$TOP_LEVEL_KEYS_COLLECTOR_SMALI" "replace" \
        '<init>(Landroid/content/Context;)V' \
        'filled-new-array/range {v1 .. v36}, [Ljava/lang/String;' \
        '    const-string v37, "top_level_unica"\n\n    filled-new-array/range {v1 .. v37}, [Ljava/lang/String;' \
        > /dev/null
fi

# Show Vulkan renderer toggle if required
if [[ "$(GET_PROP "ro.hwui.use_vulkan")" != "true" ]]; then
    SET_PROP "system" "persist.sys.unica.vulkan" "false"
fi

unset -f ADD_UNICA_SETTINGS_SEARCH_INDEX_DATA_PROVIDER
unset PATCH_INST CONTENT SEARCH_INDEX_RESOURCES SEARCH_INDEX_RESOURCES_SMALI TOP_LEVEL_KEYS_COLLECTOR TOP_LEVEL_KEYS_COLLECTOR_SMALI

LOG_STEP_OUT

# ROM-native Battery Saving: the native worker only accepts fixed kernel controls.
LOG "- Installing Battery Saving service policy"
python3 "$MODPATH/battery/install-policy.py" "$WORK_DIR" || ABORT "Failed to install Battery Saving service policy"
python3 "$MODPATH/ota/install-policy.py" "$WORK_DIR" || ABORT "Failed to install OTA staging service policy"
if [[ "$TARGET_CODENAME" == "dm3q" ]]; then
    python3 "$MODPATH/movecertificate/install-policy.py" "$WORK_DIR" || ABORT "Failed to install Move Certificate service policy"
else
    DELETE_FROM_WORK_DIR "system" "system/bin/unica-movecert"
    DELETE_FROM_WORK_DIR "system" "system/etc/init/unica_movecert.rc"
fi

# dm3q-only GPU image editor and recovery backup policy. Other targets keep no
# partition access or service; the shared Settings page reports unsupported.
if [[ "$TARGET_CODENAME" == "dm3q" ]]; then
    "$MODPATH/gpu/verify-firmware.sh" "$WORK_DIR/kernel/vendor_boot.img" \
        "$WORK_DIR/vendor_dlkm/lib/modules/msm_kgsl.ko" || ABORT "GPU capability check failed; review the changed table/driver contract before shipping"
    python3 "$MODPATH/gpu/install-policy.py" "$WORK_DIR" || ABORT "Failed to install GPU tuning service policy"
else
    DELETE_FROM_WORK_DIR "system" "system/bin/unica-gpu"
    DELETE_FROM_WORK_DIR "system" "system/etc/init/unica_gpu.rc"
    DELETE_FROM_WORK_DIR "system" "system/etc/unica/gpu"
fi

# User-triggered Troubleshooting worker; no automatic boot-time module changes.
# Compile verified compatibility rules for this source tree; no network access.
python3 "$MODPATH/troubleshooting/compile-catalog.py" \
    --output-dir "$WORK_DIR/system/system/etc/unica" --no-backup || ABORT "Failed to compile compatibility catalog"
for _UNICA_COMPAT_TABLE in troubleshooting-root-purposes.tsv troubleshooting-lsposed-purposes.json troubleshooting-lsposed-conflicts.json troubleshooting-registry-info.json; do
    SET_METADATA "system" "system/etc/unica/$_UNICA_COMPAT_TABLE" 0 0 644 "u:object_r:system_file:s0"
done
unset _UNICA_COMPAT_TABLE
python3 "$MODPATH/troubleshooting/install-policy.py" "$WORK_DIR" || ABORT "Failed to install Troubleshooting policy"
python3 "$MODPATH/troubleshooting/registry.py" "$WORK_DIR/system/system/etc/unica/troubleshooting-targets.tsv" || ABORT "Failed to index ROM conflict targets"
SET_METADATA "system" "system/etc/unica/troubleshooting-targets.tsv" 0 0 644 "u:object_r:system_file:s0"
SET_METADATA "system" "system/bin/unica-troubleshooting" 0 0 755 "u:object_r:unica_troubleshooting_exec:s0"
