#!/usr/bin/env python3
"""Copy NPL catalog entries into resources_info_feature.json (picker featured row).

Does not touch drawables. Never sets isDefault. Stock featured tiles stay in place.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) < 4:
        print(
            "usage: inject_feature.py <resources_info.json> <resources_info_feature.json> <npl_name.png>...",
            file=sys.stderr,
        )
        return 2

    catalog_path = Path(sys.argv[1])
    feature_path = Path(sys.argv[2])
    names = sys.argv[3:]

    catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
    if feature_path.is_file():
        feature = json.loads(feature_path.read_text(encoding="utf-8"))
    else:
        feature = {"phone": []}

    by_name: dict[str, list[dict]] = {}
    for entry in catalog.get("phone", []):
        filename = str(entry.get("filename", ""))
        if filename:
            by_name.setdefault(filename, []).append(entry)

    phone = [e for e in feature.get("phone", []) if not str(e.get("filename", "")).startswith("npl_")]
    max_index = max((entry.get("index", 0) for entry in phone), default=-1)

    added = 0
    for name in names:
        entries = by_name.get(name, [])
        if not entries:
            print(f"warning: {name} not in resources_info.json", file=sys.stderr)
            continue
        src = next((e for e in entries if e.get("which") == 1), entries[0])
        max_index += 1
        entry = dict(src)
        entry["index"] = max_index
        entry["isDefault"] = False
        phone.append(entry)
        added += 1

    feature["phone"] = phone
    feature_path.parent.mkdir(parents=True, exist_ok=True)
    feature_path.write_text(json.dumps(feature, indent=4) + "\n", encoding="utf-8")
    print(f"featured json: {added} NPL tile(s), {len(phone)} total")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
