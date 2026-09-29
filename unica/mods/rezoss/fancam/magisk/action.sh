#!/system/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
MODDIR=${0%/*}
REPORT="$MODDIR/diagnostics.txt"
(
    echo "UN1CA FanCam SYNTHETIC bridge diagnostics"
    date
    sha256sum /system/lib64/libmediacontextanalyzer.so /system/lib64/libfancam_bridge.so /system/lib64/libauto-reframing-arm64-v8a.so
    cat /system/etc/fancam-bridge.mode
    echo "--- Recent app logs, BEFORE the separate CLI probe ---"
    logcat -d -v threadtime -t 1500 -s UNICA_FanCamBridge:V MCA_ModelDelegatorSNAP:V MCA_HumanPetDetector:V linker:V '*:S'
    echo "--- CLI contract test; this does NOT prove the Gallery path ---"
    hash_of() { sha256sum "$1" 2>/dev/null | cut -d ' ' -f 1; }
    [ "$(hash_of /system/lib64/libmediacontextanalyzer.so)" = 3bb6c3e63d2108e86f6c213c77280577e1040ec99f47d48f18d7da5d4ddcce07 ] || { echo "STOP: unexpected live MCA; reboot after installation."; exit 1; }
    [ "$(hash_of /system/lib64/libauto-reframing-arm64-v8a.so)" = e8921a043f2050d69d11f906820a400054c835bebd0f743bff7a33405c42f835 ] || { echo "STOP: unexpected auto-reframing build."; exit 1; }
    LD_LIBRARY_PATH="$MODDIR/system/lib64:/system/lib64" UNICA_FANCAM_TEST=synthetic "$MODDIR/probe/mca_probe" /system/lib64/libmediacontextanalyzer.so
) > "$REPORT" 2>&1
result=$?
cat "$REPORT"
echo "Report: $REPORT"
exit "$result"
