#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Package the verified 0.1-test1 binary bundle without rebuilding native code."""
import argparse
import hashlib
import stat
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

EXPECTED = {
    "libfancam_bridge.so": "2ebd6d94c9c2f42d8fab760e175ec2b24b1a663ac0d93cd477d62f367412c26a",
    "libmediacontextanalyzer.so": "3bb6c3e63d2108e86f6c213c77280577e1040ec99f47d48f18d7da5d4ddcce07",
    "mca_probe": "b185c4502b4da392bf69fcbe5f011cb57777b376409bcd6ef6340edfb986661a",
}

def package(bundle, donor, destination):
    payload = {}
    for name, expected in EXPECTED.items():
        data = (bundle / name).read_bytes()
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError(f"Unexpected {name}; requires the tested binary bundle")
        target = "probe/" if name == "mca_probe" else "system/lib64/"
        payload[target + name] = data
    report = (bundle / "host-test.txt").read_bytes()
    if b"PASS:" not in report:
        raise ValueError("Missing successful host test report")
    payload["validation/host-test.txt"] = report
    payload["system/etc/fancam-bridge.mode"] = b"synthetic-v1\n"
    donor_files = {
        "system/lib64/libauto-reframing-arm64-v8a.so": ("lib64/libauto-reframing-arm64-v8a.so", "e8921a043f2050d69d11f906820a400054c835bebd0f743bff7a33405c42f835"),
        "system/etc/mediacontextanalyzer/Locator.dlc": ("etc/mediacontextanalyzer/07-21_Video_ReframingTargetLocator_v0.1.0_SM8650_SNPE223.dlc", "95deac748b418bbe0146dea6e9ba656e0551e0ad2caec3793b04b90c69d4eab0"),
        "system/etc/mediacontextanalyzer/Reid.dlc": ("etc/mediacontextanalyzer/07-22_Video_ReframingReID_v0.2.3_SM8650_SNPE223.dlc", "a3b17b46074e630f8379b2f0d3728c67526fcebe3fcaa4abe7c189f883451c67"),
    }
    for target, (relative, expected) in donor_files.items():
        data = (donor / relative).read_bytes()
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError(f"Unsupported donor: {relative}")
        payload[target] = data
    source = Path(__file__).resolve().parent / "magisk"
    for name in ("module.prop", "customize.sh", "action.sh", "README.txt"):
        payload[name] = (source / name).read_bytes()
    payload["payload.sha256"] = "".join(
        f"{hashlib.sha256(data).hexdigest()}  {name}\n" for name, data in sorted(payload.items())
    ).encode()
    destination.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(destination, "x", compression=ZIP_DEFLATED) as archive:
        for name, data in sorted(payload.items()):
            info = ZipInfo(name, (2026, 9, 18, 0, 0, 0))
            info.create_system = 3
            mode = 0o755 if name.endswith(".sh") or name == "probe/mca_probe" else 0o644
            info.external_attr = (stat.S_IFREG | mode) << 16
            info.compress_type = ZIP_DEFLATED
            archive.writestr(info, data)
    with ZipFile(destination) as archive:
        assert archive.testzip() is None
        assert set(archive.namelist()) == set(payload)
        for line in archive.read("payload.sha256").decode().splitlines():
            expected, name = line.split("  ", 1)
            assert hashlib.sha256(archive.read(name)).hexdigest() == expected
    print(f"{hashlib.sha256(destination.read_bytes()).hexdigest()}  {destination}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundle", type=Path)
    parser.add_argument("donor", type=Path, help="S24U system/system directory")
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    package(args.bundle, args.donor, args.destination)
