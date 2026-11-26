#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Check every injected native bridge call has a public static implementation."""
import argparse,re
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('compiled',type=Path);p.add_argument('patches',type=Path,nargs='+');a=p.parse_args()
methods=set();classes=set()
if a.compiled.is_file():methods=set(a.compiled.read_text().splitlines())
else:
    for path in a.compiled.rglob('*.smali'):
     text=path.read_text();owner=re.search(r'^\.class (?:.* )?(L[^;]+;)$',text,re.M)[1];classes.add(owner)
     for m in re.finditer(r'^\.method (.*) (\S+)$',text,re.M):
      if 'public' in m[1].split() and 'static' in m[1].split():methods.add(owner+'->'+m[2])
count=0;errors=[]
for patch in a.patches:
 paths=list(patch.rglob('*.patch'))if patch.is_dir()else[patch]
 for path in paths:
  if path.name=='0190-implementation.patch':continue # Helper-internal calls may use package access.
  for line in path.read_text().splitlines():
   if not line.startswith('+')or 'invoke-static' not in line:continue
   m=re.search(r', (Ldev/denver/dressroomadvanced/[^;]+;->\S+)',line)
   if m:
    count+=1
    if m[1] not in methods:errors.append(str(path)+': '+m[1])
if errors:raise SystemExit('\n'.join(errors[:20])+f'\n{len(errors)} unresolved external bridge calls')
print(f'{count} injected static bridge calls resolve to public implementations')
