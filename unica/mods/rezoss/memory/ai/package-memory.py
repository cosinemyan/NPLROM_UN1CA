#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Use original Samsung ZIP assets/native libraries; replace only approved code and manifest."""
from pathlib import Path
import copy,sys,zipfile
source,rebuilt,helper,out=map(Path,sys.argv[1:])
if out.exists(): raise SystemExit('Refusing to overwrite '+str(out))
with zipfile.ZipFile(source) as src,zipfile.ZipFile(rebuilt) as patched:
    replacements={'AndroidManifest.xml':patched.read('AndroidManifest.xml'),
                  'classes2.dex':patched.read('classes2.dex')}
    # Indexing compatibility hook is the only approved classes.dex change.
    if Path(str(rebuilt)+'.indexing').exists(): replacements['classes.dex']=patched.read('classes.dex')
    dex=helper.read_bytes()
    if not dex.startswith(b'dex\n'):raise ValueError('Invalid helper DEX')
    if 'classes5.dex' in src.namelist():raise ValueError('Unexpected Memory DEX layout')
    with zipfile.ZipFile(out,'w') as dst:
        for info in src.infolist():
            if info.filename.startswith('META-INF/') and info.filename.upper().endswith(('.RSA','.DSA','.EC','.SF','MANIFEST.MF')):continue
            dst.writestr(copy.copy(info),replacements.get(info.filename,src.read(info)))
        dst.writestr('classes5.dex',dex,compress_type=zipfile.ZIP_STORED)
    with zipfile.ZipFile(out) as dst:
        for info in src.infolist():
            if info.filename in replacements or info.filename.startswith('META-INF/'):continue
            if src.read(info)!=dst.read(info.filename):raise ValueError('Unrelated entry changed: '+info.filename)
            if info.filename.startswith('lib/') and info.compress_type!=dst.getinfo(info.filename).compress_type:raise ValueError('Native compression changed')
