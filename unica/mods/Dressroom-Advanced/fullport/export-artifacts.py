#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Export reviewable implementation patches and linkage/hash metadata after build."""
import argparse,hashlib,re,subprocess,sys
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('compiled',type=Path);p.add_argument('output',type=Path);a=p.parse_args();root=Path(__file__).resolve().parent
if a.output.exists():raise SystemExit('Output exists; choose a new export directory')
a.output.mkdir(parents=True)
for name,extra in [('0001-Native-runtime.patch',[]),('0002-Wallpaper-runtime.patch',['--wallpaper'])]:
 subprocess.run([sys.executable,str(root/'emit-helpers.py'),str(a.compiled),str(a.output/name),*extra],check=True)
methods=[]
for path in (a.compiled/'smali').rglob('*.smali'):
 text=path.read_text();owner=re.search(r'^\.class (?:.* )?(L[^;]+;)$',text,re.M)[1]
 for m in re.finditer(r'^\.method (.*) (\S+)$',text,re.M):
  if 'public' in m[1].split() and 'static' in m[1].split():methods.append(owner+'->'+m[2])
(a.output/'public-callbacks.txt').write_text('\n'.join(sorted(methods))+'\n')
files=sorted(p for p in root.rglob('*.java')if'tests'not in p.relative_to(root).parts)
(a.output/'SOURCES.sha256').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.relative_to(root).as_posix()+'\n'for p in files))
