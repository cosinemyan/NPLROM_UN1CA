#!/system/bin/sh

LOG_TAG="RezossBootAnim"
STAGE_DIR="/data/misc/bootanim"
STATE_LOG="$STAGE_DIR/rezoss_bootanimation.log"
USER_ZIP="/data/media/0/bootanimation.zip"
STAGED_ZIP="$STAGE_DIR/bootanimation.zip"
STAGED_NEXT="$STAGE_DIR/bootanimation.zip.next"
STAGED_TMP="$STAGED_ZIP.tmp"
SYSTEM_ZIP="/system/media/bootanimation.zip"
AOSP_BOOTANIM="/system/bin/bootanimation_zip"
SYSTEM_BOOTANIM="/system/bin/bootanimation"
MODE="apply"
BIND_CHANGED=0

case "$1" in
    ""|--apply)
        MODE="apply"
        ;;
    --stage-only)
        MODE="stage-only"
        ;;
    --wait-stage)
        MODE="wait-stage"
        ;;
    --stage-now)
        MODE="stage-now"
        ;;
esac

prepare_stage_dir()
{
    mkdir -p "$STAGE_DIR" 2>/dev/null
    chown system:system "$STAGE_DIR" 2>/dev/null
    chmod 0755 "$STAGE_DIR" 2>/dev/null
    restorecon "$STAGE_DIR" 2>/dev/null
}

log_msg()
{
    log -t "$LOG_TAG" "$1"
    [ -d "$STAGE_DIR" ] || return 0

    TS="$(date '+%m-%d %H:%M:%S' 2>/dev/null)"
    [ -n "$TS" ] || TS="unknown"
    echo "$TS [$MODE] $1" >> "$STATE_LOG" 2>/dev/null
}

is_mounted()
{
    grep -q " $1 " /proc/mounts 2>/dev/null
}

bind_file()
{
    SRC="$1"
    DST="$2"

    if is_mounted "$DST"; then
        log_msg "$DST is already bind mounted"
        return 0
    fi

    if mount -o bind "$SRC" "$DST"; then
        BIND_CHANGED=1
        return 0
    fi

    return 1
}

stage_user_zip()
{
    FORCE_ACTIVE="$1"

    prepare_stage_dir

    cp -f "$USER_ZIP" "$STAGED_TMP" || return 1
    if [ ! -s "$STAGED_TMP" ]; then
        rm -f "$STAGED_TMP"
        return 1
    fi

    if [ "$FORCE_ACTIVE" != "1" ] && is_mounted "$SYSTEM_ZIP"; then
        mv -f "$STAGED_TMP" "$STAGED_NEXT" || return 1
        chown system:system "$STAGED_NEXT"
        chmod 0644 "$STAGED_NEXT"
        chcon u:object_r:bootanim_data_file:s0 "$STAGED_NEXT" 2>/dev/null || restorecon "$STAGED_NEXT" 2>/dev/null
        sync "$STAGED_NEXT" 2>/dev/null
        log_msg "$SYSTEM_ZIP is mounted, staged updated bootanimation.zip for next boot"
        return 0
    fi

    mv -f "$STAGED_TMP" "$STAGED_ZIP" || return 1
    chown system:system "$STAGED_ZIP"
    chmod 0644 "$STAGED_ZIP"
    chcon u:object_r:bootanim_data_file:s0 "$STAGED_ZIP" 2>/dev/null || restorecon "$STAGED_ZIP" 2>/dev/null
    sync "$STAGED_ZIP" 2>/dev/null
    return 0
}

stage_from_user_zip()
{
    prepare_stage_dir
    WAIT_STAGE="$1"
    FORCE_ACTIVE="$2"
    log_msg "checking $USER_ZIP"

    if [ "$WAIT_STAGE" = "1" ]; then
        WAITED=0
        while [ ! -r "$USER_ZIP" ] && [ "$WAITED" -lt 180 ]; do
            sleep 2
            WAITED=$((WAITED + 2))
        done
    fi

    if [ -r "$USER_ZIP" ]; then
        if stage_user_zip "$FORCE_ACTIVE"; then
            log_msg "staged custom bootanimation.zip"
        else
            log_msg "failed to stage bootanimation.zip"
        fi
        return 0
    fi

    if [ "$WAIT_STAGE" = "1" ]; then
        log_msg "custom bootanimation.zip not readable after wait, keeping staged copy"
    elif [ "$FORCE_ACTIVE" = "1" ]; then
        log_msg "custom bootanimation.zip not readable, keeping staged copy"
    elif [ "$(getprop sys.user.0.ce_available 2>/dev/null)" != "true" ]; then
        log_msg "user storage not available, keeping staged copy"
    else
        rm -f "$STAGED_ZIP" "$STAGED_NEXT" "$STAGED_TMP"
        log_msg "no custom bootanimation.zip, cleared staged copy"
    fi

    return 0
}

restart_bootanim()
{
    if [ "$(getprop sys.boot_completed 2>/dev/null)" = "1" ]; then
        log_msg "boot completed, not restarting bootanim"
        return 0
    fi

    if [ "$BIND_CHANGED" != "1" ]; then
        log_msg "no new bind mount, not restarting bootanim"
        return 0
    fi

    BOOTANIM_STATE="$(getprop init.svc.bootanim 2>/dev/null)"
    if [ "$BOOTANIM_STATE" != "running" ]; then
        log_msg "bootanim service is $BOOTANIM_STATE, bind will apply when it starts"
        return 0
    fi

    setprop ctl.restart bootanim 2>/dev/null
    log_msg "requested bootanim restart"
}

apply_staged_zip()
{
    ZIP_BOUND=0

    prepare_stage_dir

    if [ -s "$STAGED_NEXT" ] && ! is_mounted "$SYSTEM_ZIP"; then
        mv -f "$STAGED_NEXT" "$STAGED_ZIP" && log_msg "promoted next bootanimation.zip"
    fi

    if [ ! -s "$STAGED_ZIP" ]; then
        log_msg "no staged bootanimation.zip, keeping Samsung QMG bootanimation"
        return 0
    fi

    if [ ! -f "$AOSP_BOOTANIM" ]; then
        log_msg "$AOSP_BOOTANIM is missing"
        return 0
    fi

    if [ ! -e "$SYSTEM_ZIP" ]; then
        log_msg "$SYSTEM_ZIP bind target is missing"
        return 0
    fi

    if [ ! -e "$SYSTEM_BOOTANIM" ]; then
        log_msg "$SYSTEM_BOOTANIM bind target is missing"
        return 0
    fi

    if ! is_mounted "$SYSTEM_ZIP"; then
        if ! mount -o bind "$STAGED_ZIP" "$SYSTEM_ZIP"; then
            log_msg "failed to bind staged zip to $SYSTEM_ZIP"
            return 0
        fi
        ZIP_BOUND=1
        BIND_CHANGED=1
    else
        log_msg "$SYSTEM_ZIP is already bind mounted"
    fi

    if ! bind_file "$AOSP_BOOTANIM" "$SYSTEM_BOOTANIM"; then
        [ "$ZIP_BOUND" = "1" ] && umount "$SYSTEM_ZIP" 2>/dev/null
        log_msg "failed to bind AOSP bootanimation binary"
        return 0
    fi

    log_msg "using staged AOSP bootanimation.zip"
    restart_bootanim
    return 0
}

case "$MODE" in
    stage-only)
        stage_from_user_zip 0
        ;;
    wait-stage)
        stage_from_user_zip 1
        ;;
    stage-now)
        stage_from_user_zip 0 1
        ;;
    *)
        apply_staged_zip
        ;;
esac

exit 0
