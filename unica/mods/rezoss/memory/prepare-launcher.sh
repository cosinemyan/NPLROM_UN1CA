#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
[[ $# == 2 ]] || { echo "Usage: source buildenv.sh dm3q; $0 ORIGINAL.apk OUTPUT.apk" >&2; exit 2; }
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
MEMORY_PORT_SOURCE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
MEMORY_PORT_INPUT=$(realpath "$1")
MEMORY_PORT_OUTPUT=$(realpath -m "$2")
[[ ! -e "$MEMORY_PORT_OUTPUT" ]] || { echo "Refusing to overwrite $MEMORY_PORT_OUTPUT" >&2; exit 1; }
[[ "$(sha256sum "$MEMORY_PORT_INPUT" | cut -d ' ' -f 1)" == \
    4630968289288d355aa3f8884ef45679ced56c0b3c0925d96a9a77ca6fcf47cf ]] || {
    echo "Unexpected One UI Home; revalidate Memory gesture patch" >&2; exit 1;
}
MEMORY_PORT_TMP=$(mktemp -d /tmp/rezoss-memory-launcher.XXXXXX)
"$SRC_DIR/out/tools/bin/apktool" d -r --no-debug-info -j 4 \
    -p "$SRC_DIR/out/tools/apktool/framework" "$MEMORY_PORT_INPUT" -o "$MEMORY_PORT_TMP/decoded"
LC_ALL=C patch --dry-run --fuzz=0 -p1 -d "$MEMORY_PORT_TMP/decoded" -N --forward \
    < "$MEMORY_PORT_SOURCE/patches/0001-Add-independent-Memory-capture-gestures.patch"
LC_ALL=C patch --fuzz=0 --no-backup-if-mismatch -p1 -d "$MEMORY_PORT_TMP/decoded" -N --forward \
    < "$MEMORY_PORT_SOURCE/patches/0001-Add-independent-Memory-capture-gestures.patch"
smali assemble --api 35 "$MEMORY_PORT_TMP/decoded/smali_classes3" -o "$MEMORY_PORT_TMP/classes3.dex"
smali assemble --api 35 "$MEMORY_PORT_TMP/decoded/smali_classes6" -o "$MEMORY_PORT_TMP/classes6.dex"
python3 "$MEMORY_PORT_SOURCE/package-dex.py" "$MEMORY_PORT_INPUT" \
    "$MEMORY_PORT_TMP/classes3.dex" "$MEMORY_PORT_TMP/classes6.dex" "$MEMORY_PORT_TMP/unsigned.apk"
MEMORY_PORT_CERT=aosp
if ${ROM_IS_OFFICIAL:-false}; then MEMORY_PORT_CERT=unica; fi
"$SRC_DIR/out/tools/bin/signapk" "$SRC_DIR/security/${MEMORY_PORT_CERT}_platform.x509.pem" \
    "$SRC_DIR/security/${MEMORY_PORT_CERT}_platform.pk8" \
    "$MEMORY_PORT_TMP/unsigned.apk" "$MEMORY_PORT_TMP/signed.apk"
apksigner verify "$MEMORY_PORT_TMP/signed.apk"
zipalign -c -p 4 "$MEMORY_PORT_TMP/signed.apk"
unzip -tq "$MEMORY_PORT_TMP/signed.apk"
mkdir -p "$(dirname "$MEMORY_PORT_OUTPUT")"
cp -p "$MEMORY_PORT_TMP/signed.apk" "$MEMORY_PORT_OUTPUT"
echo "Memory launcher package: $MEMORY_PORT_OUTPUT"
echo "Temporary validation files: $MEMORY_PORT_TMP"
