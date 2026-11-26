#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Build isolated test variants; never called by the normal ROM integration.
set -euo pipefail
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
HERE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
OUTPUT_DIR=${1:?Usage: build.sh NEW_OUTPUT_DIRECTORY}
[[ ! -e "$OUTPUT_DIR" ]] || { echo "Refusing existing output directory: $OUTPUT_DIR" >&2; exit 1; }
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}; TOOLS=$SDK/build-tools/37.0.0
WORK=$(mktemp -d /tmp/unica-gemma-backends.XXXXXX)
BASE=$ROOT/prebuilt/AIOS-V73-base.apk
printf '%s  %s\n' aef3822a21d4c8821bbfd0b8db2630d8db7b3a1f86bd3d3e9c5e2a0f7fbd5a8c "$BASE" | sha256sum -c -
python3 "$HERE/fetch_runtime.py" "$WORK/deps" "${GEMMA_RUNTIME_CACHE:-$SRC_DIR/out/archived/gemma4-e2b-device-20261031}"
python3 - "$WORK" <<'PY'
from pathlib import Path
from zipfile import ZipFile
import sys
work=Path(sys.argv[1])
with ZipFile(work/'deps/litertlm-android-0.19.0.aar') as z:
    (work/'deps/litertlm-classes.jar').write_bytes(z.read('classes.jar'))
    (work/'deps/liblitertlm_jni.so').write_bytes(z.read('jni/arm64-v8a/liblitertlm_jni.so'))
PY
mkdir -p "$OUTPUT_DIR"
for BACKEND in cpu gpu; do
    STAGE=$WORK/$BACKEND
    mkdir -p "$STAGE/classes" "$STAGE/dex"
    cp -a "$ROOT/backend" "$STAGE/"
    patch --dry-run --fuzz=0 -p1 -d "$STAGE" -i "$HERE/0001-Use-LiteRT-for-experimental-local-inference.patch"
    patch --fuzz=0 -p1 -d "$STAGE" -i "$HERE/0001-Use-LiteRT-for-experimental-local-inference.patch"
    cp -a "$HERE/java/." "$STAGE/backend/java/"
    cat > "$STAGE/backend/java/io/mesalabs/rezoss/nudge/GemmaBuild.java" <<JAVA
package io.mesalabs.rezoss.nudge;
final class GemmaBuild { static final String BACKEND="$BACKEND"; }
JAVA
    python3 - "$STAGE/backend/AndroidManifest.xml" "$BACKEND" <<'PY'
import sys,xml.etree.ElementTree as ET
path,backend=sys.argv[1:];ns='{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android',ns[1:-1]);tree=ET.parse(path);root=tree.getroot()
root.set(ns+'versionCode','142801210');root.set(ns+'versionName','1.4.28')
app=root.find('application');app.set(ns+'label','AIOS Gemma E2B '+backend.upper()+' (experimental)')
for lib in ['libOpenCL.so','libvndksupport.so']:
    ET.SubElement(app,'uses-native-library',{ns+'name':lib,ns+'required':'false'})
activity=ET.SubElement(app,'activity',{ns+'name':'io.mesalabs.rezoss.nudge.GemmaSetupActivity',ns+'exported':'true',ns+'configChanges':'orientation|screenSize|uiMode'})
f=ET.SubElement(activity,'intent-filter');ET.SubElement(f,'action',{ns+'name':'android.intent.action.MAIN'});ET.SubElement(f,'category',{ns+'name':'android.intent.category.LAUNCHER'})
tree.write(path,encoding='utf-8',xml_declaration=True)
PY
    mapfile -t SOURCES < <(find "$STAGE/backend/java" -name '*.java' -type f | sort)
    javac -source 8 -target 8 -Xlint:-options -classpath "$SDK/platforms/android-37.0/android.jar:$WORK/deps/litertlm-classes.jar:$WORK/deps/kotlin-stdlib.jar" -d "$STAGE/classes" "${SOURCES[@]}"
    jar cf "$STAGE/backend.jar" -C "$STAGE/classes" .
    "$TOOLS/d8" --min-api 30 --lib "$SDK/platforms/android-37.0/android.jar" --output "$STAGE/dex" "$STAGE/backend.jar" "$WORK/deps/"*.jar
    "$TOOLS/aapt2" link --manifest "$STAGE/backend/AndroidManifest.xml" -I "$SDK/platforms/android-37.0/android.jar" -o "$STAGE/manifest.apk"
    python3 "$HERE/package.py" "$BASE" "$STAGE" "$WORK/deps" "$BACKEND"
    "$TOOLS/zipalign" -P 16 -f 4 "$STAGE/unsigned.apk" "$STAGE/aligned.apk"
    CERT=aosp
    if ${ROM_IS_OFFICIAL:-false}; then CERT=unica; fi
    OUTPUT=$OUTPUT_DIR/AIOS-Gemma4-E2B-${BACKEND^^}-experimental.apk
    "$TOOLS/apksigner" sign --key "$SRC_DIR/security/${CERT}_platform.pk8" --cert "$SRC_DIR/security/${CERT}_platform.x509.pem" --out "$OUTPUT" "$STAGE/aligned.apk"
    "$TOOLS/apksigner" verify "$OUTPUT"
    "$TOOLS/zipalign" -c -P 16 4 "$OUTPUT"
    unzip -tq "$OUTPUT"
done
python3 "$HERE/write_metadata.py" "$OUTPUT_DIR"
printf 'Staged source and build intermediates: %s\n' "$WORK"
printf 'Test APKs: %s\n' "$OUTPUT_DIR"
