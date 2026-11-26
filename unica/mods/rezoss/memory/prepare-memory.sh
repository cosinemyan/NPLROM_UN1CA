#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
[[ $# == 2 ]] || { echo 'Usage: source buildenv.sh dm3q; prepare-memory.sh ORIGINAL_V48.apk OUTPUT.apk' >&2; exit 2; }
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
MEMORY_AI_SOURCE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
MEMORY_AI_INPUT=$(realpath "$1")
MEMORY_AI_OUTPUT=$(realpath -m "$2")
[[ ! -e "$MEMORY_AI_OUTPUT" ]] || { echo "Refusing to overwrite $MEMORY_AI_OUTPUT" >&2; exit 1; }
[[ $(sha256sum "$MEMORY_AI_INPUT" | cut -d ' ' -f1) == ea088fe8329289b9b1494d805ac0555eea9f888757cba394c99b429985f3939a ]] || {
    echo 'Unexpected Memory baseline; revalidate against exact 1.0.00.48' >&2; exit 1;
}
MEMORY_AI_SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
MEMORY_AI_ANDROID_JAR="${ANDROID_JAR:-$MEMORY_AI_SDK/platforms/android-37.0/android.jar}"
MEMORY_AI_TOOLS="$MEMORY_AI_SDK/build-tools/37.0.0"
MEMORY_AI_APKTOOL="${MEMORY_APKTOOL:-$SRC_DIR/out/tools/bin/apktool}"
MEMORY_AI_FRAMEWORK="$SRC_DIR/out/tools/apktool/framework"
MEMORY_AI_TMP=$(mktemp -d /tmp/rezoss-memory-ai.XXXXXX)
"$MEMORY_AI_APKTOOL" d -j 4 -p "$MEMORY_AI_FRAMEWORK" "$MEMORY_AI_INPUT" -o "$MEMORY_AI_TMP/decoded"
LC_ALL=C patch --dry-run --fuzz=0 -p1 -d "$MEMORY_AI_TMP/decoded" -N --forward < "$MEMORY_AI_SOURCE/ai/0001-Add-UN1CA-Memory-backend.patch"
LC_ALL=C patch --fuzz=0 --no-backup-if-mismatch -p1 -d "$MEMORY_AI_TMP/decoded" -N --forward < "$MEMORY_AI_SOURCE/ai/0001-Add-UN1CA-Memory-backend.patch"
mkdir -p "$MEMORY_AI_TMP/classes" "$MEMORY_AI_TMP/dex"
python3 "$MEMORY_AI_SOURCE/ai/compile-stubs.py" "$MEMORY_AI_TMP/stubs"
MEMORY_AI_JAVA_ROOTS=("$MEMORY_AI_SOURCE/ai/java" "$MEMORY_AI_TMP/stubs")
# The indexing adapter is an independently maintained patch/helper.
[[ -f "$MEMORY_AI_SOURCE/indexing/0001-Add-Memory-index-compatibility.patch" ]] || {
    echo 'Missing Memory index compatibility patch' >&2; exit 1;
}
    LC_ALL=C patch --dry-run --fuzz=0 -p1 -d "$MEMORY_AI_TMP/decoded" -N --forward < "$MEMORY_AI_SOURCE/indexing/0001-Add-Memory-index-compatibility.patch"
    LC_ALL=C patch --fuzz=0 --no-backup-if-mismatch -p1 -d "$MEMORY_AI_TMP/decoded" -N --forward < "$MEMORY_AI_SOURCE/indexing/0001-Add-Memory-index-compatibility.patch"
    MEMORY_AI_JAVA_ROOTS+=("$MEMORY_AI_SOURCE/indexing/java")
    touch "$MEMORY_AI_TMP/rebuilt.apk.indexing"
mapfile -t MEMORY_AI_JAVA < <(find "${MEMORY_AI_JAVA_ROOTS[@]}" -name '*.java' -type f | sort)
javac --release 8 -encoding UTF-8 -classpath "$MEMORY_AI_ANDROID_JAR" -d "$MEMORY_AI_TMP/classes" "${MEMORY_AI_JAVA[@]}"
# Compile-only app signatures are excluded. Only io/ helpers enter this DEX.
jar cf "$MEMORY_AI_TMP/helpers.jar" -C "$MEMORY_AI_TMP/classes" io
"${D8:-$MEMORY_AI_TOOLS/d8}" --min-api 35 --lib "$MEMORY_AI_ANDROID_JAR" --output "$MEMORY_AI_TMP/dex" "$MEMORY_AI_TMP/helpers.jar"
baksmali disassemble --api 35 "$MEMORY_AI_TMP/dex/classes.dex" -o "$MEMORY_AI_TMP/helper-smali"
python3 "$MEMORY_AI_SOURCE/ai/tests/verify-linkage.py" "$MEMORY_AI_TMP/decoded" "$MEMORY_AI_TMP/helper-smali"
"$MEMORY_AI_APKTOOL" b -j 4 -p "$MEMORY_AI_FRAMEWORK" "$MEMORY_AI_TMP/decoded" -o "$MEMORY_AI_TMP/rebuilt.apk"
python3 "$MEMORY_AI_SOURCE/ai/package-memory.py" "$MEMORY_AI_INPUT" "$MEMORY_AI_TMP/rebuilt.apk" "$MEMORY_AI_TMP/dex/classes.dex" "$MEMORY_AI_TMP/unsigned.apk"
"$MEMORY_AI_TOOLS/zipalign" -P 16 -f 4 "$MEMORY_AI_TMP/unsigned.apk" "$MEMORY_AI_TMP/aligned.apk"
MEMORY_AI_CERT=aosp
if ${ROM_IS_OFFICIAL:-false}; then MEMORY_AI_CERT=unica; fi
"$MEMORY_AI_TOOLS/apksigner" sign --key "$SRC_DIR/security/${MEMORY_AI_CERT}_platform.pk8" --cert "$SRC_DIR/security/${MEMORY_AI_CERT}_platform.x509.pem" --out "$MEMORY_AI_TMP/signed.apk" "$MEMORY_AI_TMP/aligned.apk"
"$MEMORY_AI_TOOLS/apksigner" verify "$MEMORY_AI_TMP/signed.apk"
"$MEMORY_AI_TOOLS/zipalign" -c -P 16 4 "$MEMORY_AI_TMP/signed.apk"
unzip -tq "$MEMORY_AI_TMP/signed.apk"
mkdir -p "$(dirname "$MEMORY_AI_OUTPUT")"
cp -p "$MEMORY_AI_TMP/signed.apk" "$MEMORY_AI_OUTPUT"
echo "Memory patched package: $MEMORY_AI_OUTPUT"
echo "Memory temporary verification files: $MEMORY_AI_TMP"
