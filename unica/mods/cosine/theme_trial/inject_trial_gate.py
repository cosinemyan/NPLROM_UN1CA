#!/usr/bin/env python3
"""Prefix ThemeCenter period methods with persist.sys.unica.theme_trial early-return."""
from __future__ import annotations

import re
import sys
from pathlib import Path

PROP = "persist.sys.unica.theme_trial"
MARKER = "npl_theme_trial_gate"

TARGETS: dict[str, list[tuple[str, str]]] = {
    "PeriodManager.smali": [
        ("isAlarmExist(I)Z", "false"),
        ("setSideloadAlarm(IJLjava/lang/String;)V", "void"),
        ("setAlarm(IJLjava/lang/String;)V", "void"),
        ("cancelSideloadAlarm(I)V", "void"),
        ("cancelAlarm(I)V", "void"),
    ],
    "ThemeNotiUtils.smali": [
        ("postTrialNotification(Landroid/content/Context;Ljava/lang/String;ZLjava/lang/String;)V", "void"),
        ("setIsTrialEnd(Z)V", "void"),
        ("setTrialExpiredPackage(Ljava/lang/String;)V", "void"),
        ("clearTrialNotification(Landroid/content/Context;)V", "void"),
    ],
}


def _const_zero(reg: str, n: int) -> str:
    # const/4 only accepts v0–v15.
    if n <= 15:
        return f"    const/4 {reg}, 0x0"
    return f"    const/16 {reg}, 0x0"


def gate_lines(kind: str, r0: int, r1: int, label: str) -> list[str]:
    v0, v1 = f"v{r0}", f"v{r1}"
    if r0 <= 15 and r1 <= 15:
        invoke = (
            f"    invoke-static {{{v0}, {v1}}}, "
            "Landroid/os/SystemProperties;->getBoolean(Ljava/lang/String;Z)Z"
        )
    else:
        invoke = (
            f"    invoke-static/range {{{v0} .. {v1}}}, "
            "Landroid/os/SystemProperties;->getBoolean(Ljava/lang/String;Z)Z"
        )
    lines = [
        f"    # {MARKER}",
        f"    const-string {v0}, \"{PROP}\"",
        _const_zero(v1, r1),
        invoke,
        f"    move-result {v0}",
        f"    if-eqz {v0}, :{label}",
    ]
    if kind == "void":
        lines.append("    return-void")
    elif kind == "false":
        lines.append(_const_zero(v0, r0))
        lines.append(f"    return {v0}")
    else:
        raise ValueError(kind)
    lines.append(f"    :{label}")
    lines.append("")
    return [line + "\n" for line in lines]


def inject_method(block: list[str], kind: str, idx: int) -> list[str]:
    rest = block[1:]
    locals_idx = None
    locals_n = 0
    for i, line in enumerate(rest):
        match = re.match(r"^([ \t]*\.locals )(\d+)", line)
        if match:
            locals_idx = i
            locals_n = int(match.group(2))
            rest[i] = f"{match.group(1)}{locals_n + 2}\n"
            break
    gate = gate_lines(kind, locals_n, locals_n + 1, f"npl_tt_{idx}")
    if locals_idx is None:
        rest = ["    .locals 2\n"] + gate + rest
    else:
        rest = rest[: locals_idx + 1] + gate + rest[locals_idx + 1 :]
    return [block[0]] + rest


def patch_file(path: Path, methods: list[tuple[str, str]]) -> int:
    text = path.read_text(encoding="utf-8", errors="replace")
    if MARKER in text:
        return 0
    lines = text.splitlines(keepends=True)
    out: list[str] = []
    patched = 0
    i = 0
    while i < len(lines):
        line = lines[i]
        matched = None
        if line.startswith(".method"):
            for sig, kind in methods:
                if sig in line:
                    matched = (sig, kind)
                    break
        if matched is None:
            out.append(line)
            i += 1
            continue
        block = [line]
        i += 1
        while i < len(lines) and not lines[i].startswith(".end method"):
            block.append(lines[i])
            i += 1
        if i < len(lines):
            block.append(lines[i])
            i += 1
        out.extend(inject_method(block, matched[1], patched))
        patched += 1
    if patched != len(methods):
        raise SystemExit(f"{path}: patched {patched}/{len(methods)} methods")
    path.write_text("".join(out), encoding="utf-8")
    return patched


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: inject_trial_gate.py <apktool-decoded-ThemeCenter-dir>", file=sys.stderr)
        return 2
    root = Path(sys.argv[1])
    if not root.is_dir():
        print(f"not a directory: {root}", file=sys.stderr)
        return 1
    total = 0
    for name, methods in TARGETS.items():
        matches = list(root.rglob(name))
        matches = [p for p in matches if "thememanager/period" in str(p).replace("\\", "/")]
        if not matches:
            print(f"missing {name} under {root}", file=sys.stderr)
            return 1
        total += patch_file(matches[0], methods)
        print(f"  - {matches[0].relative_to(root)}: {len(methods)} method(s)")
    print(f"  - Theme trial gate on {total} method(s) ({PROP})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
