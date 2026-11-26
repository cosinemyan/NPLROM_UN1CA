#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generate hooks against the S23U keyboard after its existing 0001 patch."""
import difflib
from pathlib import Path
import sys

decoded, compiled, output = map(Path, sys.argv[1:])
parts = ["Subject: [PATCH] Keep Qwen replies until manual input or editor exit\n\n"]


def add_diff(before_lines, after_lines, fromfile, tofile):
    for line in difflib.unified_diff(before_lines, after_lines,
                                     fromfile=fromfile, tofile=tofile):
        if line == " \n":
            line = "\n"
        elif line.startswith("+") and not line.startswith("+++"):
            line = line.rstrip(" \t\r\n") + "\n"
        parts.append(line)


def edit(relative, transform):
    before = (decoded / relative).read_text()
    after = transform(before)
    assert before != after
    add_diff(before.splitlines(True), after.splitlines(True),
             fromfile="a/" + relative, tofile="b/" + relative)


def service(source):
    hooks = {
        ".method public onStartInput(Landroid/view/inputmethod/EditorInfo;Z)V":
            "    invoke-static {p0, p1, p2}, Lio/mesalabs/rezoss/ai/KeyboardReplyRetention;->start(Landroid/inputmethodservice/InputMethodService;Landroid/view/inputmethod/EditorInfo;Z)V\n\n",
        ".method public onFinishInput()V":
            "    invoke-static {}, Lio/mesalabs/rezoss/ai/KeyboardReplyRetention;->finish()V\n\n",
        ".method public onFinishInputView(Z)V":
            "    invoke-static {}, Lio/mesalabs/rezoss/ai/KeyboardReplyRetention;->finish()V\n\n",
        ".method public onUpdateSelection(IIIIII)V":
            "    invoke-static {p0}, Lio/mesalabs/rezoss/ai/KeyboardReplyRetention;->selection(Landroid/inputmethodservice/InputMethodService;)V\n\n",
    }
    for method, instructions in hooks.items():
        start = source.index(method)
        pos = source.index("\n\n", source.index("    .locals", start)) + 2
        source = source[:pos] + instructions + source[pos:]
    for instruction, hook in (
        ("    invoke-super {p0, p1, p2}, Landroid/inputmethodservice/InputMethodService;->onStartInputView(Landroid/view/inputmethod/EditorInfo;Z)V",
         "    invoke-static {}, Lio/mesalabs/rezoss/ai/ShadeReplyKeyboard;->viewStarted()V"),
        ("    invoke-super {p0, p1, p2}, Landroid/inputmethodservice/InputMethodService;->onAppPrivateCommand(Ljava/lang/String;Landroid/os/Bundle;)V",
         "    invoke-static {p1, p2}, Lio/mesalabs/rezoss/ai/ShadeReplyKeyboard;->command(Ljava/lang/String;Landroid/os/Bundle;)V"),
    ):
        assert source.count(instruction) == 1
        source = source.replace(instruction, instruction + "\n\n" + hook)
    return source


edit("smali_classes3/com/samsung/android/honeyboard/service/HoneyBoardService.smali", service)


def receiver(source):
    start = source.index(".method public onReceive(Landroid/content/Context;Landroid/content/Intent;)V")
    pos = source.index('    const-string p1, "intent"', start)
    source = source[:pos] + '''    invoke-static {p0, p2, p1}, Lio/mesalabs/rezoss/ai/KeyboardReplyRetention;->consume(Ljava/lang/Object;Landroid/content/Intent;Landroid/content/Context;)Z

    move-result v0

    if-eqz v0, :rezoss_original_receive

    return-void

    :rezoss_original_receive
''' + source[pos:]

    marker = "    invoke-direct {v4, v5, v3}, Lcom/samsung/android/honeyboard/base/suggestedreplies/data/SuggestedData$e;-><init>(Lcom/samsung/android/honeyboard/base/suggestedreplies/data/SuggestedData$State;Ljava/lang/String;)V"
    assert source.count(marker) == 1
    return source.replace(marker, marker + "\n\n    invoke-static {v4}, Lio/mesalabs/rezoss/ai/ShadeReplyKeyboard;->tagItem(Ljava/lang/Object;)V")


edit("smali_classes2/com/samsung/android/honeyboard/base/suggestedreplies/receiver/SuggestedRepliesReceiver.smali", receiver)

def hide(source):
    start = source.index(".method public final k()V")
    pos = source.index("    invoke-direct", start)
    return source[:pos] + '''    invoke-static {}, Lio/mesalabs/rezoss/ai/KeyboardReplyRetention;->keep()Z

    move-result v0

    if-eqz v0, :rezoss_original_hide

    return-void

    :rezoss_original_hide
''' + source[pos:]


edit("smali_classes2/jk/t.smali", hide)

def rts_manager(source):
    marker = "    iput-object v1, p0, Lcom/samsung/android/honeyboard/icecone/rts/manager/RtsManager;->rtsAppsPolicyManager:Lmo/e;\n"
    assert source.count(marker) == 1
    return source.replace(marker, marker + """
    invoke-static {v1}, Lio/mesalabs/rezoss/ai/KeyboardRtsPolicy;->enable(Ljava/lang/Object;)V
""")


edit("smali_classes2/com/samsung/android/honeyboard/icecone/rts/manager/RtsManager.smali", rts_manager)

def rts_allowlist(source):
    old = '''    {
      "item": "org.telegram.messenger",
      "default_on": false
    },
'''
    new = '''    {
      "item": "org.telegram.messenger",
      "default_on": true
    },
    {
      "item": "org.telegram.messenger.web",
      "default_on": true
    },
'''
    assert source.count(old) == 1
    return source.replace(old, new)


edit("res/raw/sticker_rts_allow_list.json", rts_allowlist)

def click_guard(source):
    marker = ".method public final N(Landroid/content/Context;Lcom/samsung/android/honeyboard/base/suggestedreplies/data/SuggestedData;IZ)V\n    .locals 3\n"
    assert source.count(marker) == 1
    return source.replace(marker, marker + """
    invoke-static {p2}, Lio/mesalabs/rezoss/ai/ShadeReplyKeyboard;->allowClick(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :rezoss_shade_reply_click_allowed

    return-void

    :rezoss_shade_reply_click_allowed
""")


edit("smali_classes3/ok0/r.smali", click_guard)


def floating_replies(source):
    marker = "    check-cast v1, Lcom/samsung/android/honeyboard/base/suggestedreplies/data/SuggestedData$e;"
    assert source.count(marker) == 1
    return source.replace(marker, """    invoke-static {v1}, Lio/mesalabs/rezoss/ai/ShadeReplyKeyboard;->isBridgeItem(Ljava/lang/Object;)Z

    move-result v2

    if-nez v2, :cond_0

""" + marker)


edit("smali_classes2/com/samsung/android/honeyboard/icecone/suggestedreplies/view/SuggestedRepliesViewController.smali", floating_replies)

for path in sorted(compiled.rglob("*.smali")):
    relative = "smali_classes5/" + path.relative_to(compiled).as_posix()
    assert not (decoded / relative).exists()
    add_diff([], path.read_text().splitlines(True),
             fromfile="/dev/null", tofile="b/" + relative)
if output.exists():
    raise SystemExit("Refusing to overwrite existing patch")
output.write_text("".join(parts))
