#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Build the settings/API app, with model assets only for the separate user APK.
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
OUTPUT=${1:-$SRC_DIR/out/archived/QWEN_LLM/QWEN_LLM_rezoss_v9.apk}
MODE=${2:-full}
[[ "$MODE" == full || "$MODE" == --stub ]] || { echo "Usage: build.sh [OUTPUT_APK] [--stub]" >&2; exit 1; }
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}; TOOLS=$SDK/build-tools/37.0.0
WORK=$(mktemp -d /tmp/rezoss-api-app.XXXXXX)
trap 'rm -rf "$WORK"' EXIT
PAYLOAD=$ROOT/../../aioskernel/native-now-nudge/payload
if [[ "$MODE" == full ]]; then
    python3 "$ROOT/../../aioskernel/native-now-nudge/verify_payload.py" "$PAYLOAD"
fi
mkdir -p "$WORK/classes" "$WORK/dex"
javac -source 8 -target 8 -Xlint:-options -classpath "$SDK/platforms/android-37.0/android.jar" \
    -d "$WORK/classes" "$ROOT"/java/io/mesalabs/rezoss/qwen/*.java
jar cf "$WORK/classes.jar" -C "$WORK/classes" .
"$TOOLS/d8" --min-api 30 --lib "$SDK/platforms/android-37.0/android.jar" --output "$WORK/dex" "$WORK/classes.jar"
MANIFEST="$WORK/AndroidManifest.xml"
python3 - "$ROOT/AndroidManifest.xml" "$MANIFEST" "$MODE" "${UNICA_LOCAL_LLM_BACKEND:-gemma-gpu}" "${TARGET_CODENAME:-dm3q}" "${UNICA_ENABLE_NATIVE_NOW_NUDGE:-true}" <<'MANIFEST_PY'
from pathlib import Path
import sys, xml.etree.ElementTree as ET
source, output, mode, backend, target, native = sys.argv[1:]
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
tree = ET.parse(source)
if mode == '--stub':
    root = tree.getroot()
    root.set('{http://schemas.android.com/apk/res/android}versionCode', '1')
    root.set('{http://schemas.android.com/apk/res/android}versionName', '3.2-stub')
    if backend == 'gemma-gpu' and target == 'dm3q' and native == 'true':
        root.set('{http://schemas.android.com/apk/res/android}versionCode', '11')
        root.set('{http://schemas.android.com/apk/res/android}versionName', '3.4-memory-api')
    app = root.find('application')
    for provider in list(app.findall('provider')):
        if provider.get('{http://schemas.android.com/apk/res/android}name') == '.ModelProvider':
            app.remove(provider)
tree.write(output, encoding='utf-8', xml_declaration=True)
MANIFEST_PY
"$TOOLS/aapt2" link --manifest "$MANIFEST" -I "$SDK/platforms/android-37.0/android.jar" -o "$WORK/base.apk"
cp "$WORK/dex/classes.dex" "$WORK/"
(
    cd "$WORK"
    zip -q -0 base.apk classes.dex
)
if [[ "$MODE" == full ]]; then
python3 - "$WORK" "$PAYLOAD" <<'PY'
from pathlib import Path
import json, shutil, sys, zipfile
work, payload = map(Path, sys.argv[1:])
contents = json.loads((payload/'CONTENTS.json').read_text())
prefix = 'system/etc/unica/now-nudge-v73/'
info = {'contract':'qwen3-0.6b-sm8550-v73-1024-v1'}
with zipfile.ZipFile(work/'base.apk', 'a', compression=zipfile.ZIP_STORED, allowZip64=True) as z:
    for filename, key in [('hybrid_llama_qnn.pte','model'), ('tokenizer.json','tokenizer')]:
        source = payload/prefix/filename
        parts = [source] if source.is_file() else sorted(source.parent.glob(filename+'.[0-9][0-9]'))
        with z.open('assets/v73/'+filename, 'w') as dst:
            for part in parts:
                with part.open('rb') as src: shutil.copyfileobj(src, dst, 1024*1024)
        info[key+'_size'] = contents[prefix+filename]['size']
        info[key+'_sha256'] = contents[prefix+filename]['sha256']
    z.writestr('assets/v73/model-info.json', json.dumps(info, sort_keys=True))
PY
fi
"$TOOLS/zipalign" -P 16 -f 4 "$WORK/base.apk" "$WORK/aligned.apk"
CERT=aosp
if ${ROM_IS_OFFICIAL:-false}; then CERT=unica; fi
"$TOOLS/apksigner" sign --key "$SRC_DIR/security/${CERT}_platform.pk8" --cert "$SRC_DIR/security/${CERT}_platform.x509.pem" \
    --out "$WORK/signed.apk" "$WORK/aligned.apk"
"$TOOLS/apksigner" verify --verbose "$WORK/signed.apk"
"$TOOLS/zipalign" -c -P 16 4 "$WORK/signed.apk"
unzip -tq "$WORK/signed.apk"
mkdir -p "$(dirname -- "$OUTPUT")"
if [[ -e "$OUTPUT" ]]; then
    BACKUP="$OUTPUT.bak.$(date +%Y%m%d_%H%M%S)"
    [[ ! -e "$BACKUP" ]] || { echo "Backup exists: $BACKUP" >&2; exit 1; }
    cp -a "$OUTPUT" "$BACKUP"
    [[ ! -e "$OUTPUT.version" ]] || cp -a "$OUTPUT.version" "$BACKUP.version"
fi
mv "$WORK/signed.apk" "$OUTPUT"
APK_SHA256_LINE=$(sha256sum "$OUTPUT")
APK_SHA256=${APK_SHA256_LINE%% *}
APK_SIZE=$(stat -c %s "$OUTPUT")
GIT_COMMIT=$(git -C "$SRC_DIR" rev-parse --short=12 HEAD 2>/dev/null || true)
VERSION_FILE="$OUTPUT.version"
read -r VERSION_CODE VERSION_NAME < <(python3 - "$MANIFEST" <<'VERSION_PY'
import sys, xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
ns = '{http://schemas.android.com/apk/res/android}'
print(root.get(ns+'versionCode'), root.get(ns+'versionName'))
VERSION_PY
)
{
    printf "package=io.mesalabs.rezoss.qwen\n"
    printf "versionCode=%s\n" "$VERSION_CODE"
    printf "versionName=%s\n" "$VERSION_NAME"
    printf "apk=%s\n" "$OUTPUT"
    printf "sizeBytes=%s\n" "$APK_SIZE"
    printf "sha256=%s\n" "$APK_SHA256"
    printf "backend=local-v73-or-explicit-api\n"
    printf "packaging=%s\n" "${MODE#--}"
    printf "model=Qwen3-0.6B-SM8550-V73\n"
    if [[ "$MODE" == full ]]; then
        printf "modelAssets=included\n"
    else
        printf "modelAssets=separate-install-required\n"
    fi
    printf "credentialStorage=android-keystore-aes-gcm\n"
    printf "builtAtUtc=%s\n" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    [[ -n "$GIT_COMMIT" ]] && printf "sourceCommit=%s\n" "$GIT_COMMIT"
} > "$VERSION_FILE"
sha256sum "$OUTPUT"
echo "version metadata: $VERSION_FILE"
