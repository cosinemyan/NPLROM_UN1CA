#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Install Volume Up recovery policy into the build working tree (never firmware inputs)."""
from pathlib import Path
import hashlib
import sys

source = Path(__file__).resolve().parent
work = Path(sys.argv[1])
platform = work / 'system/system/etc/selinux'
policy = platform / 'plat_sepolicy.cil'
contexts = platform / 'plat_file_contexts'
identifier = platform / 'plat_sepolicy_and_mapping.sha256'
updates = []
for target, prefix, payload in [
    (policy, ';', (source / 'selinux/volumeup.cil').read_text().rstrip()),
    (contexts, '#', '/system/bin/unica-volumeup u:object_r:unica_volumeup_exec:s0'),
    (platform / 'plat_property_contexts', '#',
     'sys.unica.volumeup.recovery u:object_r:unica_volumeup_request_prop:s0 exact bool'),
]:
    old = target.read_text()
    begin = prefix + ' BEGIN UN1CA VOLUMEUP DIAGNOSTIC'
    end = prefix + ' END UN1CA VOLUMEUP DIAGNOSTIC'
    if old.count(begin) != old.count(end) or old.count(begin) > 1:
        raise SystemExit(f'Invalid previous Volume Up policy block: {target}')
    block = begin + '\n' + payload + '\n' + end
    if begin in old:
        before, tail = old.split(begin, 1)
        _, after = tail.split(end, 1)
        new = before + block + after
    else:
        new = old.rstrip() + '\n\n' + block + '\n'
    updates.append((target, new))
# Require the cache identifier before making any changes.
identifier.read_text()
for target, new in updates:
    target.write_text(new)
# Force init to compile changed CIL rather than load stale precompiled policy.
identifier.write_text(hashlib.sha256(policy.read_bytes()).hexdigest() + '\n')
