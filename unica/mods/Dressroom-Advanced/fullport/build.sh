#!/usr/bin/env bash
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
WORK=${1:?Usage: build.sh NEW_TEMP_DIRECTORY}
[[ ! -e "$WORK" ]] || { echo 'Output exists; use a new build directory' >&2; exit 2; }
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
ANDROID_JAR=${ANDROID_JAR:-$SDK/platforms/android-37.0/android.jar}
mkdir -p "$WORK/classes" "$WORK/dex" "$WORK/wallpaper-classes" "$WORK/wallpaper-dex"
find "$ROOT" -type d -name tests -prune -o -type f -name '*.java' -print | sort > "$WORK/sources"
javac --release 17 -encoding UTF-8 -cp "$ANDROID_JAR" -d "$WORK/classes" @"$WORK/sources"
jar cf "$WORK/fullport.jar" -C "$WORK/classes" .
"$SDK/build-tools/37.0.0/d8" --min-api 36 --lib "$ANDROID_JAR" --output "$WORK/dex" "$WORK/fullport.jar"
baksmali disassemble --api 35 "$WORK/dex/classes.dex" -o "$WORK/smali"
for NAME in NativeWallpaper WallpaperMotionPacket WallpaperMotionChannel; do
    javac --release 17 -encoding UTF-8 -cp "$ANDROID_JAR:$WORK/classes" -d "$WORK/wallpaper-classes" \
        "$ROOT/depth-wallpaper/java/dev/denver/dressroomadvanced/$NAME.java"
done
jar cf "$WORK/wallpaper.jar" -C "$WORK/wallpaper-classes" .
"$SDK/build-tools/37.0.0/d8" --min-api 36 --lib "$ANDROID_JAR" --output "$WORK/wallpaper-dex" "$WORK/wallpaper.jar"
baksmali disassemble --api 35 "$WORK/wallpaper-dex/classes.dex" -o "$WORK/wallpaper-smali"
printf 'Native full port classes: %s\n' "$WORK/smali"

python3 "$ROOT/export-artifacts.py" "$WORK" "$WORK/export"
