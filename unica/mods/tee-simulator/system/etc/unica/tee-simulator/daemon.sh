#!/system/bin/sh
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later

umask 077
# Stay fail-closed while recovery has disabled the backend or state is unreadable.
# Android Toybox rejects "sleep infinity". A finite wait keeps init's service alive
# and allows an explicitly removed marker to be noticed without a reboot.
while :; do
    state_readable=false
    marker_present=false
    if /system/bin/ls -a /data/misc/unica-teesim >/dev/null 2>&1; then
        state_readable=true
        # Check the directory entry: stat on an unlabeled recovery marker may fail.
        for entry in /data/misc/unica-teesim/*; do
            if [ "$entry" = /data/misc/unica-teesim/disable ]; then
                marker_present=true
                break
            fi
        done
    fi
    if [ "$state_readable" = true ] && [ "$marker_present" = false ]; then
        break
    fi
    /system/bin/sleep 2
done
export CLASSPATH=/system/framework/unica-teesim.jar
exec /system/bin/app_process64 /system/bin --nice-name=unica-teesim org.matrix.teesim.RomApp /system/etc/unica/tee-simulator
