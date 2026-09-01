# Applied by scripts/internal/apply_modules.sh during make_rom (same path as displayconfig).
SKIPUNZIP=1

FSTAB_SRC="$MODPATH/vendor/etc/fstab.qcom"
[ -f "$FSTAB_SRC" ] || ABORT "Missing $FSTAB_SRC"

ADD_TO_WORK_DIR "$MODPATH" "vendor" "etc/fstab.qcom" 0 2000 644 "u:object_r:vendor_configs_file:s0"

# Keep inode/mode on any extra copies (overlay, sku, …).
while IFS= read -r dest; do
    [ -n "$dest" ] || continue
    if [ "$dest" != "$WORK_DIR/vendor/etc/fstab.qcom" ]; then
        LOG "- Replacing ${dest#"$WORK_DIR/"}"
        cat "$FSTAB_SRC" > "$dest"
    fi
done < <(find "$WORK_DIR/vendor" -name 'fstab.qcom' -type f 2>/dev/null | sort)

DATA_LINE="$(grep -E '/data[[:space:]]|userdata' "$WORK_DIR/vendor/etc/fstab.qcom" | grep -v '^#' | head -1 || true)"
LOG "- /data fstab: ${DATA_LINE:-missing}"
if ! grep -q 'encryptable' "$WORK_DIR/vendor/etc/fstab.qcom"; then
    LOGW "fstab.qcom has no encryptable flag — recovery decrypt may still fail"
fi

unset FSTAB_SRC DATA_LINE
