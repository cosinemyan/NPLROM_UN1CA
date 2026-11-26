# SPDX-License-Identifier: GPL-3.0-or-later
"""Exact-firmware speaker transformation from Speaker-profile-mod.txt.

All three stages run in memory; inputs are never modified.
Critical guards remain enabled under python -O.
"""
"""Hold each speaker/content class at its original 26% profile; retain compressors."""
from pathlib import Path
import hashlib
import json
import struct
profile_ORIGINAL = '6edeb2dfdb96baa553093f236ecb79909d674e855d9c1ebdaa6878fa48eb6e6f'

def profile_generate(inputs):
    p = inputs['vendor/etc/SoundBoosterParam.txt']
    if not hashlib.sha256(p).hexdigest() == profile_ORIGINAL:
        raise ValueError('Unexpected reference tuning')
    lines = p.decode().splitlines(keepends=True)
    rows = {l.strip().split(',')[0]: l.strip().split(',') for l in lines if ',' in l}
    result = []
    changes = []
    for line in lines:
        a = line.strip().split(',')
        b = a[:]
        key = a[0]
        if len(key) == 3 and key[0] in 'ABCD' and (key[1] in 'HABCDMPVK') and (key[2] in 'BCDE'):
            ref = rows[key[:2] + 'A']
            if not len(a) == len(ref):
                raise ValueError('Speaker payload validation failed')
            start = 2 if key[1] in 'MPVK' else 1
            b[start:] = ref[start:]
        if key in ['THC', 'FMG']:
            if not len(a) == 11:
                raise ValueError('Speaker payload validation failed')
            b[1:6] = [a[1]] * 5
            b[6:11] = [a[6]] * 5
        for i in range(1, len(a)):
            if a[i] != b[i]:
                changes.append({'row': key, 'field': i, 'before': a[i], 'after': b[i]})
        result.append(','.join(b) + ('\n' if line.endswith('\n') else '') if a != b else line)
    data = ''.join(result).encode()
    return (data, {'original_sha256': profile_ORIGINAL, 'sha256': hashlib.sha256(data).hexdigest(), 'reference': 'Original profileA, native indices0..5 including26%', 'compressors': 'Kept at original reference settings for each channel/content class', 'changes': changes})

"""Disable only legacy pre/MB/OB compression; retain adaptive mode1 and fixed EQ.
The diagnostic-only tuning-path redirect is never included in ROM libraries.
"""
legacy_BASE = {'lib64': ('0f0b8d7e155717a614c3be7c87c1f0174a3abbf40764f38f8dedee3ebb5dd16b', 103832, '612248b9', 'e1031f2a'), 'lib': ('0f91016fe6739cde00fd9bd436adb2c4bacc9bdca30ea7d58d55de3bbce3a72e', 95260, '1c1895e5', '0010a0e3')}
legacy_PARAM_SHA = '3b9e2c8a5ddf888aadcf606d9a583d021b9a1efd639b27950d008229e2f79523'
legacy_sha = lambda b: hashlib.sha256(b).hexdigest()

def legacy_patch(data, abi):
    expected, addr, old, new = legacy_BASE[abi]
    if not legacy_sha(data) == expected:
        raise ValueError((abi, 'unexpected source'))
    b = bytearray(data)
    if not (b[:4] == b'\x7fELF' and b[5] == 1):
        raise ValueError('Speaker payload validation failed')
    if b[4] == 2:
        phoff = struct.unpack_from('<Q', b, 32)[0]
        entsize, n = struct.unpack_from('<HH', b, 54)
    else:
        phoff = struct.unpack_from('<I', b, 28)[0]
        entsize, n = struct.unpack_from('<HH', b, 42)
    offsets = []
    for i in range(n):
        if b[4] == 2:
            typ, flags, off, va, pa, size, mem, align = struct.unpack_from('<IIQQQQQQ', b, phoff + i * entsize)
        else:
            typ, off, va, pa, size, mem, flags, align = struct.unpack_from('<IIIIIIII', b, phoff + i * entsize)
        if typ == 1 and va <= addr and (addr + 4 <= va + size):
            offsets.append(off + addr - va)
    if not len(offsets) == 1:
        raise ValueError('Speaker payload validation failed')
    off = offsets[0]
    if not b[off:off + 4] == bytes.fromhex(old):
        raise ValueError('Speaker payload validation failed')
    b[off:off + 4] = bytes.fromhex(new)
    if not b.count(b'/vendor/etc/SoundBoosterParam.txt\x00') == 1:
        raise ValueError('Speaker payload validation failed')
    if not b'/data/local/tmp/' not in b:
        raise ValueError('Speaker payload validation failed')
    return (bytes(b), {'address': hex(addr), 'offset': off, 'before': old, 'after': new})

def legacy_generate(inputs):
    payload = {}
    report = {'scope': 'dm3q legacy mode2 only; adaptive mode1 retained', 'files': {}, 'fields': [], 'binary_patches': {}}
    for abi in legacy_BASE:
        raw = inputs[f'system/system/{abi}/lib_SoundBooster_ver1100.so']
        data, change = legacy_patch(raw, abi)
        report['binary_patches'][abi] = change
        for partition in ['system/system', 'vendor']:
            rel = f'{partition}/{abi}/lib_SoundBooster_ver1100.so'
            payload[rel] = data
            report['files'][rel] = {'before': legacy_sha(raw), 'after': legacy_sha(data)}
    raw = inputs['vendor/etc/SoundBoosterParam.txt']
    if not legacy_sha(raw) == legacy_PARAM_SHA:
        raise ValueError('Speaker payload validation failed')
    lines = raw.decode().splitlines(keepends=True)
    for i, line in enumerate(lines):
        v = line.rstrip('\r\n').split(',')
        if v[0] in ['AL', 'BL']:
            for j in [1, 52]:
                if not v[j] == '1':
                    raise ValueError('Speaker payload validation failed')
                report['fields'].append({'record': v[0], 'csv_index': j, 'before': 1, 'after': 0})
                v[j] = '0'
            lines[i] = ','.join(v) + line[len(line.rstrip('\r\n')):]
    if not len(report['fields']) == 4:
        raise ValueError('Speaker payload validation failed')
    rel = 'vendor/etc/SoundBoosterParam.txt'
    data = ''.join(lines).encode()
    payload[rel] = data
    report['files'][rel] = {'before': legacy_sha(raw), 'after': legacy_sha(data)}
    return (payload, report)

"""Hash-guarded speaker stream routing. No engine/tuning/amplifier changes."""
streams_sha = lambda b: hashlib.sha256(b).hexdigest()
streams_BASE = {'libcorefx.so': 'c3d11e18a053fd48257fdbb9235120079956a65d2bc62e8db63f09c3b52098f4', 'stage_policy.conf': '40b388a8b0726f70a0f09d8e028d45cfa5922e76b4bb407981977c75dcb9a51c'}
streams_OLD = bytes.fromhex('097043798a20805208b441b93f010a6aa00000541f0100716800805208019f1a0500001468001837480080520200001428008052')
streams_NEW = bytes.fromhex('08b441b968010035097043798a2080523f010a6a48008052690080522811881a050000141f2003d51f2003d51f2003d528008052')
streams_STREAMS = ['VOICE_CALL', 'SYSTEM', 'RING', 'MUSIC', 'ALARM', 'NOTIFICATION', 'BLUETOOTH_SCO', 'ENFORCED_AUDIBLE', 'DTMF', 'TTS', 'ACCESSIBILITY', 'ASSISTANT']
streams_PATHS = ['primary', 'media', 'low', 'low_media', 'raw', 'fast_media', 'fastmixer']

class streams_Node:

    def __init__(self, name, parent=None, start=0):
        self.name = name
        self.parent = parent
        self.start = start
        self.end = 0
        self.children = []
        self.fields = {}

    def child(self, name):
        return next((x for x in self.children if x.name == name))

def streams_parse(s):
    root = streams_Node('root')
    n = root
    for i, l in enumerate(s.splitlines(True)):
        q = l.strip()
        if not q or q.startswith('#'):
            continue
        if q.endswith('{'):
            c = streams_Node(q[:-1].strip(), n, i)
            n.children.append(c)
            n = c
        elif q == '}':
            n.end = i
            n = n.parent
        else:
            k, v = q.split(None, 1)
            if not k not in n.fields:
                raise ValueError((n.name, k))
            n.fields[k] = (v, i)
    if not n is root:
        raise ValueError('Speaker payload validation failed')
    return root

def streams_generate(inputs):
    b = {'libcorefx.so': inputs['system/system/lib64/libcorefx.so'], 'stage_policy.conf': inputs['system/system/etc/stage_policy.conf']}
    for k, v in b.items():
        if not streams_sha(v) == streams_BASE[k]:
            raise ValueError(k)
    lib = bytearray(b['libcorefx.so'])
    if not lib[232972:233024] == streams_OLD:
        raise ValueError('Speaker payload validation failed')
    lib[232972:233024] = streams_NEW
    if not len(lib) == len(b['libcorefx.so']):
        raise ValueError('Speaker payload validation failed')
    lines = b['stage_policy.conf'].decode().splitlines(True)
    tree = streams_parse(''.join(lines))
    edits = []
    coverage = []
    for path in streams_PATHS:
        output = tree.child(path).child('output')
        if not output.fields['opendevice'][0] == 'AUDIO_DEVICE_OUT_SPEAKER':
            raise ValueError('Speaker payload validation failed')
        normal = output.child('main').child('normal')
        if not (normal.fields['samplerate'][0] == '48000' and normal.fields['channel'][0] == 'AUDIO_CHANNEL_OUT_STEREO'):
            raise ValueError('Speaker payload validation failed')
        music = normal.child('sub1')
        if 'integ-post' in music.fields:
            value, i = music.fields['integ-post']
            if 'STAGE_EFFECT_SOUNDBOOSTER' not in value:
                value = ('' if value == 'STAGE_EFFECT_NONE' else value + '|') + 'STAGE_EFFECT_SOUNDBOOSTER:lazy'
                edits.append((i, i + 1, '                    integ-post ' + value + '\n'))
        else:
            edits.append((music.end, music.end, '                    integ-post STAGE_EFFECT_SOUNDBOOSTER:lazy\n'))
        if path == 'primary':
            edits.append((music.start, music.start + 1, '                speaker_music26 {\n'))
        block = ''
        for sid, name in enumerate(streams_STREAMS):
            if name == 'MUSIC':
                continue
            block += f'                speaker_{name.lower()}26 {{\n                    streamtype AUDIO_STREAM_{name}\n                    prefx STAGE_EFFECT_NONE\n                    postfx STAGE_EFFECT_NONE\n                    integ-post STAGE_EFFECT_SOUNDBOOSTER:lazy\n                }}\n'
        edits.append((normal.end, normal.end, block))
        for child in normal.children:
            if child.name == 'mono_tts':
                value, i = child.fields['postfx']
                if not value == 'STAGE_EFFECT_SOUNDBOOSTER:lazy':
                    raise ValueError('Speaker payload validation failed')
                edits.append((i, i + 1, '                    postfx STAGE_EFFECT_NONE\n                    integ-post STAGE_EFFECT_SOUNDBOOSTER:lazy\n'))
        coverage.append({'path': path, 'streams': streams_STREAMS, 'rate': 48000, 'channels': 2, 'per_stream_volume': True})
    for start, end, text in sorted(edits, reverse=True):
        lines[start:end] = [text]
    policy = ''.join(lines)
    newtree = streams_parse(policy)
    for path in streams_PATHS:
        normal = newtree.child(path).child('output').child('main').child('normal')
        for sid, name in enumerate(streams_STREAMS):
            nodes = [x for x in normal.children if x.fields.get('streamtype', ('',))[0] == 'AUDIO_STREAM_' + name and x.name != 'mono_tts']
            if not len(nodes) == 1:
                raise ValueError((path, name, len(nodes)))
            if not nodes[0].fields['integ-post'][0].count('STAGE_EFFECT_SOUNDBOOSTER') == 1:
                raise ValueError('Speaker payload validation failed')
    for old in tree.children:
        if old.name not in streams_PATHS:
            nn = newtree.child(old.name)
            if not ''.join(b['stage_policy.conf'].decode().splitlines(True)[old.start:old.end + 1]) == ''.join(policy.splitlines(True)[nn.start:nn.end + 1]):
                raise ValueError(old.name)
    payload = {'system/system/lib64/libcorefx.so': bytes(lib), 'system/system/etc/stage_policy.conf': policy.encode()}
    report = {'revision': 'speaker-streams26-v1', 'scope': '12 Android stream types on seven stereo speaker mixer routes, separate integrated volume domains', 'coverage': coverage, 'mode_selector': {'offset': '0x38e0c', 'old': streams_OLD.hex(), 'new': streams_NEW.hex(), 'semantics': 'nonzero stream mask selects adaptive mode1; zero-mask parent retains original fast mode3/nonfast mode2'}, 'untouched_special_routes': ['offload DSP/proxy', 'haptic channels', 'quad immersive', 'UHQ/SUHQ dedicated branches', 'headphones/external outputs', 'input/recording'], 'files': {p: {'old_sha256': streams_BASE[Path(p).name], 'sha256': streams_sha(v), 'bytes': len(v)} for p, v in payload.items()}, 'engine_tuning': 'unchanged adaptive26 and legacy-off; no diagnostic path redirects in production'}
    return (payload, report)

# Hashes refer to the merged build inputs, after paradigm's donor imports.
SOURCE = {
    'vendor/etc/SoundBoosterParam.txt': profile_ORIGINAL,
    'system/system/lib64/libcorefx.so': streams_BASE['libcorefx.so'],
    'system/system/etc/stage_policy.conf': streams_BASE['stage_policy.conf'],
    **{f'{partition}/{abi}/lib_SoundBooster_ver1100.so': values[0]
       for partition in ['system/system', 'vendor'] for abi, values in legacy_BASE.items()},
}
FINAL = {
    'vendor/etc/SoundBoosterParam.txt': '2b7784a6917d878566bda49dfce59272e560ec7a4917c80398b6be49db46ef3b',
    'system/system/lib64/libcorefx.so': '1853960137540bf84749fe4eb707fbd8d4d689460601abce79f1a0132e0513cc',
    'system/system/etc/stage_policy.conf': '253b44d6bd3d599febe24d4c42a87111c8e17edc4e50fe6c6f8f46d9b4dd1e9c',
    **{f'{partition}/{abi}/lib_SoundBooster_ver1100.so': digest
       for partition in ['system/system', 'vendor'] for abi, digest in [
           ('lib', '8229a9bb6b9a09deaabd257bcaca06918c02a1ba4b27d36168d1e24005abe974'),
           ('lib64', 'bc1de927a68a79678d2496bdf951ea75e6fc6d037b2705f256758854e0dc490f')]},
}
SYSTEM_ROOT = 'system/system/etc/unica/portrom-audio'
VENDOR_ROOT = 'vendor/etc/unica/portrom-audio'
POLICY = 'system/system/etc/selinux/plat_sepolicy.cil'
RULES = [f'(allow init {label} (file (mounton)))' for label in
         ['system_file', 'system_lib_file', 'vendor_file', 'vendor_configs_file']]


def runtime_path(relative):
    return '/' + relative.removeprefix('system/') if relative.startswith('system/system/') else '/' + relative


def overlay_path(relative):
    root = SYSTEM_ROOT if relative.startswith('system/') else VENDOR_ROOT
    return root + '/speaker/' + relative.removeprefix('system/system/').removeprefix('vendor/')


def generate(work):
    inputs = {p: (work / p).read_bytes() for p in SOURCE}
    for path, digest in SOURCE.items():
        if hashlib.sha256(inputs[path]).hexdigest() != digest:
            raise ValueError('Unexpected speaker baseline: ' + path)
    tuning, profile_report = profile_generate(inputs)
    inputs['vendor/etc/SoundBoosterParam.txt'] = tuning
    legacy, legacy_report = legacy_generate(inputs)
    inputs.update(legacy)
    streams, stream_report = streams_generate(inputs)
    payload = {**legacy, **streams}
    if {p: hashlib.sha256(b).hexdigest() for p, b in payload.items()} != FINAL:
        raise ValueError('Final speaker payload differs from reviewed guideline')
    return payload, {'profile': profile_report, 'legacy': legacy_report, 'streams': stream_report}


def prepare(work, stage):
    payload, reports = generate(work)
    for relative, data in payload.items():
        dest = stage / overlay_path(relative)
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
    report = {'original': SOURCE, 'modified': FINAL, 'stages': reports}
    dest = stage / SYSTEM_ROOT
    (dest / 'speaker-changes.json').write_text(json.dumps(report, indent=2) + '\n')
    # Settings checks all live files before showing the combined selection active.
    (dest / 'speaker-hashes.txt').write_text(''.join(
        f'{digest} {runtime_path(path)}\n' for path, digest in sorted(FINAL.items())))
    policy = (work / POLICY).read_text()
    for rule in RULES:
        if rule not in policy:
            policy += '\n; PortROM boot-selected speaker file overlays\n' + rule + '\n'
    dest = stage / POLICY
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(policy)
    (dest.parent / 'plat_sepolicy_and_mapping.sha256').write_text(
        hashlib.sha256(policy.encode()).hexdigest() + '\n')
    # Generate init's seven mounts from the same paths as the payload manifest.
    mounts = ''.join(f'    mount none {runtime_path(overlay_path(p))} {runtime_path(p)} bind\n'
                     for p in sorted(FINAL))
    rc = ('# SPDX-License-Identifier: GPL-3.0-or-later\n'
          '# Boot only: never mount or unmount in response to a live toggle.\n'
          'on load_persist_props_action && property:persist.sys.unica.speaker_profile=true\n'
          + mounts + '\non load_persist_props_action && property:persist.sys.unica.portrom_audio=true\n'
          '    mount none /system/etc/unica/portrom-audio/audio /system/media/audio bind\n')
    dest = stage / 'system/system/etc/init/unica_portrom_audio.rc'
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(rc)


def label(relative):
    if '/speaker/' in relative and relative.endswith('.so'):
        return 'vendor_file' if relative.startswith('vendor/') else 'system_lib_file'
    if relative.endswith('/SoundBoosterParam.txt'):
        return 'vendor_configs_file'
    return 'vendor_file' if relative.startswith('vendor/') else 'system_file'
