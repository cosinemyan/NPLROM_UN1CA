#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Package post-install-armable v5; never deploy or change device/ROM state."""
import argparse, hashlib, json, shutil, subprocess, sys, zipfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--output',type=Path,required=True)
a=p.parse_args()
out=a.output.resolve()
if not out.is_relative_to(Path('/tmp')) or out.exists():
    raise SystemExit('Output must be new and under /tmp')
subprocess.run([sys.executable,str(HERE/'build_candidate.py'),'--output',str(out)],check=True)
m=out/'module'
(m/'module.prop').write_text('''id=rezoss_callscreen_compat_tx_v5
name=Call Screening TX compatibility v5 TEST
version=0.4-contextfix
versionCode=4
author=Rezoss
description=Experimental TX fallback. Installs inactive; arm_v5.sh explicitly enables NEXT-BOOT Mountify overlay. Not a proven audio fix.
''')
shutil.copy2(HERE/'arm_v5.sh',m/'arm_v5.sh')
# Existing installer remains inactive and pinned. Add arming script executable bit.
with (m/'customize.sh').open('a') as f:
    f.write('\nset_perm "$MODPATH/arm_v5.sh" 0 0 0755\n')
(m/'action.sh').write_text('''#!/system/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
MODDIR=${0%/*}
if [ -f "$MODDIR/skip_mount" ]; then
    exec sh "$MODDIR/preflight.sh"
fi
echo "V5 is armed. Read-only active-path verification:"
ls -lZ /vendor/lib64/libcs-pal.so /vendor/lib64/hw/audio.primary.kalama.so
sha256sum /vendor/lib64/libcs-pal.so /vendor/lib64/hw/audio.primary.kalama.so
''')
report=json.loads((out/'build-report.json').read_text())
report.update(status='V5 HOST/ISOLATED-LOADER TESTED; REAL CALL UNVERIFIED',directory_context_fix='Preserve stock vendor_hal_file on hw directory; busybox chcon --reference, no policy relaxations',activation='explicit arm_v5.sh after installer, then authorized reboot',
              loader_evidence='Full patched HAL and six shim symbols load in private vendor-path overlay; no PAL APIs called; not tested under audio HAL SELinux domain')
(out/'build-report.json').write_text(json.dumps(report,indent=2)+'\n')
archive=out/'rezoss-callscreen-compat-tx-v5-contextfix.zip'
with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED) as z:
    for file in sorted(m.rglob('*')):
        if file.is_file(): z.write(file,file.relative_to(m))
print(json.dumps({'v5':str(archive),'sha256':hashlib.sha256(archive.read_bytes()).hexdigest()},indent=2))
