#!/usr/bin/env bash
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
SOURCE_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
ANDROID_JAR="${ANDROID_JAR:-$SDK/platforms/android-37.0/android.jar}"
D8="${D8:-$SDK/build-tools/37.0.0/d8}"
WORK=$(mktemp -d /tmp/unica-pif-settings.XXXXXX)
mkdir -p "$WORK/classes" "$WORK/dex" "$WORK/generated/io/mesalabs/unica/pif"
python3 - "$SOURCE_DIR/../../pif/framework.jar/0001-Introduce-PlayIntegrityHooks.patch" "$WORK/generated/io/mesalabs/unica/pif/Defaults.java" <<'PY'
import pathlib,re,sys,json
patch=pathlib.Path(sys.argv[1]).read_text()
lines=['package io.mesalabs.unica.pif;','final class Defaults {']
for key,field in [('FINGERPRINT','FINGERPRINT'),('SECURITY_PATCH','SECURITY_PATCH'),('FIRST_API','DEVICE_INITIAL_SDK_INT')]:
    match=re.search(r'\.field[^\n]* PIF_'+field+r':[^\n]* = "([^"\n]+)"',patch)
    if not match: raise SystemExit('Missing PIF default '+key)
    lines.append('static final String '+key+'='+json.dumps(match[1])+';')
pathlib.Path(sys.argv[2]).write_text('\n'.join(lines+['}']))
PY
mapfile -t SOURCES < <(find "$SOURCE_DIR/java" "$WORK/generated" -type f -name '*.java' | sort)
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -classpath "$ANDROID_JAR" -d "$WORK/classes" "${SOURCES[@]}"
javac -encoding UTF-8 -classpath "$WORK/classes" -d "$WORK/tests" "$SOURCE_DIR/tests/ProfileTest.java"
java -classpath "$WORK/tests:$WORK/classes" io.mesalabs.unica.pif.ProfileTest
jar cf "$WORK/classes.jar" -C "$WORK/classes" .
"$D8" --min-api 35 --lib "$ANDROID_JAR" --output "$WORK/dex" "$WORK/classes.jar"
baksmali disassemble --api 35 "$WORK/dex/classes.dex" -o "$WORK/smali"
smali assemble --api 35 "$WORK/smali" -o "$WORK/verified.dex"
python3 "$SOURCE_DIR/generate-patch.py" "$WORK/smali"
echo "Compiler output retained at $WORK"
