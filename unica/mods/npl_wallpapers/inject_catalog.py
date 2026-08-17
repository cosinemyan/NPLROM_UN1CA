#!/usr/bin/env python3
"""Append NPL static wallpaper entries to Samsung resources_info.json."""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: inject_catalog.py <resources_info.json> <npl_name.png>...", file=sys.stderr)
        return 2

    json_path = Path(sys.argv[1])
    names = sys.argv[2:]
    set_default = os.environ.get("NPL_SET_DEFAULT", "0") == "1"

    data = json.loads(json_path.read_text(encoding="utf-8"))
    phone = data.setdefault("phone", [])
    if not phone:
        print("phone[] is empty", file=sys.stderr)
        return 1

    max_index = max(entry.get("index", 0) for entry in phone)
    template = next((e for e in phone if e.get("type") == 0), phone[0])
    cmf_info = list(template.get("cmf_info", ["ze", "zr"]))

    if set_default:
        for entry in phone:
            if entry.get("type") == 0:
                entry["isDefault"] = False

    which = 1
    for i, catalog_name in enumerate(names):
        max_index += 1
        phone.append(
            {
                "isDefault": set_default and i == 0,
                "index": max_index,
                "which": which,
                "screen": 0,
                "type": 0,
                "filename": catalog_name,
                "frame_no": -1,
                "cmf_info": cmf_info,
            }
        )
        which = 2 if which == 1 else 1

    json_path.write_text(json.dumps(data, indent=4) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
