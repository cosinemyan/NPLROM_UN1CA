#!/system/bin/sh
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
# Explicit next-boot arming only. No live mounts/restarts/reboot.
set -eu
MODDIR=${0%/*}
fail(){ echo "REFUSED: $*"; exit 1; }
[ "$(id -u)" = 0 ] || fail "Root required"
[ "$(getprop ro.product.device)" = dm3q ] || fail "dm3q only"
[ -f "$MODDIR/skip_mount" ] || fail "Already armed/missing guard"
[ ! -e "$MODDIR/disable" ] && [ ! -e "$MODDIR/remove" ] || fail "Module disabled/removing"
[ ! -e "$MODDIR/system" ] || fail "Existing preparation: inspect before retry"
META=$(readlink -f /data/adb/metamodule)
[ "$META" = /data/adb/modules/mountify ] || fail "Mountify required"
[ ! -e "$META/disable" ] && [ ! -e "$META/remove" ] || fail "Mountify disabled/removing"
sha256sum -c "$MODDIR/stock.sha256" || fail "Live paths differ from pinned stock; restore and reboot first"
(cd "$MODDIR" && sha256sum -c payload.sha256) || fail "Candidate hashes differ"
for id in rezoss_callscreen_compat_tx_test rezoss_callscreen_compat_tx_v5; do
    D=/data/adb/modules/$id
    [ ! -d "$D" ] || [ -f "$D/disable" ] || [ -f "$D/remove" ] || fail "Competing module $id"
done
mkdir "$MODDIR/system"
cp -a "$MODDIR/candidate/vendor" "$MODDIR/system/vendor"
# Directory-specific labels are mandatory: hw uses vendor_hal_file, etc uses
# vendor_configs_file. Never recursively replace these with vendor_file.
for rel in vendor vendor/lib64 vendor/lib64/hw vendor/etc vendor/etc/audio vendor/etc/audio/sku_kalama; do
    /data/adb/ksu/bin/busybox chcon --reference="/$rel" "$MODDIR/system/$rel"
    case "$rel" in
        vendor/lib64/hw) EXPECTED=vendor_hal_file;;
        vendor/etc*) EXPECTED=vendor_configs_file;;
        *) EXPECTED=vendor_file;;
    esac
    case "$(ls -Zd "$MODDIR/system/$rel")" in *u:object_r:$EXPECTED:s0*) ;; *) fail "Wrong directory label: $rel";; esac
done
for rel in vendor/lib64/hw/audio.primary.kalama.so vendor/lib64/libcs-pal.so vendor/etc/audio/sku_kalama/resourcemanager.xml vendor/etc/audio/sku_kalama/mixer_paths.xml; do
    REF=/$rel
    [ "$rel" != vendor/lib64/libcs-pal.so ] || REF=/vendor/lib64/libar-pal.so
    /data/adb/ksu/bin/busybox chcon --reference="$REF" "$MODDIR/system/$rel"
    case "$rel" in vendor/etc/*) EXPECTED=vendor_configs_file;; *) EXPECTED=vendor_file;; esac
    case "$(ls -Zd "$MODDIR/system/$rel")" in *u:object_r:$EXPECTED:s0*) ;; *) fail "Wrong file label: $rel";; esac
    cmp "$MODDIR/candidate/$rel" "$MODDIR/system/$rel" || fail "Copy differs: $rel"
done
# Last step only; every earlier failure retains skip_mount.
mv "$MODDIR/skip_mount" "$MODDIR/armed_skip_mount_backup"
sync
echo "ARMED v6 for next boot. No live mount, restart or reboot performed."
echo "Rollback: create $MODDIR/disable and reboot."
