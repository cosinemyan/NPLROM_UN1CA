#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
[[ $# == 2 ]] || { echo "Usage: $0 JAVA_SOURCE_DIR SMALI_OUTPUT_DIR" >&2; exit 2; }
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
ANDROID_JAR="$SDK/platforms/android-37.0/android.jar"
D8="$SDK/build-tools/37.0.0/d8"
WORK=$(mktemp -d /tmp/rezoss-qwen-java.XXXXXX)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/classes" "$WORK/dex"
mapfile -t SOURCES < <(find "$1" -name '*.java' -type f | sort)
if [[ "$1" == *keyboard-java* ]]; then
    mapfile -t SHARED < <(find "$(dirname "$0")/../aioskernel/native-now-nudge/shade-reply/shared-java" -name '*.java' -type f | sort)
    SOURCES+=("${SHARED[@]}")
fi
javac -source 8 -target 8 -Xlint:-options -classpath "$ANDROID_JAR" -d "$WORK/classes" "${SOURCES[@]}"
jar cf "$WORK/classes.jar" -C "$WORK/classes" .
"$D8" --min-api 35 --lib "$ANDROID_JAR" --output "$WORK/dex" "$WORK/classes.jar"
baksmali disassemble --api 35 "$WORK/dex/classes.dex" -o "$2"
