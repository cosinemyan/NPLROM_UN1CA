#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Resolve known AOD obfuscation drift and preflight the ordered patch stack."""
import argparse
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path


def unique(values, description):
    if len(values) != 1:
        raise ValueError(f'{description}: expected one match, found {len(values)}')
    return values[0]


def adapt_startup_patch(content, decoded):
    provider = unique(list(decoded.glob(
        'smali*/com/samsung/android/app/aodservice/provider/AODContentProvider.smali')),
        'AODContentProvider')
    text = provider.read_text()
    method = unique(re.findall(
        r'^\.method public final onCreate\(\)Z\n(.*?)^\.end method',
        text, re.M | re.S), 'AODContentProvider.onCreate method')
    if not method.startswith('    .locals 2\n\n'):
        raise ValueError('AODContentProvider.onCreate register layout changed')
    owner = 'Lcom/samsung/android/app/aodservice/provider/AODContentProvider;'
    manager = 'Lcom/samsung/android/app/aodservice/common/provider/DB/DBManager;'
    adapter = 'Lcom/samsung/android/app/aodservice/common/provider/DB/DBAdapter;'
    # Require the complete context/DB initialization sequence: v1 must still
    # contain Context at the hook, and v0 must contain the adapter being stored.
    prefix = (
        '    invoke-virtual {p0}, Landroid/content/ContentProvider;->getContext()Landroid/content/Context;\n\n'
        '    move-result-object v1\n\n'
        f'    invoke-virtual {{v0, v1}}, {manager}->init(Landroid/content/Context;)V\n\n'
        f'    invoke-virtual {{v0}}, {manager}->getDBAdapter(){adapter}\n\n'
        '    move-result-object v0\n\n')
    field = unique(re.findall(
        re.escape(prefix) + r'    iput-object v0, p0, ' + re.escape(owner)
        + r'->([^\s:]+):' + re.escape(adapter)
        + r'\n\n    const/4 p0, 0x1\n\n    return p0\n', method),
        'AOD startup DB assignment with live Context in v1')
    unique(re.findall(r'^\.field [^\n]*\b' + re.escape(field + ':' + adapter)
                      + r'$', text, re.M), 'AOD DB adapter field declaration')
    pattern = (r'(?m)^     iput-object v0, p0, ' + re.escape(owner)
               + r'->[^\s:]+:' + re.escape(adapter) + r'$')
    unique(re.findall(pattern, content), 'startup patch DB assignment anchor')
    content = re.sub(pattern, lambda _: f'     iput-object v0, p0, {owner}->{field}:{adapter}', content)
    content = content.replace(
        'smali_classes2/com/samsung/android/app/aodservice/provider/AODContentProvider.smali',
        provider.relative_to(decoded).as_posix())
    return content


def prepare(decoded, templates, output, native_full=False):
    owners = []
    for path in decoded.glob('smali*/**/*.smali'):
        if path.parent.name != 'aod':
            continue
        text = path.read_text()
        if all(re.search(r'^\.field .*\b' + name + ':' + kind + r'$', text, re.M)
               for name, kind in [('sSystemFontMap', 'Ljava/util/HashMap;'),
                                  ('sIsSystemFontMapInitialized', 'Z'),
                                  ('sIsSystemFontListInitialized', 'Z')]):
            owners.append((path, text))
    owner, text = unique(owners, 'system font-map owner')
    descriptor = unique(re.findall(r'^\.class .* (L[^;]+;)$', text, re.M), 'font owner descriptor')
    # The hook uses v0/v1 and p0/p1; reject changed parameter/register layouts.
    unique(re.findall(r'\.method public static h\(Landroid/content/Context;Ljava/lang/String;\)Landroid/graphics/Typeface;\n    \.locals 6\n\n    sget-object v0, '
                      + re.escape(descriptor) + r'->sSystemFontMap:Ljava/util/HashMap;', text),
           'font lookup method entry')
    font_path = owner.relative_to(decoded).as_posix()
    provider = unique(list(decoded.glob('smali*/com/samsung/android/app/facewidget/utils/KeyguardClockProvider.smali')),
                      'KeyguardClockProvider')
    provider_text = provider.read_text()
    method = unique(re.findall(r'^\.method public final getAODClockType\(\)I\n(.*?)^\.end method',
                              provider_text, re.M | re.S), 'getAODClockType method')
    if not method.startswith('    .locals 4\n\n'):
        raise ValueError('getAODClockType register layout changed')
    unique(re.findall(r'^\.method public getLockClockType\(\)I$', provider_text, re.M),
           'lockscreen clock type getter')
    controller_type = unique(re.findall(
        r'invoke-virtual \{v0\}, Lcom/samsung/android/app/facewidget/FaceWidgetController;->b\(\)(Laod/[^;]+;)',
        method), 'AOD controller return type')
    patches = sorted(templates.glob('*.patch'))
    if len(patches) != 9:
        raise ValueError(f'Expected nine AOD patches, found {len(patches)}')
    if native_full:
        patches = [p for p in patches if p.name[:4] in ("0001", "0002", "0003", "0007")]
    generated = []
    for patch in patches:
        content = patch.read_text()
        content = content.replace('smali_classes3/aod/rl2.smali', font_path)
        content = content.replace('Laod/rl2;', descriptor)
        if patch.name.startswith('0003-'):
            content = adapt_startup_patch(content, decoded)
        if patch.name.startswith('0007-'):
            content = content.replace('Laod/x89;', controller_type)
            content = content.replace('smali/com/samsung/android/app/facewidget/utils/KeyguardClockProvider.smali',
                                      provider.relative_to(decoded).as_posix())
        generated.append((patch.name, content))
    # Never touch the build decode until every patch has passed in order.
    with tempfile.TemporaryDirectory(prefix='unica-aod-preflight-') as scratch:
        trial = Path(scratch) / 'decoded'
        shutil.copytree(decoded, trial)
        for name, content in generated:
            command = ['patch', '--batch', '--fuzz=0', '-p1', '-N', '--forward', '-l', '-d', str(trial)]
            for extra in (['--dry-run'], []):
                result = subprocess.run(command + extra, input=content, text=True,
                                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                        env={**os.environ, 'LC_ALL': 'C'})
                if result.returncode:
                    raise ValueError(f'{name} preflight failed:\n{result.stdout}')
            print(f'AOD preflight passed: {name}', flush=True)
    output.mkdir(parents=True, exist_ok=True)
    for name, content in generated:
        destination = output / name
        with destination.open('x') as handle:
            handle.write(content)
    print(f'AOD dynamic font owner: {descriptor} ({font_path})', flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('decoded', type=Path)
    parser.add_argument('templates', type=Path)
    parser.add_argument('output', type=Path)
    parser.add_argument('--native-full', action='store_true', help='Keep font-bank, identity synchronization and lockscreen style; native port owns normalization/widgets')
    args = parser.parse_args()
    try:
        prepare(args.decoded, args.templates, args.output, args.native_full)
    except (ValueError, OSError) as error:
        parser.exit(1, f'AOD adaptation stopped safely: {error}\n')


if __name__ == '__main__':
    main()
