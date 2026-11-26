#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Validate experimental inputs and stage Android flat module metadata."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import zlib

import abi


def digest(data):
    return hashlib.sha256(data).hexdigest()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def prepare(base, work, stage, backup):
    manifest = json.loads((base / "manifest.json").read_text())
    payload = base / "vendor_dlkm/lib/modules"
    target = work / "vendor_dlkm/lib/modules"
    names = list(manifest["modules"])
    module_names = {Path(name).stem for name in names}
    boot = (work / "kernel/boot.img").read_bytes()
    require(boot[:8] == b"ANDROID!", "Unsupported boot image magic")
    version = struct.unpack_from("<I", boot, 40)[0]
    require(version in (3, 4), "Expected Android boot header v3 or v4")
    size = struct.unpack_from("<I", boot, 8)[0]
    require(size > 0 and len(boot) >= 4096 + size, "Truncated kernel payload")
    kernel = boot[4096:4096 + size]
    if kernel[:2] == b"\x1f\x8b":
        kernel = gzip.decompress(kernel)
    for name, expected in manifest["modules"].items():
        require(digest((payload / name).read_bytes()) == expected,
                f"Candidate module checksum mismatch: {name}")
    for name, expected in manifest["stock_modules"].items():
        actual = digest((target / name).read_bytes())
        allowed = {expected}
        if name in manifest["modules"]:
            allowed.add(manifest["modules"][name])  # Repeat invocation is safe.
            # Permit replacing the exact earlier experimental payload in an
            # incremental working tree, without accepting other vendor drivers.
            allowed.update(manifest.get("superseded_modules", {}).get(name, []))
        require(actual in allowed, f"Unvalidated vendor dependency: {name}")
    for name in names:
        if (target / name).exists() and name not in manifest["stock_modules"]:
            allowed = {manifest["modules"][name]}
            allowed.update(manifest.get("superseded_modules", {}).get(name, []))
            require(digest((target / name).read_bytes()) in allowed,
                    f"Unexpected existing module: {name}")
    require(not list(target.glob("modules.*.bin")),
            "Binary module indices require regeneration; refusing stale indices")
    files = ("modules.dep", "modules.load", "modules.alias", "modules.softdep")
    metadata = {name: (target / name).read_text() for name in files}
    for line in (target / "modules.blocklist").read_text().splitlines():
        words = line.split()
        if words and words[0] == "blocklist" and len(words) > 1:
            require(words[1] not in module_names | {"cfg80211", "mac80211"},
                    f"Required module is blocklisted: {words[1]}")
    rows = {}
    for line in metadata["modules.dep"].splitlines():
        key, sep, value = line.partition(":")
        require(bool(sep) and key not in rows, "Malformed or duplicate modules.dep row")
        rows[key] = value.split()
    qca = "/vendor/lib/modules/qca_cld3_kiwi_v2.ko"
    provider = "/vendor/lib/modules/s23u_wonder_provider.ko"
    wonder = "/vendor/lib/modules/wonder.ko"
    require(qca in rows, "Stock Wi-Fi dependency row is missing")
    rows[qca] = list(dict.fromkeys([provider] + rows[qca]))
    rows[provider] = []
    rows[wonder] = [qca, "/vendor/lib/modules/mac80211.ko",
                    "/vendor/lib/modules/cfg80211.ko"]
    visited = set()

    def check_graph(key, active):
        require(key not in active, f"Cyclic module dependency: {key}")
        if key in visited:
            return
        require(key in rows, f"Missing dependency row: {key}")
        name = Path(key).name
        require(name in names or (target / name).is_file(),
                f"Missing module dependency: {key}")
        for dependency in rows[key]:
            check_graph(dependency, active | {key})
        visited.add(key)

    check_graph(wonder, set())
    # Validate the candidate stack and its complete loader dependency closure
    # against the actual selected kernel, independently of its patch release.
    abi.validate(kernel, manifest["kernel_abi"], {
        Path(key).name: ((payload if Path(key).name in names else target) /
                         Path(key).name).read_bytes()
        for key in sorted(visited)
    }, abi.vendor_boot_modules(work / "kernel/vendor_boot.img",
                               base.parent / "ksu-next/magiskboot"))
    metadata["modules.dep"] = "".join(
        f"{key}:" + (" " + " ".join(value) if value else "") + "\n"
        for key, value in rows.items())
    load = []
    inserted = False
    for line in metadata["modules.load"].splitlines():
        if Path(line.strip()).name in names:
            if not inserted:
                load.extend([Path(provider).name, Path(qca).name, Path(wonder).name])
                inserted = True
        else:
            load.append(line)
    require(inserted, "Stock Wi-Fi module is absent from modules.load")
    metadata["modules.load"] = "\n".join(load) + "\n"
    for filename, field in (("modules.alias", "alias"), ("modules.softdep", "softdep")):
        lines = []
        for line in metadata[filename].splitlines():
            words = line.split()
            owner = (words[-1] if field == "alias" else words[1]) if len(words) > 1 else ""
            if owner not in module_names:
                lines.append(line)
        for name in names:
            values = subprocess.check_output(
                ["modinfo", "-F", field, str(payload / name)], text=True).splitlines()
            for value in values:
                if value:
                    owner = Path(name).stem
                    lines.append(f"alias {value} {owner}" if field == "alias"
                                 else f"softdep {owner} {value}")
        metadata[filename] = "\n".join(lines) + "\n"
    # All input and graph validation precedes backup or staging writes.
    original = manifest["stock_modules"][Path(qca).name]
    if digest((target / Path(qca).name).read_bytes()) == original:
        backup.mkdir(parents=True, exist_ok=True)
        saved = Path(tempfile.mkdtemp(prefix="stock-", dir=backup))
        for name in (*files, Path(qca).name):
            shutil.copy2(target / name, saved / name)
        (saved / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        print(f"Stock Wi-Fi and loader metadata saved: {saved}")
    destination = stage / "vendor_dlkm/lib/modules"
    destination.mkdir(parents=True, exist_ok=True)
    for name in names:
        shutil.copy2(payload / name, destination / name)
    for name, content in metadata.items():
        (destination / name).write_text(content)
    print("Validated kernel ABI, candidate hashes, stock dependencies and loader graph")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for option in ("base", "work", "stage", "backup"):
        parser.add_argument("--" + option, type=Path, required=True)
    args = parser.parse_args()
    try:
        prepare(args.base, args.work, args.stage, args.backup)
    except (ValueError, OSError, EOFError, struct.error, zlib.error,
            subprocess.CalledProcessError) as error:
        parser.exit(1, f"Wondertap validation failed: {error}\n")
