#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
[[ $# == 1 ]] || { echo "Usage: source buildenv.sh dm3q; $0 CLEAN_SERVICES_DECODE" >&2; exit 2; }
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
MEMORY_KEY_SOURCE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
MEMORY_KEY_SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
MEMORY_KEY_ANDROID_JAR="${ANDROID_JAR:-$MEMORY_KEY_SDK/platforms/android-37.0/android.jar}"
MEMORY_KEY_D8="${D8:-$MEMORY_KEY_SDK/build-tools/37.0.0/d8}"
MEMORY_KEY_BUILD=$(mktemp -d /tmp/rezoss-memory-sidekey-build.XXXXXX)
mkdir -p "$MEMORY_KEY_BUILD/classes" "$MEMORY_KEY_BUILD/dex"
# Compile-only hidden API declarations. Only io/ is passed to D8 and packaged.
python3 - "$MEMORY_KEY_BUILD/stubs" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
sources = {
"android/content/Context.java": '''package android.content;
public abstract class Context {
 public abstract ContentResolver getContentResolver();
 public abstract <T> T getSystemService(Class<T> type);
 public abstract Context createContextAsUser(android.os.UserHandle user, int flags);
 public abstract android.content.pm.PackageManager getPackageManager();
 public abstract void startActivityAsUser(Intent intent, android.os.UserHandle user);
}''',
"android/app/ActivityManager.java": '''package android.app;
public class ActivityManager { public static int getCurrentUser() { return 0; } }''',
"android/hardware/input/KeyGestureEvent.java": '''package android.hardware.input;
public class KeyGestureEvent {
 public int getAction() { return 0; }
 public boolean isCancelled() { return false; }
 public boolean isInteractive() { return false; }
}''',
"android/os/UserHandle.java": '''package android.os;
public class UserHandle { public static UserHandle of(int user) { return null; } }''',
"android/provider/Settings.java": '''package android.provider;
public final class Settings {
 public static final class Secure {
  public static int getIntForUser(android.content.ContentResolver r, String k, int d, int u) { return d; }
  public static String getStringForUser(android.content.ContentResolver r, String k, int u) { return null; }
 }
 public static final class Global {
  public static int getInt(android.content.ContentResolver r, String k, int d) { return d; }
 }
}''',
"com/android/server/input/KeyGestureController.java": '''package com.android.server.input;
public class KeyGestureController {
 public android.content.Context mContext;
 public android.os.Handler mHandler;
 public com.android.server.security.authenticationpolicy.SecureLockDeviceService mSecureLockDeviceService;
}''',
"com/android/server/security/authenticationpolicy/SecureLockDeviceService.java": '''package com.android.server.security.authenticationpolicy;
public class SecureLockDeviceService { public boolean isSecureLockDeviceEnabled() { return false; } }''',
"com/samsung/android/rune/InputRune.java": '''package com.samsung.android.rune;
public class InputRune { public static boolean PWM_DEVICE_SHUTDOWN_VOLUP_POWER_CHN; }''',
}
for name, text in sources.items():
 path = root / name
 path.parent.mkdir(parents=True, exist_ok=True)
 path.write_text(text + "\n")
PY
mapfile -t MEMORY_KEY_JAVA < <(find "$MEMORY_KEY_SOURCE/java" "$MEMORY_KEY_BUILD/stubs" -type f -name '*.java' | sort)
javac --release 8 -encoding UTF-8 -classpath "$MEMORY_KEY_ANDROID_JAR" -d "$MEMORY_KEY_BUILD/classes" "${MEMORY_KEY_JAVA[@]}"
jar cf "$MEMORY_KEY_BUILD/helpers.jar" -C "$MEMORY_KEY_BUILD/classes" io
"$MEMORY_KEY_D8" --min-api 35 --lib "$MEMORY_KEY_ANDROID_JAR" --output "$MEMORY_KEY_BUILD/dex" "$MEMORY_KEY_BUILD/helpers.jar"
baksmali disassemble --api 35 "$MEMORY_KEY_BUILD/dex/classes.dex" -o "$MEMORY_KEY_BUILD/smali"
python3 "$MEMORY_KEY_SOURCE/generate-patch.py" "$1" "$MEMORY_KEY_BUILD/smali"
echo "Memory side-key compiler output: $MEMORY_KEY_BUILD"
