#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Reject changed native chord semantics or a previously applied Memory hook."""
import hashlib
from pathlib import Path
import sys

root = Path(sys.argv[1])
manifest = Path(__file__).with_name("targets.sha256")
for line in manifest.read_text().splitlines():
    expected, name = line.split(maxsplit=1)
    path = root / name
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise SystemExit("Memory side-key native policy changed; revalidate before applying: " + name)
print("PASS: exact Samsung chord detector, rule and local handler match validated firmware")
