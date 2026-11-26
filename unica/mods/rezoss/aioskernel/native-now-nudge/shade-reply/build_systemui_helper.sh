#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
DECODED=${1:?Usage: build_systemui_helper.sh SYSTEMUI_DECODE}
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
WORK=$(mktemp -d /tmp/shade-systemui-helper.XXXXXX)
[[ ! -e "$DECODED/smali_classes7" ]] || { echo 'Unexpected SystemUI classes7; inspect DEX overlap' >&2; exit 1; }
mkdir -p "$WORK/classes" "$WORK/dex"
mapfile -t SOURCES < <(find "$ROOT/shared-java" "$ROOT/shared-android-java" "$ROOT/systemui-java" -name '*.java' -type f | sort)
javac -source 8 -target 8 -Xlint:-options -classpath "$SDK/platforms/android-37.0/android.jar" -d "$WORK/classes" "${SOURCES[@]}"
jar cf "$WORK/helper.jar" -C "$WORK/classes" .
"$SDK/build-tools/37.0.0/d8" --min-api 35 --lib "$SDK/platforms/android-37.0/android.jar" --output "$WORK/dex" "$WORK/helper.jar"
java -cp "$SRC_DIR/out/tools/bin/apktool.jar" com.android.tools.smali.baksmali.Main disassemble --api 35 "$WORK/dex/classes.dex" -o "$WORK/smali_classes7"
cp -a "$WORK/smali_classes7" "$DECODED/"
