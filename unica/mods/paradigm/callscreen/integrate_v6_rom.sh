#!/usr/bin/env bash
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
# Sourced by Paradigm. Compile exact-firmware dm3q v6 payload into ROM images.
# Never installs a root module, changes a phone, or relaxes SELinux policy.
(
    set -euo pipefail
    [[ "${TARGET_CODENAME:-}" == dm3q ]] || exit 0
    CS_ROOT="$MODPATH/callscreen"
    CS_V6="$CS_ROOT/compat-tx/isolation-v6"
    for CS_DISABLED in "$CS_ROOT/disable" "$CS_ROOT/compat-tx/disable" "$CS_V6/disable"; do
        if [[ -f "$CS_DISABLED" ]]; then
            LOG "- Call Screening v6 ROM integration disabled: $CS_DISABLED"
            exit 0
        fi
    done
    : "${SRC_DIR:?Source buildenv.sh dm3q first}"
    : "${WORK_DIR:?ROM working directory required}"
    # The existing candidate builder remains the single source of payload
    # generation. Stage under /tmp, never inside source firmware or target.
    CS_STAGE=$(mktemp -d /tmp/unica-callscreen-v6-rom.XXXXXX) || exit 1
    LOG "- Build dm3q Call Screening v6 isolated-codec payload: $CS_STAGE"
    python3 "$CS_V6/build_v6.py" --output "$CS_STAGE/build" || exit 1
    CS_PAYLOAD="$CS_STAGE/build/module/candidate"
    # Check ALL destinations before replacing any. Resume/repeated module
    # application accepts exact v6 outputs; unrelated vendor edits fail closed.
    # Explicit failure propagation is required: the caller sources this file
    # inside an OR-list, where Bash suppresses implicit errexit behavior.
    python3 - "$CS_STAGE/build/build-report.json" "$CS_PAYLOAD" "$WORK_DIR" <<'PY' || exit 1
import hashlib
import json
from pathlib import Path
import sys

report_path, payload, work = map(Path, sys.argv[1:])
report = json.loads(report_path.read_text())

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

for rel, expected in report['source_pins'].items():
    target = work / rel
    allowed = {expected}
    candidate = payload / rel
    if candidate.is_file():
        allowed.add(digest(candidate))
    if not target.is_file() or digest(target) not in allowed:
        raise SystemExit('Refusing changed/missing ROM input: '+str(target))

tinyalsa = work / 'system/system/lib64/libtinyalsa.so'
if not tinyalsa.is_file() or digest(tinyalsa) != report['tinyalsa_sha256']:
    raise SystemExit('Refusing changed/missing tinyalsa: '+str(tinyalsa))

companion = work / 'vendor/lib64/libcs-pal.so'
if companion.exists() and digest(companion) != digest(payload/'vendor/lib64/libcs-pal.so'):
    raise SystemExit('Refusing a competing PAL companion: '+str(companion))
print('Verified stock/exact-v6 ROM inputs before integration')
PY
    for CS_FILE in lib64/hw/audio.primary.kalama.so lib64/libcs-pal.so; do
        ADD_TO_WORK_DIR "$CS_PAYLOAD" vendor "$CS_FILE" 0 0 644 "u:object_r:vendor_file:s0" || exit 1
        # ADD_TO_WORK_DIR retains existing metadata, so explicitly replace it.
        SET_METADATA vendor "$CS_FILE" 0 0 644 "u:object_r:vendor_file:s0" || exit 1
    done
    for CS_FILE in etc/audio/sku_kalama/resourcemanager.xml etc/audio/sku_kalama/mixer_paths.xml; do
        ADD_TO_WORK_DIR "$CS_PAYLOAD" vendor "$CS_FILE" 0 0 644 "u:object_r:vendor_configs_file:s0" || exit 1
        SET_METADATA vendor "$CS_FILE" 0 0 644 "u:object_r:vendor_configs_file:s0" || exit 1
    done
    # Preserve the directory-specific contexts that prevented the previous
    # SurfaceFlinger/audioserver enumeration failure. No recursive relabeling.
    SET_METADATA vendor lib64 0 2000 755 "u:object_r:vendor_file:s0" || exit 1
    SET_METADATA vendor lib64/hw 0 2000 755 "u:object_r:vendor_hal_file:s0" || exit 1
    for CS_DIR in etc etc/audio etc/audio/sku_kalama; do
        SET_METADATA vendor "$CS_DIR" 0 2000 755 "u:object_r:vendor_configs_file:s0" || exit 1
    done
    LOG "- Call Screening v6 integrated in vendor image; no Mountify dependency"
)
