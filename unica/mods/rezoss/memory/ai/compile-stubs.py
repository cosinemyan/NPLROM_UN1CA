#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Compile-only signatures verified against Memory 1.0.00.48 DEX. Never package stubs."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
sources={
'com/samsung/android/libaihub/aicore/prompt/ExtractMemoryFullPrompt.java': 'package com.samsung.android.libaihub.aicore.prompt; public class ExtractMemoryFullPrompt extends IPrompt {}',
'r20/c.java': 'package r20; public interface c { h getContext(); void resumeWith(Object value); }',
'r20/h.java': 'package r20; public interface h {}',
't20/c.java': 'package t20; public abstract class c implements r20.c { public r20.h getContext(){return null;} public void resumeWith(Object o){} public abstract Object invokeSuspend(Object o); }',
# Actual t20.i extends t20.c; constructor symbol and inherited methods match exact APK.
't20/i.java': 'package t20; public abstract class i extends c { public i(int arity,r20.c completion){} }',
'n20/b0.java': 'package n20; public final class b0 { public static b0 a; }',
'n20/n.java': 'package n20; public final class n {}',
's20/a.java': 'package s20; public enum a { COROUTINE_SUSPENDED }',
'dt/a.java': 'package dt; public final class a { public static void K(Object o){} public static n20.n g(Throwable t){return null;} }',
'w50/e0.java': 'package w50; public final class e0 { public static void l(r20.h c){} }',
'ci/a.java': 'package ci; public interface a { Object a(t20.i c); Object b(String s,r20.c c); default Object c(String s,li.h c){return null;} Object d(Throwable t,t20.i c); default Object e(String s,t20.i c){return null;} }',
'li/h.java': 'package li; public abstract class h extends t20.i { public h(){super(1,null);} }',
'li/w.java': 'package li; public class w { public static com.samsung.android.libaihub.aicore.prompt.PromptType k(android.content.Context c,di.c r){return null;} public Object memoryCloud(android.content.Context c,di.c r,ci.a a,com.samsung.android.libaihub.aicore.prompt.PromptType t,t20.c k){return null;} }',
'di/c.java': 'package di; public interface c { String c(); String e(); }',
'qi/c.java': 'package qi; public final class c { public static java.util.Locale b(android.content.Context c){return null;} }',
'com/samsung/android/libaihub/aicore/prompt/PromptType.java': 'package com.samsung.android.libaihub.aicore.prompt; public enum PromptType { EXTRACT_MEMORY_SUMMARY_COLLECTION_PROMPT; public IPrompt createPrompt(){return null;} }',
'com/samsung/android/libaihub/aicore/prompt/IPrompt.java': 'package com.samsung.android.libaihub.aicore.prompt; public abstract class IPrompt { public void setInputText(String s){} public void setComponentCatalog(String s){} public void setHasImage(boolean b){} public void setImageCollage(boolean b){} public void setTargetLanguageOverride(String s){} public void init(android.content.Context c){} public String getSystemInstruction(){return null;} public String getPrompt(){return null;} }',
}
for name,source in sources.items():
 p=root/name; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(source+'\n')
