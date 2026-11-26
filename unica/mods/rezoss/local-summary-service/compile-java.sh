#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
[[ $# == 2 ]] || { echo "Usage: $0 JAVA_SOURCE_DIR SMALI_OUTPUT_DIR" >&2; exit 2; }
: "${SRC_DIR:?Source buildenv.sh first}"

_REZOSS_RESOLVE_ANDROID_SDK() {
    local cand
    for cand in \
        "${ANDROID_HOME:-}" \
        "${ANDROID_SDK_ROOT:-}" \
        "${HOME:-}/Android/Sdk" \
        "/home/cosine/Android/Sdk" \
        "/opt/android-sdk" \
        "/usr/lib/android-sdk"; do
        if [[ -n "$cand" && -f "$cand/platforms/android-37.0/android.jar" ]]; then
            printf '%s\n' "$cand"
            return 0
        fi
    done
    return 1
}

SDK="$(_REZOSS_RESOLVE_ANDROID_SDK)" || {
    echo "Android SDK with platforms/android-37.0 not found (set ANDROID_HOME)" >&2
    exit 1
}
ANDROID_JAR="$SDK/platforms/android-37.0/android.jar"
D8="$SDK/build-tools/37.0.0/d8"
APKTOOL_JAR="${SRC_DIR}/out/tools/bin/apktool.jar"
WORK=$(mktemp -d /tmp/rezoss-qwen-java.XXXXXX)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/classes" "$WORK/dex"
mapfile -t SOURCES < <(find "$1" -name '*.java' -type f | sort)
if [[ "$1" == *keyboard-java* ]]; then
    mapfile -t SHARED < <(find "$(dirname "$0")/../aioskernel/native-now-nudge/shade-reply/shared-java" -name '*.java' -type f | sort)
    SOURCES+=("${SHARED[@]}")
fi
[[ -f "$ANDROID_JAR" ]] || { echo "Missing $ANDROID_JAR" >&2; exit 1; }
[[ -x "$D8" ]] || { echo "Missing $D8" >&2; exit 1; }
[[ -f "$APKTOOL_JAR" ]] || { echo "Missing $APKTOOL_JAR" >&2; exit 1; }
javac -source 8 -target 8 -Xlint:-options -classpath "$ANDROID_JAR" -d "$WORK/classes" "${SOURCES[@]}"
jar cf "$WORK/classes.jar" -C "$WORK/classes" .
"$D8" --min-api 35 --lib "$ANDROID_JAR" --output "$WORK/dex" "$WORK/classes.jar"
java -cp "$APKTOOL_JAR" com.android.tools.smali.baksmali.Main \
    disassemble --api 35 "$WORK/dex/classes.dex" -o "$2"
