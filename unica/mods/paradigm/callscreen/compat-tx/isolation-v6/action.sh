#!/system/bin/sh
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
set -eu
MODDIR=${0%/*}
(cd "$MODDIR" && sha256sum -c payload.sha256)
if [ -f "$MODDIR/skip_mount" ]; then
    echo "Payload OK. INACTIVE: this check does not arm the module."
else
    echo "Armed. Mounted hashes must match the candidate after reboot:"
    sha256sum /vendor/lib64/hw/audio.primary.kalama.so /vendor/lib64/libcs-pal.so \
        /vendor/etc/audio/sku_kalama/resourcemanager.xml /vendor/etc/audio/sku_kalama/mixer_paths.xml
    echo "Kernel source state NOW (idle ZERO is not a call isolation test):"
    "$MODDIR/source_probe"
fi
