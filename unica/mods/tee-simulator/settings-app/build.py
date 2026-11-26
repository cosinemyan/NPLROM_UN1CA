#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build/check the ROM-only WebView app without touching firmware or work_dir."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[3]
HELPERS = REPO / 'unica/mods/settings/keybox/java/io/mesalabs/unica/keybox'
OUTPUT = ROOT / 'payload/system/priv-app/TeeSimulatorSettings/TeeSimulatorSettings.apk'

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def inputs(prefix):
    paths = [p for p in ROOT.rglob('*') if p.is_file() and
             p.relative_to(ROOT).parts[0] not in ('payload', '__pycache__') and
             p.name != 'payload.json']
    paths += [HELPERS / (name + '.java') for name in ('Backend', 'Profiles', 'Keybox', 'Roots', 'Diagnostics')]
    paths += [REPO / 'security' / (prefix + '_platform.x509.pem')]
    return {str(p.relative_to(REPO)): digest(p) for p in sorted(paths)}

def run(*args):
    subprocess.run([str(a) for a in args], check=True)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--cert-prefix', default='aosp', choices=['aosp', 'unica'])
    args = parser.parse_args()
    snapshot = inputs(args.cert_prefix)
    if args.check:
        manifest = json.loads((ROOT / 'payload.json').read_text())
        if manifest != {'inputs': snapshot, 'apk_sha256': digest(OUTPUT), 'certificate': args.cert_prefix}:
            raise SystemExit('TEE Settings APK is stale; run settings-app/build.py')
        for f in (ROOT / 'config').glob('*.xml'):
            subdir = 'permissions' if f.name.startswith('privapp-') else 'sysconfig'
            if (ROOT / 'payload/system/etc' / subdir / f.name).read_bytes() != f.read_bytes():
                raise SystemExit('TEE Settings staged configuration is stale')
        print('TEE Settings source/payload hashes verified')
        return
    sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Android/Sdk')))
    jar = sdk / 'platforms/android-37.0/android.jar'
    tools = sdk / 'build-tools/37.0.0'
    work = Path(tempfile.mkdtemp(prefix='unica-tee-settings-', dir='/tmp'))
    for name in ('classes', 'dex'):
        (work / name).mkdir()
    helpers = [HELPERS / (name + '.java') for name in ('Backend', 'Profiles', 'Keybox', 'Roots', 'Diagnostics')]
    run('javac', '--release', '17', '-encoding', 'UTF-8', '-classpath', jar, '-d', work / 'classes',
        *sorted((ROOT / 'java').rglob('*.java')), *helpers)
    run('jar', 'cf', work / 'classes.jar', '-C', work / 'classes', '.')
    run(tools / 'd8', '--release', '--min-api', '35', '--lib', jar, '--output', work / 'dex', work / 'classes.jar')
    run(tools / 'aapt2', 'link', '-I', jar, '--manifest', ROOT / 'AndroidManifest.xml',
        '-A', ROOT / 'assets', '-o', work / 'base.apk')
    with zipfile.ZipFile(work / 'base.apk', 'a', zipfile.ZIP_DEFLATED) as archive:
        for dex in sorted((work / 'dex').glob('classes*.dex')):
            archive.write(dex, dex.name)
        archive.write(ROOT / 'LICENSE.upstream', 'assets/LICENSE.TEESimulator')
    run(tools / 'zipalign', '-P', '16', '-f', '4', work / 'base.apk', work / 'aligned.apk')
    run(tools / 'apksigner', 'sign', '--key', REPO / 'security' / (args.cert_prefix + '_platform.pk8'),
        '--cert', REPO / 'security' / (args.cert_prefix + '_platform.x509.pem'),
        '--out', work / 'signed.apk', work / 'aligned.apk')
    run(tools / 'apksigner', 'verify', '--verbose', work / 'signed.apk')
    run(tools / 'zipalign', '-c', '-P', '16', '4', work / 'signed.apk')
    with zipfile.ZipFile(work / 'signed.apk') as archive:
        assert archive.testzip() is None
        assert archive.read('classes.dex').startswith(b'dex\n')
        assert not any('/bridge/shell.js' in n or '/bridge/ksu.js' in n for n in archive.namelist())
    if inputs(args.cert_prefix) != snapshot:
        raise SystemExit('Inputs changed during build; refusing stale APK')
    for f in (ROOT / 'config').glob('*.xml'):
        subdir = 'permissions' if f.name.startswith('privapp-') else 'sysconfig'
        dest = ROOT / 'payload/system/etc' / subdir / f.name
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(f, dest)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    if OUTPUT.exists():
        shutil.copy2(OUTPUT, work / 'previous.apk')
    shutil.copy2(work / 'signed.apk', OUTPUT)
    (ROOT / 'payload.json').write_text(json.dumps({'inputs': snapshot, 'apk_sha256': digest(OUTPUT), 'certificate': args.cert_prefix}, indent=2) + '\n')
    print(f'Built {OUTPUT}; temporary build and any previous APK retained: {work}')

if __name__ == '__main__':
    main()
