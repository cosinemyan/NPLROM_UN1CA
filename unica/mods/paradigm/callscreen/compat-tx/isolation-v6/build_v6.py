#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build an inactive exact-firmware v6 test module. Never deploy or reboot."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[5]
CONFIG = 'vendor/etc/audio/sku_kalama'
KEY = 'rezoss_cs_isolated'
PATH = 'rezoss-call-screen-isolated-mic'
TINYALSA_SHA = '2503734a6b1b18602dea0cc472a1ff8751eeb4af513f10b59407e344784de84c'
PINS = {
    'vendor/lib64/hw/audio.primary.kalama.so': '4ac2a7b0844088a7f1fae3207ccb8210cef2e1a480092536bb80a25acf21489a',
    'vendor/lib64/libar-pal.so': '51d2ea15bbc5dfb4361946845e552ecdd80d2842d96504701069781145280bbd',
    CONFIG+'/mixer_paths.xml': 'bb7aa21698ea6ddcfd4304fd20a77e94524f7f0b647b358fe28c825c2ea5ed71',
    CONFIG+'/resourcemanager.xml': '4de0c8c242cab3bb01ea2dbf32cc1a4483397248c022f0b764c17c8e97e857da',
}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def patched_configs(vendor):
    # Text insertion preserves all unrelated XML bytes/comments and ordering.
    rm = (vendor/'etc/audio/sku_kalama/resourcemanager.xml').read_text()
    tree = ET.fromstring(rm)
    handset = [d for d in tree.findall('./device_profile/in-device') if d.findtext('id') == 'PAL_DEVICE_IN_HANDSET_MIC']
    assert len(handset) == 1
    voice = [u for u in handset[0].findall('usecase') if u.findtext('name') == 'PAL_STREAM_VOICE_CALL']
    assert len(voice) == 1 and voice[0].findtext('channels') == '4'
    assert not tree.findall(f'.//custom-config[@key="{KEY}"]')
    marker = '                <snd_device_name>call-handset-mic</snd_device_name>\n'
    assert rm.count(marker) == 1
    rm = rm.replace(marker, marker+f'''                <custom-config key="{KEY}">
                    <snd_device_name>{PATH}</snd_device_name>
                </custom-config>
''')
    mixer = (vendor/'etc/audio/sku_kalama/mixer_paths.xml').read_text()
    assert PATH not in mixer and mixer.count('</mixer>') == 1
    controls = ''.join(f'        <ctl name="TX {bank} MUX{i}" value="ZERO" />\n' for bank in ('DMIC','SMIC') for i in range(8))
    # Keep the four digital capture lanes clocked, sourcing silence rather
    # than referencing main-mic/multi-mic (which enable physical sources).
    controls += ''.join(f'        <ctl name="TX_AIF1_CAP Mixer DEC{i}" value="1" />\n' for i in range(4))
    mixer = mixer.replace('</mixer>', f'    <!-- Call Screening test: digital silence upstream of AI injection. -->\n    <path name="{PATH}">\n{controls}    </path>\n</mixer>')
    route = ET.fromstring(mixer).findall(f'./path[@name="{PATH}"]')
    assert len(route) == 1 and not route[0].findall('path')
    assert len(route[0].findall('ctl')) == 20
    new_voice = [u for d in ET.fromstring(rm).findall('./device_profile/in-device') if d.findtext('id') == 'PAL_DEVICE_IN_HANDSET_MIC' for u in d.findall('usecase') if u.findtext('name') == 'PAL_STREAM_VOICE_CALL'][0]
    assert new_voice.findtext(f'custom-config[@key="{KEY}"]/snd_device_name') == PATH
    return rm, mixer

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--ndk', type=Path, default=Path.home()/'Android/Sdk/ndk/28.2.13676358')
    a = p.parse_args()
    assert os.environ.get('SRC_DIR'), 'Source buildenv.sh dm3q first'
    out = a.output.resolve()
    assert out.is_relative_to(Path('/tmp')) and not out.exists(), 'Use a new /tmp output directory'
    fw = ROOT/'out/fw/SM-S918B_EUX'
    for rel, expected in PINS.items():
        assert sha(fw/rel) == expected, 'Unsupported source '+rel
    assert sha(fw/'system/system/lib64/libtinyalsa.so') == TINYALSA_SHA
    spec = importlib.util.spec_from_file_location('v4_builder', HERE.parent/'build_candidate.py')
    old = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(old)
    # Also hook stop so a stopped capture is not mistaken for a running one.
    functions = [*old.FUNCTIONS, 'stop']
    old.FUNCTIONS = functions
    hal, edits = old.patch_hal((fw/'vendor/lib64/hw/audio.primary.kalama.so').read_bytes())
    rm, mixer = patched_configs(fw/'vendor')
    module = out/'module'
    libs = module/'candidate/vendor/lib64'
    (libs/'hw').mkdir(parents=True)
    (libs/'hw/audio.primary.kalama.so').write_bytes(hal)
    config = module/'candidate'/CONFIG
    config.mkdir(parents=True)
    (config/'resourcemanager.xml').write_text(rm)
    (config/'mixer_paths.xml').write_text(mixer)
    compiler = a.ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android33-clang'
    flags = ['-std=c11','-D_GNU_SOURCE','-Wall','-Wextra','-Werror','-O2','-Wl,-z,relro,-z,now,-z,max-page-size=16384']
    definitions = ['-Dpal_stream_'+s+'=cs__stream_'+s for s in functions]
    subprocess.run([str(compiler),*flags,'-fPIC','-shared','-Wl,-soname,libcs-pal.so',*definitions,
                    str(HERE/'compat_tx.c'),str(HERE/'sources.c'),'-Wl,--no-as-needed',str(fw/'vendor/lib64/libar-pal.so'),
                    '-Wl,--as-needed','-Wl,--allow-shlib-undefined','-ldl','-llog','-o',str(libs/'libcs-pal.so')],check=True)
    subprocess.run([str(compiler),*flags,str(HERE/'probe.c'),str(HERE/'sources.c'),'-ldl','-o',str(module/'source_probe')],check=True)
    (module/'module.prop').write_text('''id=rezoss_callscreen_compat_tx_v6
name=Call Screening TX v6 isolation TEST
version=0.6-isolated-codec
versionCode=6
author=Rezoss
description=Inactive until arm_v6.sh. Dedicated ZERO-source route and kernel readback; audio repair unverified.
''')
    (module/'skip_mount').touch()
    (module/'stock.sha256').write_text(''.join(v+'  /'+k+'\n' for k,v in PINS.items())+TINYALSA_SHA+'  /system/lib64/libtinyalsa.so\n')
    for file in ('arm_v6.sh','action.sh','customize.sh'):
        shutil.copy2(HERE/file,module/file)
        (module/file).chmod(0o755)
    (module/'source_probe').chmod(0o755)
    payload = sorted(p for p in (module/'candidate').rglob('*') if p.is_file())
    (module/'payload.sha256').write_text(''.join(sha(p)+'  '+str(p.relative_to(module))+'\n' for p in payload))
    for unsafe in ('system','vendor','post-fs-data.sh','service.sh','metamount.sh','boot-completed.sh'):
        assert not (module/unsafe).exists()
    report = {
        'status':'INACTIVE HOST-TESTED CANDIDATE; NOT INSTALLED OR DEVICE-AUDIO-VERIFIED',
        'source_pins':PINS,'dynamic_string_edits':edits,
        'tinyalsa_sha256':TINYALSA_SHA,
        'payload':{str(p.relative_to(module)):sha(p) for p in payload},
        'source':{p.name:sha(p) for p in (HERE/'compat_tx.c',HERE/'sources.c',HERE/'build_v6.py')},
        'isolation':'Screening TX key selects dedicated codec source ZERO path; read 16 kernel enum controls before/after open/start/route and on mute/param',
        'not_a_mute_getter':'No pal_stream_get_mute or pal_get_mic_mute calls; HAL mute requests pass through',
        'remaining':['Actual HAL-domain tinyalsa control access','PAL honors dedicated key and silent four-lane route starts','Voice DSP graph startup','Caller hears AI and ASR works','Caller-side physical isolation test','Direct PAL users outside patched HAL are not covered by capture-admission hooks'],
    }
    (out/'build-report.json').write_text(json.dumps(report,indent=2)+'\n')
    archive = out/'rezoss-callscreen-compat-tx-v6-isolation.zip'
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED) as z:
        for file in sorted(module.rglob('*')):
            if file.is_file():z.write(file,file.relative_to(module))
    print(json.dumps({'module':str(archive),'sha256':sha(archive),'report':str(out/'build-report.json')},indent=2))

if __name__ == '__main__':
    main()
