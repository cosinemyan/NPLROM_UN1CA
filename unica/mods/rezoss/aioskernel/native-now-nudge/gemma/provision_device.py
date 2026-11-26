#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Manual Gemma import for a rooted primary-user phone with a blocked file picker.

Run on a computer with Python 3 and ADB. This copies only the exact supported
model into AIOS storage. It installs no APK, changes no policy/settings and
refuses to overwrite an existing import. No device is contacted by --help.
"""
import argparse
import json
import re
import shlex
import subprocess

HASH = '181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c'
SIZE = 2588147712
APP = '/data/user/0/com.samsung.android.aioskernelservice'
FOLDER = APP + '/files/gemma4-e2b'


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb', help='ADB executable, e.g. /mnt/c/platform-tools/adb.exe in WSL')
    parser.add_argument('--serial', required=True, help='Phone serial shown by adb devices')
    parser.add_argument('--source', default='/sdcard/Download/gemma-4-E2B-it.litertlm', help='Absolute model path on the phone')
    args = parser.parse_args()
    require(args.source.startswith(('/sdcard/', '/storage/emulated/0/')) and '\n' not in args.source and '\r' not in args.source,
            'Source must be a shared-storage file on the primary user phone.')
    adb = [args.adb, '-s', args.serial]

    def root(command):
        result = subprocess.run(adb + ['shell', 'su -c ' + shlex.quote(command)], text=True, capture_output=True, check=True)
        return result.stdout.strip()

    model = FOLDER + '/gemma-4-E2B-it.litertlm'
    marker = FOLDER + '/verified.json'
    part = FOLDER + '/model-provision.part'
    pending = FOLDER + '/verified-provision.part'
    root('set -eu; test "$(id -u)" = 0; test -d ' + APP + '; test ! -L ' + APP + '/files; test ! -L ' + FOLDER)
    for path in [model, marker, part, pending, FOLDER + '/model-import.part']:
        try:
            root('test ! -L ' + path + ' && test ! -e ' + path)
        except subprocess.CalledProcessError:
            raise RuntimeError('Existing model/import files found; nothing replaced. If already imported, use Prepare local engine.') from None
    source = shlex.quote(args.source)
    root('test -f ' + source)
    require(int(root('stat -c %s ' + source)) == SIZE, 'Wrong model size; import cancelled.')
    print('Verifying downloaded model...', flush=True)
    require(root('sha256sum ' + source).split()[0] == HASH, 'Wrong model SHA-256; import cancelled.')
    owner = root('stat -c %u:%g ' + APP)
    require(re.fullmatch(r'\d+:\d+', owner) is not None and not owner.startswith('0:'), 'Unexpected AIOS owner.')
    root('set -eu; mkdir -p ' + FOLDER + '; chown ' + owner + ' ' + APP + '/files ' + FOLDER + '; chmod 700 ' + APP + '/files ' + FOLDER + '; restorecon ' + APP + '/files; restorecon -RF ' + FOLDER)
    print('Copying into AIOS private storage...', flush=True)
    root('set -eu; test ! -e ' + part + '; cp ' + source + ' ' + part + '; chown ' + owner + ' ' + part + '; chmod 600 ' + part + '; restorecon ' + part)
    require(int(root('stat -c %s ' + part)) == SIZE, 'Copied model size mismatch.')
    require(root('sha256sum ' + part).split()[0] == HASH, 'Copied model hash mismatch.')
    seconds = int(root('stat -c %Y ' + part))
    fraction = re.search(r'\.(\d+)', root('stat -c %y ' + part))
    modified = seconds * 1000 + int(((fraction.group(1) if fraction else '') + '000')[:3])
    data = json.dumps({'sha256': HASH, 'size': SIZE, 'modified': modified}, separators=(',', ':'))
    root('set -eu; test ! -e ' + pending + '; printf %s ' + shlex.quote(data) + ' > ' + pending + '; chown ' + owner + ' ' + pending + '; chmod 600 ' + pending + '; restorecon ' + pending)
    root('set -eu; test ! -e ' + model + '; test ! -e ' + marker + '; mv ' + part + ' ' + model + '; mv ' + pending + ' ' + marker)
    require(root('cat ' + marker) == data, 'Verification marker mismatch.')
    print('Model copied and verified. Open Local model setup and tap Prepare local engine.')
    print('After engine preparation succeeds, the original Download file can be deleted.')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, ValueError) as error:
        raise SystemExit('Manual import failed: ' + str(error))
