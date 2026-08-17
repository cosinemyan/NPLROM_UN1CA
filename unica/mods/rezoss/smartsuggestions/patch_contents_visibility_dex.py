#!/usr/bin/env python3

import hashlib
import struct
import sys
import zlib
from pathlib import Path


PATCHES = (
    # Keep CategorySpecKt.isChecked() unpatched so category toggles follow user state.
    (
        "Lcom/samsung/android/smartsuggestions/feature/aisuggestion/category/spec/CategorySpecKt;",
        "isVisible",
        True,
    ),
    (
        "Lcom/samsung/android/smartsuggestions/feature/aisuggestion/category/policy/ContentsCategoryPolicy;",
        "isBlockedByPolicy",
        False,
    ),
    (
        "Lcom/samsung/android/smartsuggestions/feature/aisuggestion/category/policy/ContentsCategoryPolicy;",
        "isEnabled",
        True,
    ),
)


def u16(data, off):
    return struct.unpack_from("<H", data, off)[0]


def u32(data, off):
    return struct.unpack_from("<I", data, off)[0]


def read_uleb128(data, off):
    result = 0
    shift = 0
    while True:
        b = data[off]
        off += 1
        result |= (b & 0x7F) << shift
        if b < 0x80:
            return result, off
        shift += 7


def read_dex_string(data, off):
    _, off = read_uleb128(data, off)
    end = data.index(0, off)
    return data[off:end].decode("utf-8", errors="replace")


def update_dex_header(data):
    data[8:32] = b"\x00" * 24
    data[12:32] = hashlib.sha1(data[32:]).digest()
    struct.pack_into("<I", data, 8, zlib.adler32(data[12:]) & 0xFFFFFFFF)


def dex_tables(data):
    string_ids_size = u32(data, 0x38)
    string_ids_off = u32(data, 0x3C)
    type_ids_size = u32(data, 0x40)
    type_ids_off = u32(data, 0x44)
    method_ids_size = u32(data, 0x58)
    method_ids_off = u32(data, 0x5C)
    class_defs_size = u32(data, 0x60)
    class_defs_off = u32(data, 0x64)

    strings = [
        read_dex_string(data, u32(data, string_ids_off + i * 4))
        for i in range(string_ids_size)
    ]
    types = [strings[u32(data, type_ids_off + i * 4)] for i in range(type_ids_size)]
    methods = []
    for i in range(method_ids_size):
        off = method_ids_off + i * 8
        methods.append(
            {
                "class": types[u16(data, off)],
                "name": strings[u32(data, off + 4)],
            }
        )

    return {
        "types": types,
        "methods": methods,
        "class_defs_size": class_defs_size,
        "class_defs_off": class_defs_off,
    }


def find_code_item(data, tables, class_name, method_name):
    try:
        class_idx = tables["types"].index(class_name)
    except ValueError as exc:
        raise RuntimeError(f"class not found: {class_name}") from exc

    class_data_off = 0
    for i in range(tables["class_defs_size"]):
        off = tables["class_defs_off"] + i * 32
        if u32(data, off) == class_idx:
            class_data_off = u32(data, off + 24)
            break
    if class_data_off == 0:
        raise RuntimeError(f"class data not found: {class_name}")

    pos = class_data_off
    static_fields_size, pos = read_uleb128(data, pos)
    instance_fields_size, pos = read_uleb128(data, pos)
    direct_methods_size, pos = read_uleb128(data, pos)
    virtual_methods_size, pos = read_uleb128(data, pos)

    for _ in range(static_fields_size + instance_fields_size):
        _, pos = read_uleb128(data, pos)
        _, pos = read_uleb128(data, pos)

    for method_count in (direct_methods_size, virtual_methods_size):
        method_idx = 0
        for _ in range(method_count):
            method_idx_diff, pos = read_uleb128(data, pos)
            method_idx += method_idx_diff
            _, pos = read_uleb128(data, pos)
            code_off, pos = read_uleb128(data, pos)
            method = tables["methods"][method_idx]
            if method["class"] == class_name and method["name"] == method_name:
                if code_off == 0:
                    raise RuntimeError(f"method has no code: {class_name}->{method_name}")
                return code_off

    raise RuntimeError(f"method not found: {class_name}->{method_name}")


def patch_return_boolean(data, tables, class_name, method_name, value):
    code_off = find_code_item(data, tables, class_name, method_name)
    insns_size = u32(data, code_off + 12)
    if insns_size < 2:
        raise RuntimeError(f"method too small: {class_name}->{method_name}")

    insn_off = code_off + 16
    patch = b"\x12\x10\x0f\x00" if value else b"\x12\x00\x0f\x00"
    if data[insn_off : insn_off + 4] == patch:
        print(f"classes14.dex: {method_name} already forced {str(value).lower()}")
        return False

    data[insn_off : insn_off + 4] = patch
    print(
        f"classes14.dex: forced {class_name}->{method_name} "
        f"to {str(value).lower()} at 0x{insn_off:x}"
    )
    return True


def main():
    if len(sys.argv) != 2:
        print(
            f"usage: {Path(sys.argv[0]).name} <classes14.dex>",
            file=sys.stderr,
        )
        return 2

    dex_path = Path(sys.argv[1])
    data = bytearray(dex_path.read_bytes())
    if not data.startswith(b"dex\n"):
        raise RuntimeError("not a dex file")

    tables = dex_tables(data)
    changed = False
    for class_name, method_name, value in PATCHES:
        changed |= patch_return_boolean(data, tables, class_name, method_name, value)

    if changed:
        update_dex_header(data)
        dex_path.write_bytes(data)

    return 0


if __name__ == "__main__":
    sys.exit(main())
