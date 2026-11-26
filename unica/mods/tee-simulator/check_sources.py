#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Check the complete backend input snapshot, including added/deleted files."""
import hashlib
from pathlib import Path


def current(root):
    sources = sorted(p for p in (root / 'source').rglob('*') if p.is_file())
    sources += [root / name for name in (
        'build_backend.py', 'upstream.lock.json', 'jvm-dependencies.sha256.json')]
    return ''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.relative_to(root)}\n'
                   for p in sources)


def matches(root):
    try:
        return current(root) == (root / 'source.sha256').read_text()
    except OSError:
        return False


if __name__ == '__main__':
    raise SystemExit(0 if matches(Path(__file__).resolve().parent) else 1)
