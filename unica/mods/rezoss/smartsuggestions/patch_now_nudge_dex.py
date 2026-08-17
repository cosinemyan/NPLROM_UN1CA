#!/usr/bin/env python3

import hashlib
import struct
import sys
import zlib
from pathlib import Path


TARGET_CLASS = "Lcom/samsung/android/smartsuggestions/featureconfig/rune/Rune;"
TARGET_METHOD_GROUPS = {
    "Now Nudge": (
        "SUPPORT_NOW_NUDGE_delegate$lambda$149",
        "SUPPORT_NOW_NUDGE_delegate$lambda$146",
        "SUPPORT_NOW_NUDGE_delegate$lambda$115",
        "SUPPORT_NOW_NUDGE_delegate$lambda$111",
    ),
    "Now Nudge in-app": (
        "SUPPORT_IN_APP_NUDGE_delegate$lambda$20",
    ),
    "Now Nudge autofill": (
        "SUPPORT_AUTOFILL_NUDGE_delegate$lambda$163",
        "SUPPORT_AUTOFILL_NUDGE_delegate$lambda$160",
    ),
}
TARGET_METHODS = {
    method_name: group_name
    for group_name, method_names in TARGET_METHOD_GROUPS.items()
    for method_name in method_names
}


def read_uleb128(data, offset):
    value = 0
    shift = 0
    pos = offset
    while True:
        byte = data[pos]
        pos += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, pos
        shift += 7


def read_string(data, string_ids_off, index):
    string_data_off = struct.unpack_from("<I", data, string_ids_off + index * 4)[0]
    _, pos = read_uleb128(data, string_data_off)
    end = data.index(0, pos)
    return data[pos:end].decode("utf-8")


def update_dex_header(data):
    signature = hashlib.sha1(data[32:]).digest()
    data[12:32] = signature
    checksum = zlib.adler32(data[12:]) & 0xFFFFFFFF
    data[8:12] = struct.pack("<I", checksum)


def find_target_code_offsets(data):
    if not data.startswith(b"dex\n"):
        raise RuntimeError("not a dex file")

    string_ids_size, string_ids_off = struct.unpack_from("<II", data, 0x38)
    type_ids_size, type_ids_off = struct.unpack_from("<II", data, 0x40)
    method_ids_size, method_ids_off = struct.unpack_from("<II", data, 0x58)
    class_defs_size, class_defs_off = struct.unpack_from("<II", data, 0x60)

    def string_at(index):
        if index >= string_ids_size:
            raise RuntimeError(f"string index out of range: {index}")
        return read_string(data, string_ids_off, index)

    def type_at(index):
        if index >= type_ids_size:
            raise RuntimeError(f"type index out of range: {index}")
        string_idx = struct.unpack_from("<I", data, type_ids_off + index * 4)[0]
        return string_at(string_idx)

    def method_at(index):
        if index >= method_ids_size:
            raise RuntimeError(f"method index out of range: {index}")
        class_idx, _, name_idx = struct.unpack_from("<HHI", data, method_ids_off + index * 8)
        return type_at(class_idx), string_at(name_idx)

    for class_def_index in range(class_defs_size):
        off = class_defs_off + class_def_index * 32
        class_idx, _, _, _, _, _, class_data_off, _ = struct.unpack_from("<IIIIIIII", data, off)
        if type_at(class_idx) != TARGET_CLASS:
            continue
        if class_data_off == 0:
            raise RuntimeError("target class has no class data")

        pos = class_data_off
        static_fields_size, pos = read_uleb128(data, pos)
        instance_fields_size, pos = read_uleb128(data, pos)
        direct_methods_size, pos = read_uleb128(data, pos)
        virtual_methods_size, pos = read_uleb128(data, pos)

        for _ in range(static_fields_size + instance_fields_size):
            _, pos = read_uleb128(data, pos)
            _, pos = read_uleb128(data, pos)

        method_idx = 0
        for _ in range(direct_methods_size):
            method_idx_diff, pos = read_uleb128(data, pos)
            method_idx += method_idx_diff
            _, pos = read_uleb128(data, pos)
            code_off, pos = read_uleb128(data, pos)
            owner, name = method_at(method_idx)
            if owner == TARGET_CLASS and name in TARGET_METHODS:
                if code_off == 0:
                    raise RuntimeError("target method has no code item")
                yield code_off, name

        return

    raise RuntimeError("target class not found")


def main():
    if len(sys.argv) != 2:
        raise SystemExit(f"Usage: {Path(sys.argv[0]).name} <classes15.dex>")

    dex_path = Path(sys.argv[1])
    data = bytearray(dex_path.read_bytes())
    found_groups = set()
    changed = False

    for code_off, method_name in find_target_code_offsets(data):
        found_groups.add(TARGET_METHODS[method_name])
        insn_off = code_off + 16

        if data[insn_off] != 0x12:
            raise RuntimeError(f"unexpected first opcode at 0x{insn_off:x}: 0x{data[insn_off]:02x}")

        if data[insn_off + 1] == 0x10:
            print(f"{TARGET_METHODS[method_name]} rune already patched in {method_name}")
            continue

        if data[insn_off + 1] != 0x00:
            raise RuntimeError(
                f"unexpected const/4 operand at 0x{insn_off + 1:x}: 0x{data[insn_off + 1]:02x}"
            )

        data[insn_off + 1] = 0x10
        changed = True
        print(f"Patched {TARGET_METHODS[method_name]} rune {method_name} at dex offset 0x{insn_off:x}")

    missing_groups = set(TARGET_METHOD_GROUPS) - found_groups
    if missing_groups:
        raise RuntimeError(f"target method group(s) not found: {', '.join(sorted(missing_groups))}")

    if changed:
        update_dex_header(data)
        dex_path.write_bytes(data)


if __name__ == "__main__":
    main()
