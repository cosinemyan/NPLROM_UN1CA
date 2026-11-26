#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""ROM build integration for the constrained native kernel worker."""
from pathlib import Path
import sys
import re
import hashlib

source = Path(__file__).resolve().parent / 'selinux'
work = Path(sys.argv[1])
platform = work / 'system/system/etc/selinux'
vendor = work / 'vendor/etc/selinux'
files = [('battery.cil', platform / 'plat_sepolicy.cil'),
         ('vendor.cil', vendor / 'vendor_sepolicy.cil'),
         ('file_contexts', platform / 'plat_file_contexts'),
         ('property_contexts', platform / 'plat_property_contexts')]
for _, target in files:
    if not target.is_file():
        raise SystemExit(f'Battery Saving: required policy input missing: {target}')
policy = (vendor / 'vendor_sepolicy.cil').read_text()
for symbol in ('proc_swappiness', 'vendor_sysfs_kgsl'):
    if f'(type {symbol})' not in policy:
        raise SystemExit(f'Battery Saving: vendor lacks required type {symbol}')
updates = []
for name, target in files:
    prefix = ';' if name.endswith('.cil') else '#'
    begin, end = f'{prefix} BEGIN UN1CA BATTERY SAVING', f'{prefix} END UN1CA BATTERY SAVING'
    old = target.read_text()
    payload = (source / name).read_text().rstrip()
    if name == 'battery.cil':
        # Preserve the actual build's consumers of the three formerly generic
        # sysfs nodes. Never hard-code opaque base_typeattr numbers across ROMs.
        for line in old.splitlines():
            if re.match(r'^\(allow \S+ sysfs \(file ', line):
                payload += '\n' + line.replace(' sysfs (file ', ' unica_battery_sysfs (file ')
    block = begin + '\n' + payload + '\n' + end
    if begin in old:
        before, tail = old.split(begin, 1)
        if end not in tail:
            raise SystemExit(f'Battery Saving: incomplete previous policy block: {target}')
        _, after = tail.split(end, 1)
        new = before + block + after
    else:
        new = old.rstrip() + '\n\n' + block + '\n'
    updates.append((target, new))
for target, new in updates:
    target.write_text(new)
print('Installed constrained Battery Saving worker policy and protected IPC labels')

# A changed platform identifier prevents a stale vendor/odm precompiled policy
# from hiding the new domain. Android init then compiles the modified CIL.
identifier = hashlib.sha256((platform / 'plat_sepolicy.cil').read_bytes()
                            + (vendor / 'vendor_sepolicy.cil').read_bytes()).hexdigest()
(platform / 'plat_sepolicy_and_mapping.sha256').write_text(identifier + '\n')
