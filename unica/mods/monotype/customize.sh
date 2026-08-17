# The One UI 8.5 path is handled by the static smali patch in
# smali/system/priv-app/SecSettings/SecSettings.apk.

FONT_PRELOAD_PACKAGE="com.monotype.android.font.samsungsans"
FONT_PRELOAD_CONFIG="$WORK_DIR/system/system/etc/sysconfig/allowed-system-preload-apps.xml"

if [ -f "$FONT_PRELOAD_CONFIG" ] && ! grep -q "package=\"$FONT_PRELOAD_PACKAGE\"" "$FONT_PRELOAD_CONFIG"; then
    LOG "- Allowing SamsungSans Monotype preload package"
    sed -i "/<\/config>/i\\    <allowed-system-preload package=\"$FONT_PRELOAD_PACKAGE\"/>" "$FONT_PRELOAD_CONFIG"
fi
