SCRIPT_FILE="$TMP_DIR/META-INF/com/google/android/updater-script"

if [ -f "$SCRIPT_FILE" ]; then
    LOG "- Adding package parser cache invalidation"
    {
        echo 'ui_print("Invalidating Android package parser cache...");'
        echo 'run_program("/sbin/sh", "-c", "mount /data 2>/dev/null || true; rm -rf /data/system/package_cache /data/system/package_cache_*");'
    } >> "$SCRIPT_FILE" || return 1
fi

unset SCRIPT_FILE
