#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
# Sparse Android DAT → raw image (xpirt-compatible transfer.list v1–4).
"""Convert vendor.new.dat + vendor.transfer.list into a raw vendor.img."""

from __future__ import annotations

import argparse
import os
import sys

BLOCK_SIZE = 4096


def rangeset(src: str) -> list[tuple[int, int]]:
    nums = [int(x) for x in src.split(",")]
    if not nums or len(nums) != nums[0] + 1:
        raise ValueError(f"bad rangeset: {src}")
    return [(nums[i], nums[i + 1]) for i in range(1, len(nums), 2)]


def parse_transfer_list(path: str) -> tuple[int, int, list[tuple[str, str]]]:
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        version = int(fh.readline().strip())
        new_blocks = int(fh.readline().strip())
        if version >= 2:
            fh.readline()
            fh.readline()
        commands: list[tuple[str, str]] = []
        for raw in fh:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            if " " in line:
                cmd, data = line.split(" ", 1)
            else:
                cmd, data = line, ""
            commands.append((cmd, data))
    return version, new_blocks, commands


def sdat2img(transfer_list: str, new_dat: str, output_img: str) -> int:
    _, _, commands = parse_transfer_list(transfer_list)
    max_block = 0
    for cmd, data in commands:
        if cmd in ("new", "zero", "erase") and data:
            for _start, end in rangeset(data):
                if end > max_block:
                    max_block = end

    os.makedirs(os.path.dirname(os.path.abspath(output_img)) or ".", exist_ok=True)
    with open(output_img, "wb") as img, open(new_dat, "rb") as dat:
        img.truncate(max_block * BLOCK_SIZE)
        for cmd, data in commands:
            if cmd != "new" or not data:
                continue
            for start, end in rangeset(data):
                img.seek(start * BLOCK_SIZE)
                remaining = (end - start) * BLOCK_SIZE
                while remaining:
                    chunk = dat.read(remaining)
                    if not chunk:
                        raise EOFError("vendor.new.dat ended before transfer.list")
                    img.write(chunk)
                    remaining -= len(chunk)
    return max_block * BLOCK_SIZE


def main() -> int:
    parser = argparse.ArgumentParser(description="Convert .new.dat + transfer.list to a raw .img")
    parser.add_argument("transfer_list")
    parser.add_argument("new_dat")
    parser.add_argument("output_img")
    args = parser.parse_args()
    size = sdat2img(args.transfer_list, args.new_dat, args.output_img)
    print(f"Wrote {args.output_img} ({size} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
