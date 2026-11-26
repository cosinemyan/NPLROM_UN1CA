#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a merged read-only audio tree in new staging, without editing inputs."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import speaker

HERE = Path(__file__).resolve().parent
MARKER = '.portrom-v1.0.2'


def prepare(work, stage):
    original = work / 'system/system/media/audio'
    destination = stage / 'system/system/etc/unica/portrom-audio'
    manifest = json.loads((HERE / 'assets/manifest.json').read_text())
    if not original.is_dir() or destination.exists():
        raise ValueError('Missing original audio or staging output already exists')
    if (original / MARKER).exists():
        raise ValueError('Input is already overlaid with PortROM; use unmounted build inputs')
    # These native consumers must exist; do not silently ship an inert power sound.
    init = work / 'system/system/etc/init/hw/init.rc'
    if 'trigger load_persist_props_action' not in init.read_text():
        raise ValueError('Persistent-property boot event missing')
    policy = (work / 'system/system/etc/selinux/plat_sepolicy.cil').read_text()
    if '(allow init system_file (dir (mounton)))' not in policy:
        raise ValueError('Init lacks the existing system_file bind-mount permission')
    if not (work / 'system/system/bin/samsungpowersoundplay').is_file():
        raise ValueError('Native Samsung boot sound player missing')
    if not (work / 'system/system/etc/init/powersnd.rc').is_file():
        raise ValueError('Enable the bootsound mod before using PortROM audio')
    names = set()
    for item in manifest['files']:
        relative = Path(item['path'])
        if relative.is_absolute() or '..' in relative.parts or item['path'] in names:
            raise ValueError('Invalid or duplicate asset path')
        names.add(item['path'])
        source = HERE / 'assets/audio' / relative
        if source.is_symlink() or hashlib.sha256(source.read_bytes()).hexdigest() != item['sha256']:
            raise ValueError('Asset integrity failure: ' + str(relative))
    if len(names) != 298:
        raise ValueError('Expected 194 replacements, 102 additions and two power sounds')
    # Keep all unrelated original sounds, including future additions. Existing
    # OMC/enterprise sound overrides continue to follow Samsung's own precedence.
    shutil.copytree(original, destination / 'audio')
    for item in manifest['files']:
        target = destination / 'audio' / item['path']
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(HERE / 'assets/audio' / item['path'], target)
    (destination / 'audio' / MARKER).write_text('PortROM V1.0.2\n')
    shutil.copyfile(HERE / 'assets/manifest.json', destination / 'manifest.json')
    shutil.copyfile(HERE / 'PortROM-TERMS.txt', destination / 'PortROM-TERMS.txt')
    speaker.prepare(work, stage)
    for path in destination.rglob('*'):
        if path.is_symlink():
            raise ValueError('Symlink in merged audio: ' + str(path))
        path.chmod(0o755 if path.is_dir() else 0o644)
    # Give ADD_TO_WORK_DIR complete metadata rather than relying on fallback
    # labels for a directory that did not exist in the source firmware.
    system = stage / 'system/system'
    paths = [system, *sorted(system.rglob('*'))]
    entries = [('system' + ('/' + p.relative_to(system).as_posix() if p != system else ''), p)
               for p in paths]
    (stage / 'fs_config-system').write_text(''.join(
        f'{name} 0 0 {"755" if p.is_dir() else "644"} capabilities=0x0\n'
        for name, p in entries))
    # Match common_utils.sh:_HANDLE_SPECIAL_CHARS exactly; its lookup uses
    # fixed strings, so extra regex escapes would miss the supplied metadata.
    escapes = str.maketrans({char: '\\' + char for char in '.+[]*'})
    (stage / 'file_context-system').write_text(''.join(
        f'/{name.translate(escapes)} u:object_r:{speaker.label(p.relative_to(stage).as_posix())}:s0\n' for name, p in entries))
    vendor = stage / 'vendor'
    vendor_paths = [vendor, *sorted(vendor.rglob('*'))]
    (stage / 'fs_config-vendor').write_text(''.join(
        f'{p.relative_to(stage).as_posix()} 0 0 {"755" if p.is_dir() else "644"} capabilities=0x0\n'
        for p in vendor_paths))
    (stage / 'file_context-vendor').write_text(''.join(
        f'/{p.relative_to(stage).as_posix().translate(escapes)} u:object_r:{speaker.label(p.relative_to(stage).as_posix())}:s0\n'
        for p in vendor_paths))
    print('Prepared 298 PortROM assets and all three speaker stages; originals untouched')


if __name__ == '__main__':
    prepare(Path(sys.argv[1]), Path(sys.argv[2]))
