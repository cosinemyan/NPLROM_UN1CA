#!/usr/bin/env python3
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real ROM storage adaptation against an in-memory SettingsProvider."""
from pathlib import Path
import os
import subprocess
import tempfile
import urllib.request
ROOT=Path(__file__).resolve().parents[1]
REPO=ROOT.parents[3]
WORK=Path(tempfile.mkdtemp(prefix='tee-settings-tests-',dir='/tmp'))
SDK=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'Android/Sdk')))
android=SDK/'platforms/android-37.0/android.jar'
jsonjar=WORK/'json.jar'
with urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',timeout=30) as r:jsonjar.write_bytes(r.read())
files={
'android/content/Context.java': '''package android.content; public class Context { public ContentResolver getContentResolver(){return null;} public android.content.pm.PackageManager getPackageManager(){return null;} }''',
'android/provider/Settings.java': '''package android.provider; public class Settings { public static class Secure { public static final java.util.Map<String,String> values=new java.util.HashMap<>(); public static String getString(android.content.ContentResolver r,String key){return values.get(key);} public static boolean putString(android.content.ContentResolver r,String key,String value){values.put(key,value);return true;} } }''',
'android/os/SystemProperties.java': '''package android.os; public class SystemProperties { public static String get(String key,String fallback){if(key.equals("persist.sys.pif.security_patch"))return "2026-11-05";if(key.equals("ro.build.version.security_patch"))return "2026-10-05";return fallback;} }''',
'android/os/SystemClock.java': '''package android.os; public class SystemClock {public static long elapsedRealtime(){return 100000;} }'''}
for name,body in files.items():
 p=WORK/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body)
helpers=REPO/'unica/mods/settings/keybox/java/io/mesalabs/unica/keybox'
subprocess.run(['javac','--release','17','-cp',str(jsonjar)+os.pathsep+str(android),'-d',str(WORK),*[str(WORK/n) for n in files],
str(ROOT/'java/io/mesalabs/unica/keybox/RomStore.java'),str(ROOT/'tests/RomStoreTest.java'),*[str(helpers/(n+'.java')) for n in ('Profiles','Keybox','Roots','Backend','Diagnostics')]],check=True)
subprocess.run(['java','-cp',os.pathsep.join(map(str,[WORK,jsonjar,android])),'io.mesalabs.unica.keybox.RomStoreTest'],check=True)
print('Host test workspace:',WORK)
