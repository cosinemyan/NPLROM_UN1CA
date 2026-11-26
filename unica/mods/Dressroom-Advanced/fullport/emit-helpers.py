#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Emit source additions as a patch. Never writes a decoded APK."""
import argparse,difflib
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('compiled',type=Path);p.add_argument('output',type=Path);p.add_argument('--wallpaper',action='store_true');a=p.parse_args()
if a.output.exists():raise SystemExit('Output exists; preserve it')
classes=a.compiled/('wallpaper-smali'if a.wallpaper else'smali');sources=sorted(classes.rglob('*.smali'))
if not sources:raise SystemExit('Compiled source closure missing')
root=Path(__file__).resolve().parent.parent
text='AUTHOR BlueOutside, Rezoss\nSubject: [PATCH] Add native Dressroom Advanced implementation and attribution\n\n'
for source in sources:
 text+=''.join(difflib.unified_diff([],source.read_text().splitlines(True),fromfile='/dev/null',tofile='b/smali_classes4/'+source.relative_to(classes).as_posix()))
for name in ['LICENSE.upstream','PROVENANCE.txt']:
 text+=''.join(difflib.unified_diff([],(root/name).read_text().splitlines(True),fromfile='/dev/null',tofile='b/assets/unica/dressroom-advanced/'+name))
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(text)
print(f'{len(sources)} classes emitted: {a.output}')
