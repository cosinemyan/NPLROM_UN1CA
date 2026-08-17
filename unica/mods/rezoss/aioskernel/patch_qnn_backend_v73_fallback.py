#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later

import argparse
from pathlib import Path


# On SM8550/V73 several backend/device-create/perf-setting function pointers
# used by the S26U libsnap_qnn.so can be null. These are experimental test-only
# bypasses; if backend handles are required later they will move the failure
# deeper instead of fixing inference.
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

ORIGINAL_V2_CREATE = bytes.fromhex(
    "681a41f9"  # ldr x8, [x19, #0x230]
    "e01b00fd"  # str d0, [sp, #0x30]
    "ff1300b9"  # str wzr, [sp, #0x10]
    "bf031ff8"  # stur xzr, [x29, #-0x10]
    "00013fd6"  # blr x8
    "c00800b4"  # cbz x0, initialized path
)
PATCHED_V2_CREATE = bytes.fromhex(
    "681a41f9"
    "e01b00fd"
    "ff1300b9"
    "bf031ff8"
    "000080d2"  # mov x0, #0
    "c00800b4"
)

ORIGINAL_DEVICE_CREATE = bytes.fromhex(
    "881a41f9"  # ldr x8, [x20, #0x230]
    "e91b00b9"  # str w9, [sp, #0x18]
    "ea0700f9"  # str x10, [sp, #0x8]
    "abff3ea9"  # stp x11, xzr, [x29, #-0x18]
    "a16300d1"  # sub x1, x29, #0x18
    "00013fd6"  # blr x8
    "a00300b4"  # cbz x0, initialized path
)
PATCHED_DEVICE_CREATE = bytes.fromhex(
    "881a41f9"
    "e91b00b9"
    "ea0700f9"
    "abff3ea9"
    "a16300d1"
    "000080d2"  # mov x0, #0
    "a00300b4"
)

ORIGINAL_SET_PERF_BEFORE_OPEN = bytes.fromhex(
    "fd7bbea9"  # stp x29, x30, [sp, #-0x20]!
    "f44f01a9"  # stp x20, x19, [sp, #0x10]
    "fd030091"  # mov x29, sp
    "14601091"  # add x20, x0, #0x418
    "f30300aa"  # mov x19, x0
    "88fedf08"  # ldarb w8, [x20]
    "c8000037"  # tbnz w8, #0, initialized path
    "60e20e91"  # add x0, x19, #0x3b8
    "61220091"  # add x1, x19, #0x8
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
    "a8831ff8"  # stur x8, [x29, #-0x8]
    "28a440f9"  # ldr x8, [x1, #0x148]
    "ff0b00f9"  # str xzr, [sp, #0x10]
    "00013fd6"  # blr x8
    "f50b40f9"  # ldr x21, [sp, #0x10]
    "28008052"  # mov w8, #1
)
PATCHED_PERF_INITIALIZE = bytes.fromhex(
    "e0430091"
    "a8831ff8"
    "28a440f9"
    "ff0b00f9"
    "0c000014"  # b function cleanup/return
    "f50b40f9"
    "28008052"
)


def replace_once(data: bytes, original: bytes, patched: bytes, label: str) -> tuple[bytes, int | None]:
    original_count = data.count(original)
    patched_count = data.count(patched)

    if original_count == 0 and patched_count == 1:
        return data, None

    if original_count != 1 or patched_count != 0:
        raise SystemExit(
            f"unexpected libsnap_qnn.so for {label}: "
            f"original matches={original_count}, patched matches={patched_count}"
        )

    offset = data.index(original)
    output = data[:offset] + patched + data[offset + len(original) :]
    return output, offset


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Bypass null QNN backend/device-create/perf-setting callbacks in AIOSKernelService libsnap_qnn.so"
    )
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    data = args.input.read_bytes()
    data, legacy_offset = replace_once(
        data, ORIGINAL_LEGACY_CREATE, PATCHED_LEGACY_CREATE, "legacy backend create"
    )
    data, v2_offset = replace_once(data, ORIGINAL_V2_CREATE, PATCHED_V2_CREATE, "v2 backend create")
    data, device_offset = replace_once(
        data, ORIGINAL_DEVICE_CREATE, PATCHED_DEVICE_CREATE, "SnapQnnHandler device create"
    )
    data, set_perf_offset = replace_once(
        data, ORIGINAL_SET_PERF_BEFORE_OPEN, PATCHED_SET_PERF_BEFORE_OPEN, "SetPerfBeforeOpen"
    )
    data, perf_init_offset = replace_once(
        data, ORIGINAL_PERF_INITIALIZE, PATCHED_PERF_INITIALIZE, "QnnHtpPerfSetting initialize"
    )

    args.output.write_bytes(data)
    if (
        legacy_offset is None
        and v2_offset is None
        and device_offset is None
        and set_perf_offset is None
        and perf_init_offset is None
    ):
        print("QNN backend V73 fallback already patched")
    else:
        offsets = []
        if legacy_offset is not None:
            offsets.append(f"0x{legacy_offset + 8:x}")
        if v2_offset is not None:
            offsets.append(f"0x{v2_offset + 16:x}")
        if device_offset is not None:
            offsets.append(f"0x{device_offset + 20:x}")
        if set_perf_offset is not None:
            offsets.append(f"0x{set_perf_offset:x}")
        if perf_init_offset is not None:
            offsets.append(f"0x{perf_init_offset + 16:x}")
        print(f"patched QNN backend V73 fallback at file offsets {', '.join(offsets)}")


if __name__ == "__main__":
    main()
