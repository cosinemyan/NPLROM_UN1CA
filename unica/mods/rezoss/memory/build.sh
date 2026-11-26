#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
[[ $# == 1 ]] || { echo "Usage: source buildenv.sh dm3q; $0 CLEAN_LAUNCHER_DECODE" >&2; exit 2; }
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
MEMORY_SOURCE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
MEMORY_SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
MEMORY_ANDROID_JAR="${ANDROID_JAR:-$MEMORY_SDK/platforms/android-37.0/android.jar}"
MEMORY_D8="${D8:-$MEMORY_SDK/build-tools/37.0.0/d8}"
MEMORY_BUILD=$(mktemp -d /tmp/rezoss-memory-helpers.XXXXXX)
mkdir -p "$MEMORY_BUILD/classes" "$MEMORY_BUILD/dex" "$MEMORY_BUILD/stubs/com/honeyspace/gesture/multifinger" \
    "$MEMORY_BUILD/stubs/com/honeyspace/gesture/inputproxy" "$MEMORY_BUILD/stubs/com/honeyspace/sdk/gesture"
# Compile-only signatures from the exact launcher. These classes are never packaged.
cat > "$MEMORY_BUILD/stubs/com/honeyspace/sdk/gesture/MultiFingerGestureType.java" <<'JAVA'
package com.honeyspace.sdk.gesture;
public enum MultiFingerGestureType { SWIPE_LEFT, SWIPE_RIGHT, SWIPE_UP, SWIPE_DOWN }
JAVA
cat > "$MEMORY_BUILD/stubs/com/honeyspace/gesture/multifinger/MultiFingerGestureEvent.java" <<'JAVA'
package com.honeyspace.gesture.multifinger;
public class MultiFingerGestureEvent {
    public com.honeyspace.sdk.gesture.MultiFingerGestureType getType() { return null; }
    public boolean isSwipeRepeat() { return false; }
    public static final class ThreeFingerGesture extends MultiFingerGestureEvent {}
}
JAVA
cat > "$MEMORY_BUILD/stubs/com/honeyspace/gesture/multifinger/MultiFingerGestureTrailTouchRelay.java" <<'JAVA'
package com.honeyspace.gesture.multifinger;
public final class MultiFingerGestureTrailTouchRelay {
    public void suppressHoldRippleBecauseGestureActionWillRun() {}
    public void setAllowMoveTrailAfterResolvedGesture(boolean allow) {}
}
JAVA
cat > "$MEMORY_BUILD/stubs/com/honeyspace/gesture/inputproxy/InputMonitorProxy.java" <<'JAVA'
package com.honeyspace.gesture.inputproxy;
public interface InputMonitorProxy { void pilferPointers(); }
JAVA
mapfile -t MEMORY_JAVA < <(find "$MEMORY_SOURCE/java" "$MEMORY_BUILD/stubs" -name '*.java' -type f | sort)
javac --release 8 -encoding UTF-8 -classpath "$MEMORY_ANDROID_JAR" -d "$MEMORY_BUILD/classes" "${MEMORY_JAVA[@]}"
jar cf "$MEMORY_BUILD/helpers.jar" -C "$MEMORY_BUILD/classes" io
"$MEMORY_D8" --min-api 35 --lib "$MEMORY_ANDROID_JAR" --output "$MEMORY_BUILD/dex" "$MEMORY_BUILD/helpers.jar"
baksmali disassemble --api 35 "$MEMORY_BUILD/dex/classes.dex" -o "$MEMORY_BUILD/smali"
python3 "$MEMORY_SOURCE/generate-patch.py" "$1" "$MEMORY_BUILD/smali"
bash "$MEMORY_SOURCE/tests/run.sh"
echo "Memory helper compiler output: $MEMORY_BUILD"
