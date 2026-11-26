#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Rebuild Java from source while preserving the tested native runtime and stub.
set -euo pipefail
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
OUTPUT=${1:?Usage: build_rom_backend.sh NEW_OUTPUT_APK}
[[ ! -e "$OUTPUT" ]] || { echo "Refusing existing output: $OUTPUT" >&2; exit 1; }
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}; TOOLS=$SDK/build-tools/37.0.0
BASE=$ROOT/prebuilt/AIOS-V73-base.apk
printf '%s  %s\n' aef3822a21d4c8821bbfd0b8db2630d8db7b3a1f86bd3d3e9c5e2a0f7fbd5a8c "$BASE" | sha256sum -c -
WORK=$(mktemp -d /tmp/nudge-rom-backend.XXXXXX)
mkdir -p "$WORK/classes" "$WORK/dex"
mapfile -t SOURCES < <(find "$ROOT/backend/java" -name '*.java' -type f | sort)
javac -source 8 -target 8 -Xlint:-options -classpath "$SDK/platforms/android-37.0/android.jar" -d "$WORK/classes" "${SOURCES[@]}"
jar cf "$WORK/backend.jar" -C "$WORK/classes" .
"$TOOLS/d8" --min-api 30 --lib "$SDK/platforms/android-37.0/android.jar" --output "$WORK/dex" "$WORK/backend.jar"
"$TOOLS/aapt2" link --replace-version --version-code 142801209 --manifest "$ROOT/backend/AndroidManifest.xml" -I "$SDK/platforms/android-37.0/android.jar" -o "$WORK/manifest.apk"
python3 - "$BASE" "$WORK" <<'PY'
from pathlib import Path
import sys, zipfile
base, work = map(Path, sys.argv[1:])
with zipfile.ZipFile(base) as src, zipfile.ZipFile(work/'manifest.apk') as manifest, zipfile.ZipFile(work/'unsigned.apk', 'x') as dst:
    for info in src.infolist():
        if info.filename.startswith('META-INF/'):
            continue
        if info.filename == 'classes2.dex':
            data = (work/'dex/classes.dex').read_bytes()
        elif info.filename == 'AndroidManifest.xml':
            data = manifest.read('AndroidManifest.xml')
        else:
            data = src.read(info)
        dst.writestr(info, data)
PY
"$TOOLS/zipalign" -P 16 -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
CERT=aosp
if ${ROM_IS_OFFICIAL:-false}; then CERT=unica; fi
"$TOOLS/apksigner" sign --key "$SRC_DIR/security/${CERT}_platform.pk8" --cert "$SRC_DIR/security/${CERT}_platform.x509.pem" \
    --out "$OUTPUT" "$WORK/aligned.apk"
"$TOOLS/apksigner" verify "$OUTPUT"
"$TOOLS/zipalign" -c -P 16 4 "$OUTPUT"
unzip -tq "$OUTPUT"
