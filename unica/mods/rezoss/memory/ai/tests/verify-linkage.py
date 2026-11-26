#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Check helper invokes against exact app declarations, including inherited APIs.

Arguments: final patched APK decode, disassembled helper classes5.dex.
Android/Java boot classes remain the platform verifier's responsibility.
"""
from pathlib import Path
import re
import sys

decoded, helpers = map(Path, sys.argv[1:])
files = {}
for root in [*decoded.glob('smali*'), helpers]:
    for path in root.rglob('*.smali'):
        files['L' + path.relative_to(root).as_posix()[:-6] + ';'] = path
cache = {}

def declaration(owner):
    if owner not in cache:
        if owner not in files:
            return None
        text = files[owner].read_text()
        parents = re.findall(r'^\.(?:super|implements) (L[^;]+;)', text, re.M)
        methods = {}
        for flags, name, proto in re.findall(r'^\.method ((?:[^\s(]+ )*)([^\s(]+)(\([^\n]+)', text, re.M):
            methods[name + proto] = 'static' in flags.split()
        fields = set(re.findall(r'^\.field (?:[^\s:]+ )*([^\s:]+:[^\s=]+)', text, re.M))
        cache[owner] = (parents, methods, fields, bool(re.search(r'^\.class .*\binterface\b', text, re.M)))
    return cache[owner]

def resolve(owner, member, field=False, seen=None):
    seen = set() if seen is None else seen
    if owner in seen:
        return None
    seen.add(owner)
    info = declaration(owner)
    if info is None:
        return None
    parents, methods, fields, interface = info
    if member in (fields if field else methods):
        return info
    if member.startswith('<init>'):
        return None
    for parent in parents:
        found = resolve(parent, member, field, seen)
        if found is not None:
            return found
    return None

checks = 0
errors = []
boot = ('Landroid/', 'Ljava/', 'Ljavax/', 'Lorg/json/')
for path in helpers.rglob('*.smali'):
    text = path.read_text()
    for kind, owner, member in re.findall(
            r'^\s+invoke-(\w+)(?:/range)? \{[^}]*\}, (L[^;]+;)->([^\n]+)', text, re.M):
        if owner.startswith(boot):
            continue
        if owner not in files:
            errors.append(f'{path.name}: missing class {owner}')
            continue
        checks += 1
        found = resolve(owner, member)
        # These may be inherited directly from the boot Object class.
        if found is None and member in ('toString()Ljava/lang/String;', 'hashCode()I', 'equals(Ljava/lang/Object;)Z'):
            continue
        if found is None and member == 'name()Ljava/lang/String;' and 'Ljava/lang/Enum;' in declaration(owner)[0]:
            continue
        if found is None:
            errors.append(f'{path.name}: missing method {owner}->{member}')
            continue
        if kind == 'static' and not found[1][member]:
            errors.append(f'{path.name}: static invoke of instance method {owner}->{member}')
        if kind == 'interface' and not declaration(owner)[3]:
            errors.append(f'{path.name}: interface invoke of concrete class {owner}')
    for owner, field in re.findall(r'^\s+[si](?:get|put)[\w/-]* [^\n]*?, (L[^;]+;)->([^\n]+)', text, re.M):
        if owner.startswith(boot):
            continue
        checks += 1
        if resolve(owner, field, field=True) is None:
            errors.append(f'{path.name}: missing field {owner}->{field}')
if errors:
    raise SystemExit('\n'.join(sorted(set(errors))))
print(f'PASS: {checks} helper app-method/field linkage checks against exact patched .48 declarations')
