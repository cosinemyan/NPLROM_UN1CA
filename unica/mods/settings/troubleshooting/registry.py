#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Conservative, versioned target index from enabled HMA/PIF source patches.
Runtime also checks the feature's framework class before matching APK references.
This is an overlap index, not a claim that a module executes a hook.
"""
from pathlib import Path
import re,sys
root=Path(__file__).resolve().parents[4]
rows=set()
for feature,gate in [('hma','io.mesalabs.unica.HmaPolicy'),('pif','io.mesalabs.unica.PlayIntegrityHooks')]:
    mod=root/'unica/mods'/feature
    if (mod/'disable').exists():continue
    for patch in mod.rglob('*.patch'):
        if any((p/'disable').exists() or '.bak' in p.name for p in [patch.parent,*patch.parents] if p!=root and root in p.parents):continue
        target=None;methods=set()
        def flush():
            if target:
                for method in methods or {''}:rows.add((feature,target,method,str(patch.relative_to(root)),gate))
        for line in patch.read_text().splitlines():
            if line.startswith('diff --git '):
                flush();methods=set();m=re.search(r'b/smali[^/]*/((?:android|com)/[^ ]+)\.smali$',line)
                target=m.group(1).replace('/','.') if m else None
            m=re.match(r'[ +]\.method .*?([^\s(]+)\(',line)
            if m and not m.group(1).startswith('<'):methods.add(m.group(1))
        flush()
output=Path(sys.argv[1]);output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(''.join('\t'.join(row)+'\n' for row in sorted(rows)))
print('ROM overlap targets:',len(rows))
