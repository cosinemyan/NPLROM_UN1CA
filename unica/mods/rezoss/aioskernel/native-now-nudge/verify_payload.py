#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Verify complete payloads, including fresh-checkout numeric split chunks."""
import hashlib
import json
from pathlib import Path
import sys


def verify(root):
    for name, expected in json.loads((root / 'CONTENTS.json').read_text()).items():
        full = root / name
        parts = [full] if full.is_file() else sorted(full.parent.glob(full.name + '.[0-9][0-9]'))
        if not parts:
            raise ValueError(f'Missing payload: {name}')
        if parts != [full]:
            for index, part in enumerate(parts):
                if part.name != f'{full.name}.{index:02}':
                    raise ValueError(f'Missing or unordered chunk: {name}.{index:02}')
        digest = hashlib.sha256()
        size = 0
        for part in parts:
            with part.open('rb') as stream:
                while block := stream.read(1024 * 1024):
                    digest.update(block)
                    size += len(block)
        if size != expected['size'] or digest.hexdigest() != expected['sha256']:
            raise ValueError(f'Payload mismatch: {name}')
        print(f'{name}: OK ({size} bytes)')


if __name__ == '__main__':
    verify(Path(sys.argv[1]))
