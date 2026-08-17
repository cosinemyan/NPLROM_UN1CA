#!/usr/bin/env python3
#
# Sanitizes Firewall APK location data before apktool rebuilds it. The native
# SDB keeps fixed-width ASCII placeholders; RezossRegionTranslator maps those
# placeholders back to full English at runtime.

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


SDB_50001 = Path("assets/50001.sdb")

BINARY_FIXED_FILES = (
    Path("assets/100009.sdb"),
    Path("assets/1769507429.mdic"),
    Path("assets/yd.sdb"),
    Path("unknown/com/google/i18n/phonenumbers/carrier/data/852_zh"),
    Path("unknown/com/google/i18n/phonenumbers/carrier/data/852_zh_Hant"),
    Path("unknown/com/google/i18n/phonenumbers/carrier/data/86_zh"),
    Path("unknown/com/google/i18n/phonenumbers/geocoding/data/86_zh"),
)

TEXT_FILES = (
    Path("assets/ted/loc/cities.dat"),
    Path("assets/ted/loc/version.dat"),
    Path("res/values-bo/strings.xml"),
    Path("res/values-ug-rCN/strings.xml"),
    Path("res/values-zh-rCN/strings.xml"),
    Path("res/values-zh-rHK/strings.xml"),
    Path("res/values-zh-rTW/strings.xml"),
)

CITY_LIST_XML = Path("res/xml/city_list.xml")


def load_rows(mapping_file: Path) -> list[tuple[str, str, str]]:
    rows: list[tuple[str, str, str]] = []
    for line_no, line in enumerate(mapping_file.read_text(encoding="ascii").splitlines(), 1):
        line = line.strip()
        if not line or line.startswith("#"):
            continue

        parts = line.split("\t")
        if len(parts) != 3:
            raise ValueError(f"{mapping_file}:{line_no}: expected 3 tab-separated columns")

        source = parts[0].encode("ascii").decode("unicode_escape")
        placeholder = parts[1]
        english = parts[2]
        if not source or not placeholder or not english:
            raise ValueError(f"{mapping_file}:{line_no}: empty mapping field")
        rows.append((source, placeholder, english))

    return rows


def unique_english_map(rows: list[tuple[str, str, str]]) -> dict[str, str]:
    out: dict[str, str] = {}
    for source, _placeholder, english in rows:
        out.setdefault(source, english)
    return out


def smali_escape(text: str) -> str:
    return "".join(f"\\u{ord(ch):04x}" if ord(ch) > 0x7F else ch for ch in text)


def replace_text_terms(text: str, mapping: dict[str, str]) -> str:
    for source in sorted(mapping, key=len, reverse=True):
        english = mapping[source]
        text = text.replace(source, english)
        text = text.replace(smali_escape(source), english)
    return text


def fit_ascii(text: str, size: int) -> str:
    value = re.sub(r"[^A-Za-z0-9 /().,_+-]", "", text).strip()
    value = value or "X"
    if len(value) > size:
        value = value[:size]
    return value.ljust(size)


def patch_fixed_binary(path: Path, mapping: dict[str, str]) -> bool:
    if not path.is_file():
        return False

    data = path.read_bytes()
    original = data

    for source in sorted(mapping, key=len, reverse=True):
        english = mapping[source]

        source_utf8 = source.encode("utf-8")
        repl_utf8 = fit_ascii(english, len(source_utf8)).encode("ascii")
        data = data.replace(source_utf8, repl_utf8)

        for encoding in ("utf-16le", "utf-16be"):
            source_utf16 = source.encode(encoding)
            repl_utf16 = fit_ascii(english, len(source)).encode(encoding)
            data = data.replace(source_utf16, repl_utf16)

    if data == original:
        return False

    path.write_bytes(data)
    return True


def patch_text_file(path: Path, mapping: dict[str, str]) -> bool:
    if not path.is_file():
        return False

    text = path.read_text(encoding="utf-8")
    new_text = replace_text_terms(text, mapping)
    if new_text == text:
        return False

    path.write_text(new_text, encoding="utf-8")
    return True


def title_from_pinyin(pinyin: str) -> str:
    parts = []
    for chunk in pinyin.split("/"):
        words = [word.capitalize() for word in chunk.split() if word]
        parts.append(" ".join(words))
    return "/".join(parts)


def patch_city_list(path: Path, mapping: dict[str, str]) -> bool:
    if not path.is_file():
        return False

    tree = ET.parse(path)
    root = tree.getroot()
    changed = False

    for city in root.iter("City"):
        city_name = city.get("cityName", "")
        pinyin = city.get("pinyin", "")
        if city_name and pinyin and re.search(r"[\u3400-\u9fff]", city_name):
            city.set("cityName", title_from_pinyin(pinyin))
            changed = True

        province_name = city.get("provinceName", "")
        english = mapping.get(province_name)
        if english:
            city.set("provinceName", english)
            changed = True

    if not changed:
        return False

    tree.write(path, encoding="utf-8", xml_declaration=True)
    return True


def patch_smali_tree(apk_dir: Path, mapping: dict[str, str]) -> list[str]:
    touched: list[str] = []
    for path in sorted(apk_dir.rglob("*.smali")):
        if patch_text_file(path, mapping):
            touched.append(str(path.relative_to(apk_dir)))
    return touched


def patch_50001_sdb(path: Path, rows: list[tuple[str, str, str]]) -> bool:
    if not path.is_file():
        return False

    by_source: dict[str, list[str]] = {}
    for source, placeholder, _english in rows:
        by_source.setdefault(source, []).append(placeholder)

    data = bytearray(path.read_bytes())
    original = bytes(data)
    changed = False

    # Known UTF-16LE string pools in assets/50001.sdb:
    # province table: 30 entries, offsets 0x100 -> 0x13e
    # country table:  230 entries, offsets 0x3470 -> 0x363e
    for table_start, data_start, count in ((0x100, 0x13E, 30), (0x3470, 0x363E, 230)):
        offsets = [int.from_bytes(data[table_start + i * 2 : table_start + i * 2 + 2], "little") for i in range(count + 1)]
        seen: dict[str, int] = {}

        for i in range(count):
            start = data_start + offsets[i] * 2
            end = data_start + offsets[i + 1] * 2
            source = bytes(data[start:end]).decode("utf-16le")

            placeholders = by_source.get(source)
            if not placeholders:
                continue

            occurrence = seen.get(source, 0)
            seen[source] = occurrence + 1
            placeholder = placeholders[min(occurrence, len(placeholders) - 1)]

            if len(placeholder) != len(source):
                raise ValueError(f"placeholder width mismatch for {source!r}")

            replacement = placeholder.encode("utf-16le")
            if data[start:end] != replacement:
                data[start:end] = replacement
                changed = True

    if not changed or bytes(data) == original:
        return False

    path.write_bytes(data)
    return True


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: patch_region_strings.py <decoded Firewall.apk dir> <region_names.tsv>", file=sys.stderr)
        return 2

    apk_dir = Path(sys.argv[1])
    mapping_file = Path(sys.argv[2])
    rows = load_rows(mapping_file)
    mapping = unique_english_map(rows)
    touched: list[str] = []

    if patch_50001_sdb(apk_dir / SDB_50001, rows):
        touched.append(str(SDB_50001))

    for rel in BINARY_FIXED_FILES:
        if patch_fixed_binary(apk_dir / rel, mapping):
            touched.append(str(rel))

    for rel in TEXT_FILES:
        if patch_text_file(apk_dir / rel, mapping):
            touched.append(str(rel))

    if patch_city_list(apk_dir / CITY_LIST_XML, mapping):
        touched.append(str(CITY_LIST_XML))

    touched.extend(patch_smali_tree(apk_dir, mapping))

    for rel in touched:
        print(f"patched {rel}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
