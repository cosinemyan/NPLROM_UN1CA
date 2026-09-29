UN1CA My FanCam SYNTHETIC Bridge Test 0.1-test1

Install this ZIP using Magisk or KernelSU Manager in booted Android, then reboot.
KernelSU needs a working module-mount implementation, such as Mountify.
Requires SM8550 and the exact tested MCA/auto-reframing libraries. The installer
checks hashes and rejects overlapping native-library, model, and linker modules.
Disable older FanCam test modules and reboot before installing this package.
Includes the tested decoder plus clean S24U DLCs as Locator.dlc/Reid.dlc; these
graphs are bypassed in synthetic mode, not converted to SM8550. Requires existing
My FanCam UI gates. The current phone already has TARGET_TRACKING enabled.

TEST
Open a short disposable video in Gallery and enter My FanCam as before.
Video pixels are ignored: this supplies one moving box and fixed pose/identity
fixtures. Any selected target/crop is synthetic. Samsung's tracker may reject the
fixtures, or a media app may crash. Real person tracking is NOT implemented.
The isolated MCA and box-decoder tests have passed on SM8550; boot mounting,
Gallery's library namespace, tracking, and export remain unverified.

Expected logcat tag: UNICA_FanCamBridge. Look for "SYNTHETIC TEST ONLY: Locator",
"SYNTHETIC TEST ONLY: Reid", and "execute count=1 OK" in the editor's process.
Namespace errors, SELinux denials, or a crash need fresh logs; do not assume a
successful CLI check proves the app is using the bridge.

After trying My FanCam, press the module's Action button. It saves a bounded
native log, then runs a separate CLI check. Report location:
/data/adb/modules/unica_fancam_bridge_test/diagnostics.txt
CLI messages have their own PID. The saved log is captured before that CLI run.
Live filtered logging from this workstation:
/mnt/c/platform-tools/adb.exe logcat -v threadtime -s UNICA_FanCamBridge:V MCA_ModelDelegatorSNAP:V MCA_HumanPetDetector:V linker:V '*:S'

CONTENTS
system/lib64/libmediacontextanalyzer.so: hash-pinned copy; only two SNAP dlopen
strings changed to libfancam_bridge.so. ELF size unchanged.
system/lib64/libfancam_bridge.so: private session adapter and synthetic fixtures.
system/etc/fancam-bridge.mode: exact line synthetic-v1 activates the test in apps.
The installer copies the live linker.config.pb into the module, backs it up, and
adds absent decoder/bridge shared-library entries. This boot-time linker overlay
is removed on disabling the module and rebooting. Do not keep it across ROM updates.
Other model names forward to original SNAP using four observed MCA methods;
other MCA features have not been regression-tested. No APKs, vendor runtime,
SELinux rules, properties, or boot scripts are replaced. App linker namespace
accessibility remains a test boundary. A linker problem can affect app startup.

ROLLBACK
Disable or remove this module in the manager and reboot. Loaded libraries remain
in existing processes until restart. Disable the module before updating the ROM.
If root ADB works:
/mnt/c/platform-tools/adb.exe shell "su -c 'touch /data/adb/modules/unica_fancam_bridge_test/disable'"
/mnt/c/platform-tools/adb.exe reboot
If Android cannot boot, use your root manager's safe mode or recovery with access
to decrypted /data to disable this module. App data is never cleared by this ZIP.

Manager-installable layout: https://topjohnwu.github.io/Magisk/guides.html
This package does not support custom-recovery flashing.
