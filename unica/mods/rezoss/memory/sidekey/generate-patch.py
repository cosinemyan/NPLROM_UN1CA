#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generate the framework patch without modifying any decoded input."""
import difflib
from pathlib import Path
import sys
import time

root = Path(__file__).resolve().parent
clean, helpers = map(Path, sys.argv[1:])
name = "smali/com/android/server/input/KeyGestureController$LocalKeyGestureEventHandler.smali"
old = (clean / name).read_text()
anchor = """    :cond_2
    iget-object v0, v0, Lcom/android/server/input/KeyGestureController$LocalKeyGestureEventHandler;->this$0:Lcom/android/server/input/KeyGestureController;

    const/16 v1, 0x66"""
replacement = anchor.rsplit("    const/16", 1)[0] + """    invoke-static {v0, v1}, Lio/mesalabs/rezoss/memory/MemorySideKey;->handle(Lcom/android/server/input/KeyGestureController;Landroid/hardware/input/KeyGestureEvent;)Z

    move-result v2

    if-eqz v2, :rezoss_memory_stock_side_key

    return-void

    :rezoss_memory_stock_side_key
    const/16 v1, 0x66"""
if old.count(anchor) != 1:
    raise ValueError("Expected exact Samsung type-1001 branch; revalidate this firmware")
files = {name: (old, old.replace(anchor, replacement, 1))}
generated = sorted(helpers.rglob("*.smali"))
if not generated or any(not str(p.relative_to(helpers)).startswith("io/mesalabs/rezoss/memory/") for p in generated):
    raise ValueError("Missing helpers or packaged compile-only stub")
for path in generated:
    name = "smali_classes2/" + str(path.relative_to(helpers))
    if (clean / name).exists():
        raise ValueError("Helper already present: " + name)
    files[name] = ("", path.read_text())
patch = "From: UN1CA Rezoss\nSubject: [PATCH] Add accessibility-aware Memory Volume Up Side shortcut\n\n"
for name, (before, after) in sorted(files.items()):
    patch += f"diff --git a/{name} b/{name}\n"
    patch += "".join(difflib.unified_diff(before.splitlines(True), after.splitlines(True),
                        fromfile="a/" + name if before else "/dev/null", tofile="b/" + name, n=5))
output = root / "0001-Add-Memory-volume-up-side-key.patch"
if output.exists() and output.read_text() != patch:
    backup = Path("/tmp") / ("rezoss-memory-sidekey-" + str(time.time_ns()) + ".patch.bak")
    backup.write_bytes(output.read_bytes())
    print("Previous patch backed up:", backup)
output.write_text(patch)
print("Generated:", output)
