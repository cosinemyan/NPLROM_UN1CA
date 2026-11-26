#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Generate helper smali from maintained Java; never hand-edit extracted smali.
set -euo pipefail
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
DECODED=${1:?Usage: build_client_helper.sh SMARTSUGGESTIONS_DECODE}
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
TOOLS=$SDK/build-tools/37.0.0
WORK=$(mktemp -d /tmp/nudge-client-helper.XXXXXX)
[[ ! -e "$DECODED/smali_classes17" ]] || { echo 'Unexpected classes17; inspect DEX overlap' >&2; exit 1; }
mkdir -p "$WORK/classes" "$WORK/dex"
mapfile -t SOURCES < <(find "$ROOT/client-java" -name '*.java' ! -name 'ContextActionParser.java' -type f | sort)
mapfile -t SHADE_SOURCES < <(find "$ROOT/shade-reply/shared-java" "$ROOT/shade-reply/shared-android-java" -name '*.java' -type f | sort)
javac -source 8 -target 8 -Xlint:-options -classpath "$SDK/platforms/android-37.0/android.jar" \
    -d "$WORK/classes" "${SOURCES[@]}" "${SHADE_SOURCES[@]}"
jar cf "$WORK/client.jar" -C "$WORK/classes" .
"$TOOLS/d8" --min-api 28 --lib "$SDK/platforms/android-37.0/android.jar" --output "$WORK/dex" "$WORK/client.jar"
java -cp "$SRC_DIR/out/tools/bin/apktool.jar" com.android.tools.smali.baksmali.Main \
    disassemble --api 28 "$WORK/dex/classes.dex" -o "$WORK/smali_classes17"
# D8 can emit the same synthetic annotation already embedded by Rezoss 0026.
# Reuse only byte-identical definitions; reject any actual helper class conflict.
python3 - "$DECODED" "$WORK/smali_classes17" <<'PYCODE'
from pathlib import Path
import sys
decoded, generated = map(Path, sys.argv[1:])
for source in generated.rglob("*.smali"):
    relative = source.relative_to(generated)
    for dex in decoded.glob("smali*"):
        existing = dex / relative
        if existing.is_file():
            if existing.read_bytes() != source.read_bytes():
                raise SystemExit("Conflicting helper class: " + str(relative))
            source.unlink()  # Only discard the identical temporary generated definition.
            break
PYCODE
cp -a "$WORK/smali_classes17" "$DECODED/"
