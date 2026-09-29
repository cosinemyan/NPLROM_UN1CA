#!/bin/bash
# Repair the captured Offline Language Model update, not the ROM stub.
set -euo pipefail
if [[ $# != 2 ]]; then
    echo "Usage: source buildenv.sh dm3q; $0 INPUT.apk OUTPUT.apk" >&2
    exit 2
fi
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
INPUT=$(realpath "$1")
OUTPUT=$(realpath -m "$2")
[[ ! -e "$OUTPUT" ]] || { echo 'Output already exists' >&2; exit 1; }
MODPATH=$(cd -- "$(dirname -- "$0")" && pwd)
TMP=$(mktemp -d /tmp/offlinelm-repair.XXXXXX)
echo "Evidence and working files: $TMP"
python3 - "$INPUT" "$TMP" <<'PY'
import hashlib,sys,zipfile
from pathlib import Path
p=Path(sys.argv[1]); t=Path(sys.argv[2])
with p.open('rb') as f:
    assert hashlib.file_digest(f,'sha256').hexdigest() == 'f0728f73ede5f914694d7ced182b0438262e532e5b5849ae9024fcea4a6ce768', 'Unexpected APK baseline'
with zipfile.ZipFile(p) as z, zipfile.ZipFile(t/'code.apk','w') as out:
    for n in ['classes.dex','classes2.dex']:
        out.writestr(n,z.read(n))
    for n in ['assets/config/supported_config.json','lib/arm64-v8a/libjniAIOSKernelNative.so','lib/arm64-v8a/libssneural_vndk.so','lib/arm64-v8a/libsnap_qnn.so']:
        f=t/'decoded'/n; f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(z.read(n))
PY
apktool d -r --no-assets -j 2 "$TMP/code.apk" -o "$TMP/smali"
cp -a "$TMP/decoded/." "$TMP/smali/"
for PATCH in "$MODPATH/OfflineLanguageModel.apk/"*.patch; do
    patch --batch --dry-run --fuzz=0 -p1 -d "$TMP/smali" < "$PATCH"
    patch --batch --fuzz=0 -p1 -d "$TMP/smali" < "$PATCH"
done
python3 "$MODPATH/patch_native.py" "$TMP/smali/lib/arm64-v8a/libjniAIOSKernelNative.so" "$TMP/native-fixed.so"
python3 "$MODPATH/../aioskernel/patch_sm8550_chipset.py" "$TMP/smali/lib/arm64-v8a/libssneural_vndk.so" "$TMP/ssneural-fixed.so"
python3 "$MODPATH/../aioskernel/patch_qnn_logging_nullguard.py" "$TMP/smali/lib/arm64-v8a/libsnap_qnn.so" "$TMP/snap-qnn-fixed.so"
apktool b -j 2 "$TMP/smali" -o "$TMP/code-fixed.apk"
python3 - "$INPUT" "$TMP" <<'PY'
import sys,zipfile,shutil
from pathlib import Path
p=Path(sys.argv[1]);t=Path(sys.argv[2])
with zipfile.ZipFile(t/'code-fixed.apk') as z:
    replacements={n:z.read(n) for n in ['classes.dex','classes2.dex']}
replacements['assets/config/supported_config.json']=(t/'smali/assets/config/supported_config.json').read_bytes()
replacements['lib/arm64-v8a/libjniAIOSKernelNative.so']=(t/'native-fixed.so').read_bytes()
replacements['lib/arm64-v8a/libssneural_vndk.so']=(t/'ssneural-fixed.so').read_bytes()
replacements['lib/arm64-v8a/libsnap_qnn.so']=(t/'snap-qnn-fixed.so').read_bytes()
assert all(isinstance(n, str) for n in replacements), 'Invalid replacement entry'
with zipfile.ZipFile(p) as src, zipfile.ZipFile(t/'unsigned.apk','w',allowZip64=True) as out:
    for info in src.infolist():
        if info.filename.startswith('META-INF/') and info.filename.upper().endswith(('.SF','.RSA','.DSA','.EC','MANIFEST.MF')):
            continue
        if info.filename in replacements:out.writestr(info,replacements[info.filename]);continue
        with src.open(info) as a,out.open(info,'w',force_zip64=info.file_size>2**31) as b:shutil.copyfileobj(a,b,1024*1024)
PY
# Normalize the streamed ZIP container before alignment; final signing is last.
signapk "$SRC_DIR/security/aosp_platform.x509.pem" "$SRC_DIR/security/aosp_platform.pk8" "$TMP/unsigned.apk" "$TMP/normalized.apk"
zipalign -f -p 4 "$TMP/normalized.apk" "$TMP/aligned.apk"
apksigner sign --key "$SRC_DIR/security/aosp_platform.pk8" --cert "$SRC_DIR/security/aosp_platform.x509.pem" --out "$OUTPUT" "$TMP/aligned.apk"
apksigner verify "$OUTPUT"
zipalign -c -p 4 "$OUTPUT"
unzip -tq "$OUTPUT"
echo "Repaired APK: $OUTPUT (not installed)"
