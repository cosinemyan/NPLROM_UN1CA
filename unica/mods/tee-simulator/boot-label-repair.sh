#!/system/bin/sh
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later

# Compatibility safeguard for installed ROMs predating the forced init relabel.
# Never replace the frozen state or broaden SELinux access to system_data_file.
[ -f /system/etc/init/unica-teesim.rc ] || exit 0
while [ "$(/system/bin/getprop sys.boot_completed)" != "1" ]; do
    /system/bin/sleep 2
done
/system/bin/restorecon -RF /data/misc/unica-teesim || {
    /system/bin/log -t unica-teesim-label-repair -p e "State relabel failed"
    exit 1
}
# Init automatically retries a previously failed daemon. Do not restart a healthy one.
