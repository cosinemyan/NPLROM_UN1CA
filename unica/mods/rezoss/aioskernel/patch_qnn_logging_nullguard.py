#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later

import argparse
from pathlib import Path


# AIOSKernelService's QNN wrapper calls the backend logging-create callback
# without checking whether the loaded backend exported it. On SM8550/V73 this
# slot can be null, causing a native crash at pc=0 during model startup.
ORIGINAL_1 = bytes.fromhex(
    "886e40f9"  # ldr x8, [x20, #0xd8]
    "e00315aa"  # mov x0, x21
    "e103162a"  # mov w1, w22
    "e20313aa"  # mov x2, x19
    "00013fd6"  # blr x8
    "c00600b4"  # cbz x0, return
)
PATCHED_1 = bytes.fromhex(
    "886e40f9"
    "480700b4"  # cbz x8, return
    "e103162a"
    "e20313aa"
    "00013fd6"
    "c00600b4"
)

ORIGINAL_2 = bytes.fromhex(
    "886e40f9"  # ldr x8, [x20, #0xd8]
    "e00315aa"  # mov x0, x21
    "e103162a"  # mov w1, w22
    "e20313aa"  # mov x2, x19
    "00013fd6"  # blr x8
    "a0f6ffb5"  # cbnz x0, retry/log path
)
PATCHED_2 = bytes.fromhex(
    "886e40f9"
    "c8fdffb4"  # cbz x8, return
    "e103162a"
    "e20313aa"
    "00013fd6"
    "a0f6ffb5"
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
    return data[:offset] + patched + data[offset + len(original) :], offset + 4


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Guard null QNN logging callback in AIOSKernelService libsnap_qnn.so"
    )
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    data = args.input.read_bytes()
    data, offset_1 = replace_once(data, ORIGINAL_1, PATCHED_1, "normal logging init")
    data, offset_2 = replace_once(data, ORIGINAL_2, PATCHED_2, "alternate logging init")

    args.output.write_bytes(data)
    if offset_1 is None and offset_2 is None:
        print("QNN logging null guards already patched")
    else:
        offsets = []
        if offset_1 is not None:
            offsets.append(f"0x{offset_1:x}")
        if offset_2 is not None:
            offsets.append(f"0x{offset_2:x}")
        print(f"patched QNN logging null guards at file offsets {', '.join(offsets)}")


if __name__ == "__main__":
    main()
