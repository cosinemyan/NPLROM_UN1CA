#!/usr/bin/env bash
# Disable Force Encryption (DFE) Module

LOGI "Applying Disable Force Encryption (DFE) patch to fstab..."

for fstab in $(find "$WORK_DIR/vendor/etc" "$WORK_DIR/system/system/etc" -name "fstab.*" 2>/dev/null); do
  if [ -f "$fstab" ]; then
    LOG "- Patching encryption flags in $(basename "$fstab")"
    sed -i 's/fileencryption=[^:,]*//g' "$fstab"
    sed -i 's/forceencrypt=[^:,]*//g' "$fstab"
    sed -i 's/forcefbe=[^:,]*//g' "$fstab"
  fi
done

LOGI "DFE patch applied successfully."
