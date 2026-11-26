#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Emit a narrow provider patch. Never modifies the supplied APK decode."""
import argparse
import difflib
from pathlib import Path
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("manifest", type=Path)
parser.add_argument("output", type=Path)
args = parser.parse_args()
text = args.manifest.read_text()
root = ET.fromstring(text)
if root.tag != "manifest" or root.attrib.get("package") != "com.samsung.android.app.dressroom":
    raise SystemExit("Wrong native provider host")
authority = "io.mesalabs.unica.dressroomadvanced.settings"
if authority in text or "dev.denver.dressroomadvanced.SettingsProvider" in text:
    raise SystemExit("Provider already exists; inspect before regenerating")
if text.count("</application>") != 1:
    raise SystemExit("Expected one application closing tag")
provider = ('    <provider android:name="dev.denver.dressroomadvanced.SettingsProvider" '
            'android:authorities="' + authority + '" android:exported="true" '
            'android:grantUriPermissions="false" />\n')
new = text.replace("</application>", provider + "</application>")
ET.fromstring(new)
if args.output.exists():
    raise SystemExit("Output already exists; preserve previous patch first")
args.output.write_text("AUTHOR BlueOutside, Rezoss\nSubject: [PATCH] Host native Dressroom Advanced settings in DressRoom\n\n" + "".join(
    difflib.unified_diff(text.splitlines(True), new.splitlines(True),
                         fromfile="a/AndroidManifest.xml", tofile="b/AndroidManifest.xml")))
