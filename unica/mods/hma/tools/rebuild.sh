#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-or-later
# Regenerate reviewable patches from retained Java sources and exact firmware.
set -euo pipefail

if [[ $# != 4 ]]; then
    echo "Usage: source buildenv.sh dm3q; bash unica/mods/hma/tools/rebuild.sh <new-tmp-dir> <android.jar> <json.jar> <r8.jar>" >&2
    exit 2
fi
HMA_WORK=$(realpath -m "$1")
HMA_ANDROID=$(realpath "$2")
HMA_JSON=$(realpath "$3")
HMA_R8=$(realpath "$4")
HMA_MOD=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
HMA_REPO=$(cd -- "$HMA_MOD/../../.." && pwd)
[[ ! -e "$HMA_WORK" ]] || { echo "Use a new temporary directory to preserve previous evidence." >&2; exit 1; }
[[ -n ${SRC_DIR:-} ]] || { echo "Source buildenv.sh dm3q first." >&2; exit 1; }
mkdir -p "$HMA_WORK/classes" "$HMA_WORK/ui-classes" "$HMA_WORK/stub-classes" "$HMA_WORK/policy-dex" "$HMA_WORK/ui-dex"
python3 "$HMA_MOD/tools/make_ui_stubs.py" "$HMA_WORK"
mapfile -t HMA_STUBS < <(find "$HMA_WORK/stubs" -name '*.java' -type f | sort)
javac --release 8 -cp "$HMA_ANDROID" -d "$HMA_WORK/stub-classes" "${HMA_STUBS[@]}"
javac --release 8 -cp "$HMA_ANDROID:$HMA_JSON" -d "$HMA_WORK/classes" "$HMA_MOD"/src/io/mesalabs/unica/*.java
javac --release 8 -cp "$HMA_ANDROID:$HMA_JSON:$HMA_WORK/stub-classes" -d "$HMA_WORK/ui-classes" "$HMA_MOD/src/io/mesalabs/unica/settings/spoof/HideDeveloperStatusFragment.java"
for HMA_PART in policy ui; do
    HMA_CLASSES="$HMA_WORK/classes"
    [[ "$HMA_PART" != ui ]] || HMA_CLASSES="$HMA_WORK/ui-classes"
    jar cf "$HMA_WORK/$HMA_PART-classes.jar" -C "$HMA_CLASSES" .
    java -cp "$HMA_R8" com.android.tools.r8.D8 --min-api 26 --output "$HMA_WORK/$HMA_PART-dex" "$HMA_WORK/$HMA_PART-classes.jar"
    java -cp "$HMA_REPO/out/tools/bin/apktool.jar" com.android.tools.smali.baksmali.Main d "$HMA_WORK/$HMA_PART-dex/classes.dex" -o "$HMA_WORK/$HMA_PART-smali"
done
for HMA_PART in framework services; do
    "$HMA_REPO/out/tools/bin/apktool" d --no-debug-info -r "$HMA_REPO/out/fw/SM-S918B_EUX/system/system/framework/$HMA_PART.jar" -o "$HMA_WORK/$HMA_PART-nodebug"
done
python3 "$HMA_MOD/tools/generate_patches.py" "$HMA_WORK"
echo "Generated patches; prior versions backed up under $HMA_WORK/backups/generated. Run clean ordered patch and rebuild validation before use."
