#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Validate only. Does not sign, install, or wire this exact-version patch into the ROM.
set -euo pipefail
HERE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
PHONE=${1:?Usage: validate_phone.sh PHONE_18.1.00.75_APK NEW_OUTPUT_DIRECTORY}
OUTPUT=${2:?Supply a new output directory}
[[ ! -e "$OUTPUT" ]] || { echo "Refusing existing output directory" >&2; exit 1; }
printf '%s  %s\n' 19b1c9aca978d6346ef014b6e99c49afe88ee4f8e0d56491d25f419a6aa32f5e "$PHONE" | sha256sum -c -
SDK=${ANDROID_HOME:-$HOME/Android/Sdk}
mkdir -p "$OUTPUT"
OUTPUT=$(cd "$OUTPUT" && pwd)
apktool d -r "$PHONE" -o "$OUTPUT/decoded"
patch --dry-run --fuzz=0 -p1 -d "$OUTPUT/decoded" -i "$HERE/0001-Pass-saved-contact-identity-18.1.00.75.patch"
patch --fuzz=0 -p1 -d "$OUTPUT/decoded" -i "$HERE/0001-Pass-saved-contact-identity-18.1.00.75.patch"
mkdir "$OUTPUT/classes" "$OUTPUT/dex"
javac -source 8 -target 8 -Xlint:-options -cp "$SDK/platforms/android-37.0/android.jar" -d "$OUTPUT/classes" "$HERE/java/io/mesalabs/rezoss/callscreen/CallerIdentity.java"
jar cf "$OUTPUT/helper.jar" -C "$OUTPUT/classes" .
"$SDK/build-tools/37.0.0/d8" --min-api 36 --lib "$SDK/platforms/android-37.0/android.jar" --output "$OUTPUT/dex" "$OUTPUT/helper.jar"
apktool b "$OUTPUT/decoded" -o "$OUTPUT/phone-assembly-validation-unsigned.apk"
unzip -tq "$OUTPUT/phone-assembly-validation-unsigned.apk"
echo 'Static patch and helper validation passed. Assembly APK lacks the helper DEX and is NOT an installable test release.'
