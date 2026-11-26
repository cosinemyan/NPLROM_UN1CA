#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Preflight native slices in order on a disposable decode; publish patches only."""
import argparse,subprocess,tempfile,shutil,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('kind',choices=['dressroom','aod','systemui','wallpaper']);p.add_argument('decoded',type=Path);p.add_argument('output',type=Path);p.add_argument('--implementation',action='store_true');a=p.parse_args()
if a.output.exists():raise SystemExit('Output exists; preserve it and choose a new directory')
a.output.mkdir(parents=True)
with tempfile.TemporaryDirectory(prefix='dressroom-full-preflight-') as temporary:
 trial=Path(temporary)/'decoded';shutil.copytree(a.decoded,trial)
 generators=[]
 if a.kind=='dressroom':
  generators+=[('0100-editor',[ROOT/'editor-runtime/generate.py',trial]),('0110-provider',[ROOT/'font-storage/generate-manifest-patch.py',trial/'AndroidManifest.xml'])]
 if a.kind in ('dressroom','aod','wallpaper'):
  generators+=[('0120-depth',[ROOT/'depth-wallpaper/generate.py','--kind',a.kind,'--decoded',trial,'--output'])]
 if a.kind=='aod':generators+=[('0130-clock',[ROOT/'clock-runtime/generate.py',trial])]
 if a.kind in ('dressroom','aod','systemui'):generators+=[('0140-widgets-extras',[ROOT/'widgets-extras/generate_patches.py',a.kind,trial])]
 for name,args in generators:
  patch=a.output/(name+'.patch')
  subprocess.run([sys.executable,*map(str,args),str(patch)],check=True)
  for extra in (['--dry-run'],[]):
   subprocess.run(['patch','--batch','--fuzz=0','-p1','-d',str(trial),'-i',str(patch),*extra],check=True,stdout=subprocess.DEVNULL)
  print('Strict ordered preflight passed: '+patch.name,flush=True)

if a.implementation:
 canonical=ROOT/'patches'/('0002-Wallpaper-runtime.patch' if a.kind=='wallpaper' else '0001-Native-runtime.patch')
 if not canonical.is_file():raise SystemExit('Native implementation patch missing')
 # A fresh contiguous DEX is required; Android loads classesN.dex in sequence.
 indices=sorted(1 if p.name=='smali' else int(p.name.removeprefix('smali_classes')) for p in a.decoded.glob('smali*') if p.is_dir())
 if indices!=list(range(1,max(indices)+1)):raise SystemExit('Non-contiguous DEX layout')
 for source in a.decoded.glob('smali*/dev/denver/dressroomadvanced/*.smali'):
  raise SystemExit('Native implementation already exists; use a clean target: '+str(source))
 dex='smali_classes'+str(max(indices)+1)
 content=canonical.read_text().replace('+++ b/smali_classes4/','+++ b/'+dex+'/')
 implementation=a.output/'0190-implementation.patch';implementation.write_text(content)
 subprocess.run(['patch','--batch','--dry-run','--fuzz=0','-p1','-d',str(a.decoded),'-i',str(implementation)],check=True,stdout=subprocess.DEVNULL)
 callbacks=[p for p in sorted(a.output.glob('*.patch')) if p!=implementation]
 subprocess.run([sys.executable,str(ROOT/'validate-callbacks.py'),str(ROOT/'patches/public-callbacks.txt'),*map(str,callbacks)],check=True)
 print('Native implementation preflight passed in '+dex,flush=True)
