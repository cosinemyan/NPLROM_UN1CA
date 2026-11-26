#!/system/bin/sh
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
# Read-only staging checks. This script NEVER mounts or loads the candidate.
MODDIR=${0%/*}
fail() { echo "FAIL: $*"; exit 1; }
[ "$(id -u)" = 0 ] || fail "Run through the root manager"
[ "$(getprop ro.product.device)" = dm3q ] || fail "dm3q only"
[ -f "$MODDIR/skip_mount" ] || fail "Missing skip_mount guard"
for entry in system vendor post-fs-data.sh service.sh metamount.sh boot-completed.sh; do
    [ ! -e "$MODDIR/$entry" ] || fail "Unexpected active overlay/boot entry: $entry"
done
[ -f "$MODDIR/payload.sha256" ] || fail "Missing payload manifest"
(cd "$MODDIR" && sha256sum -c payload.sha256) || fail "Staged payload changed"
HAL=$(sha256sum /vendor/lib64/hw/audio.primary.kalama.so | cut -d ' ' -f 1)
PAL=$(sha256sum /vendor/lib64/libar-pal.so | cut -d ' ' -f 1)
[ "$HAL" = 4ac2a7b0844088a7f1fae3207ccb8210cef2e1a480092536bb80a25acf21489a ] || fail "Live HAL is not original supported firmware"
[ "$PAL" = 51d2ea15bbc5dfb4361946845e552ecdd80d2842d96504701069781145280bbd ] || fail "Live PAL differs"
for path in "$MODDIR/candidate/vendor" "$MODDIR/candidate/vendor/lib64" \
    "$MODDIR/candidate/vendor/lib64/hw" \
    "$MODDIR/candidate/vendor/lib64/hw/audio.primary.kalama.so" \
    "$MODDIR/candidate/vendor/lib64/libcs-pal.so"; do
    CONTEXT=$(ls -Zd "$path") || fail "Missing staged path: $path"
    case "$CONTEXT" in
        *u:object_r:vendor_file:s0*) ;;
        *) fail "Wrong staged SELinux label: $path. Run prepare_labels.sh, then check again." ;;
    esac
done
OLD=/data/adb/modules/rezoss_callscreen_compat_tx_test
if [ -d "$OLD" ] && [ ! -f "$OLD/disable" ] && [ ! -f "$OLD/remove" ]; then
    fail "The failed v3 module must remain disabled"
fi
echo "PASS: staged hashes, original firmware and staged labels verified."
echo "NOT VERIFIED: visibility and loading in the audio HAL namespace."
echo "No overlay activated; no library loaded; no audio service restarted."
