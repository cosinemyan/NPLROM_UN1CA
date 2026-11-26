#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Hide the Settings entry when its device-specific backing mod is disabled."""
from pathlib import Path
import re
import sys

def configure(path, active):
    if path.is_dir():
        for menu in sorted(path.glob('unica_*.xml')):
            configure(menu, active)
        return
    if active:
        return
    old = path.read_text()
    pattern = (r'    <io\.mesalabs\.unica\.settings\.preference\.IconPreference[^>]*'
               r'android:key="unica_power_schedule"[^>]*>\s*'
               r'<intent[^>]*/>\s*</io\.mesalabs\.unica\.settings\.preference\.IconPreference>\n')
    new, count = re.subn(pattern, '', old)
    if count > 1 or ('android:key="unica_power_schedule"' in new):
        raise ValueError('Unexpected Power schedule preference structure')
    if old != new:
        path.write_text(new)

if __name__ == '__main__':
    root = Path(__file__).resolve().parent
    active = sys.argv[2] == 'dm3q' and not (root / 'disable').exists() and not (root.parent / 'disable').exists()
    configure(Path(sys.argv[1]), active)
