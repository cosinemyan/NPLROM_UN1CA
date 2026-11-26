#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Append the mod's policy to the generated system_ext policy through customize.sh."""
from pathlib import Path
import argparse


def append_unique(path, lines):
    original = path.read_text()
    present = set(original.splitlines())
    extra = [line for line in lines if line and not line.startswith(';') and line not in present]
    if extra:
        path.write_text(original.rstrip() + '\n' + '\n'.join(extra) + '\n')


def install(work, mod):
    selinux = work / 'system_ext/etc/selinux'
    if not selinux.is_dir():
        selinux = work / 'system/system/system_ext/etc/selinux'
    policy = selinux / 'system_ext_sepolicy.cil'
    contexts = selinux / 'system_ext_file_contexts'
    props = selinux / 'system_ext_property_contexts'
    if not all(p.is_file() for p in (policy, contexts, props)):
        raise SystemExit('TEE Simulator requires system_ext CIL, file and property contexts')
    append_unique(policy, (mod / 'policy/unica-teesim.cil').read_text().splitlines())
    append_unique(contexts, [r'/data/misc/unica-teesim(/.*)? u:object_r:unica_teesim_data_file:s0'])
    append_unique(props, [f'sys.unica.teesim.{name} u:object_r:unica_teesim_status_prop:s0 exact int'
                          for name in ('ready', 'heartbeat', 'connected', 'ack', 'loaded', 'config_error')])
    services = selinux / 'system_ext_service_contexts'
    if not services.exists():
        services.touch()
    append_unique(services, ['unica.teesim u:object_r:unica_teesim_service:s0'])
    # Prevent init selecting an old precompiled policy after adding the new domain. Change the
    # platform digest (same method Android uses to invalidate the vendor precompiled cache).
    import hashlib
    digest = hashlib.sha256(policy.read_bytes()).hexdigest() + '\n'
    (selinux / 'system_ext_sepolicy_and_mapping.sha256').write_text(digest)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('work', type=Path)
    parser.add_argument('mod', type=Path)
    args = parser.parse_args()
    install(args.work, args.mod)
