SKIPUNZIP=1

# Property-gated ThemeCenter period stubs. Extra settings writes persist.sys.unica.theme_trial.
THEME_APK="system/priv-app/ThemeCenter/ThemeCenter.apk"
THEME_APK_FILE="$WORK_DIR/system/system/priv-app/ThemeCenter/ThemeCenter.apk"

if [ ! -f "$THEME_APK_FILE" ]; then
    LOGW "ThemeCenter.apk missing — skip trial toggle hook"
    return 0
fi

LOG "- Decoding ThemeCenter.apk for trial-period gate"
DECODE_APK "system" "$THEME_APK" || return 1

DECODED="$APKTOOL_DIR/system/priv-app/ThemeCenter/ThemeCenter.apk"
python3 "$MODPATH/inject_trial_gate.py" "$DECODED" || return 1
unset DECODED THEME_APK THEME_APK_FILE
