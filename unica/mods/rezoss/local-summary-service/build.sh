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

# Prefer QSSI/SOURCE firmware (NPL: SM-S911B), then any extracted FW, then work_dir.
FRAMEWORK_RES=""
if [[ -n "${SOURCE_FIRMWARE:-}" ]]; then
    _REZOSS_FW_NAME="${SOURCE_FIRMWARE%%/*}_$(cut -d'/' -f2 <<<"$SOURCE_FIRMWARE")"
    for _cand in \
        "$FW_DIR/${_REZOSS_FW_NAME}/system/system/framework/framework-res.apk" \
        "$FW_DIR/${SOURCE_FIRMWARE%%/*}_EUX/system/system/framework/framework-res.apk"; do
        if [[ -f "$_cand" ]]; then
            FRAMEWORK_RES="$_cand"
            break
        fi
    done
    unset _REZOSS_FW_NAME _cand
fi
if [[ -z "$FRAMEWORK_RES" ]]; then
    for _cand in \
        "$FW_DIR"/SM-S911B_*/system/system/framework/framework-res.apk \
        "$FW_DIR"/SM-S918B_*/system/system/framework/framework-res.apk \
        "$FW_DIR"/*/system/system/framework/framework-res.apk \
        "${WORK_DIR:-}/system/system/framework/framework-res.apk"; do
        if [[ -f "$_cand" ]]; then
            FRAMEWORK_RES="$_cand"
            break
        fi
    done
    unset _cand
fi
[[ -f "$FRAMEWORK_RES" ]] || {
    echo "Missing required local-summary artifact: framework-res.apk under $FW_DIR" >&2
    exit 1
}

cp -a "$MODPATH/smali" "$WORK/smali"
for patch_file in "$MODPATH"/patches/*.patch; do
    patch --batch --fuzz=0 --dry-run -d "$WORK/smali" -p1 < "$patch_file"
    patch --batch --fuzz=0 -d "$WORK/smali" -p1 < "$patch_file"
done
bash "$MODPATH/compile-java.sh" "$MODPATH/java" "$WORK/java-smali"
cp -a "$WORK/java-smali/." "$WORK/smali/"

APKTOOL_JAR="${SRC_DIR}/out/tools/bin/apktool.jar"
SDK=""
for _cand in \
    "${ANDROID_HOME:-}" \
    "${ANDROID_SDK_ROOT:-}" \
    "${HOME:-}/Android/Sdk" \
    "/home/cosine/Android/Sdk" \
    "/opt/android-sdk" \
    "/usr/lib/android-sdk"; do
    if [[ -n "$_cand" && -x "$_cand/build-tools/37.0.0/aapt2" ]]; then
        SDK="$_cand"
        break
    fi
done
unset _cand
[[ -n "$SDK" ]] || { echo "Android SDK build-tools/37.0.0 not found (set ANDROID_HOME)" >&2; exit 1; }
AAPT2="${SDK}/build-tools/37.0.0/aapt2"
ZIPALIGN="${SRC_DIR}/out/tools/bin/zipalign"
[[ -x "$ZIPALIGN" ]] || ZIPALIGN="${SDK}/build-tools/37.0.0/zipalign"
APKSIGNER="${SDK}/build-tools/37.0.0/apksigner"
[[ -f "$APKTOOL_JAR" ]] || { echo "Missing $APKTOOL_JAR" >&2; exit 1; }
[[ -x "$AAPT2" ]] || { echo "Missing $AAPT2" >&2; exit 1; }
[[ -x "$ZIPALIGN" ]] || { echo "Missing zipalign" >&2; exit 1; }
[[ -x "$APKSIGNER" ]] || { echo "Missing $APKSIGNER" >&2; exit 1; }

java -cp "$APKTOOL_JAR" com.android.tools.smali.smali.Main \
    assemble --api 35 --output "$WORK/classes.dex" "$WORK/smali"
"$AAPT2" link --manifest "$MODPATH/AndroidManifest.xml" -I "$FRAMEWORK_RES" -o "$WORK/unsigned.apk"
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
"$ZIPALIGN" -c -p 4 "$WORK/signed.apk"
"$APKSIGNER" verify --verbose "$WORK/signed.apk" >/dev/null

EXPECTED_CERT_SHA256="$({ keytool -printcert -file "$CERT_X509" 2>/dev/null || true; } | sed -n 's/.*SHA256: //p' | tr -d ':' | tr '[:upper:]' '[:lower:]' | head -n1)"
# build-tools 35+ prints "V3.0 Signer: certificate SHA-256 digest:"; older used "Signer #1 ...".
SIGNED_CERT_SHA256="$("$APKSIGNER" verify --print-certs "$WORK/signed.apk" 2>/dev/null \
    | sed -n -E 's/.*(Signer #[0-9]+|V[0-9]+(\.[0-9]+)?) Signer: certificate SHA-256 digest: //p' \
    | tr -d ':' | tr '[:upper:]' '[:lower:]' | head -n1)"
if [[ -z "$EXPECTED_CERT_SHA256" || "$SIGNED_CERT_SHA256" != "$EXPECTED_CERT_SHA256" ]]; then
    echo "RezossAI signing certificate mismatch" >&2
    echo "Expected: $EXPECTED_CERT_SHA256" >&2
    echo "Actual:   $SIGNED_CERT_SHA256" >&2
    exit 1
fi

mkdir -p "$(dirname -- "$OUTPUT")"
mv "$WORK/signed.apk" "$OUTPUT"
