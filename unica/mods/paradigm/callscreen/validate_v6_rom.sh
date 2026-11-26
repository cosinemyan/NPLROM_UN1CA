#!/usr/bin/env bash
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
# Exercise real ROM copy/metadata helpers only against a fresh /tmp fixture.
set -eo pipefail
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
CS_TEST_ROOT=$(mktemp -d /tmp/callscreen-v6-rom-test.XXXXXX)
export WORK_DIR="$CS_TEST_ROOT/work"
export MODPATH="$SRC_DIR/unica/mods/paradigm"
export TARGET_CODENAME=dm3q
source "$SRC_DIR/scripts/utils/common_utils.sh"
python3 - "$SRC_DIR/out/fw/SM-S918B_EUX" "$WORK_DIR" <<'PY'
from pathlib import Path
import shutil
import sys
fw, work = map(Path, sys.argv[1:])
files = ['vendor/lib64/hw/audio.primary.kalama.so', 'vendor/lib64/libar-pal.so',
         'vendor/etc/audio/sku_kalama/mixer_paths.xml', 'vendor/etc/audio/sku_kalama/resourcemanager.xml',
         'system/system/lib64/libtinyalsa.so']
for rel in files:
    p = work / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(fw / rel, p)
(work/'configs').mkdir()
for name in ('fs_config-vendor', 'file_context-vendor'):
    shutil.copy2(fw / name, work/'configs'/name)
PY
source "$MODPATH/callscreen/integrate_v6_rom.sh" > "$CS_TEST_ROOT/first-build.txt" 2>&1
python3 - "$WORK_DIR" "$CS_TEST_ROOT" <<'PY'
from pathlib import Path
import hashlib
import json
import sys
import xml.etree.ElementTree as ET
work, root = map(Path, sys.argv[1:])
expected = {
    'vendor/lib64/hw/audio.primary.kalama.so': '17275eed60e1d5d5043bd968a80f197684d46e0ea2bd2aaaff871d8630ffbfb3',
    'vendor/lib64/libcs-pal.so': '24a7ac34830da22b1840171a2fcaf2b70a4fd020d4a32847c1b2d70c78614f21',
    'vendor/etc/audio/sku_kalama/resourcemanager.xml': 'b7d3bf613261156f59caec353c8a3738e9a3eb542489a58379cc90613495c7af',
    'vendor/etc/audio/sku_kalama/mixer_paths.xml': '002ae4de2d95d8daefa43f0dd2bd8d8365cc41f0ddba1a656d13d450bfe33093',
}
for rel, wanted in expected.items():
    assert hashlib.sha256((work/rel).read_bytes()).hexdigest() == wanted, rel
contexts = dict(line.split(' ', 1) for line in (work/'configs/file_context-vendor').read_text().splitlines() if ' ' in line)
for rel, label in {
    '/vendor/lib64': 'vendor_file', '/vendor/lib64/hw': 'vendor_hal_file',
    '/vendor/etc': 'vendor_configs_file', '/vendor/etc/audio': 'vendor_configs_file',
    '/vendor/etc/audio/sku_kalama': 'vendor_configs_file',
    '/vendor/lib64/hw/audio\\.primary\\.kalama\\.so': 'vendor_file',
    '/vendor/lib64/libcs-pal\\.so': 'vendor_file',
    '/vendor/etc/audio/sku_kalama/mixer_paths\\.xml': 'vendor_configs_file',
    '/vendor/etc/audio/sku_kalama/resourcemanager\\.xml': 'vendor_configs_file',
}.items():
    assert contexts[rel] == 'u:object_r:'+label+':s0', (rel, contexts.get(rel))
entries = {line.split()[0]: line.split()[1:4] for line in (work/'configs/fs_config-vendor').read_text().splitlines() if line.strip()}
for rel in ('vendor/lib64', 'vendor/lib64/hw', 'vendor/etc', 'vendor/etc/audio', 'vendor/etc/audio/sku_kalama'):
    assert entries[rel] == ['0', '2000', '755'], rel
for rel in expected:
    assert entries[rel] == ['0', '0', '644'], rel
mixer = ET.parse(work/'vendor/etc/audio/sku_kalama/mixer_paths.xml').getroot()
route = mixer.find('path[@name="rezoss-call-screen-isolated-mic"]')
assert route is not None and not route.findall('path')
assert len(route.findall('ctl')) == 20
assert sum(c.get('value') == 'ZERO' for c in route.findall('ctl')) == 16
for unsafe in ('data', 'post-fs-data.sh', 'service.sh', 'skip_mount'):
    assert not (work/unsafe).exists()
manifest = {str(p.relative_to(work)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in work.rglob('*') if p.is_file()}
(root/'first-manifest.json').write_text(json.dumps(manifest, sort_keys=True))
print('PASS: exact four v6 payload hashes, stock ownership, HAL/config contexts, ZERO-source route, no root-module hooks')
PY
source "$MODPATH/callscreen/integrate_v6_rom.sh" > "$CS_TEST_ROOT/repeated-build.txt" 2>&1
python3 - "$WORK_DIR" "$CS_TEST_ROOT/first-manifest.json" <<'PY'
from pathlib import Path
import hashlib, json, sys
work = Path(sys.argv[1])
now = {str(p.relative_to(work)): hashlib.sha256(p.read_bytes()).hexdigest() for p in work.rglob('*') if p.is_file()}
assert now == json.loads(Path(sys.argv[2]).read_text()), 'Repeat application changed payload or metadata'
print('PASS: repeated ROM integration is byte-idempotent')
PY
# Fail before touching any target file when another mod edits a pinned input.
printf '\n<!-- competing edit -->\n' >> "$WORK_DIR/vendor/etc/audio/sku_kalama/mixer_paths.xml"
find "$WORK_DIR" -type f -exec sha256sum {} + | sort > "$CS_TEST_ROOT/before-refusal.sha256"
if (set -e; source "$MODPATH/callscreen/integrate_v6_rom.sh") > "$CS_TEST_ROOT/refused-build.txt" 2>&1; then
    echo "FAIL: accepted a competing XML edit" >&2
    exit 1
fi
find "$WORK_DIR" -type f -exec sha256sum {} + | sort > "$CS_TEST_ROOT/after-refusal.sha256"
cmp "$CS_TEST_ROOT/before-refusal.sha256" "$CS_TEST_ROOT/after-refusal.sha256"
echo "PASS: competing XML edit rejected before any payload/metadata mutation"
# Restore the exact known candidate XML and simulate a failing copy helper.
CS_LOGGED_STAGE=$(sed -n 's/^- Build dm3q Call Screening v6 isolated-codec payload: //p' "$CS_TEST_ROOT/first-build.txt")
cp "$CS_LOGGED_STAGE/build/module/candidate/vendor/etc/audio/sku_kalama/mixer_paths.xml" "$WORK_DIR/vendor/etc/audio/sku_kalama/mixer_paths.xml"
find "$WORK_DIR" -type f -exec sha256sum {} + | sort > "$CS_TEST_ROOT/before-copy-failure.sha256"
if (ADD_TO_WORK_DIR(){ return 23; }; source "$MODPATH/callscreen/integrate_v6_rom.sh") > "$CS_TEST_ROOT/copy-failure.txt" 2>&1; then
    echo "FAIL: ignored a copy helper failure" >&2
    exit 1
fi
find "$WORK_DIR" -type f -exec sha256sum {} + | sort > "$CS_TEST_ROOT/after-copy-failure.sha256"
cmp "$CS_TEST_ROOT/before-copy-failure.sha256" "$CS_TEST_ROOT/after-copy-failure.sha256"
echo "PASS: copy-helper failure propagates even when sourced inside a conditional"
# Device/disable guards run before native compilation, even with no firmware.
TARGET_CODENAME=other source "$MODPATH/callscreen/integrate_v6_rom.sh"
mkdir -p "$CS_TEST_ROOT/disabled-mod/callscreen/compat-tx/isolation-v6"
touch "$CS_TEST_ROOT/disabled-mod/callscreen/compat-tx/isolation-v6/disable"
MODPATH="$CS_TEST_ROOT/disabled-mod" source "$MODPATH/callscreen/integrate_v6_rom.sh"
echo "PASS: other devices and disabled v6 skip integration"
echo "Fixture evidence: $CS_TEST_ROOT"
