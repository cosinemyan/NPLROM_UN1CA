SET_PROP_IF_DIFF "vendor" "ro.security.fips.ux" "Disabled"

DEKNOX_HEX_PATCH()
{
    local FILE="$1"
    local FROM="$2"
    local TO="$3"

    if [ ! -f "$FILE" ]; then
        LOGW "File not found: ${FILE//$WORK_DIR/}"
        return 0
    fi

    HEX_PATCH "$FILE" "$FROM" "$TO"
}

DELETE_FROM_WORK_DIR "system" "system/app/BlockchainBasicKit"
# Support legacy sdFAT kernel drivers (pre-API 35)
# Check unica/patches/legacy/customize.sh for more info.
if [ "$TARGET_PLATFORM_SDK_VERSION" -lt "35" ] && \
        grep -q "SDFAT" "$WORK_DIR/kernel/boot.img" && \
        ! grep -q "bogus directory:" "$WORK_DIR/kernel/boot.img"; then
    if xxd -p -c 0 "$WORK_DIR/system/system/bin/vold" | grep -q "2c74696d655f6f66667365743d2564"; then
        LOG_STEP_IN
        # ",time_offset=%d" -> "NUL"
        HEX_PATCH "$WORK_DIR/system/system/bin/vold" "2c74696d655f6f66667365743d2564" "000000000000000000000000000000"
        LOG_STEP_OUT
    fi
fi
DELETE_FROM_WORK_DIR "system" "system/bin/dualdard"
DELETE_FROM_WORK_DIR "system" "system/bin/sdp_cryptod"
DELETE_FROM_WORK_DIR "system" "system/etc/init/dualdard.rc"
DELETE_FROM_WORK_DIR "system" "system/etc/init/kpp.init.rc"
DELETE_FROM_WORK_DIR "system" "system/etc/init/kss.init.rc"
DELETE_FROM_WORK_DIR "system" "system/etc/init/sdp_cryptod.rc"
DELETE_FROM_WORK_DIR "system" "system/etc/permissions/privapp-permissions-com.samsung.android.hdmapp.xml"
DELETE_FROM_WORK_DIR "system" "system/etc/permissions/privapp-permissions-com.samsung.android.kgclient.xml"
DELETE_FROM_WORK_DIR "system" "system/etc/permissions/privapp-permissions-com.samsung.android.knox.kfbp.xml"
DELETE_FROM_WORK_DIR "system" "system/etc/permissions/privapp-permissions-com.samsung.android.knox.knnr.xml"
DELETE_FROM_WORK_DIR "system" "system/etc/permissions/privapp-permissions-com.samsung.android.knox.mpos.xml"
DELETE_FROM_WORK_DIR "system" "system/etc/permissions/privapp-permissions-com.samsung.android.knox.pushmanager.xml"
DELETE_FROM_WORK_DIR "system" "system/etc/permissions/privapp-permissions-com.samsung.android.knox.sandbox.xml"
DELETE_FROM_WORK_DIR "system" "system/etc/permissions/privapp-permissions-com.samsung.android.knox.zt.framework.xml"
DELETE_FROM_WORK_DIR "system" "system/etc/permissions/signature-permissions-com.samsung.android.kgclient.xml"
DELETE_FROM_WORK_DIR "system" "system/etc/sysconfig/preinstalled-packages-com.samsung.android.coldwalletservice.xml"
DELETE_FROM_WORK_DIR "system" "system/lib/libdualdar.so"
DELETE_FROM_WORK_DIR "system" "system/lib/libepm.so"
DELETE_FROM_WORK_DIR "system" "system/lib/libhermes_cred.so"
DELETE_FROM_WORK_DIR "system" "system/lib/libkeyutils.so"
DELETE_FROM_WORK_DIR "system" "system/lib/libknox_filemanager.so"
DELETE_FROM_WORK_DIR "system" "system/lib/libmdfpp_req.so"
DELETE_FROM_WORK_DIR "system" "system/lib/libpersona.so"
DELETE_FROM_WORK_DIR "system" "system/lib/libsdp_crypto.so"
DELETE_FROM_WORK_DIR "system" "system/lib/libsdp_kekm.so"
DELETE_FROM_WORK_DIR "system" "system/lib/libsdp_sdk.so"
DELETE_FROM_WORK_DIR "system" "system/lib64/libmdfpp_req.so"
DELETE_FROM_WORK_DIR "system" "system/lib64/libsdp_crypto.so"
DELETE_FROM_WORK_DIR "system" "system/lib64/libsdp_kekm.so"
DELETE_FROM_WORK_DIR "system" "system/lib64/libsdp_sdk.so"
DELETE_FROM_WORK_DIR "system" "system/priv-app/HdmApk"
DELETE_FROM_WORK_DIR "system" "system/priv-app/KnoxFrameBufferProvider"
DELETE_FROM_WORK_DIR "system" "system/priv-app/KnoxGuard"
DELETE_FROM_WORK_DIR "system" "system/priv-app/KnoxMposAgent"
DELETE_FROM_WORK_DIR "system" "system/priv-app/KnoxNeuralNetworkRuntime"
DELETE_FROM_WORK_DIR "system" "system/priv-app/KnoxPushManager"
DELETE_FROM_WORK_DIR "system" "system/priv-app/KnoxSandbox"
DELETE_FROM_WORK_DIR "system" "system/priv-app/KnoxZtFramework"

# OneUI 8.5: old a73xqxx donor swaps are unsafe because they replace core
# executables/libs from a different platform build. Keep current 8.5 binaries
# and stub the DDAR/MDF native entry points that the donor blobs removed.
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libepm.so" \
    "3f2303d5fe0f1df8f65701a9f44f02a928004039290840f9f30301aa" \
    "5f2403d5e0031f2ac0035fd61f2003d51f2003d51f2003d51f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libepm.so" \
    "3f2303d5fe4fbfa9842e0094892e0094" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libepm.so" \
    "3f2303d5fe0f1ef8f44f01a948008052" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libepm.so" \
    "3f2303d5ffc301d1fd7b01a9fc6f02a9" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libepm.so" \
    "3f2303d5fd7bbaa9fc6f01a9fa6702a9f85f03a9f65704a9f44f05a9ff0740d1ff0305d100e4006f" \
    "5f2403d5e0031f2ac0035fd61f2003d51f2003d51f2003d51f2003d51f2003d51f2003d51f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libepm.so" \
    "3f2303d5ff8301d1fe2300f9f44f05a9" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libepm.so" \
    "3f2303d5fe0f1df8f65701a9f44f02a928004039290840f9f40302aa" \
    "5f2403d5e0031f2ac0035fd61f2003d51f2003d51f2003d51f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libepm.so" \
    "3f2303d5fd7bbaa9fc6f01a9fa6702a9f85f03a9f65704a9f44f05a9ff0740d1ff8304d100e4006f" \
    "5f2403d5e0031f2ac0035fd61f2003d51f2003d51f2003d51f2003d51f2003d51f2003d51f2003d5"

# Some Knox-era shared objects are kept only as loader shims because
# libandroid_servers.so has direct/transitive DT_NEEDED entries:
# - hidl_comm_ddar_client.so
# - vendor.samsung.hardware.tlc.ddar@1.0.so
# - android.hardware.weaver@1.0.so through libhermes_cred.so
# lib64/libdualdar.so is also kept for libepm's 8.5 BIND_NOW dependency chain.
# The libepm entry points above prevent DDAR use.
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "5f2403d5c00100b43f2303d5fd7bbfa9" \
    "5f2403d520008012c0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5ffc300d1fd7b02a9fd83009100e4006fe0ffffb000781691" \
    "5f2403d520008012c0035fd61f2003d51f2003d51f2003d51f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5fd7bbea9f30b00f9fd030091" \
    "5f2403d520008012c0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5ffc300d1fd7b02a9fd83009100e4006fe0ffffb000b40b91" \
    "5f2403d5e0031f2ac0035fd61f2003d51f2003d51f2003d51f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5fd7bbfa9fd0300917e040094" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5fd7bbfa9fd03009175040094" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5fd7bbfa9fd0300916c040094" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5fd7bbfa9fd03009163040094" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5fd7bbfa9fd0300915a040094" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5fd7bbfa9fd030091e5000094" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5ff0302d1fd7b06a9f33b00f9" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5fd7bbfa9fd03009142000094" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5fd7bbfa9fd03009139000094" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5fd7bbfa9fd03009130000094" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5fd7bbfa9fd03009127000094" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5fd7bbfa9fd03009122000094" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib64/libmdf.so" \
    "3f2303d5fd7bbfa9fd03009117000094" \
    "5f2403d5e0031f2ac0035fd61f2003d5"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b558b101460748" "6ff00100704700bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b58ab02648c0ef" "6ff00100704700bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "10b5002002f09ce9" "6ff00100704700bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b58ab01348c0ef" "0020704700bf00bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b502f0b0e90021" "0020704700bf00bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b502f0a6e90238" "0020704700bf00bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b502f09ee90021" "0020704700bf00bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b502f096e90438" "0020704700bf00bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b502f08ee90138" "0020704700bf00bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b501f0b8eb0128" "0020704700bf00bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "10b598b004461648" "0020704700bf00bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b501f0a6eac0b2" "0020704700bf00bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b501f088eac0b2" "0020704700bf00bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b501f08ceac0b2" "0020704700bf00bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b501f08eeac0b2" "0020704700bf00bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b501f06aeac0b2" "0020704700bf00bf"
DEKNOX_HEX_PATCH "$WORK_DIR/system/system/lib/libmdf.so" \
    "80b501f06ceac0b2" "0020704700bf00bf"

if [[ "$TARGET_OS_SINGLE_SYSTEM_IMAGE" == "mssi" ]] || [[ "$TARGET_OS_SINGLE_SYSTEM_IMAGE" == "qssi" ]]; then
    DECODE_APK "system" "system/framework/framework.jar"
    if grep -R -F -q 'unlockCeStorage(ILjava/lang/String;[B)V' \
            "$APKTOOL_DIR/system/framework/framework.jar"/smali_classes*/android/os/IVold.smali 2> /dev/null; then
        LOG "- Skipping unlockCeStorage token patch; framework.jar already uses token argument"
    else
        APPLY_PATCH "system" "system/framework/framework.jar" \
            "$MODPATH/vold/framework.jar/0001-Add-token-argument-in-unlockCeStorage.patch"
        APPLY_PATCH "system" "system/framework/services.jar" \
            "$MODPATH/vold/services.jar/0001-Add-token-argument-in-unlockCeStorage.patch"
    fi
fi

DECODE_APK "system" "system/framework/services.jar"
SOURCE_FILE_ATTR="$(grep -F ".source" "$APKTOOL_DIR/system/framework/services.jar/smali/android/gsi/GsiProgress.smali")"
SOURCE_FILE_ATTR="${SOURCE_FILE_ATTR//\./\\\.}"
SOURCE_FILE_ATTR="${SOURCE_FILE_ATTR//\"/\\\"}"
SOURCE_FILE_ATTR="${SOURCE_FILE_ATTR//\//\\\/}"
LOG "- Replacing SourceFile attribute in /system/system/framework/services.jar"
find "$APKTOOL_DIR/system/framework/services.jar" -type f -name "*.smali" -print0 \
    | xargs -0 -I "{}" -P "$(nproc)" sed -i "s/^\.source.*/\.source \"SourceFile\"/g" "{}"
if [[ "$SOURCE_PRODUCT_SHIPPING_API_LEVEL" != "$TARGET_PRODUCT_SHIPPING_API_LEVEL" ]]; then
    SMALI_PATCH "system" "system/framework/services.jar" \
        "smali/com/android/server/knox/dar/ddar/ta/TAProxy.smali" "replace" \
        "updateServiceHolder(Z)V" \
        "$TARGET_PRODUCT_SHIPPING_API_LEVEL" \
        "$SOURCE_PRODUCT_SHIPPING_API_LEVEL" \
        > /dev/null
fi

# SEC_PRODUCT_FEATURE_KNOX_SUPPORT_SDP
APPLY_PATCH "system" "system/framework/framework.jar" \
    "$MODPATH/sdp/framework.jar/0001-Nuke-Knox-SDP.patch"
APPLY_PATCH "system" "system/framework/services.jar" \
    "$MODPATH/sdp/services.jar/0001-Nuke-Knox-SDP.patch"

# SEC_PRODUCT_FEATURE_KNOX_SUPPORT_DUAL_DAR
APPLY_PATCH "system" "system/app/Traceur/Traceur.apk" \
    "$MODPATH/ddar/Traceur.apk/0001-Nuke-Knox-DualDAR.patch"
APPLY_PATCH "system" "system/framework/framework.jar" \
    "$MODPATH/ddar/framework.jar/0001-Nuke-Knox-DualDAR.patch"
APPLY_PATCH "system" "system/framework/framework.jar" \
    "$MODPATH/ddar/framework.jar/0002-Nuke-MDF.patch"
APPLY_PATCH "system" "system/framework/knoxsdk.jar" \
    "$MODPATH/ddar/knoxsdk.jar/0001-Nuke-Knox-DualDAR.patch"
APPLY_PATCH "system" "system/framework/services.jar" \
    "$MODPATH/ddar/services.jar/0001-Nuke-Knox-DualDAR.patch"
APPLY_PATCH "system" "system/priv-app/DeviceDiagnostics/DeviceDiagnostics.apk" \
    "$MODPATH/ddar/DeviceDiagnostics.apk/0001-Nuke-Knox-DualDAR.patch"
APPLY_PATCH "system" "system/priv-app/KnoxCore/KnoxCore.apk" \
    "$MODPATH/ddar/KnoxCore.apk/0001-Nuke-Knox-DualDAR.patch"
APPLY_PATCH "system" "system/priv-app/ManagedProvisioning/ManagedProvisioning.apk" \
    "$MODPATH/ddar/ManagedProvisioning.apk/0001-Nuke-Knox-DualDAR.patch"
APPLY_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "$MODPATH/ddar/SecSettings.apk/0001-Nuke-Knox-DualDAR.patch"
APPLY_PATCH "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" \
    "$MODPATH/ddar/SecSettingsIntelligence.apk/0001-Nuke-Knox-DualDAR.patch"
APPLY_PATCH "system_ext" "priv-app/StorageManager/StorageManager.apk" \
    "$MODPATH/ddar/StorageManager.apk/0001-Nuke-Knox-DualDAR.patch"

# SEC_PRODUCT_FEATURE_KNOX_SUPPORT_HDM
DECODE_APK "system" "system/framework/knoxsdk.jar"

HDM_VERSION="$(grep "const.* - .*\\w\"" "$APKTOOL_DIR/system/framework/knoxsdk.jar/smali/com/samsung/android/knox/hdm/HdmManager.smali" | tr -d "\"" | awk '{print $3}' -)"
HDM_POLICY_TYPE="$(grep "const.* - .*\\w\"" "$APKTOOL_DIR/system/framework/knoxsdk.jar/smali/com/samsung/android/knox/hdm/HdmManager.smali" | tr -d "\"" | awk '{print $5}' -)"

SMALI_PATCH "system" "system/app/Traceur/Traceur.apk" \
    "smali/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_VERSION" \
    "HDM_VERSION" \
    > /dev/null
SMALI_PATCH "system" "system/app/Traceur/Traceur.apk" \
    "smali/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_POLICY_TYPE" \
    "HDM_POLICY_TYPE" \
    > /dev/null
APPLY_PATCH "system" "system/app/Traceur/Traceur.apk" \
    "$MODPATH/hdm/Traceur.apk/0001-Nuke-Knox-HDM.patch"
SMALI_PATCH "system" "system/framework/knoxsdk.jar" \
    "smali/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_VERSION" \
    "HDM_VERSION" \
    > /dev/null
SMALI_PATCH "system" "system/framework/knoxsdk.jar" \
    "smali/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_POLICY_TYPE" \
    "HDM_POLICY_TYPE" \
    > /dev/null
APPLY_PATCH "system" "system/framework/knoxsdk.jar" \
    "$MODPATH/hdm/knoxsdk.jar/0001-Nuke-Knox-HDM.patch"
if [[ "$SOURCE_PRODUCT_SHIPPING_API_LEVEL" != "$TARGET_PRODUCT_SHIPPING_API_LEVEL" ]]; then
    SMALI_PATCH "system" "system/framework/services.jar" \
        "smali/com/android/server/enterprise/hdm/HdmSakManager.smali" "replace" \
        "isSupported(Landroid/content/Context;)Z" \
        "$TARGET_PRODUCT_SHIPPING_API_LEVEL" \
        "$SOURCE_PRODUCT_SHIPPING_API_LEVEL" \
        > /dev/null
    SMALI_PATCH "system" "system/framework/services.jar" \
        "smali/com/android/server/enterprise/hdm/HdmVendorController.smali" "replace" \
        "<init>()V" \
        "$TARGET_PRODUCT_SHIPPING_API_LEVEL" \
        "$SOURCE_PRODUCT_SHIPPING_API_LEVEL" \
        > /dev/null
fi
# Nuke HDM service and vendor controller
APPLY_PATCH "system" "system/framework/services.jar" \
    "$MODPATH/hdm/services.jar/0001-Nuke-Knox-HDM.patch"
SMALI_PATCH "system" "system/priv-app/DeviceDiagnostics/DeviceDiagnostics.apk" \
    "smali/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_VERSION" \
    "HDM_VERSION" \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/DeviceDiagnostics/DeviceDiagnostics.apk" \
    "smali/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_POLICY_TYPE" \
    "HDM_POLICY_TYPE" \
    > /dev/null
APPLY_PATCH "system" "system/priv-app/DeviceDiagnostics/DeviceDiagnostics.apk" \
    "$MODPATH/hdm/DeviceDiagnostics.apk/0001-Nuke-Knox-HDM.patch"
SMALI_PATCH "system" "system/priv-app/ManagedProvisioning/ManagedProvisioning.apk" \
    "smali/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_VERSION" \
    "HDM_VERSION" \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/ManagedProvisioning/ManagedProvisioning.apk" \
    "smali/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_POLICY_TYPE" \
    "HDM_POLICY_TYPE" \
    > /dev/null
APPLY_PATCH "system" "system/priv-app/ManagedProvisioning/ManagedProvisioning.apk" \
    "$MODPATH/hdm/ManagedProvisioning.apk/0001-Nuke-Knox-HDM.patch"
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali_classes4/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_VERSION" \
    "HDM_VERSION" \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali_classes4/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_POLICY_TYPE" \
    "HDM_POLICY_TYPE" \
    > /dev/null
APPLY_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "$MODPATH/hdm/SecSettings.apk/0001-Nuke-Knox-HDM.patch"
SMALI_PATCH "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" \
    "smali_classes2/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_VERSION" \
    "HDM_VERSION" \
    > /dev/null
SMALI_PATCH "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" \
    "smali_classes2/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_POLICY_TYPE" \
    "HDM_POLICY_TYPE" \
    > /dev/null
APPLY_PATCH "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" \
    "$MODPATH/hdm/SecSettingsIntelligence.apk/0001-Nuke-Knox-HDM.patch"
SMALI_PATCH "system_ext" "priv-app/StorageManager/StorageManager.apk" \
    "smali/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_VERSION" \
    "HDM_VERSION" \
    > /dev/null
SMALI_PATCH "system_ext" "priv-app/StorageManager/StorageManager.apk" \
    "smali/com/samsung/android/knox/hdm/HdmManager.smali" "replaceall" \
    "$HDM_POLICY_TYPE" \
    "HDM_POLICY_TYPE" \
    > /dev/null
APPLY_PATCH "system_ext" "priv-app/StorageManager/StorageManager.apk" \
    "$MODPATH/hdm/StorageManager.apk/0001-Nuke-Knox-HDM.patch"

unset HDM_VERSION HDM_POLICY_TYPE

#SEC_PRODUCT_FEATURE_KNOX_SUPPORT_BLDP
SMALI_PATCH "system" "system/app/Traceur/Traceur.apk" \
    "smali/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isBldpEventSupported()Z' 'false'
SMALI_PATCH "system" "system/framework/knoxsdk.jar" \
    "smali/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isBldpEventSupported()Z' 'false'
SMALI_PATCH "system" "system/priv-app/DeviceDiagnostics/DeviceDiagnostics.apk" \
    "smali/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isBldpEventSupported()Z' 'false'
SMALI_PATCH "system" "system/priv-app/ManagedProvisioning/ManagedProvisioning.apk" \
    "smali/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isBldpEventSupported()Z' 'false'
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali_classes4/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isBldpEventSupported()Z' 'false'
SMALI_PATCH "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" \
    "smali_classes2/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isBldpEventSupported()Z' 'false'
SMALI_PATCH "system_ext" "priv-app/StorageManager/StorageManager.apk" \
    "smali/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isBldpEventSupported()Z' 'false'
SMALI_PATCH "system_ext" "priv-app/SystemUI/SystemUI.apk" \
    "smali_classes4/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isBldpEventSupported()Z' 'false'

# SEC_PRODUCT_FEATURE_KNOX_SUPPORT_MPOS
# TODO add services.jar patch
SMALI_PATCH "system" "system/app/Traceur/Traceur.apk" \
    "smali/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isMposSupported()Z' 'false'
SMALI_PATCH "system" "system/framework/knoxsdk.jar" \
    "smali/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isMposSupported()Z' 'false'
SMALI_PATCH "system" "system/priv-app/DeviceDiagnostics/DeviceDiagnostics.apk" \
    "smali/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isMposSupported()Z' 'false'
SMALI_PATCH "system" "system/priv-app/ManagedProvisioning/ManagedProvisioning.apk" \
    "smali/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isMposSupported()Z' 'false'
SMALI_PATCH "system" "system/priv-app/SecSettings/SecSettings.apk" \
    "smali_classes4/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isMposSupported()Z' 'false'
SMALI_PATCH "system" "system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" \
    "smali_classes2/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isMposSupported()Z' 'false'
SMALI_PATCH "system_ext" "priv-app/StorageManager/StorageManager.apk" \
    "smali/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isMposSupported()Z' 'false'
SMALI_PATCH "system_ext" "priv-app/SystemUI/SystemUI.apk" \
    "smali_classes4/com/samsung/android/knox/integrity/EnhancedAttestationPolicy.smali" "return" \
    'isMposSupported()Z' 'false'

#SEC_PRODUCT_FEATURE_KNOX_SUPPORT_KNOXGUARD
APPLY_PATCH "system" "system/framework/services.jar" \
    "$MODPATH/knoxguard/services.jar/0001-Disable-KnoxGuard.patch"

# SEC_PRODUCT_FEATURE_SECURITY_SUPPORT_KNOX_MATRIX_AI_PRIVACY
APPLY_PATCH "system" "system/framework/framework.jar" \
    "$MODPATH/kmxai/framework.jar/0001-Nuke-Knox-Matrix-AI-Privacy.patch"

#SEC_PRODUCT_FEATURE_FRAMEWORK_SUPPORT_BLOCKCHAIN_SERVICE
SET_FLOATING_FEATURE_CONFIG "SEC_FLOATING_FEATURE_FRAMEWORK_SUPPORT_BLOCKCHAIN_SERVICE" --delete
SMALI_PATCH "system" "system/framework/framework.jar" \
    "smali_classes6/com/samsung/android/ProductPackagesRune.smali" "replaceall" \
    "SERVICE_SAMSUNG_BLOCKCHAIN:Z = true" \
    "SERVICE_SAMSUNG_BLOCKCHAIN:Z = false"
if [[ "$TARGET_SECURITY_CONFIG_ESE_CHIP_VENDOR" == "none" ]] && [[ "$TARGET_SECURITY_CONFIG_ESE_COS_NAME" == "none" ]]; then
    APPLY_PATCH "system" "system/framework/services.jar" \
        "$MODPATH/ese+blockchain/services.jar/0001-Nuke-BlockchainTZService.patch"
else
    APPLY_PATCH "system" "system/framework/services.jar" \
        "$MODPATH/blockchain/services.jar/0001-Nuke-BlockchainTZService.patch"
fi

# TODO get rid of the following features
# SEC_PRODUCT_FEATURE_KNOX_SUPPORT_UCS
# SEC_PRODUCT_FEATURE_FRAMEWORK_SUPPORT_MOBILE_PAYMENT

LOG "- Restoring original SourceFile attribute in /system/system/framework/services.jar"
find "$APKTOOL_DIR/system/framework/services.jar" -type f -name "*.smali" -print0 \
    | xargs -0 -I "{}" -P "$(nproc)" sed -i "s/^\.source.*/$SOURCE_FILE_ATTR/g" "{}"

unset SOURCE_FILE_ATTR
