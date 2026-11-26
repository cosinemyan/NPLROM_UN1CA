#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""The SDHMS field/method ABI and persistent startup path were inspected here."""
import hashlib
from pathlib import Path
import sys

EXPECTED = '0d3ecbe93aa269bd81fb23500ce53a316f39484187d6f085456499c285a098fe'
path = Path(sys.argv[1])
if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != EXPECTED:
    raise SystemExit('Relax SDHMS: uninspected APK; review the policy ABI and persistent restart path before enabling')
print('Relax SDHMS: inspected S23U APK confirmed')
