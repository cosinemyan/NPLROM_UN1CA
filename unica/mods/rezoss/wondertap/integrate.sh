# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later

# Sourced after kernel selection: validate the actual image being packaged.
_REZOSS_INTEGRATE_WONDERTAP()
{
    local BASE="$MODPATH/wondertap"
    local ENABLE="${REZOSS_ENABLE_WONDERTAP_TEST:-true}"
    [[ "$TARGET_CODENAME" == "dm3q" ]] || return 0
    if [[ -f "$BASE/disable" || "$ENABLE" == "false" ]]; then
        LOG "- Wondertap experiment disabled"
        return 0
    fi
    [[ "$ENABLE" == "true" ]] || { LOGE "REZOSS_ENABLE_WONDERTAP_TEST must be true or false"; return 1; }
    local STAGE
    STAGE="$(mktemp -d "$TMP_DIR/wondertap.XXXXXX")" || return 1
    LOG "- Validating experimental Wondertap kernel and vendor modules"
    python3 "$BASE/prepare.py" --base "$BASE" --work "$WORK_DIR" \
        --stage "$STAGE" --backup "$SRC_DIR/out/archived/wondertap-rom-stock" || return 1
    local FILE
    for FILE in qca_cld3_kiwi_v2.ko s23u_wonder_provider.ko wonder.ko \
        modules.dep modules.load modules.alias modules.softdep; do
        ADD_TO_WORK_DIR "$STAGE" vendor_dlkm "lib/modules/$FILE" \
            0 0 644 "u:object_r:vendor_file:s0" || return 1
    done
    LOGW "Wondertap test modules packaged; firmware operation and Apple transfers remain unverified"
}

_REZOSS_INTEGRATE_WONDERTAP || return 1
unset -f _REZOSS_INTEGRATE_WONDERTAP
