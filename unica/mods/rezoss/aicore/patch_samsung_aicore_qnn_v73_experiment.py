#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later

import argparse
from pathlib import Path


# SamsungAiCore 3.0.02.18 carries a qc_sm8850/V81 QNN wrapper. These byte
# patches are intentionally scoped to that exact libsnap_qnn.so build id. They
# are an experiment: they retarget the visible HTP architecture constant to V73
# and avoid known optional callback/logger paths that crash before useful model
# errors can be observed on SM8550.
ORIGINAL_DEVICE_ARCH_V81 = bytes.fromhex(
    "28008052"  # mov w8, #1
    "290a8052"  # mov w9, #0x51 (HTP arch 81 / V81)
    "606240f9"
    "e81700b9"
    "e8530091"
    "e91f00b9"
    "691a41f9"
)
PATCHED_DEVICE_ARCH_V73 = bytes.fromhex(
    "28008052"
    "29098052"  # mov w9, #0x49 (HTP arch 73 / V73)
    "606240f9"
    "e81700b9"
    "e8530091"
    "e91f00b9"
    "691a41f9"
)

ORIGINAL_LEGACY_CREATE = bytes.fromhex(
    "60064ca9"  # ldp x0, x1, [x19, #0xc0]
    "687e40f9"  # ldr x8, [x19, #0xf8]
    "00013fd6"  # blr x8
    "00f9ffb4"  # cbz x0, newer backend-create path
)
PATCHED_LEGACY_CREATE = bytes.fromhex(
    "60064ca9"
    "687e40f9"
    "000080d2"  # mov x0, #0
    "00f9ffb4"
)

ORIGINAL_SET_PERF_BEFORE_OPEN = bytes.fromhex(
    "fd7bbea9"
    "f44f01a9"
    "fd030091"
    "14601091"
    "f30300aa"
    "88fedf08"
    "c8000037"
    "60e20e91"
    "61220091"
)
PATCHED_SET_PERF_BEFORE_OPEN = bytes.fromhex(
    "20008052"  # mov w0, #1
    "c0035fd6"  # ret
    "fd030091"
    "14601091"
    "f30300aa"
    "88fedf08"
    "c8000037"
    "60e20e91"
    "61220091"
)

ORIGINAL_PERF_INITIALIZE = bytes.fromhex(
    "e0430091"  # add x0, sp, #0x10
    "881640f9"  # ldr x8, [x20, #0x28]
    "a8831ff8"  # stur x8, [x29, #-0x8]
    "28a440f9"  # ldr x8, [x1, #0x148]
    "ff0b00f9"  # str xzr, [sp, #0x10]
    "00013fd6"  # blr x8
    "f50b40f9"
    "28008052"
)
PATCHED_PERF_INITIALIZE = bytes.fromhex(
    "e0430091"
    "881640f9"
    "a8831ff8"
    "28a440f9"
    "ff0b00f9"
    "10000014"  # b function cleanup/return
    "f50b40f9"
    "28008052"
)

ORIGINAL_LOGGER_ENTRY = bytes.fromhex(
    "ff8305d1"
    "fd7b10a9"
    "fc8b00f9"
    "fa6712a9"
    "f85f13a9"
    "f65714a9"
    "f44f15a9"
)
PATCHED_LOGGER_ENTRY = bytes.fromhex(
    "c0035fd6"  # ret
    "fd7b10a9"
    "fc8b00f9"
    "fa6712a9"
    "f85f13a9"
    "f65714a9"
    "f44f15a9"
)


def replace_once(data: bytes, original: bytes, patched: bytes, label: str) -> tuple[bytes, int | None]:
    original_count = data.count(original)
    patched_count = data.count(patched)

    if original_count == 0 and patched_count == 1:
        return data, None

    if original_count != 1 or patched_count != 0:
        raise SystemExit(
            f"unexpected SamsungAiCore libsnap_qnn.so for {label}: "
            f"original matches={original_count}, patched matches={patched_count}"
        )

    offset = data.index(original)
    return data[:offset] + patched + data[offset + len(original) :], offset


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Experimental SamsungAiCore QNN retargeting for SM8550/HTP V73"
    )
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    data = args.input.read_bytes()
    patches = (
        ("device architecture V81->V73", ORIGINAL_DEVICE_ARCH_V81, PATCHED_DEVICE_ARCH_V73),
        ("legacy backend-create callback", ORIGINAL_LEGACY_CREATE, PATCHED_LEGACY_CREATE),
        ("SetPerfBeforeOpen", ORIGINAL_SET_PERF_BEFORE_OPEN, PATCHED_SET_PERF_BEFORE_OPEN),
        ("QnnHtpPerfSetting initialize", ORIGINAL_PERF_INITIALIZE, PATCHED_PERF_INITIALIZE),
        ("QNN logger no-op", ORIGINAL_LOGGER_ENTRY, PATCHED_LOGGER_ENTRY),
    )

    offsets = []
    for label, original, patched in patches:
        data, offset = replace_once(data, original, patched, label)
        if offset is not None:
            offsets.append(f"{label}=0x{offset:x}")

    args.output.write_bytes(data)
    if offsets:
        print("patched SamsungAiCore QNN V73 experiment: " + ", ".join(offsets))
    else:
        print("SamsungAiCore QNN V73 experiment already patched")


if __name__ == "__main__":
    main()
