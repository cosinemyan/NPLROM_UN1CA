#!/usr/bin/env python3
"""Resolve DressRoom font-hook paths without editing decoded smali.

Only class/dex relocation is supported. Instruction/register changes, missing
hooks and ambiguous matches must be reviewed instead of applying with fuzz.
The complete generated patch is dry-run before it is returned to the build.
"""

import argparse
import os
from pathlib import Path
import re
import subprocess
import sys


# Template path -> stable API anchor and minimum number of local registers.
HOOKS = {
    "smali_classes2/yc/x.smali": ("getClockFontList()[I", 4),
    "smali_classes2/ig/b.smali": ("getSystemFontList()Ljava/util/ArrayList;", 3),
    "smali_classes2/yc/a0.smali": ("getSystemFontMap()Ljava/util/HashMap;", 3),
}
MANAGER = "Lcom/samsung/android/clockpack/plugins/clock/ClockManager;->"


def resolve(decoded, template):
    parts = re.split(r"(?=^diff -ruN )", template, flags=re.MULTILINE)
    needles = {}
    for part in parts[1:]:
        header = re.search(r"^--- base/(\S+)$", part, re.MULTILINE)
        if header is None or header[1] not in HOOKS:
            continue  # Keep the four new ClockFontBank classes verbatim.
        path = header[1]
        if path in needles:
            raise ValueError(f"Duplicate template section: {path}")
        hunks = re.split(r"^@@[^\n]*@@[^\n]*\n", part, flags=re.MULTILINE)
        if len(hunks) != 2:
            raise ValueError(f"Expected one hook hunk: {path}")
        # GNU patch also accepts an empty context line without its leading space.
        old = "".join(line[1:] if line.startswith((" ", "-")) else line
                      for line in hunks[1].splitlines(keepends=True)
                      if not line.startswith("+"))
        if MANAGER + HOOKS[path][0] not in old:
            raise ValueError(f"Missing API anchor in template: {path}")
        needles[path] = old
    if needles.keys() != HOOKS.keys():
        raise ValueError("Expected all three DressRoom font hooks in template")

    matches = {path: [] for path in HOOKS}
    for dex in sorted(decoded.glob("smali*")):
        if not dex.is_dir():
            continue
        for smali in dex.rglob("*.smali"):
            code = smali.read_text()
            if MANAGER not in code:
                continue
            for path, needle in needles.items():
                start = 0
                while (pos := code.find(needle, start)) != -1:
                    matches[path].append((smali, code, pos))
                    start = pos + 1

    for path, candidates in matches.items():
        if len(candidates) != 1:
            raise ValueError(f"{HOOKS[path][0]}: expected one exact hook, found "
                             f"{len(candidates)}. Use a clean decode; changed "
                             "instructions/registers require patch review.")
        smali, code, pos = candidates[0]
        method_start = code.rfind("\n.method ", 0, pos)
        method_end = code.find("\n.end method", method_start)
        if method_start < 0 or method_end < pos + len(needles[path]):
            raise ValueError(f"Hook is not inside a single method: {smali}")
        locals_match = re.search(r"^    \.locals (\d+)$",
                                 code[method_start:pos], re.MULTILINE)
        if locals_match is None or int(locals_match[1]) < HOOKS[path][1]:
            raise ValueError(f"Unsupported register layout: {smali}")
        resolved = smali.relative_to(decoded).as_posix()
        # Replace diff paths only; never rewrite class names or instructions.
        for index, part in enumerate(parts):
            if part.startswith(f"diff -ruN base/{path} mod/{path}\n"):
                parts[index] = part.replace(f"base/{path}", f"base/{resolved}", 2)
                parts[index] = parts[index].replace(f"mod/{path}", f"mod/{resolved}", 2)
        print(f"DressRoom: {path} -> {resolved}", file=sys.stderr)
    return "".join(parts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("decoded", type=Path)
    parser.add_argument("template", type=Path)
    args = parser.parse_args()
    try:
        patch = resolve(args.decoded, args.template.read_text())
        result = subprocess.run(
            ["patch", "--batch", "--dry-run", "--fuzz=0", "-p1", "-N",
             "--forward", "-d", str(args.decoded)],
            input=patch, text=True, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, env={**os.environ, "LC_ALL": "C"},
        )
        print(result.stdout, file=sys.stderr, end="")
        if result.returncode:
            raise ValueError("Complete font patch failed strict dry-run; no smali changed")
    except (OSError, ValueError) as error:
        parser.exit(1, f"DressRoom font patch: {error}\n")
    sys.stdout.write(patch)


if __name__ == "__main__":
    main()
