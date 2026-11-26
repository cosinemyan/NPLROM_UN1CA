#!/usr/bin/env bash
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
SOURCE_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
REPO=$(git -C "$SOURCE_DIR" rev-parse --show-toplevel)
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
TOOLS="$SDK/build-tools/37.0.0"
ANDROID_JAR="${ANDROID_JAR:-$SDK/platforms/android-37.0/android.jar}"
WORK=$(mktemp -d /tmp/unica-keybox-browser.XXXXXX)
mkdir -p "$WORK/classes" "$WORK/dex" "$WORK/assets"
cp "$SOURCE_DIR/../../SecSettings.apk/assets/unica-keybox/repository.html" "$WORK/assets/repository.html"
mapfile -t SOURCES < <(find "$SOURCE_DIR/java" -type f -name '*.java' | sort)
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -classpath "$ANDROID_JAR" -d "$WORK/classes" "${SOURCES[@]}"
jar cf "$WORK/classes.jar" -C "$WORK/classes" .
"$TOOLS/d8" --min-api 35 --lib "$ANDROID_JAR" --output "$WORK/dex" "$WORK/classes.jar"
"$TOOLS/aapt2" link -I "$ANDROID_JAR" --manifest "$SOURCE_DIR/AndroidManifest.xml" -A "$WORK/assets" -o "$WORK/base.apk"
python3 - "$WORK/base.apk" "$WORK/dex/classes.dex" <<'PY'
from pathlib import Path
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], 'a', zipfile.ZIP_DEFLATED) as archive:
    archive.write(sys.argv[2], 'classes.dex')
PY
"$TOOLS/zipalign" -P 16 -f 4 "$WORK/base.apk" "$WORK/aligned.apk"
# No shared UID, platform signature or privileged permissions are needed by this app.
"$TOOLS/apksigner" sign --key "$REPO/security/aosp_testkey.pk8" --cert "$REPO/security/aosp_testkey.x509.pem" --out "$WORK/signed.apk" "$WORK/aligned.apk"
"$TOOLS/apksigner" verify "$WORK/signed.apk"
"$TOOLS/zipalign" -c -P 16 4 "$WORK/signed.apk"
OUTPUT="$SOURCE_DIR/../../system/app/UnicaKeyboxBrowser/UnicaKeyboxBrowser.apk"
mkdir -p "$(dirname "$OUTPUT")"
if [ -f "$OUTPUT" ]; then cp -p "$OUTPUT" "$WORK/previous.apk"; fi
cp "$WORK/signed.apk" "$OUTPUT"
echo "Built normal-UID repository browser: $OUTPUT (work: $WORK)"
