LOG_STEP_IN "- Processing local KernelSU boot image"

KERNELSU_MANAGER_APK="https://github.com/KernelSU-Next/KernelSU-Next/releases/download/v3.3.0/KernelSU_Next_v3.3.0-spoofed_33214-release.apk"
LOCAL_BOOT_IMG="${KERNEL_BOOT_IMG:-$MODPATH/boot.img}"

IS_ANDROID_BOOT_IMAGE()
{
    [ -f "$1" ] && [[ "$(xxd -p -l 8 "$1")" == "414e44524f494421" ]]
}

REPLACE_KERNEL_BINARIES()
{
    echo "Installing local boot.img..."
    mkdir -p "$WORK_DIR/kernel"

    if [ ! -f "$LOCAL_BOOT_IMG" ]; then
        ABORT "Kernel boot image not found: $LOCAL_BOOT_IMG"
    fi
    if ! IS_ANDROID_BOOT_IMAGE "$LOCAL_BOOT_IMG"; then
        ABORT "Kernel boot image is invalid: $LOCAL_BOOT_IMG"
    fi

    cp -a "$LOCAL_BOOT_IMG" "$WORK_DIR/kernel/boot.img"
}

ADD_MANAGER_APK_TO_PRELOAD()
{
    # https://github.com/tiann/KernelSU/issues/886
    local APK_PATH="system/preload/KernelSU-Next/com.rifsxd.ksunext-mesa==/base.apk"

    echo "Adding KernelSU-Next.apk to preload apps if available"
    mkdir -p "$WORK_DIR/system/$(dirname "$APK_PATH")"
    rm -f "$WORK_DIR/system/$APK_PATH"
    if ! curl -L --fail --silent --show-error -o "$WORK_DIR/system/$APK_PATH" "$KERNELSU_MANAGER_APK"; then
        rm -f "$WORK_DIR/system/$APK_PATH"
        LOGW "KernelSU-Next manager unavailable; continuing without it"
    elif ! unzip -tq "$WORK_DIR/system/$APK_PATH" > /dev/null; then
        rm -f "$WORK_DIR/system/$APK_PATH"
        LOGW "KernelSU-Next manager is invalid; continuing without it"
    fi

    sed -i "/system\/preload/d" "$WORK_DIR/configs/fs_config-system" \
        && sed -i "/system\/preload/d" "$WORK_DIR/configs/file_context-system"
    while read -r i; do
        FILE="${i/$WORK_DIR\/system\//}"
        [ -d "$i" ] && echo "$FILE 0 0 755 capabilities=0x0" >> "$WORK_DIR/configs/fs_config-system"
        [ -f "$i" ] && echo "$FILE 0 0 644 capabilities=0x0" >> "$WORK_DIR/configs/fs_config-system"
        FILE="$(echo -n "$FILE" | sed 's/\./\\./g')"
        echo "/$FILE u:object_r:system_file:s0" >> "$WORK_DIR/configs/file_context-system"
    done <<< "$(find "$WORK_DIR/system/system/preload")"

    rm -f "$WORK_DIR/system/system/etc/vpl_apks_count_list.txt"
    while read -r i; do
        FILE="${i/$WORK_DIR\/system/}"
        echo "$FILE" >> "$WORK_DIR/system/system/etc/vpl_apks_count_list.txt"
    done <<< "$(find "$WORK_DIR/system/system/preload" -name "*.apk" | sort)"
}

REPLACE_KERNEL_BINARIES
ADD_MANAGER_APK_TO_PRELOAD

unset KERNELSU_MANAGER_APK LOCAL_BOOT_IMG
