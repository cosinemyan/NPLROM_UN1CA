#!/usr/bin/env python3
import hashlib
import struct
import sys
import zlib
from pathlib import Path


DEV_MANAGER = "Lcom/samsung/android/app/deepsky/baseutil/developermode/impl/DeveloperModeManagerImpl;"
DEV_UTILS = "Lcom/samsung/android/smartsuggestions/settings/about/DeveloperModeUtils;"
PASSWORD_HANDLER = (
    "Lcom/samsung/android/smartsuggestions/settings/about/"
    "SmartSuggestionsAboutActivity$handlePassword$1$1;"
)
DEV_ALIAS = (
    "com.samsung.android.smartsuggestions.settings.about.developermode."
    "DeveloperModeActivityAlias"
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
        result |= (b & 0x7f) << shift
        if b < 0x80:
            return result, off
        shift += 7


def read_dex_string(data, off):
    _, off = read_uleb128(data, off)
    end = data.index(0, off)
    return data[off:end].decode("utf-8", errors="replace")


def update_dex_header(data):
    struct.pack_into("<I", data, 0x20, len(data))
    data[8:32] = b"\x00" * 24
    signature = hashlib.sha1(data[32:]).digest()
    data[12:32] = signature
    checksum = zlib.adler32(data[12:]) & 0xffffffff
    struct.pack_into("<I", data, 8, checksum)


def dex_tables(data):
    string_ids_size = u32(data, 0x38)
    string_ids_off = u32(data, 0x3c)
    type_ids_size = u32(data, 0x40)
    type_ids_off = u32(data, 0x44)
    method_ids_size = u32(data, 0x58)
    method_ids_off = u32(data, 0x5c)
    class_defs_size = u32(data, 0x60)
    class_defs_off = u32(data, 0x64)

    strings = []
    for i in range(string_ids_size):
        strings.append(read_dex_string(data, u32(data, string_ids_off + i * 4)))

    types = []
    for i in range(type_ids_size):
        types.append(strings[u32(data, type_ids_off + i * 4)])

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


def find_code_item(data, class_name, method_name):
    tables = dex_tables(data)
    class_idx = None
    for i, value in enumerate(tables["types"]):
        if value == class_name:
            class_idx = i
            break
    if class_idx is None:
        raise RuntimeError(f"class not found: {class_name}")

    class_def_off = None
    for i in range(tables["class_defs_size"]):
        off = tables["class_defs_off"] + i * 32
        if u32(data, off) == class_idx:
            class_def_off = off
            break
    if class_def_off is None:
        raise RuntimeError(f"class def not found: {class_name}")

    class_data_off = u32(data, class_def_off + 24)
    if class_data_off == 0:
        raise RuntimeError(f"class has no class_data: {class_name}")

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
            if method["name"] == method_name:
                if code_off == 0:
                    raise RuntimeError(f"method has no code: {class_name}->{method_name}")
                return code_off

    raise RuntimeError(f"method not found: {class_name}->{method_name}")


def code_bounds(data, code_off):
    insns_size = u32(data, code_off + 12)
    start = code_off + 16
    end = start + insns_size * 2
    return start, end


def find_code_unit(data, start, end, opcodes):
    for off in range(start, end, 2):
        if data[off] in opcodes:
            return off
    return None


def patch_is_dev_mode_enabled(data):
    code_off = find_code_item(data, DEV_MANAGER, "isDevModeEnabled")
    start, end = code_bounds(data, code_off)
    for off in range(start, end, 2):
        if data[off] == 0x12 and data[off + 1] in (0x00, 0x10):
            if data[off + 1] == 0x10:
                print("classes11.dex: isDevModeEnabled already forced true")
                return False
            data[off + 1] = 0x10
            print(f"classes11.dex: forced isDevModeEnabled false fallback at 0x{off:x}")
            return True
    raise RuntimeError("isDevModeEnabled false fallback instruction not found")


def patch_is_dev_mode_enabled_cache(data):
    code_off = find_code_item(data, DEV_MANAGER, "isDevModeEnabledUsingCache")
    start, _ = code_bounds(data, code_off)
    if data[start : start + 4] == b"\x12\x10\x00\x00":
        print("classes11.dex: isDevModeEnabledUsingCache already forced true")
        return False
    if data[start] != 0x63:
        raise RuntimeError("unexpected isDevModeEnabledUsingCache first instruction")
    data[start : start + 4] = b"\x12\x10\x00\x00"
    print(f"classes11.dex: forced isDevModeEnabledUsingCache at 0x{start:x}")
    return True


def patch_is_already_allowed(data, label):
    code_off = find_code_item(data, DEV_UTILS, "isAlreadyAllowed")
    start, end = code_bounds(data, code_off)
    off = find_code_unit(data, start, end, {0x29, 0x38})
    if off is None:
        raise RuntimeError("isAlreadyAllowed gate branch not found")
    if data[off] == 0x29:
        print(f"{label}: isAlreadyAllowed already forced allowed")
        return False
    data[off] = 0x29
    data[off + 1] = 0x00
    print(f"{label}: forced isAlreadyAllowed at 0x{off:x}")
    return True


def patch_password_handler(data, label):
    code_off = find_code_item(data, PASSWORD_HANDLER, "invokeSuspend")
    start, end = code_bounds(data, code_off)
    off = find_code_unit(data, start, end, {0x38})
    if off is None:
        print(f"{label}: password mismatch branch already absent")
        return False
    data[off : off + 4] = b"\x00\x00\x00\x00"
    print(f"{label}: bypassed password mismatch branch at 0x{off:x}")
    return True


def read_len8(data, off):
    value = data[off]
    off += 1
    if value & 0x80:
        value = ((value & 0x7f) << 8) | data[off]
        off += 1
    return value, off


def read_len16(data, off):
    value = u16(data, off)
    off += 2
    if value & 0x8000:
        value = ((value & 0x7fff) << 16) | u16(data, off)
        off += 2
    return value, off


def parse_string_pool(data, off):
    chunk_type = u16(data, off)
    if chunk_type != 0x0001:
        raise RuntimeError("expected string pool chunk")
    header_size = u16(data, off + 2)
    string_count = u32(data, off + 8)
    flags = u32(data, off + 16)
    strings_start = u32(data, off + 20)
    is_utf8 = bool(flags & 0x00000100)
    offsets_off = off + header_size
    strings_base = off + strings_start

    strings = []
    for i in range(string_count):
        string_off = strings_base + u32(data, offsets_off + i * 4)
        if is_utf8:
            _, pos = read_len8(data, string_off)
            byte_len, pos = read_len8(data, pos)
            strings.append(data[pos : pos + byte_len].decode("utf-8", errors="replace"))
        else:
            char_len, pos = read_len16(data, string_off)
            strings.append(data[pos : pos + char_len * 2].decode("utf-16le", errors="replace"))
    return strings


def find_manifest_strings(data):
    if u16(data, 0) != 0x0003:
        raise RuntimeError("not an Android binary XML file")
    off = 8
    while off < len(data):
        chunk_type = u16(data, off)
        chunk_size = u32(data, off + 4)
        if chunk_type == 0x0001:
            return parse_string_pool(data, off)
        off += chunk_size
    raise RuntimeError("manifest string pool not found")


def attr_value(strings, data, attr_off):
    raw_value = u32(data, attr_off + 8)
    value_type = data[attr_off + 15]
    value_data = u32(data, attr_off + 16)
    if raw_value != 0xffffffff:
        return strings[raw_value]
    if value_type == 0x03:
        return strings[value_data]
    return value_data


def patch_manifest_alias(data):
    strings = find_manifest_strings(data)
    off = 8
    while off < len(data):
        chunk_type = u16(data, off)
        chunk_size = u32(data, off + 4)
        if chunk_size <= 0:
            raise RuntimeError(f"invalid XML chunk at 0x{off:x}")

        if chunk_type == 0x0102:
            element_name = strings[u32(data, off + 20)]
            if element_name == "activity-alias":
                attr_start = u16(data, off + 24)
                attr_size = u16(data, off + 26)
                attr_count = u16(data, off + 28)
                attrs_off = off + 16 + attr_start
                name_value = None
                enabled_attr = None
                for i in range(attr_count):
                    attr_off = attrs_off + i * attr_size
                    attr_name = strings[u32(data, attr_off + 4)]
                    if attr_name == "name":
                        name_value = attr_value(strings, data, attr_off)
                    elif attr_name == "enabled":
                        enabled_attr = attr_off

                if name_value == DEV_ALIAS:
                    if enabled_attr is None:
                        raise RuntimeError("DeveloperModeActivityAlias has no enabled attr")
                    if data[enabled_attr + 15] != 0x12:
                        raise RuntimeError("DeveloperModeActivityAlias enabled attr is not boolean")
                    current = u32(data, enabled_attr + 16)
                    if current == 0xffffffff:
                        print("AndroidManifest.xml: DeveloperModeActivityAlias already enabled")
                        return False
                    struct.pack_into("<I", data, enabled_attr + 16, 0xffffffff)
                    print("AndroidManifest.xml: enabled DeveloperModeActivityAlias")
                    return True

        off += chunk_size

    raise RuntimeError("DeveloperModeActivityAlias manifest entry not found")


def patch_classes11(path):
    data = bytearray(Path(path).read_bytes())
    changed = patch_is_dev_mode_enabled(data)
    changed |= patch_is_dev_mode_enabled_cache(data)
    if changed:
        update_dex_header(data)
        Path(path).write_bytes(data)


def patch_settings_dex(path):
    label = Path(path).name
    data = bytearray(Path(path).read_bytes())
    changed = False
    found = False
    for patcher, class_name in (
        (patch_is_already_allowed, DEV_UTILS),
        (patch_password_handler, PASSWORD_HANDLER),
    ):
        try:
            changed |= patcher(data, label)
            found = True
        except RuntimeError as exc:
            if f"class not found: {class_name}" not in str(exc):
                raise
            print(f"{label}: {class_name} not present; skipping")
    if changed:
        update_dex_header(data)
        Path(path).write_bytes(data)
    return found


def patch_manifest(path):
    data = bytearray(Path(path).read_bytes())
    if patch_manifest_alias(data):
        Path(path).write_bytes(data)


def main():
    if len(sys.argv) < 4:
        print(
            "usage: patch_smartsuggestions_dev_mode.py "
            "<classes11.dex> <settings.dex> [<settings.dex> ...] <AndroidManifest.xml>",
            file=sys.stderr,
        )
        return 2
    patch_classes11(sys.argv[1])
    found_settings_dex = False
    for path in sys.argv[2:-1]:
        found_settings_dex |= patch_settings_dex(path)
    if not found_settings_dex:
        raise RuntimeError("developer mode settings classes not found in supplied dex files")
    patch_manifest(sys.argv[-1])
    return 0


if __name__ == "__main__":
    sys.exit(main())
