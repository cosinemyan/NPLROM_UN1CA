# Copyright (c) 2026 UN1CA contributors
# SPDX-License-Identifier: GPL-3.0-or-later
SKIPUNZIP=1

# Runs after paradigm/PortROM. No root-manager mounts or audio-service restarts.
if [[ "$TARGET_CODENAME" == "dm3q" && "$TARGET_PLATFORM_SDK_VERSION" == "37" ]]; then
    VIPER_STAGE=$(mktemp -d /tmp/unica-viper-stage.XXXXXX)
    python3 "$MODPATH/prepare.py" "$WORK_DIR" "$VIPER_STAGE" \
        || { ABORT "ViPER audio preparation failed"; return 1; }
    while IFS=$'\t' read -r VIPER_FILE VIPER_LABEL; do
        VIPER_PARTITION=${VIPER_FILE%%/*}
        VIPER_PATH=${VIPER_FILE#*/}
        VIPER_PARENT=$(dirname "$VIPER_PATH")
        while [[ "$VIPER_PARENT" != "." && ! -d "$WORK_DIR/$VIPER_PARTITION/$VIPER_PARENT" ]]; do
            SET_METADATA "$VIPER_PARTITION" "$VIPER_PARENT" 0 0 755 \
                "u:object_r:$VIPER_LABEL:s0" || return 1
            VIPER_PARENT=$(dirname "$VIPER_PARENT")
        done
        ADD_TO_WORK_DIR "$VIPER_STAGE" "$VIPER_PARTITION" "$VIPER_PATH" \
            0 0 644 "u:object_r:$VIPER_LABEL:s0" || return 1
    done < "$VIPER_STAGE/install.tsv"
    LOG "- ViPER staging retained at $VIPER_STAGE"
    unset VIPER_STAGE VIPER_FILE VIPER_LABEL VIPER_PARTITION VIPER_PATH VIPER_PARENT
fi
