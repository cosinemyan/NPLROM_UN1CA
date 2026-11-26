#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Stage additive effect registration; never write to the input ROM tree."""
import hashlib
import json
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parent
UUID = '90380da3-8536-4744-a6a3-5731970e640f'
NS = {'a': 'http://schemas.android.com/audio/audio_effects_conf/v2_0'}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def patch_effects(data):
    text = data.decode()
    root = ET.fromstring(text)
    libraries, effects = root.find('a:libraries', NS), root.find('a:effects', NS)
    if libraries is None or effects is None:
        return None
    if any(e.get('uuid') == UUID for e in root.iter()):
        library = next((e for e in libraries if e.get('name') == 'v4a_re'), None)
        effect = next((e for e in effects if e.get('name') == 'v4a_standard_re'), None)
        if library is None or library.get('path') != 'libv4a_re.so' or effect is None or effect.get('uuid') != UUID or effect.get('library') != 'v4a_re':
            raise ValueError('Conflicting existing ViPER registration')
        return data
    if 'v4a_re' in text or 'v4a_standard_re' in text:
        raise ValueError('Conflicting ViPER library/effect name')
    nl = '\r\n' if '\r\n' in text else '\n'
    for tag, entry in (
        ('libraries', '<library name="v4a_re" path="libv4a_re.so"/>'),
        ('effects', f'<effect name="v4a_standard_re" library="v4a_re" uuid="{UUID}"/>'),
    ):
        if text.count(f'<{tag}>') != 1:
            raise ValueError(f'Unexpected {tag} structure')
        text = text.replace(f'<{tag}>', f'<{tag}>{nl}        {entry}', 1)
    ET.fromstring(text)
    return text.encode()

def main(work, stage):
    manifest = json.loads((HERE / 'payload.json').read_text())
    for name, expected in manifest['sha256'].items():
        if sha(HERE / name) != expected:
            raise ValueError(f'Payload checksum mismatch: {name}')
    # Reject an AIDL vendor or an unexpected legacy effects ABI.
    manifests = list((work / 'vendor/etc/vintf').rglob('*.xml'))
    hals = [hal for p in manifests for hal in ET.parse(p).getroot().iter('hal')
            if hal.findtext('name') == 'android.hardware.audio.effect']
    if not hals or any(hal.get('format') != 'hidl' or hal.findtext('version') != '7.0' for hal in hals):
        raise ValueError('Expected dm3q HIDL effects 7.0; refusing unreviewed audio HAL')
    outputs = []
    def write(relative, data, label):
        dest = stage / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
        outputs.append((relative, label))
    configs = []
    for prefix in ('vendor/etc', 'system/system/etc', 'odm/etc'):
        for config in sorted((work / prefix).rglob('audio_effects*.xml')):
            if config.is_symlink():
                continue
            data = patch_effects(config.read_bytes())
            if data is None:
                continue
            relative = config.relative_to(work).as_posix()
            label = 'system_file' if relative.startswith('system/') else 'vendor_configs_file'
            write(relative, data, label)
            configs.append(relative)
    if 'vendor/etc/audio_effects_sec.xml' not in configs:
        raise ValueError('Samsung effect configuration missing')
    with zipfile.ZipFile(HERE / 'ViPER4Android-RE-v2.1.1.zip') as archive:
        for arch, abi in (('lib', 'armeabi-v7a'), ('lib64', 'arm64-v8a')):
            data = archive.read(f'common/files/libv4a_re_{abi}.so')
            if data[:4] != b'\x7fELF':
                raise ValueError('Invalid driver ELF')
            write(f'vendor/{arch}/soundfx/libv4a_re.so', data, 'vendor_file')
    write('system/system/priv-app/ViPER4Android/ViPER4Android.apk', (HERE / 'ViPER4Android.apk').read_bytes(), 'system_file')
    write('system/system/etc/permissions/privapp-permissions-viper4android.xml', b'''<?xml version="1.0" encoding="utf-8"?>
<permissions>
    <privapp-permissions package="com.llsl.viper4android">
        <permission name="android.permission.MODIFY_AUDIO_ROUTING"/>
        <permission name="android.permission.START_FOREGROUND_SERVICES_FROM_BACKGROUND"/>
    </privapp-permissions>
</permissions>
''', 'system_file')
    # Samsung PackageManager requires a preload entry in addition to priv-app placement.
    relative = 'system/system/etc/sysconfig/allowed-system-preload-apps.xml'
    preload = (work / relative).read_text()
    ET.fromstring(preload)
    if 'package="com.llsl.viper4android"' not in preload:
        if preload.count('</config>') != 1:
            raise ValueError('Unexpected preload allowlist structure')
        preload = preload.replace('</config>', '    <allowed-system-preload package="com.llsl.viper4android"/>\n</config>')
    write(relative, preload.encode(), 'system_file')
    # Pregrant notifications so the persistent processing service is visible.
    write('system/system/etc/default-permissions/default-permissions-viper4android.xml', b'''<?xml version="1.0" encoding="utf-8"?>
<exceptions>
    <exception package="com.llsl.viper4android">
        <permission name="android.permission.POST_NOTIFICATIONS" fixed="false"/>
    </exception>
</exceptions>
''', 'system_file')
    (stage / 'install.tsv').write_text(''.join(f'{path}\t{label}\n' for path, label in outputs))
    print(f'Staged {len(configs)} effect configurations and {len(outputs)} total files at {stage}')

if __name__ == '__main__':
    main(Path(sys.argv[1]).resolve(), Path(sys.argv[2]).resolve())
