#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Route the existing entry only when the enabled dm3q ROM TEE mod installs its APK."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

OLD = 'android:targetPackage="com.android.settings" android:targetClass="io.mesalabs.unica.keybox.KeyboxActivity"'
NEW = 'android:targetPackage="io.mesalabs.unica.teesettings" android:targetClass="io.mesalabs.unica.keybox.RomSettingsActivity"'
# Never publish an intent to a package Samsung will refuse to register.
from preload import verify
if len(sys.argv) != 3:
    raise SystemExit('Expected Settings XML and ROM work directory')
verify(Path(sys.argv[2]))
work = Path(sys.argv[2])
for relative in (
    'system/system/priv-app/TeeSimulatorSettings/TeeSimulatorSettings.apk',
    'system/system/etc/permissions/privapp-permissions-io.mesalabs.unica.teesettings.xml',
    'system/system/etc/sysconfig/preinstalled-packages-io.mesalabs.unica.teesettings.xml',
):
    if not (work / relative).is_file():
        raise SystemExit('Missing TEE Settings install dependency: ' + relative)
path = Path(sys.argv[1])
text = path.read_text()
ET.fromstring(text)
if text.count(NEW) == 1 and OLD not in text:
    raise SystemExit(0)
if text.count(OLD) != 1 or NEW in text:
    raise SystemExit('Unexpected Manage Keybox intent; refusing ambiguous routing')
path.write_text(text.replace(OLD, NEW))
