# Nuke WSM
DELETE_FROM_WORK_DIR "system" "system/etc/public.libraries-wsm.samsung.txt"
DELETE_FROM_WORK_DIR "system" "system/lib/libhal.wsm.samsung.so"
DELETE_FROM_WORK_DIR "system" "system/lib/vendor.samsung.hardware.security.wsm.service-V1-ndk.so"
DELETE_FROM_WORK_DIR "system" "system/lib64/libhal.wsm.samsung.so"
DELETE_FROM_WORK_DIR "system" "system/lib64/vendor.samsung.hardware.security.wsm.service-V1-ndk.so"

# Add KnoxPatchHooks
APPLY_PATCH "system" "system/framework/framework.jar" \
    "$MODPATH/framework.jar/0001-Introduce-KnoxPatchHooks.patch"
SMALI_PATCH "system" "system/framework/framework.jar" \
    "smali/android/app/Instrumentation.smali" "replace" \
    'newApplication(Ljava/lang/Class;Landroid/content/Context;)Landroid/app/Application;' \
    'return-object p0' \
    '    invoke-static {p1}, Lio/mesalabs/unica/KnoxPatchHooks;->init(Landroid/content/Context;)V\n\n    return-object p0' \
    > /dev/null
SMALI_PATCH "system" "system/framework/framework.jar" \
    "smali/android/app/Instrumentation.smali" "replace" \
    'newApplication(Ljava/lang/ClassLoader;Ljava/lang/String;Landroid/content/Context;)Landroid/app/Application;' \
    'return-object p0' \
    '    invoke-static {p3}, Lio/mesalabs/unica/KnoxPatchHooks;->init(Landroid/content/Context;)V\n\n    return-object p0' \
    > /dev/null
APPLY_PATCH "system" "system/framework/knoxsdk.jar" \
    "$MODPATH/knoxsdk.jar/0001-Introduce-KnoxPatchHooks.patch"

# Bypass ICD verification
SMALI_PATCH "system" "system/framework/samsungkeystoreutils.jar" \
    "smali/com/samsung/android/security/keystore/AttestParameterSpec.smali" "return" \
    'isVerifiableIntegrity()Z' 'true'
APPLY_PATCH "system" "system/framework/services.jar" \
    "$MODPATH/services.jar/0001-Bypass-ICD-verification.patch"

# Disable SAK in DarManagerService
SMALI_PATCH "system" "system/framework/services.jar" \
    "smali/com/android/server/knox/dar/DarManagerService.smali" "return" \
    'checkDeviceIntegrity([Ljava/security/cert/Certificate;)Z' 'true'

# Disable DRK in DarManagerService
SMALI_PATCH "system" "system/framework/services.jar" \
    "smali/com/android/server/knox/dar/DarManagerService.smali" "return" \
    'isDeviceRootKeyInstalled()Z' 'true'

# Disable root checks in StorageManagerService
SMALI_PATCH "system" "system/framework/services.jar" \
    "smali/com/android/server/StorageManagerService.smali" "return" \
    'isRootedDevice()Z' 'false'

# Spoof ROT/IntegrityStatus in Knox Matrix
if [ -f "$WORK_DIR/system/system/priv-app/KmxService/KmxService.apk" ]; then
    LOG "- Downloading latest Knox Matrix app"
    DOWNLOAD_FILE "$(GET_GALAXY_STORE_DOWNLOAD_URL "com.samsung.android.kmxservice")" \
        "$WORK_DIR/system/system/priv-app/KmxService/KmxService.apk"
    DECODE_APK "system" "system/priv-app/KmxService/KmxService.apk"

    KMX_APKTOOL_DIR="$APKTOOL_DIR/system/priv-app/KmxService/KmxService.apk"
    KMX_RECEIVER="$(find "$KMX_APKTOOL_DIR" -type f \
        -path "*/com/samsung/android/kmxservice/common/receiver/KmxServiceReceiver.smali" \
        -print -quit)"
    if [ -n "$KMX_RECEIVER" ] && \
            grep -q -F 'FabricEscrowVault;->evIsExistKey()Z' "$KMX_RECEIVER"; then
        APPLY_PATCH "system" "system/priv-app/KmxService/KmxService.apk" \
            "$MODPATH/KmxService.apk/0002-Ignore-FabricEscrowVault-errors-in-KmxServiceReceiver.patch"
    else
        LOG "- Skipping obsolete FabricEscrowVault receiver patch"
    fi

    readarray -t KMX_ROOT_OF_TRUST_FILES < <(find "$KMX_APKTOOL_DIR" -type f \
        -path "*/com/samsung/android/kmxservice/*/RootOfTrust.smali" | sort)
    readarray -t KMX_INTEGRITY_STATUS_FILES < <(find "$KMX_APKTOOL_DIR" -type f \
        -path "*/com/samsung/android/kmxservice/*/IntegrityStatus.smali" | sort)
    if [ "${#KMX_ROOT_OF_TRUST_FILES[@]}" -eq 0 ] || \
            [ "${#KMX_INTEGRITY_STATUS_FILES[@]}" -eq 0 ]; then
        LOGE "Knox Matrix integrity classes not found"
        return 1
    fi

    for KMX_FILE in "${KMX_ROOT_OF_TRUST_FILES[@]}"; do
        KMX_FILE="${KMX_FILE#"$KMX_APKTOOL_DIR/"}"
        SMALI_PATCH "system" "system/priv-app/KmxService/KmxService.apk" \
            "$KMX_FILE" "return" 'getVerifiedBootState()I' '0'
        SMALI_PATCH "system" "system/priv-app/KmxService/KmxService.apk" \
            "$KMX_FILE" "return" 'isDeviceLocked()Z' 'true'
    done
    for KMX_FILE in "${KMX_INTEGRITY_STATUS_FILES[@]}"; do
        KMX_FILE="${KMX_FILE#"$KMX_APKTOOL_DIR/"}"
        if grep -q -F 'getStatus()I' "$KMX_APKTOOL_DIR/$KMX_FILE"; then
            SMALI_PATCH "system" "system/priv-app/KmxService/KmxService.apk" \
                "$KMX_FILE" "return" 'getStatus()I' '0'
        fi
        SMALI_PATCH "system" "system/priv-app/KmxService/KmxService.apk" \
            "$KMX_FILE" "return" 'isNormal()Z' 'true'
    done
fi
