# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Offline ARM64 5.15 module ABI checks; never execute or load target code."""
import array
import re
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
import zlib


def require(condition, message):
    if not condition:
        raise ValueError(message)


def cstring(data, offset):
    require(0 <= offset < len(data), "String offset outside ELF section")
    end = data.find(b"\0", offset)
    require(end >= 0, "Unterminated ELF string")
    return data[offset:end].decode("ascii")


def module_abi(data):
    """Read CRC imports/exports from an ELF64 little-endian ARM64 module."""
    require(len(data) >= 64 and data[:6] == b"\x7fELF\x02\x01",
            "Expected ELF64 little-endian module")
    require(struct.unpack_from("<HH", data, 16) == (1, 183),
            "Expected relocatable ARM64 module")
    offset = struct.unpack_from("<Q", data, 40)[0]
    size, count, strings = struct.unpack_from("<HHH", data, 58)
    require(size == 64 and 0 < strings < count and offset + count * size <= len(data),
            "Invalid ELF section table")
    sections = [struct.unpack_from("<IIQQQQIIQQ", data, offset + i * size)
                for i in range(count)]

    def section(index):
        require(0 <= index < count, "Invalid ELF section index")
        row = sections[index]
        require(row[4] + row[5] <= len(data), "Truncated ELF section")
        return data[row[4]:row[4] + row[5]]

    names = [cstring(section(strings), row[0]) for row in sections]
    require(names.count("__versions") == 1, "Missing/ambiguous module __versions table")
    versions = section(names.index("__versions"))
    require(versions and len(versions) % 64 == 0, "Invalid module __versions table")
    imports = {}
    for offset in range(0, len(versions), 64):
        name = cstring(versions[offset:offset + 64], 8)
        crc = struct.unpack_from("<Q", versions, offset)[0]
        require(name and name not in imports and crc <= 0xffffffff,
                "Invalid/duplicate versioned module import")
        imports[name] = crc
    require("module_layout" in imports, "Module lacks module_layout ABI CRC")
    exports = {}
    for index, row in enumerate(sections):
        if row[1] != 2:  # SHT_SYMTAB
            continue
        symbols, names_data = section(index), section(row[6])
        require(row[9] == 24 and len(symbols) % 24 == 0, "Invalid ELF symbol table")
        for offset in range(0, len(symbols), 24):
            name, _, _, owner, value, _ = struct.unpack_from("<IBBHQQ", symbols, offset)
            name = cstring(names_data, name)
            if name.startswith("__crc_") and owner == 0xfff1:  # SHN_ABS
                require(value <= 0xffffffff, "Invalid exported symbol CRC")
                exports[name[6:]] = value
    return imports, exports


def kernel_profile(image, policy):
    require(len(image) >= 64 and image[56:60] == b"ARM\x64", "Expected ARM64 kernel Image")
    release = re.search(rb"Linux version ([^ \x00\n]+)", image)
    require(release is not None, "Kernel release banner unavailable")
    release = release[1].decode("ascii")
    require(re.match(re.escape(policy["release_family"]) + r"\.\d+(?:-|$)", release),
            f"Unsupported Wondertap kernel family: {release}")
    start = image.find(b"IKCFG_ST")
    require(start >= 0, "Kernel embedded configuration unavailable; cannot verify module ABI")
    config = zlib.decompress(image[start + 8:], 31).decode("ascii")
    values = dict(re.findall(r"^(CONFIG_\w+)=(.*)$", config, re.M))
    for key, expected in policy["required_config"].items():
        actual = values.get(key, "n")
        require(actual == expected,
                f"Kernel ABI configuration mismatch: {key}={actual}, expected {expected}")
    return release


def kernel_exports(image):
    """Recover linked PREL32 ksymtab + adjacent absolute CRC tables.

    This supported GKI layout was calibrated against the complete 220 build's
    Module.symvers. An unknown layout is rejected, never treated as compatible.
    """
    words = array.array("i")
    words.frombytes(image[:len(image) // 4 * 4])
    require(words.itemsize == 4, "Host has unsupported integer width")
    if sys.byteorder != "little":
        words.byteswap()
    targets = {match.start() for match in re.finditer(b"module_layout\0", image)}

    def entry(offset):
        if offset < 0 or offset + 12 > len(image):
            return None
        value, name, namespace = struct.unpack_from("<iii", image, offset)
        name += offset + 4
        namespace += offset + 8
        # Exported data may live in BSS beyond the file-backed Image.
        if not (0 <= offset + value < 0x10000000 and
                0 <= name < len(image) and 0 <= namespace < len(image)):
            return None
        end = image.find(b"\0", name, name + 256)
        if end < 0 or not re.fullmatch(rb"[A-Za-z_][A-Za-z_0-9]*", image[name:end]):
            return None
        if image[namespace] and not re.match(
                rb"[A-Za-z_][A-Za-z_0-9]*\0", image[namespace:namespace + 128]):
            return None
        return image[name:end].decode("ascii")

    candidates = [i * 4 - 4 for i, value in enumerate(words)
                  if i * 4 + value in targets and entry(i * 4 - 4) == "module_layout"]
    require(len(candidates) == 1, "Cannot identify kernel export table unambiguously")
    start = end = candidates[0]
    while entry(start - 12):
        start -= 12
    while entry(end):
        end += 12
    names = [entry(offset) for offset in range(start, end, 12)]
    require(len(names) > 10000 and len(names) == len(set(names)) and
            sum(names[i] < names[i - 1] for i in range(1, len(names))) == 1,
            "Unsupported kernel export-table layout")
    require(end + 4 * len(names) <= len(image), "Truncated kernel CRC tables")
    return dict(zip(names, struct.unpack_from("<" + "I" * len(names), image, end)))


def check_imports(name, imports, providers):
    for symbol, expected in imports.items():
        require(symbol in providers, f"{name}: missing ABI symbol {symbol}")
        actual = providers[symbol]
        require(actual == expected,
                f"{name}: ABI CRC mismatch for {symbol}: "
                f"module=0x{expected:08x}, provider=0x{actual:08x}")


def cpio_entries(data):
    entries = {}
    offset = 0
    while offset + 110 <= len(data):
        require(data[offset:offset + 6] in (b"070701", b"070702"),
                "Unsupported vendor ramdisk CPIO format")
        fields = [int(data[offset + 6 + i * 8:offset + 14 + i * 8], 16)
                  for i in range(13)]
        size, length = fields[6], fields[11]
        require(length > 0 and offset + 110 + length <= len(data), "Truncated CPIO name")
        name = cstring(data[offset + 110:offset + 110 + length], 0)
        start = (offset + 110 + length + 3) & ~3
        require(start + size <= len(data), "Truncated CPIO entry")
        if name == "TRAILER!!!":
            return entries
        require(name not in entries, f"Duplicate CPIO entry: {name}")
        entries[name] = data[start:start + size]
        offset = (start + size + 3) & ~3
    raise ValueError("Missing vendor ramdisk CPIO trailer")


def vendor_boot_modules(image, magiskboot):
    """Read only normally preloaded providers; recovery-only files aren't loaded."""
    with image.open("rb") as stream:
        require(stream.read(8) == b"VNDRBOOT", "Expected vendor_boot image")
    with tempfile.TemporaryDirectory(prefix="wondertap-vendor-abi-") as temp:
        tool = Path(temp) / "magiskboot"
        shutil.copy2(magiskboot, tool)
        tool.chmod(tool.stat().st_mode | 0o100)
        result = subprocess.run([str(tool), "unpack", str(image.resolve())],
                                cwd=temp, capture_output=True, text=True)
        # This cached magiskboot reports successful vendor_boot unpack as 3.
        # The new temporary directory and CPIO checks below prevent stale output.
        require(result.returncode in (0, 3), "Cannot unpack vendor_boot for ABI validation: " +
                result.stderr[-1000:])
        archives = sorted(Path(temp).glob("vendor_ramdisk/*.cpio"))
        require(archives, "No vendor_boot ramdisk available for ABI validation")
        entries = {}
        for archive in archives:
            for name, data in cpio_entries(archive.read_bytes()).items():
                require(name not in entries or entries[name] == data,
                        f"Conflicting vendor ramdisk entry: {name}")
                entries[name] = data
        loads = [data for name, data in entries.items() if Path(name).name == "modules.load"]
        require(loads, "Missing vendor ramdisk modules.load")
        names = {Path(line.split("#", 1)[0].strip()).name
                 for data in loads for line in data.decode("ascii").splitlines()
                 if line.split("#", 1)[0].strip()}
        modules = {Path(name).name: data for name, data in entries.items()
                   if name.endswith(".ko") and Path(name).name in names}
        require(names <= modules.keys(), "Missing preloaded vendor ramdisk module")
        return modules


def validate(image, policy, modules, preloaded=None):
    release = kernel_profile(image, policy)
    providers = dict(kernel_exports(image))
    parsed = {}
    def add(name, data):
        try:
            imports, exports = module_abi(data)
        except (ValueError, UnicodeError, struct.error) as error:
            raise ValueError(f"{name}: {error}") from error
        parsed[name] = imports
        for symbol, crc in exports.items():
            require(symbol not in providers,
                    f"{name}: duplicate ABI symbol provider {symbol}")
            providers[symbol] = crc
    for name, data in modules.items():
        add(name, data)
    # Resolve first-stage provider modules only when the Wi-Fi stack needs them,
    # then recursively validate their imports too. Do not approve unrelated mods.
    catalog = {}
    preloaded = {name: data for name, data in (preloaded or {}).items() if name not in modules}
    for name, data in preloaded.items():
        _, exports = module_abi(data)
        for symbol in exports:
            catalog.setdefault(symbol, []).append(name)
    checked = set()
    while parsed.keys() - checked:
        name = next(iter(parsed.keys() - checked))
        for symbol in parsed[name]:
            if symbol not in providers and symbol in catalog:
                candidates = catalog[symbol]
                require(len(candidates) == 1, f"Ambiguous provider for ABI symbol {symbol}")
                candidate = candidates[0]
                if candidate not in parsed:
                    add(candidate, preloaded[candidate])
        check_imports(name, parsed[name], providers)
        checked.add(name)
    print(f"Validated kernel ABI: {release}, {len(parsed)} modules, "
          f"{sum(map(len, parsed.values()))} versioned imports")
    print("Kernel Image identity is informational; runtime Wi-Fi/CFI and Apple sharing remain unverified")
