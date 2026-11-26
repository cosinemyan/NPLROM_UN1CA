#!/system/bin/sh
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
# Explicitly invoked AFTER installation. Changes only inactive staged labels.
set -eu
MODDIR=${0%/*}
[ "$(id -u)" = 0 ] || { echo "Root required"; exit 1; }
[ -f "$MODDIR/skip_mount" ] && [ ! -e "$MODDIR/system" ] && [ ! -e "$MODDIR/vendor" ] || exit 1
[ -d "$MODDIR/candidate/vendor/lib64/hw" ] || exit 1
chcon -R u:object_r:vendor_file:s0 "$MODDIR/candidate/vendor"
exec sh "$MODDIR/preflight.sh"
