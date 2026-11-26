#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Package experimental classes/native SDK without bundling model weights."""
from pathlib import Path
from zipfile import ZipFile,ZIP_STORED
import struct,sys
base,stage,deps=map(Path,sys.argv[1:4]);backend=sys.argv[4]
def classes(b):
    ss,so,ts,to=struct.unpack_from('<IIII',b,56);cs,co=struct.unpack_from('<II',b,96)
    def string(i):
        o=struct.unpack_from('<I',b,so+4*i)[0]
        while b[o]&128:o+=1
        o+=1
        return b[o:b.index(0,o)].decode(errors='replace')
    types=[string(struct.unpack_from('<I',b,to+4*i)[0]) for i in range(ts)]
    return {types[struct.unpack_from('<I',b,co+32*i)[0]] for i in range(cs)}
with ZipFile(base) as src,ZipFile(stage/'manifest.apk') as manifest,ZipFile(stage/'unsigned.apk','x',compression=ZIP_STORED) as dst:
    seen=classes(src.read('classes.dex'))
    assert 'Lio/mesalabs/rezoss/nudge/TextBackend;' not in seen
    for info in src.infolist():
        if info.filename.startswith('META-INF/') or (info.filename.startswith('classes') and info.filename.endswith('.dex') and info.filename!='classes.dex'):continue
        dst.writestr(info,manifest.read('AndroidManifest.xml') if info.filename=='AndroidManifest.xml' else src.read(info))
    dexes=sorted((stage/'dex').glob('classes*.dex'))
    for i,p in enumerate(dexes,2):
        data=p.read_bytes();names=classes(data);duplicates=seen & names
        assert not duplicates,sorted(duplicates)
        seen.update(names);dst.writestr('classes'+str(i)+'.dex',data)
    names=['liblitertlm_jni.so']
    if backend=='gpu':names += ['libLiteRtGpuAccelerator.so','libLiteRtOpenClAccelerator.so','libLiteRtWebGpuAccelerator.so','libwebgpu_dawn.so']
    for name in names:dst.write(deps/name,'lib/arm64-v8a/'+name)
    dst.write(deps/'LiteRT-LM-LICENSE','assets/gemma-licenses/LiteRT-LM-LICENSE')
    for jar in deps.glob('*.jar'):
        with ZipFile(jar) as z:
            for entry in z.namelist():
                if entry.endswith('/') or not any(t in entry.upper() for t in ['LICENSE','NOTICE','COPYING']):continue
                if not entry.startswith('META-INF/'):continue
                safe=entry.replace('/','_')
                dst.writestr('assets/gemma-licenses/'+jar.stem+'/'+safe,z.read(entry))
    dst.writestr('assets/gemma-experimental.txt','Gemma 4 E2B '+backend+' experimental AIOS backend.\nLiteRT-LM 0.19.0. Model imported separately via Android file picker.\n')
assert 'Lio/mesalabs/rezoss/nudge/GemmaRuntime;' in seen
assert 'Lio/mesalabs/rezoss/nudge/GemmaSetupActivity;' in seen
print('DEX inventory verified:',len(seen),'unique classes')
