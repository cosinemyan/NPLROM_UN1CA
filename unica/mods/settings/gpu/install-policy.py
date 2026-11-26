#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Install dm3q-specific labels; preserve existing block-device consumers."""
from pathlib import Path
import hashlib
import re
import sys

work = Path(sys.argv[1])
source = Path(__file__).resolve().parent / 'selinux'
platform = work / 'system/system/etc/selinux'
vendor = work / 'vendor/etc/selinux/vendor_sepolicy.cil'
if '(type emmcblk_device)' not in vendor.read_text():
    raise SystemExit('GPU tuning: unsupported Samsung block-device policy')

def strip(old, prefix):
    begin, end = prefix + ' BEGIN UN1CA GPU', prefix + ' END UN1CA GPU'
    if begin in old:
        before, tail = old.split(begin, 1)
        if end not in tail:
            raise SystemExit('GPU tuning: incomplete previous policy block')
        _, after = tail.split(end, 1)
        old = before + after
    return old.rstrip()

plat = platform / 'plat_sepolicy.cil'
plat_old, vendor_old = strip(plat.read_text(), ';'), strip(vendor.read_text(), ';')
# Keep legacy access to these two nodes after their labels become specific.
# Attribute membership is cloned only for simple positive lists. Refuse an
# unknown expression rather than accidentally extending a negated attribute.
extra = []
for line in (plat_old + '\n' + vendor_old).splitlines():
    if re.search(r'\bemmcblk_device\b', line):
        match = re.fullmatch(r'\(typeattributeset (\S+) \(([^()]*)\)\)', line)
        if line.startswith('(typeattributeset '):
            if not match or set(match[2].split()) & {'and', 'or', 'not', 'all'}:
                raise SystemExit('GPU tuning: unsupported block attribute expression')
            extra.append(f'(typeattributeset {match[1]} (unica_gpu_block unica_gpu_vbmeta))')
        elif re.match(r'^\(allowx? \S+ emmcblk_device ', line):
            extra.extend(line.replace(' emmcblk_device ', f' {label} ', 1)
                         for label in ('unica_gpu_block', 'unica_gpu_vbmeta'))
extra += ['(allow init emmcblk_device (blk_file (relabelfrom)))',
          '(allow init unica_gpu_block (blk_file (relabelto)))',
          '(allow init unica_gpu_vbmeta (blk_file (relabelto)))']

def block(payload, prefix):
    return '\n\n' + prefix + ' BEGIN UN1CA GPU\n' + payload.rstrip() + '\n' + prefix + ' END UN1CA GPU\n'

updates = {
    plat: plat_old + block((source / 'gpu.cil').read_text(), ';'),
    vendor: vendor_old + block('\n'.join(dict.fromkeys(extra)), ';'),
}
for name, dest in [('file_contexts', 'plat_file_contexts'), ('property_contexts', 'plat_property_contexts')]:
    target = platform / dest
    updates[target] = strip(target.read_text(), '#') + block((source / name).read_text(), '#')
for target, value in updates.items():
    target.write_text(value)
identifier = hashlib.sha256(plat.read_bytes() + vendor.read_bytes()).hexdigest()
(platform / 'plat_sepolicy_and_mapping.sha256').write_text(identifier + '\n')
print('Installed dm3q GPU worker, protected backup IPC and two specific block labels')
