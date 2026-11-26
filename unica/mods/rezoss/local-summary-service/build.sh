#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: source buildenv.sh dm3q; $0 OUTPUT.apk" >&2
    exit 2
fi

: "${SRC_DIR:?Source buildenv.sh dm3q first}"
: "${FW_DIR:?Source buildenv.sh dm3q first}"

MODPATH=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
OUTPUT=$(realpath -m "$1")
WORK=$(mktemp -d /tmp/unica-local-summary-apk.XXXXXX)
trap 'rm -rf "$WORK"' EXIT

FRAMEWORK_RES="$FW_DIR/SM-S918B_EUX/system/system/framework/framework-res.apk"

for file in "$FRAMEWORK_RES"; do
    [[ -f "$file" ]] || { echo "Missing required local-summary artifact: $file" >&2; exit 1; }
done

cp -a "$MODPATH/smali" "$WORK/smali"
for patch_file in "$MODPATH"/patches/*.patch; do
    patch --batch --fuzz=0 --dry-run -d "$WORK/smali" -p1 < "$patch_file"
    patch --batch --fuzz=0 -d "$WORK/smali" -p1 < "$patch_file"
done
bash "$MODPATH/compile-java.sh" "$MODPATH/java" "$WORK/java-smali"
cp -a "$WORK/java-smali/." "$WORK/smali/"
smali assemble --api 35 --output "$WORK/classes.dex" "$WORK/smali"
aapt2 link --manifest "$MODPATH/AndroidManifest.xml" -I "$FRAMEWORK_RES" -o "$WORK/unsigned.apk"
zip -q -j "$WORK/unsigned.apk" "$WORK/classes.dex"

CERT_PREFIX=aosp
if ${ROM_IS_OFFICIAL:-false}; then
    CERT_PREFIX=unica
fi
CERT_X509="$SRC_DIR/security/${CERT_PREFIX}_platform.x509.pem"
CERT_PK8="$SRC_DIR/security/${CERT_PREFIX}_platform.pk8"
SIGNAPK="$SRC_DIR/out/tools/bin/signapk"

for file in "$CERT_X509" "$CERT_PK8" "$SIGNAPK"; do
    [[ -f "$file" ]] || { echo "Missing required RezossAI signing artifact: $file" >&2; exit 1; }
done

"$SIGNAPK" "$CERT_X509" "$CERT_PK8" "$WORK/unsigned.apk" "$WORK/signed.apk"
zipalign -c -p 4 "$WORK/signed.apk"
apksigner verify --verbose "$WORK/signed.apk" >/dev/null

EXPECTED_CERT_SHA256="$({ keytool -printcert -file "$CERT_X509" 2>/dev/null || true; } | sed -n 's/.*SHA256: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]' | head -n1)"
SIGNED_CERT_SHA256="$(apksigner verify --print-certs "$WORK/signed.apk" | sed -n 's/Signer #1 certificate SHA-256 digest: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]' | head -n1)"
if [[ -z "$EXPECTED_CERT_SHA256" || "$SIGNED_CERT_SHA256" != "$EXPECTED_CERT_SHA256" ]]; then
    echo "RezossAI signing certificate mismatch" >&2
    echo "Expected: $EXPECTED_CERT_SHA256" >&2
    echo "Actual:   $SIGNED_CERT_SHA256" >&2
    exit 1
fi

mkdir -p "$(dirname -- "$OUTPUT")"
mv "$WORK/signed.apk" "$OUTPUT"
