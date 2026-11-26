#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
SOURCE_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-$HOME/Android/Sdk}/ndk/28.2.13676358}"
CXX="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android35-clang++"
OUTPUT="$SOURCE_DIR/payload/system/bin/unica-volumeup"
WORK=$(mktemp -d /tmp/unica-volumeup-build.XXXXXX)
mkdir -p "$(dirname -- "$OUTPUT")"
"$CXX" -std=c++17 -Wall -Wextra -Werror -Os -static \
    -Wl,-z,relro,-z,now -Wl,--strip-all "$SOURCE_DIR/native/detector.cpp" -o "$WORK/unica-volumeup"
if [[ -f "$OUTPUT" ]] && ! cmp -s "$OUTPUT" "$WORK/unica-volumeup"; then
    cp -p "$OUTPUT" "$WORK/unica-volumeup.previous"
    echo "Previous detector backed up at $WORK/unica-volumeup.previous"
fi
install -m 755 "$WORK/unica-volumeup" "$OUTPUT"
(cd "$SOURCE_DIR" && sha256sum build.sh native/detector.cpp native/hold.h payload/system/bin/unica-volumeup > build.sha256)
echo "Built recovery-enabled detector; temporary output retained at $WORK"
