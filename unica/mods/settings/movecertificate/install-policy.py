#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Install only the dedicated certificate worker's policy and labels."""
from pathlib import Path
import hashlib
import sys

source = Path(__file__).resolve().parent / 'selinux'
platform = Path(sys.argv[1]) / 'system/system/etc/selinux'
updates = {}
for name, target in [('movecertificate.cil', 'plat_sepolicy.cil'), ('file_contexts', 'plat_file_contexts')]:
    path = platform / target
    old = path.read_text()
    prefix = ';' if name.endswith('.cil') else '#'
    begin, end = prefix + ' BEGIN UN1CA MOVE CERTIFICATE', prefix + ' END UN1CA MOVE CERTIFICATE'
    block = begin + '\n' + (source / name).read_text().rstrip() + '\n' + end
    if begin in old:
        before, tail = old.split(begin, 1)
        _, after = tail.split(end, 1)
        value = before + block + after
    else:
        value = old.rstrip() + '\n\n' + block + '\n'
    updates[path] = value
for path, value in updates.items():
    path.write_text(value)
(platform / 'plat_sepolicy_and_mapping.sha256').write_text(hashlib.sha256((platform / 'plat_sepolicy.cil').read_bytes()).hexdigest() + '\n')
