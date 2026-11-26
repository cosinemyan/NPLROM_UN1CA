#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Add only Samsung power-on writes and the read-only RTC time ioctl to system_app.

alarm_device is vendor-defined, so place the cross-partition rules in vendor CIL.
Keep the identifier in sync to force compilation rather than stale precompiled policy.
"""
import hashlib
import sys
from pathlib import Path

def install(work):
    platform = work / 'system/system/etc/selinux'
    vendor = work / 'vendor/etc/selinux/vendor_sepolicy.cil'
    plat = platform / 'plat_sepolicy.cil'
    identifier = platform / 'plat_sepolicy_and_mapping.sha256'
    for path in (vendor, plat, identifier):
        if not path.is_file():
            raise ValueError(f'Missing policy input: {path}')
    old = vendor.read_text()
    for symbol in ('(type alarm_device)', 'system_app_33_0', 'rtc_device_33_0'):
        if symbol not in old:
            raise ValueError(f'Unsupported vendor policy: missing {symbol}')
    begin = '; BEGIN REZOSS SCHEDULED POWER ON'
    end = '; END REZOSS SCHEDULED POWER ON'
    block = '''; BEGIN REZOSS SCHEDULED POWER ON
; Platform-signed system UID app; no raw RTC writes, shell or reboot grants.
(allow system_app_33_0 alarm_device (chr_file (open write getattr ioctl)))
(allowx system_app_33_0 alarm_device (ioctl chr_file (0x6107)))
(allow system_app_33_0 rtc_device_33_0 (chr_file (open read getattr ioctl)))
(allowx system_app_33_0 rtc_device_33_0 (ioctl chr_file (0x7009)))
; END REZOSS SCHEDULED POWER ON'''
    if begin in old:
        before, tail = old.split(begin, 1)
        if end not in tail:
            raise ValueError('Incomplete existing power-on policy block')
        _, after = tail.split(end, 1)
        new = before + block + after
    else:
        new = old.rstrip() + '\n\n' + block + '\n'
    vendor.write_text(new)
    digest = hashlib.sha256(plat.read_bytes() + vendor.read_bytes()).hexdigest()
    identifier.write_text(digest + '\n')

if __name__ == '__main__':
    install(Path(sys.argv[1]))
