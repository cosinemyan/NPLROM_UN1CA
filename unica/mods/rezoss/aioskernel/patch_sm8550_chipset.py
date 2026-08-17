#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later

import argparse
from pathlib import Path


# Replace the "SM86" half of the first supported Snapdragon model string with
# "SM85". The surrounding instructions make this specific to checkChipset().
ORIGINAL = bytes.fromhex(
    "6baa8952"  # mov w11, #0x4d53
    "69aa8952"  # mov w9, #0x4d53
    "9f6a3338"  # strb wzr, [x20, x19]
    "0be7a672"  # movk w11, #0x3738, lsl #16
    "09c7a672"  # movk w9, #0x3638, lsl #16 ("SM86")
    "88018052"  # mov w8, #0xc
)
PATCHED = bytes.fromhex(
    "6baa8952"
    "69aa8952"
    "9f6a3338"
    "0be7a672"
    "09a7a672"  # movk w9, #0x3538, lsl #16 ("SM85")
    "88018052"
)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Allow SM8550 in AIOSKernelService libssneural_vndk.so"
    )
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    data = args.input.read_bytes()
    original_count = data.count(ORIGINAL)
    patched_count = data.count(PATCHED)

    if original_count == 0 and patched_count == 1:
        args.output.write_bytes(data)
        print("SM8550 chipset support gate already patched")
        return

    if original_count != 1 or patched_count != 0:
        raise SystemExit(
            "unexpected libssneural_vndk.so: "
            f"original matches={original_count}, patched matches={patched_count}"
        )

    offset = data.index(ORIGINAL)
    output = data[:offset] + PATCHED + data[offset + len(ORIGINAL) :]
    args.output.write_bytes(output)
    print(f"patched SM8650 to SM8550 at file offset 0x{offset + 16:x}")


if __name__ == "__main__":
    main()
