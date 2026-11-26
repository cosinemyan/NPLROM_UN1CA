#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve original APK entries; replace gesture DEX and append the helper DEX."""
from pathlib import Path
import copy
import sys
import zipfile

def package(source, dex3, dex6, output):
    replacements = {"classes3.dex": dex3.read_bytes(), "classes6.dex": dex6.read_bytes()}
    for name, data in replacements.items():
        if len(data) < 112 or not data.startswith(b"dex\n"):
            raise ValueError(f"Invalid DEX input: {name}")
    if output.exists():
        raise ValueError(f"Refusing to overwrite: {output}")
    with zipfile.ZipFile(source) as src:
        if "classes6.dex" in src.namelist() or "classes3.dex" not in src.namelist():
            raise ValueError("Unexpected launcher DEX layout")
        with zipfile.ZipFile(output, "w") as dst:
            for info in src.infolist():
                # Old signing metadata cannot describe the replaced DEX. signapk supplies it.
                if info.filename.startswith("META-INF/") and info.filename.upper().endswith((".RSA", ".DSA", ".EC", ".SF", "MANIFEST.MF")):
                    continue
                # ZipFile.writestr updates header_offset; do not mutate src's ZipInfo.
                dst.writestr(copy.copy(info), replacements.get(info.filename, src.read(info)))
            dst.writestr("classes6.dex", replacements["classes6.dex"], compress_type=zipfile.ZIP_STORED)
        with zipfile.ZipFile(output) as dst:
            for info in src.infolist():
                if info.filename == "classes3.dex" or info.filename.startswith("META-INF/"):
                    continue
                if src.read(info) != dst.read(info.filename):
                    raise ValueError(f"Unrelated APK entry changed: {info.filename}")

if __name__ == "__main__":
    if len(sys.argv) != 5:
        raise SystemExit("Usage: package-dex.py ORIGINAL.apk CLASSES3.dex CLASSES6.dex OUTPUT.apk")
    package(*map(Path, sys.argv[1:]))
