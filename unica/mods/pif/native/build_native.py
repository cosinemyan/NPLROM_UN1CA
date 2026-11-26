#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build the child-process PIF backend without modifying firmware or target decodes."""
import argparse
import datetime
import difflib
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent
MOD = ROOT.parent
REPO = MOD.parents[2]
PATCH = MOD / 'framework.jar/0003-Initialize-native-PIF-in-app-child.patch'
MANIFEST = ROOT / 'payload.json'

def run(*args):
    subprocess.run([str(a) for a in args], check=True)

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def inputs():
    return [Path(__file__), MOD/'framework.jar/0001-Introduce-PlayIntegrityHooks.patch',
            MOD/'framework.jar/0002-Spoof-PIF-system-properties.patch'] + sorted(
            p for p in (ROOT / 'source').rglob('*') if p.is_file())

def defaults_header():
    patch = (MOD/'framework.jar/0001-Introduce-PlayIntegrityHooks.patch').read_text()
    fields = {'kFingerprint':'PIF_FINGERPRINT', 'kSecurityPatch':'PIF_SECURITY_PATCH',
              'kFirstApi':'PIF_DEVICE_INITIAL_SDK_INT'}
    lines = ['// Generated in /tmp from the preceding PIF patch.', '#pragma once', 'namespace unica_pif {']
    for variable, field in fields.items():
        match = re.search(r'\.field[^\n]* '+field+r':[^\n]* = "([^"\n]+)"', patch)
        if not match: raise SystemExit('Missing PIF default: '+field)
        lines.append('inline constexpr char '+variable+'[] = '+json.dumps(match[1])+';')
    return '\n'.join(lines+['}', ''])

def verify():
    if not MANIFEST.is_file(): raise SystemExit('PIF native payload missing: run native/build_native.py')
    data = json.loads(MANIFEST.read_text())
    actual = {str(p.relative_to(MOD)): digest(p) for p in inputs()}
    if actual != data['sources']: raise SystemExit('PIF native sources changed: run native/build_native.py')
    for name, sha in data['outputs'].items():
        p = MOD / name
        if not p.is_file() or digest(p) != sha: raise SystemExit('PIF payload mismatch: ' + name)
    print('PIF native source/payload hashes verified')

def udiff(path, before, after):
    return 'diff --git a/' + path + ' b/' + path + '\n' + ''.join(difflib.unified_diff(
        before.splitlines(True), after.splitlines(True), fromfile='a/'+path if before else '/dev/null',
        tofile='b/'+path, n=3))

def build(args):
    sdk = Path(args.sdk).resolve()
    ndk = sdk / 'ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/bin'
    android = sdk / 'platforms/android-37.0/android.jar'
    if not android.is_file(): raise SystemExit('Android SDK 37 android.jar required')
    with tempfile.TemporaryDirectory(prefix='unica-pif-native-') as td:
        work = Path(td)
        (work/'defaults.hpp').write_text(defaults_header())
        for abi, triple, folder in [('arm64-v8a','aarch64-linux-android','lib64'),
                                   ('armeabi-v7a','armv7a-linux-androideabi','lib')]:
            dest = ROOT / 'payload/system' / folder / 'libunica_pif.so'
            dest.parent.mkdir(parents=True, exist_ok=True)
            output = work / (abi+'.so')
            run(ndk/(triple+'29-clang++'), '-std=c++20', '-O2', '-fPIC', '-fvisibility=hidden',
                '-shared', '-static-libstdc++', '-fstack-protector-strong', '-Wl,--exclude-libs,ALL',
                '-I'+str(work), '-Wl,--no-undefined', '-Wl,-z,relro,-z,now',
                '-Wl,-z,max-page-size=16384', ROOT/'source/bridge.cpp',
                ROOT/'source/vendor/property_virtualizer.cpp', '-llog', '-o', output)
            run(ndk/'llvm-strip', '--strip-unneeded', output)
            if dest.exists(): shutil.copy2(dest, str(dest)+'.bak.'+datetime.datetime.now().strftime('%Y%m%d_%H%M%S'))
            shutil.copy2(output, dest)
            dest.chmod(0o644)
        stub = work/'stub/android/os/SystemProperties.java'
        stub.parent.mkdir(parents=True)
        stub.write_text('package android.os; public class SystemProperties { public static boolean getBoolean(String k, boolean d) { return d; } }\n')
        stubs = work/'stubs'; stubs.mkdir()
        classes = work/'classes'; classes.mkdir()
        run('javac', '--release', '17', '-g:none', '-d', stubs, stub)
        run('javac', '--release', '17', '-g:none', '-cp', str(android)+':'+str(stubs), '-d', classes,
            ROOT/'source/java/io/mesalabs/unica/PifProcessHooks.java')
        dex = work/'dex'; dex.mkdir()
        run(sdk/'build-tools/37.0.0/d8', '--release', '--min-api', '29', '--lib', android,
            '--output', dex, classes/'io/mesalabs/unica/PifProcessHooks.class')
        smali = work/'smali'
        run('java', '-cp', REPO/'out/tools/bin/apktool.jar',
            'com.android.tools.smali.baksmali.Main', 'd', '-a', '35', dex/'classes.dex', '-o', smali)
        hook = (smali/'io/mesalabs/unica/PifProcessHooks.smali').read_text()
        if 'SystemProperties;->getBoolean' not in hook: raise SystemExit('Missing PIF enable guard')
        # Reconstruct only the added class from the preceding PIF patches in /tmp.
        raw = (MOD/'framework.jar/0001-Introduce-PlayIntegrityHooks.patch').read_text()
        section = raw.split('+++ b/smali_classes6/io/mesalabs/unica/PlayIntegrityHooks.smali\n')[1].split('\n-- \n')[0]
        old = '\n'.join(l[1:] for l in section.splitlines() if l.startswith('+'))+'\n'
        path = 'smali_classes6/io/mesalabs/unica/PlayIntegrityHooks.smali'
        original = work/'previous'/path; original.parent.mkdir(parents=True); original.write_text(old)
        second = (MOD/'framework.jar/0002-Spoof-PIF-system-properties.patch').read_text()
        block = next('diff --git '+b for b in second.split('diff --git ')[1:] if b.startswith('a/'+path+' '))
        part = work/'previous.patch'; part.write_text(block)
        run('patch','--fuzz=0','-l','-p1','-d',work/'previous','-i',part)
        old = original.read_text(); new = old
        # Native success owns Build/property handling. Preserve original fallback and process tracking.
        anchor = '    sput-object v0, Lio/mesalabs/unica/PlayIntegrityHooks;->sProcessName:Ljava/lang/String;\n'
        insertion = '''
    invoke-static {}, Lio/mesalabs/unica/PifProcessHooks;->isActive()Z

    move-result v0

    if-nez v0, :cond_3
'''
        assert new.count(anchor)==1; new=new.replace(anchor,anchor+insertion)
        anchor = '.method public static blacklist onEngineGetCertificateChain()V\n    .locals 1\n'
        insertion = '''
    # Native mode mirrors Inject with spoofProvider=false; keep the native keybox backend.
    invoke-static {}, Lio/mesalabs/unica/PifProcessHooks;->isActive()Z

    move-result v0

    if-nez v0, :goto_0
'''
        assert new.count(anchor)==1; new=new.replace(anchor,anchor+insertion)
        # Native mode defaults Play Store spoofing off; retain legacy behavior only on load failure
        # when the user explicitly opted in to Play Store Build spoofing.
        anchor = '    :cond_2\n    const-string v0, "com.android.vending"\n'
        replacement = '''    :cond_2
    const-string v0, "persist.sys.pif.vending_build"

    const/4 v1, 0x0

    invoke-static {v0, v1}, Landroid/os/SemSystemProperties;->getBoolean(Ljava/lang/String;Z)Z

    move-result v0

    if-eqz v0, :cond_3

    const-string v0, "com.android.vending"
'''
        assert new.count(anchor)==1; new=new.replace(anchor,replacement)
        zpath='smali_classes5/com/android/internal/os/ZygoteInit.smali'
        # Stable method-local anchor; register counts and existing startup control flow unchanged.
        zbefore='''    const-string v2, "ZygoteInit"

    invoke-static {v0, v1, v2}, Landroid/os/Trace;->traceBegin(JLjava/lang/String;)V

    invoke-static {}, Lcom/android/internal/os/RuntimeInit;->redirectLogStreams()V

    invoke-static {}, Lcom/android/internal/os/RuntimeInit;->commonInit()V

    invoke-static {}, Lcom/android/internal/os/ZygoteInit;->nativeZygoteInit()V

    invoke-static {p0, p1, p2, p3, p4}, Lcom/android/internal/os/RuntimeInit;->applicationInit(I[J[J[Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/Runnable;
'''
        zafter=zbefore.replace('    invoke-static {}, Lcom/android/internal/os/RuntimeInit;->commonInit()V',
            '    invoke-static {}, Lio/mesalabs/unica/PifProcessHooks;->initialize()V\n\n    invoke-static {}, Lcom/android/internal/os/RuntimeInit;->commonInit()V')
        content='From: UN1CA <noreply@unica.local>\nSubject: [PATCH] Initialize native PIF only in selected app children\n\n'
        content+=udiff(path,old,new)+udiff(zpath,zbefore,zafter)
        content+=udiff('smali_classes6/io/mesalabs/unica/PifProcessHooks.smali','',hook)
        if PATCH.exists(): shutil.copy2(PATCH,str(PATCH)+'.bak.'+datetime.datetime.now().strftime('%Y%m%d_%H%M%S'))
        PATCH.write_text('\n'.join(l.rstrip() for l in content.splitlines())+'\n')
        outputs=[PATCH,*sorted((ROOT/'payload').rglob('*.so'))]
        MANIFEST.write_text(json.dumps({'ndk':'28.2.13676358', 'source_sdk':37,
            'sources':{str(p.relative_to(MOD)):digest(p) for p in inputs()},
            'outputs':{str(p.relative_to(MOD)):digest(p) for p in outputs}},indent=2)+'\n')
    verify()

if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--check',action='store_true')
    parser.add_argument('--sdk',default=os.environ.get('ANDROID_HOME',str(Path.home()/'Android/Sdk')))
    args=parser.parse_args()
    verify() if args.check else build(args)
