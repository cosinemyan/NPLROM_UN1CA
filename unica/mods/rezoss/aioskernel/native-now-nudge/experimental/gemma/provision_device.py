#!/usr/bin/env python3
"""Explicit rooted-device workaround for the ROM's blocked SAF association.

Does not install APKs, change provider settings, or restart applications.
Refuses to replace an existing imported model or verification marker.
"""
import argparse
import json
import re
import shlex
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    adb = ['/mnt/c/platform-tools/adb.exe', '-s', args.serial]

    def root(command):
        return subprocess.check_output(adb + ['shell', 'su -c ' + shlex.quote(command)], text=True).strip()

    folder = '/data/user/0/com.samsung.android.aioskernelservice/files/gemma4-e2b'
    source = '/sdcard/Download/Gemma-E2B-tests-20261031/gemma-4-E2B-it.litertlm'
    model = folder + '/gemma-4-E2B-it.litertlm'
    digest = '181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c'
    size = 2588147712
    root('set -eu; test -d ' + folder + '; test ! -e ' + model + '; test ! -e ' + folder + '/verified.json; test ! -e ' + folder + '/model-provision.part')
    assert root('sha256sum ' + source).split()[0] == digest, 'Source hash mismatch'
    assert int(root('stat -c %s ' + source)) == size, 'Source size mismatch'
    owner = root('stat -c %u:%g ' + folder)
    assert re.fullmatch(r'\d+:\d+', owner), 'Unexpected owner'
    temporary = folder + '/model-provision.part'
    root('set -eu; cp ' + source + ' ' + temporary + '; chown ' + owner + ' ' + temporary + '; chmod 600 ' + temporary + '; restorecon ' + temporary)
    assert root('sha256sum ' + temporary).split()[0] == digest, 'Copied hash mismatch'
    root('set -eu; test ! -e ' + model + '; mv ' + temporary + ' ' + model)
    seconds = int(root('stat -c %Y ' + model))
    timestamp = root('stat -c %y ' + model)
    fraction = re.search(r'\.(\d+)', timestamp)
    modified = seconds * 1000 + int(((fraction.group(1) if fraction else '') + '000')[:3])
    marker = json.dumps({'sha256': digest, 'size': size, 'modified': modified}, separators=(',', ':'))
    root('set -eu; test ! -e ' + folder + '/verified.json; printf %s ' + shlex.quote(marker) + ' > ' + folder + '/verified-provision.part; chown ' + owner + ' ' + folder + '/verified-provision.part; chmod 600 ' + folder + '/verified-provision.part; restorecon ' + folder + '/verified-provision.part; mv ' + folder + '/verified-provision.part ' + folder + '/verified.json')
    assert root('cat ' + folder + '/verified.json') == marker
    print('Model copied and hash verified. Open Gemma setup and tap Prepare engine for testing.')


if __name__ == '__main__':
    main()
