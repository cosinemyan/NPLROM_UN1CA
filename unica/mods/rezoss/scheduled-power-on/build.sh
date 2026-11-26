#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
SRC=${SRC_DIR:-$(cd "$ROOT/../../../.." && pwd)}
OUTPUT=${1:?Usage: build.sh NEW_OUTPUT.apk}
[[ ! -e "$OUTPUT" ]] || { echo "Refusing existing output: $OUTPUT" >&2; exit 1; }
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
TOOLS=$SDK/build-tools/37.0.0
NDK=${ANDROID_NDK_HOME:-$SDK/ndk/28.2.13676358}
CERT=aosp
if ${ROM_IS_OFFICIAL:-false}; then CERT=unica; fi
WORK=$(mktemp -d /tmp/rezoss-poweron-build.XXXXXX)
echo "Build workspace: $WORK"
mkdir -p "$WORK/classes" "$WORK/dex" "$WORK/lib/arm64-v8a"
mapfile -t SOURCES < <(find "$ROOT/java" -type f -name '*.java' | sort)
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options \
    -cp "$SDK/platforms/android-37.0/android.jar" -d "$WORK/classes" "${SOURCES[@]}"
jar cf "$WORK/classes.jar" -C "$WORK/classes" .
"$TOOLS/d8" --min-api 35 --lib "$SDK/platforms/android-37.0/android.jar" --output "$WORK/dex" "$WORK/classes.jar"
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android35-clang++" \
    -std=c++17 -Os -Wall -Wextra -Werror -fstack-protector-strong -fPIC -shared \
    -static-libstdc++ -Wl,-z,relro,-z,now,-z,max-page-size=16384,--strip-all \
    "$ROOT/native/poweron.cpp" -o "$WORK/lib/arm64-v8a/librezoss_poweron.so"
"$TOOLS/aapt2" compile --dir "$ROOT/res" -o "$WORK/resources.zip"
"$TOOLS/aapt2" link --manifest "$ROOT/AndroidManifest.xml" \
    -I "$SDK/platforms/android-37.0/android.jar" -o "$WORK/unsigned.apk" "$WORK/resources.zip"
cp "$WORK/dex/classes.dex" "$WORK/classes.dex"
(cd "$WORK"; zip -q -0 unsigned.apk classes.dex lib/arm64-v8a/librezoss_poweron.so)
"$TOOLS/zipalign" -P 16 -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"
"$TOOLS/apksigner" sign --key "$SRC/security/${CERT}_platform.pk8" \
    --cert "$SRC/security/${CERT}_platform.x509.pem" --v4-signing-enabled false \
    --out "$OUTPUT" "$WORK/aligned.apk"
"$TOOLS/apksigner" verify "$OUTPUT"
"$TOOLS/zipalign" -c -P 16 4 "$OUTPUT"
unzip -tq "$OUTPUT"
