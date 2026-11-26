#!/system/bin/sh
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
SKIPUNZIP=0
[ "$(getprop ro.product.device)" = dm3q ] || abort "dm3q only"
sha256sum -c "$MODPATH/stock.sha256" || abort "Restore stock paths first: disable v5/other audio overlays and reboot"
for id in rezoss_callscreen_compat_tx_test rezoss_callscreen_compat_tx_v5; do
    D=/data/adb/modules/$id
    [ ! -d "$D" ] || [ -f "$D/disable" ] || [ -f "$D/remove" ] || abort "Disable competing module $id and reboot first"
done
ui_print "INACTIVE v6 test candidate; no boot hooks or live changes."
ui_print "Action verifies payload only. Explicit arm_v6.sh is required before reboot."
set_perm_recursive "$MODPATH/candidate" 0 0 0755 0644
set_perm "$MODPATH/arm_v6.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/source_probe" 0 0 0755
