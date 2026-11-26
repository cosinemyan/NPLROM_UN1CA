#!/usr/bin/env bash
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
SOURCE_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
ANDROID_JAR="${ANDROID_JAR:-$SDK/platforms/android-37.0/android.jar}"
D8="${D8:-$SDK/build-tools/37.0.0/d8}"
WORK=$(mktemp -d /tmp/unica-keybox-settings.XXXXXX)
mkdir -p "$WORK/classes" "$WORK/dex"
mapfile -t SOURCES < <(find "$SOURCE_DIR/java" -type f -name '*.java' | sort)
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -classpath "$ANDROID_JAR" -d "$WORK/classes" "${SOURCES[@]}"
javac -encoding UTF-8 -classpath "$WORK/classes" -d "$WORK/tests" "$SOURCE_DIR/tests/KeyboxTest.java"
java -classpath "$WORK/tests:$WORK/classes" io.mesalabs.unica.keybox.KeyboxTest "$SOURCE_DIR/../SecSettings.apk/assets/unica-keybox/aosp-keybox.txt"
node "$SOURCE_DIR/tests/repository-test.js" "$SOURCE_DIR/../SecSettings.apk/assets/unica-keybox/repository.html"
python3 "$SOURCE_DIR/tests/browser-boundary-test.py"
bash "$SOURCE_DIR/browser/build.sh"
jar cf "$WORK/classes.jar" -C "$WORK/classes" .
"$D8" --min-api 35 --lib "$ANDROID_JAR" --output "$WORK/dex" "$WORK/classes.jar"
baksmali disassemble --api 35 "$WORK/dex/classes.dex" -o "$WORK/smali"
smali assemble --api 35 "$WORK/smali" -o "$WORK/verified.dex"
python3 "$SOURCE_DIR/generate-patch.py" "$WORK/smali"
echo "Compiler output retained at $WORK"
