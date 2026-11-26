#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Sourced by the parent Rezoss mod. No changes until the app is built successfully.
if [[ -f "$MODPATH/scheduled-power-on/disable" ]]; then
    LOG "- Scheduled power on is disabled"
elif [[ "$TARGET_CODENAME" == "dm3q" ]]; then
    LOG "- Adding experimental Scheduled power on (no schedule enabled by default)"
    REZOSS_POWERON_SOURCE="$FW_DIR/SM-S918B_EUX/vendor_dlkm/lib/modules/sec_pon_alarm.ko"
    [[ -f "$REZOSS_POWERON_SOURCE" ]] || ABORT "Scheduled power on requires the inspected S918B driver"
    # Both drivers were inspected for the same 14-byte Samsung alarm ioctl ABI.
    case "$(sha256sum "$REZOSS_POWERON_SOURCE" | cut -d ' ' -f 1)" in
        9949d2045d979e8f25d0f84d7bffe2364263195e227155d2364e570bf29a31d6|\
        c187518e90a9bddd8d9a87c635970ba5cf35728ad90e47ae72980c2f2360fb10) ;;
        *) ABORT "Scheduled power-on driver changed; revalidate its ioctl ABI before enabling this mod" ;;
    esac
    REZOSS_POWERON_STAGE=$(mktemp -d /tmp/rezoss-poweron.XXXXXX) || return 1
    mkdir -p "$REZOSS_POWERON_STAGE/system/system/app/RezossPowerOn" || return 1
    bash "$MODPATH/scheduled-power-on/build.sh" \
        "$REZOSS_POWERON_STAGE/system/system/app/RezossPowerOn/RezossPowerOn.apk" || return 1
    mkdir -p "$REZOSS_POWERON_STAGE/system/system/app/RezossPowerOn/lib/arm64" || return 1
    unzip -p "$REZOSS_POWERON_STAGE/system/system/app/RezossPowerOn/RezossPowerOn.apk" \
        lib/arm64-v8a/librezoss_poweron.so > \
        "$REZOSS_POWERON_STAGE/system/system/app/RezossPowerOn/lib/arm64/librezoss_poweron.so" || return 1
    # ADD_TO_WORK_DIR reads recursive metadata from the source root.
    cat > "$REZOSS_POWERON_STAGE/fs_config-system" <<'EOF'
system/app/RezossPowerOn 0 0 755 capabilities=0x0
system/app/RezossPowerOn/RezossPowerOn.apk 0 0 644 capabilities=0x0
system/app/RezossPowerOn/lib 0 0 755 capabilities=0x0
system/app/RezossPowerOn/lib/arm64 0 0 755 capabilities=0x0
system/app/RezossPowerOn/lib/arm64/librezoss_poweron.so 0 0 644 capabilities=0x0
EOF
    cat > "$REZOSS_POWERON_STAGE/file_context-system" <<'EOF'
/system/app/RezossPowerOn u:object_r:system_file:s0
/system/app/RezossPowerOn/RezossPowerOn\.apk u:object_r:system_file:s0
/system/app/RezossPowerOn/lib u:object_r:system_file:s0
/system/app/RezossPowerOn/lib/arm64 u:object_r:system_file:s0
/system/app/RezossPowerOn/lib/arm64/librezoss_poweron\.so u:object_r:system_lib_file:s0
EOF
    python3 "$MODPATH/scheduled-power-on/install-policy.py" "$WORK_DIR" || return 1
    ADD_TO_WORK_DIR "$REZOSS_POWERON_STAGE" "system" \
        "system/app/RezossPowerOn" 0 0 755 "u:object_r:system_file:s0" || return 1
    SET_METADATA "system" "system/app/RezossPowerOn/RezossPowerOn.apk" \
        0 0 644 "u:object_r:system_file:s0" || return 1
    for REZOSS_POWERON_DIR in lib lib/arm64; do
        SET_METADATA "system" "system/app/RezossPowerOn/$REZOSS_POWERON_DIR" \
            0 0 755 "u:object_r:system_file:s0" || return 1
    done
    SET_METADATA "system" "system/app/RezossPowerOn/lib/arm64/librezoss_poweron.so" \
        0 0 644 "u:object_r:system_lib_file:s0" || return 1
    ADD_TO_WORK_DIR "$MODPATH/scheduled-power-on" "system" \
        "system/etc/sysconfig/rezoss-poweron.xml" 0 0 644 "u:object_r:system_file:s0" || return 1
    unset REZOSS_POWERON_SOURCE REZOSS_POWERON_STAGE REZOSS_POWERON_DIR
fi
