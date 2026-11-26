# Copyright (c) 2026 UN1CA contributors
# SPDX-License-Identifier: GPL-3.0-or-later
SKIPUNZIP=1

# Snapshot after paradigm's audio pack and the earlier S Pen/boot-sound mods.
# Never replace the original media/audio tree at build time.
if [[ "$TARGET_CODENAME" == "dm3q" && "$TARGET_PLATFORM_SDK_VERSION" == "37" ]]; then
    PORTROM_STAGE="$(mktemp -d /tmp/unica-portrom-stage.XXXXXX)"
    python3 "$MODPATH/prepare.py" "$WORK_DIR" "$PORTROM_STAGE" \
        || { ABORT "PortROM audio preparation failed"; return 1; }
    ADD_TO_WORK_DIR "$PORTROM_STAGE" "system" "system/etc/unica/portrom-audio" \
        0 0 755 "u:object_r:system_file:s0" || return 1
    # Directory installs do not register newly created parent directories.
    SET_METADATA "vendor" "etc/unica" 0 0 755 "u:object_r:vendor_file:s0" || return 1
    ADD_TO_WORK_DIR "$PORTROM_STAGE" "vendor" "etc/unica/portrom-audio" \
        0 0 755 "u:object_r:vendor_file:s0" || return 1
    ADD_TO_WORK_DIR "$PORTROM_STAGE" "system" "system/etc/selinux/plat_sepolicy.cil" \
        0 0 644 "u:object_r:system_file:s0" || return 1
    ADD_TO_WORK_DIR "$PORTROM_STAGE" "system" "system/etc/selinux/plat_sepolicy_and_mapping.sha256" \
        0 0 644 "u:object_r:system_file:s0" || return 1
    ADD_TO_WORK_DIR "$PORTROM_STAGE" "system" "system/etc/init/unica_portrom_audio.rc" \
        0 0 644 "u:object_r:system_file:s0" || return 1
    LOG "- PortROM staging retained at $PORTROM_STAGE"
    unset PORTROM_STAGE
fi
