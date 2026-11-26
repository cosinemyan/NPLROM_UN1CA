#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
from pathlib import Path
import sys,shutil,datetime
root=Path(__file__).resolve().parent.parent
output=root/'smali/system/priv-app/SecSettings/SecSettings.apk/0014-Add-Troubleshooting.patch'
parts=['From 0000000000000000000000000000000000000000 Mon Sep 17 00:00:00 2001\nFrom: UN1CA <unica@local>\nSubject: [PATCH] Add user-reviewed module troubleshooting and AI log analysis\n\nGenerated from settings/troubleshooting/java.\n\n']
for p in sorted(Path(sys.argv[1]).rglob('*.smali')):
    if 'io/mesalabs/unica/troubleshooting/' not in p.as_posix():continue
    path='smali_classes4/'+p.relative_to(sys.argv[1]).as_posix();lines=p.read_text().splitlines(keepends=True)
    parts.append(f'diff --git a/{path} b/{path}\nnew file mode 100644\n--- /dev/null\n+++ b/{path}\n@@ -0,0 +1,{len(lines)} @@\n');parts.extend('+'+l for l in lines);parts.append('\n')
# Diagnostic permissions only. Existing framework methods/registers are untouched.
parts.append('''diff --git a/AndroidManifest.xml b/AndroidManifest.xml
--- a/AndroidManifest.xml
+++ b/AndroidManifest.xml
@@ -1,1 +1,4 @@
-    <uses-permission android:name="android.permission.KILL_UID"/>
+    <uses-permission android:name="io.mesalabs.rezoss.permission.QWEN_ENGINE"/>
+    <uses-permission android:name="android.permission.READ_LOGS"/>
+    <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>
+    <uses-permission android:name="android.permission.KILL_UID"/>
''')
content=''.join(parts)
if output.exists() and output.read_text()!=content:shutil.copy2(output,str(output)+'.bak.'+datetime.datetime.now().strftime('%Y%m%d_%H%M%S'))
output.write_text(content);print(output)
