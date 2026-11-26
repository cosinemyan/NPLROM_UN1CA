#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Add the Samsung scan allowlist entry without replacing other ROM entries."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

PACKAGE = 'io.mesalabs.unica.teesettings'
RELATIVE = 'system/system/etc/sysconfig/allowed-system-preload-apps.xml'

def verify(work):
    path = Path(work) / RELATIVE
    root = ET.fromstring(path.read_text())
    if root.tag != 'config' or len(root.findall(f"allowed-system-preload[@package='{PACKAGE}']")) != 1:
        raise ValueError('TEE Settings missing or duplicated in Samsung preload allowlist')

def install(work):
    path = Path(work) / RELATIVE
    text = path.read_text()
    root = ET.fromstring(text)
    if root.tag != 'config':
        raise ValueError('Unexpected Samsung preload allowlist root')
    entries = root.findall(f"allowed-system-preload[@package='{PACKAGE}']")
    if len(entries) > 1:
        raise ValueError('Duplicate TEE Settings preload entries')
    if not entries:
        if text.count('</config>') != 1:
            raise ValueError('Unexpected Samsung preload allowlist structure')
        text = text.replace('</config>', f'    <allowed-system-preload package="{PACKAGE}"/>\n</config>')
        ET.fromstring(text)
        path.write_text(text)
    verify(work)

if __name__ == '__main__':
    install(sys.argv[1])
