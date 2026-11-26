#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generate unified patches in memory; never edit decoded input smali."""
from pathlib import Path
import difflib
import re
import sys
import time

ROOT = Path(__file__).resolve().parent
BRIDGE = "Lio/mesalabs/rezoss/memory/MemoryGestureBridge;"
GIH = "Lcom/honeyspace/gesture/GestureInputHandler;"
MFG = "Lcom/honeyspace/gesture/multifinger/MultiFingerGestureInputHandler;"

def replace_once(text, before, after):
    if text.count(before) != 1:
        raise ValueError(f"Expected one exact anchor, found {text.count(before)}: {before[:100]}")
    return text.replace(before, after, 1)

def edit_method(text, signature, transform):
    pattern = re.compile(r"^\.method " + re.escape(signature) + r"\n.*?^\.end method", re.M | re.S)
    matches = list(pattern.finditer(text))
    if len(matches) != 1:
        raise ValueError(f"Expected one exact method: {signature}")
    match = matches[0]
    before = match.group()
    after = transform(before)
    # Original methods retain their register declarations, labels and exception tables.
    for expression in [r"^    \.(?:locals|registers) .*", r"^    :\w+", r"^    \.catch.*"]:
        old = re.findall(expression, before, re.M)
        new = re.findall(expression, after, re.M)
        if any(item not in new for item in old):
            raise ValueError(f"Lost original method structure in {signature}")
        if "locals" in expression and old != new:
            raise ValueError(f"Changed registers in {signature}")
    return text[:match.start()] + after + text[match.end():]

def changes(clean):
    files = {}
    path = "smali_classes3/com/honeyspace/gesture/GestureInputHandler.smali"
    original = (clean / path).read_text()
    def outer(body):
        before = f'''    iget-object p0, p0, {GIH}->gestureSettingsUseCase:Lcom/honeyspace/gesture/usecase/GestureSettingsUseCase;

    invoke-virtual {{p0}}, Lcom/honeyspace/gesture/usecase/GestureSettingsUseCase;->isMultiFingerGestureEnabled()Z

    move-result p0'''
        after = f'''    iget-object v0, p0, {GIH}->gestureSettingsUseCase:Lcom/honeyspace/gesture/usecase/GestureSettingsUseCase;

    invoke-virtual {{v0}}, Lcom/honeyspace/gesture/usecase/GestureSettingsUseCase;->isMultiFingerGestureEnabled()Z

    move-result v0

    iget-object v1, p0, {GIH}->context:Landroid/content/Context;

    invoke-static {{v1, v0}}, {BRIDGE}->gate(Landroid/content/Context;Z)Z

    move-result p0'''
        return replace_once(body, before, after)
    files[path] = (original, edit_method(original, "private final isMultiFingerGestureEnabled()Z", outer))
    path = "smali_classes3/com/honeyspace/gesture/multifinger/MultiFingerGestureInputHandler.smali"
    original = (clean / path).read_text()
    def tiers(body):
        before = "    .locals 2\n"
        after = f'''    .locals 2

    iget-object v0, p0, {MFG}->context:Landroid/content/Context;

    invoke-static {{v0}}, {BRIDGE}->enabled(Landroid/content/Context;)Z

    move-result v0

    if-eqz v0, :rezoss_homeup_tiers

    const/4 v0, 0x1

    return v0

    :rezoss_homeup_tiers
'''
        return replace_once(body, before, after)
    changed = edit_method(original, "private final hasAnyMultiFingerGestureEnabled()Z", tiers)
    def observe(body):
        anchor = "    invoke-virtual {p1}, Landroid/view/MotionEvent;->getActionMasked()I"
        addition = f'''    iget-object v0, p0, {MFG}->context:Landroid/content/Context;

    invoke-static {{v0, p1}}, {BRIDGE}->observe(Landroid/content/Context;Landroid/view/MotionEvent;)V

'''
        return replace_once(body, anchor, addition + anchor)
    changed = edit_method(changed, "public final onMotionEvent(Landroid/view/MotionEvent;)V", observe)
    def dispatch(body):
        before = "    .locals 4\n"
        after = f'''    .locals 4

    iget-object v0, p0, {MFG}->context:Landroid/content/Context;

    iget-object v1, p0, {MFG}->inputMonitor:Lcom/honeyspace/gesture/inputproxy/InputMonitorProxy;

    iget-object v2, p0, {MFG}->trailTouchRelay:Lcom/honeyspace/gesture/multifinger/MultiFingerGestureTrailTouchRelay;

    invoke-static {{v0, p1, v1, v2}}, {BRIDGE}->handle(Landroid/content/Context;Lcom/honeyspace/gesture/multifinger/MultiFingerGestureEvent;Lcom/honeyspace/gesture/inputproxy/InputMonitorProxy;Lcom/honeyspace/gesture/multifinger/MultiFingerGestureTrailTouchRelay;)Z

    move-result v0

    if-eqz v0, :rezoss_homeup_dispatch

    return-void

    :rezoss_homeup_dispatch
'''
        return replace_once(body, before, after)
    changed = edit_method(changed, "private final handleMultiFingerGestureEvent(Lcom/honeyspace/gesture/multifinger/MultiFingerGestureEvent;)V", dispatch)
    files[path] = (original, changed)
    return files

def main():
    if len(sys.argv) != 3:
        raise SystemExit("Usage: generate-patch.py CLEAN_LAUNCHER_DECODE COMPILED_HELPER_SMALI")
    clean, helpers = map(Path, sys.argv[1:])
    files = changes(clean)
    if (clean / "smali_classes6").exists():
        raise ValueError("Launcher already has classes6; choose and validate a free DEX slot")
    generated = sorted(helpers.rglob("*.smali"))
    if not generated or any(not str(p.relative_to(helpers)).startswith("io/mesalabs/rezoss/memory/") for p in generated):
        raise ValueError("Helper input missing or contains compile-only stubs")
    for p in generated:
        files["smali_classes6/" + str(p.relative_to(helpers))] = ("", p.read_text())
    patch = "From: UN1CA Rezoss\nSubject: [PATCH] Add independent Memory capture gestures to launcher\n\n"
    for name, (old, new) in sorted(files.items()):
        patch += "diff --git a/" + name + " b/" + name + "\n"
        patch += "".join(difflib.unified_diff(old.splitlines(True), new.splitlines(True),
                    fromfile="a/" + name if old else "/dev/null", tofile="b/" + name, n=5))
    output = ROOT / "patches/0001-Add-independent-Memory-capture-gestures.patch"
    if output.exists() and output.read_text() != patch:
        backup = Path("/tmp") / ("rezoss-memory-gesture-patch-" + str(time.time_ns()) + ".bak")
        backup.write_bytes(output.read_bytes())
        print("Previous patch backed up:", backup)
    output.write_text(patch)
    print("Generated:", output)

if __name__ == "__main__":
    main()
