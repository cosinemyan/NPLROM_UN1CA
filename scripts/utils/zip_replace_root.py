#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Rewrite a Zip64 flashable ZIP, replacing named files at the zip root.

7-Zip's update (7z u) returns E_NOTIMPL on Zip64 archives. This copies every
other entry as-is (streamed) and writes the replacement files uncompressed
at the zip root.
"""

from __future__ import annotations

import argparse
import os
import shutil
import sys
import zipfile
from pathlib import Path

CHUNK = 8 * 1024 * 1024


def root_basename(filename: str) -> str:
    name = filename.replace("\\", "/").rstrip("/")
    if "/" in name:
        return ""
    return name


def _open_write(zout: zipfile.ZipFile, info: zipfile.ZipInfo, size: int):
    # zipfile cannot guess size while streaming; files >4 GiB need this flag.
    return zout.open(info, "w", force_zip64=size > zipfile.ZIP64_LIMIT)


def inject(src: Path, dst: Path, replacements: dict[str, Path]) -> None:
    tmp = Path(str(dst) + ".tmp")
    if tmp.exists():
        tmp.unlink()

    replace_names = set(replacements)
    with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(tmp, "w", allowZip64=True) as zout:
        for item in zin.infolist():
            base = root_basename(item.filename)
            if base in replace_names:
                continue
            if item.is_dir():
                zout.writestr(item.filename.rstrip("/") + "/", b"")
                continue
            info = zipfile.ZipInfo(filename=item.filename, date_time=item.date_time)
            info.compress_type = item.compress_type
            info.comment = item.comment
            info.create_system = item.create_system
            info.external_attr = item.external_attr
            if item.flag_bits & 0x800:
                info.flag_bits |= 0x800
            size_mib = item.file_size / (1024 * 1024)
            if size_mib >= 1:
                print(f"    copy {item.filename} ({size_mib:.0f} MiB)", flush=True)
            with zin.open(item, "r") as rf, _open_write(zout, info, item.file_size) as wf:
                shutil.copyfileobj(rf, wf, CHUNK)

        for name, path in replacements.items():
            if not path.is_file():
                raise FileNotFoundError(path)
            size = path.stat().st_size
            print(f"    add  {name} ({size / (1024 * 1024):.0f} MiB)", flush=True)
            info = zipfile.ZipInfo(filename=name)
            info.compress_type = zipfile.ZIP_STORED
            info.external_attr = 0o644 << 16
            with path.open("rb") as rf, _open_write(zout, info, size) as wf:
                shutil.copyfileobj(rf, wf, CHUNK)

    os.replace(tmp, dst)


def main() -> int:
    parser = argparse.ArgumentParser(description="Replace zip-root files in a Zip64 archive")
    parser.add_argument("src_zip")
    parser.add_argument("dst_zip")
    parser.add_argument("files", nargs="+", help="files whose basename is written at zip root")
    args = parser.parse_args()
    replacements = {Path(f).name: Path(f) for f in args.files}
    inject(Path(args.src_zip), Path(args.dst_zip), replacements)
    return 0


if __name__ == "__main__":
    sys.exit(main())
