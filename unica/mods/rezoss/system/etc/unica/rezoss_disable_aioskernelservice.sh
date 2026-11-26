#!/system/bin/sh
# Keep AIOSKernelService off by default while allowing SamsungAiCore to stay available.

disable_pkg()
{
    local pkg="$1"
    cmd package disable-user --user 0 "$pkg" >/dev/null 2>&1 || \
        pm disable-user --user 0 "$pkg" >/dev/null 2>&1 || \
        true
}

enable_pkg()
{
    local pkg="$1"
    cmd package enable "$pkg" >/dev/null 2>&1 || \
        pm enable "$pkg" >/dev/null 2>&1 || \
        true
}

if [ "$(getprop ro.unica.now_nudge_v73 2>/dev/null)" = "1" ]; then
    enable_pkg "com.samsung.android.aioskernelservice"
    # The V73 model APK now updates the QWEN system stub under one identity.
    # Re-enable it if the previous ROM disabled the legacy engine package.
    enable_pkg "io.mesalabs.rezoss.qwen"
    # Initialize only absent settings; preserve explicit user opt-out on reboot.
    for key in rezoss_aios_v73_backend rezoss_now_nudge_native; do
        if [ "$(settings get global "$key" 2>/dev/null)" = "null" ]; then
            settings put global "$key" 1
        fi
    done
elif [ "$(getprop persist.sys.unica.enable_aioskernelservice 2>/dev/null)" != "1" ]; then
    disable_pkg "com.samsung.android.aioskernelservice"
fi

if [ "$(getprop persist.sys.unica.disable_samsung_aicore 2>/dev/null)" = "1" ] || \
   [ "$(getprop persist.sys.unica.samsung_aicore.disable 2>/dev/null)" = "1" ]; then
    disable_pkg "com.samsung.android.aicore"
else
    enable_pkg "com.samsung.android.aicore"
fi
