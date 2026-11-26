#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
HERE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd "$HERE/../../.." && pwd)
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
(
    cd "$HERE"
    sha256sum -c upstream.sha256
)
WORK=$(mktemp -d /tmp/unica-viper-app.XXXXXX)
tar -xzf "$HERE/upstream-app-2.1.1.tar.gz" -C "$WORK"
python3 "$HERE/patch-app.py" "$WORK"
(
    cd "$WORK"
    ANDROID_HOME="$SDK" ./gradlew :app:assembleRelease --console=plain
)
"$SDK/build-tools/37.0.0/zipalign" -p -f 4 "$WORK/app/build/outputs/apk/release/app-release-unsigned.apk" "$WORK/aligned.apk"
# Use the ROM test certificate, not the platform identity.
"$SDK/build-tools/37.0.0/apksigner" sign \
    --key "$ROOT/security/aosp_testkey.pk8" --cert "$ROOT/security/aosp_testkey.x509.pem" \
    --out "$WORK/ViPER4Android.apk" "$WORK/aligned.apk"
"$SDK/build-tools/37.0.0/apksigner" verify --verbose "$WORK/ViPER4Android.apk"
if [[ -f "$HERE/ViPER4Android.apk" ]]; then
    cp -a "$HERE/ViPER4Android.apk" "$HERE/ViPER4Android.apk.bak.$(date +%Y%m%d_%H%M%S)"
fi
cp -a "$WORK/ViPER4Android.apk" "$HERE/ViPER4Android.apk"
python3 "$HERE/update-payload.py"
echo "ViPER app compiler output retained at $WORK"
