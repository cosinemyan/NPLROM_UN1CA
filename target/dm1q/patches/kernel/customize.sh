LOG_STEP_IN "- Processing SM8550-Common Kernel by @GoRhanHee"

KERNEL_ZIP_URL="https://github.com/GoRhanHee/android_kernel_samsung_sm8550/releases/download/v2026.08.27/GoRhanHee_Kernel-kalama-dm1q-fastboot.zip"
KERNELSU_MANAGER_APK="https://github.com/KernelSU-Next/KernelSU-Next/releases/download/v3.3.0/KernelSU_Next_v3.3.0-spoofed_33214-release.apk"

REPLACE_KERNEL_BINARIES()
{
    echo "Downloading GoRhanHee Kernel..."
    mkdir -p "$WORK_DIR/kernel"

    local KERNEL_ZIP="$TMP_DIR/gorhanhee-kernel.zip"
    rm -f "$WORK_DIR/kernel/boot.img" "$KERNEL_ZIP"
    mkdir -p "$TMP_DIR"
    if ! DOWNLOAD_FILE "$KERNEL_ZIP_URL" "$KERNEL_ZIP"; then
        rm -f "$KERNEL_ZIP"
        ABORT "Failed to download the kernel archive from $KERNEL_ZIP_URL"
    fi
    if ! unzip -j -o "$KERNEL_ZIP" "boot.img" -d "$WORK_DIR/kernel" > /dev/null; then
        rm -f "$KERNEL_ZIP"
        ABORT "Failed to extract boot.img from $KERNEL_ZIP_URL"
    fi
    rm -f "$KERNEL_ZIP"
    if [ ! -f "$WORK_DIR/kernel/boot.img" ] || \
            [[ "$(xxd -p -l 8 "$WORK_DIR/kernel/boot.img")" != "414e44524f494421" ]]; then
        rm -f "$WORK_DIR/kernel/boot.img"
        ABORT "Extracted kernel boot image is invalid: $KERNEL_ZIP_URL"
    fi
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

unset KERNEL_ZIP_URL KERNELSU_MANAGER_APK