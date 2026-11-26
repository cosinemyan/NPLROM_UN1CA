#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generate exact Memory v48 notifier/query hooks without editing the decode."""
import difflib
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: generate-patch.py CLEAN_MEMORY_V48_DECODE")
root = Path(sys.argv[1])
target = Path(__file__).resolve().parent / "0001-Add-Memory-index-compatibility.patch"
changes = {
    "smali/com/google/android/gms/internal/mlkit_genai_prompt/ts.smali": [
        ("invoke-virtual {v5, v6, v15, v3}, Landroid/content/ContentResolver;->delete(Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I",
         "invoke-static {v5, v6, v15, v3}, Lio/mesalabs/rezoss/memory/MemoryIndexAdapter;->delete(Landroid/content/ContentResolver;Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I"),
        ("invoke-virtual {v5, v6, v7, v15, v3}, Landroid/content/ContentResolver;->update(Landroid/net/Uri;Landroid/content/ContentValues;Ljava/lang/String;[Ljava/lang/String;)I",
         "invoke-static {v5, v6, v7, v15, v3}, Lio/mesalabs/rezoss/memory/MemoryIndexAdapter;->update(Landroid/content/ContentResolver;Landroid/net/Uri;Landroid/content/ContentValues;Ljava/lang/String;[Ljava/lang/String;)I"),
    ],
    "smali/t8/c.smali": [
        ("invoke-virtual {p1, p2, p3, p4, p6}, Landroid/content/ContentResolver;->query(Landroid/net/Uri;[Ljava/lang/String;Landroid/os/Bundle;Landroid/os/CancellationSignal;)Landroid/database/Cursor;",
         "invoke-static {p1, p2, p3, p4, p6}, Lio/mesalabs/rezoss/memory/MemoryIndexAdapter;->query(Landroid/content/ContentResolver;Landroid/net/Uri;[Ljava/lang/String;Landroid/os/Bundle;Landroid/os/CancellationSignal;)Landroid/database/Cursor;"),
    ],
    "smali_classes2/com/samsung/android/app/memory/data/provider/SearchCraftProvider.smali": [
        ("invoke-virtual {p0, v0, v4, v6, v5}, Landroid/content/ContentResolver;->call(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;",
         "invoke-static {p0, v0, v4, v6, v5}, Lio/mesalabs/rezoss/memory/MemoryIndexAdapter;->call(Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Landroid/os/Bundle;)Landroid/os/Bundle;"),
    ],
}
patch = []
for relative, replacements in changes.items():
    before = (root / relative).read_text()
    after = before
    for old, new in replacements:
        if after.count(old) != 1:
            raise SystemExit(f"Expected one exact v48 selector in {relative}: {old}")
        after = after.replace(old, new)
    patch.extend(difflib.unified_diff(before.splitlines(True), after.splitlines(True),
                                    fromfile="a/" + relative, tofile="b/" + relative))
if target.exists():
    import datetime
    backup = target.with_name(target.name + ".bak." + datetime.datetime.now().strftime("%Y%m%d_%H%M%S"))
    backup.write_bytes(target.read_bytes())
target.write_text("".join(patch))
print(target)
