#!/system/bin/sh

STATE_KEY="unica_now_nudge_experimental"
LOG_FILE="/data/system/unica_now_nudge_defaults.log"
SMARTSUGGESTIONS_PREF_FILE="/data/user/0/com.samsung.android.smartsuggestions/shared_prefs/com.samsung.android.smartsuggestions_preferences.xml"
FALLBACK_DEFAULT_FILE="/system/etc/unica/now_nudge_reply_fallbacks.json"
FALLBACK_DATA_DIR="/data/system/unica"
FALLBACK_DATA_FILE="$FALLBACK_DATA_DIR/now_nudge_reply_fallbacks.json"
FALLBACK_PENDING_CE_FILE="/data/user/0/com.android.settings/files/now_nudge_reply_fallbacks_pending.json"
FALLBACK_PENDING_DE_FILE="/data/user_de/0/com.android.settings/files/now_nudge_reply_fallbacks_pending.json"

log_msg() {
    mkdir -p /data/system
    printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S' 2>/dev/null)" "$*" >> "$LOG_FILE" 2>/dev/null
}

log_msg "start"

set_xml_boolean_pref() {
    FILE="$1"
    KEY="$2"
    VALUE="$3"

    if [ ! -f "$FILE" ]; then
        log_msg "skip pref $KEY: missing $FILE"
        return 0
    fi

    if grep -q "name=\"$KEY\"" "$FILE" 2>/dev/null; then
        sed -i "s#<boolean name=\"$KEY\" value=\"[^\"]*\" */>#<boolean name=\"$KEY\" value=\"$VALUE\" />#g" "$FILE" 2>/dev/null
    elif grep -q '</map>' "$FILE" 2>/dev/null; then
        sed -i "/<\/map>/i\\    <boolean name=\"$KEY\" value=\"$VALUE\" />" "$FILE" 2>/dev/null
    else
        log_msg "skip pref $KEY: invalid xml"
        return 0
    fi

    restorecon "$FILE" 2>/dev/null
    log_msg "set pref $KEY=$VALUE"
}

set_appop() {
    PKG="$1"
    OP="$2"
    MODE="$3"

    if cmd appops set "$PKG" "$OP" "$MODE" >/dev/null 2>&1; then
        log_msg "set appop $PKG $OP=$MODE"
    else
        log_msg "failed appop $PKG $OP=$MODE"
    fi
}

ensure_reply_fallbacks() {
    mkdir -p "$FALLBACK_DATA_DIR" 2>/dev/null
    restorecon "$FALLBACK_DATA_DIR" 2>/dev/null

    if [ -f "$FALLBACK_DEFAULT_FILE" ]; then
        if [ ! -f "$FALLBACK_DATA_FILE" ] || \
                ! grep -q '"category_rules"' "$FALLBACK_DATA_FILE" 2>/dev/null || \
                ! grep -q '"availability"' "$FALLBACK_DATA_FILE" 2>/dev/null || \
                ! grep -q '"greeting"' "$FALLBACK_DATA_FILE" 2>/dev/null; then
            if [ -f "$FALLBACK_DATA_FILE" ]; then
                cp "$FALLBACK_DATA_FILE" "$FALLBACK_DATA_FILE.bak.$(date '+%Y%m%d%H%M%S' 2>/dev/null)" 2>/dev/null
            fi
            if cp "$FALLBACK_DEFAULT_FILE" "$FALLBACK_DATA_FILE" 2>/dev/null; then
                log_msg "refreshed reply fallback JSON"
            fi
        fi
    fi

    for FALLBACK_PENDING_FILE in "$FALLBACK_PENDING_DE_FILE" "$FALLBACK_PENDING_CE_FILE"; do
        if [ -f "$FALLBACK_PENDING_FILE" ]; then
            if cp "$FALLBACK_PENDING_FILE" "$FALLBACK_DATA_FILE" 2>/dev/null; then
                rm -f "$FALLBACK_PENDING_FILE" 2>/dev/null
                log_msg "applied Settings reply fallback JSON from $FALLBACK_PENDING_FILE"
            else
                log_msg "failed to apply Settings reply fallback JSON from $FALLBACK_PENDING_FILE"
            fi
        fi
    done

    if [ -f "$FALLBACK_DATA_FILE" ]; then
        chown system:system "$FALLBACK_DATA_FILE" 2>/dev/null
        chmod 0666 "$FALLBACK_DATA_FILE" 2>/dev/null
        restorecon "$FALLBACK_DATA_FILE" 2>/dev/null
    fi
}

STATE="$(settings get system "$STATE_KEY" 2>/dev/null)"
case "$STATE" in
    0|false|FALSE)
        log_msg "skip: $STATE_KEY explicitly disabled"
        exit 0
        ;;
esac

settings put system "$STATE_KEY" 1 >/dev/null 2>&1
settings put global sss_enabled 1 >/dev/null 2>&1
settings put global now_nudge_setting 1 >/dev/null 2>&1
settings put global now_nudge_enabled 1 >/dev/null 2>&1
settings put global app_widget_brief_type 1 >/dev/null 2>&1
settings put global brief_now_bar_need_to_unlock 0 >/dev/null 2>&1
settings put global show_brief_without_unlock 1 >/dev/null 2>&1
settings put system ai_info_confirmed 1 >/dev/null 2>&1
settings put system suggested_replies_state 1 >/dev/null 2>&1
settings put system key_now_bar_com_samsung_android_smartsuggestions 1 >/dev/null 2>&1
settings put system now_bar_aod_enabled 1 >/dev/null 2>&1
settings put secure content_capture_enabled 1 >/dev/null 2>&1

set_appop "com.samsung.android.smartsuggestions" "GET_USAGE_STATS" "allow"

ensure_reply_fallbacks

set_xml_boolean_pref "$SMARTSUGGESTIONS_PREF_FILE" "smart_commute" "true"
set_xml_boolean_pref "$SMARTSUGGESTIONS_PREF_FILE" "key_manage_personal_data_driving_status" "true"
set_xml_boolean_pref "$SMARTSUGGESTIONS_PREF_FILE" "traffic_updates" "true"
set_xml_boolean_pref "$SMARTSUGGESTIONS_PREF_FILE" "google_map" "true"

log_msg "enabled Now Nudge defaults"
