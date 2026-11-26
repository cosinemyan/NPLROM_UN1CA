#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Compile maintained compatibility Java for Samsung's existing model contracts.
set -euo pipefail
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
DECODED=${1:?Usage: build_ssco_helper.sh SEMANTICSEARCHCORE_DECODE}
[[ -d "$DECODED" ]] || { echo "Decode directory missing: $DECODED" >&2; exit 1; }
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
TOOLS=$SDK/build-tools/37.0.0
WORK=$(mktemp -d /tmp/nudge-ssco-helper.XXXXXX)
[[ ! -e "$DECODED/smali_classes2" ]] || { echo 'Unexpected classes2; inspect DEX overlap' >&2; exit 1; }
mkdir -p "$WORK/classes" "$WORK/dex"
javac -source 8 -target 8 -Xlint:-options -classpath "$SDK/platforms/android-37.0/android.jar" \
    -d "$WORK/classes" "$ROOT"/ssco-java/io/mesalabs/rezoss/ai/*.java
jar cf "$WORK/helper.jar" -C "$WORK/classes" .
"$TOOLS/d8" --min-api 30 --lib "$SDK/platforms/android-37.0/android.jar" --output "$WORK/dex" "$WORK/helper.jar"
java -cp "$SRC_DIR/out/tools/bin/apktool.jar" com.android.tools.smali.baksmali.Main \
    disassemble --api 30 "$WORK/dex/classes.dex" -o "$WORK/smali_classes2"
cp -a "$WORK/smali_classes2" "$DECODED/"
