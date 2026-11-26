# SPDX-License-Identifier: GPL-3.0-or-later
SKIPUNZIP=1
if [[ "$TARGET_CODENAME" == "dm3q" ]]; then
    (cd "$MODPATH" && sha256sum -c build.sha256) || { ABORT "Volume Up detector sources changed; run its build.sh"; return 1; }
    python3 "$MODPATH/install-policy.py" "$WORK_DIR" || { ABORT "Volume Up detector policy installation failed"; return 1; }
    ADD_TO_WORK_DIR "$MODPATH/payload" system . 0 0 755 u:object_r:system_file:s0
    SET_METADATA system system/bin/unica-volumeup 0 0 755 u:object_r:unica_volumeup_exec:s0
    SET_METADATA system system/etc/init/unica_volumeup.rc 0 0 644 u:object_r:system_file:s0
else
    LOG "- Early boot Volume Up detector is restricted to dm3q; skipping"
fi
