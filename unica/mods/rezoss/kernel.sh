# Copyright (c) 2025–2026 NPL / Rezoss kernel helpers
# Matched to Rezoss-Reza/UN1CA lapanlima unica/mods/rezoss/customize.sh kernel path.
# Shared by customize.sh and scripts/patch_kernel_fastboot.sh
#
# Required env: SRC_DIR, WORK_DIR, MODPATH
# Optional: REZOSS_KERNEL_TMP_DIR, REZOSS_ARCHIVED_KERNEL_DIR
# Logging: LOG / LOGW / LOGE / LOG_STEP_IN / LOG_STEP_OUT / ABORT

# =============================================================================
# Kernel fallback helpers (Rezoss-compatible)
# =============================================================================
REZOSS_ARCHIVED_KERNEL_DIR="${REZOSS_ARCHIVED_KERNEL_DIR:-$SRC_DIR/out/archived/kernel}"

# NPL: archive stock images before first replace so restore works (Rezoss expects these).
_REZOSS_ARCHIVE_KERNEL_IMAGES()
{
  local IMAGE_NAME

  mkdir -p "$REZOSS_ARCHIVED_KERNEL_DIR" || return 1
  for IMAGE_NAME in boot.img init_boot.img; do
    if [ -f "$WORK_DIR/kernel/$IMAGE_NAME" ] && [ ! -f "$REZOSS_ARCHIVED_KERNEL_DIR/$IMAGE_NAME" ]; then
      LOG "- Archiving stock $IMAGE_NAME for fallback"
      cp -f "$WORK_DIR/kernel/$IMAGE_NAME" "$REZOSS_ARCHIVED_KERNEL_DIR/$IMAGE_NAME" || return 1
    fi
  done
}

_REZOSS_RESTORE_ARCHIVED_KERNEL_IMAGE()
{
  local IMAGE_NAME="$1"
  local ARCHIVED_IMAGE="$REZOSS_ARCHIVED_KERNEL_DIR/$IMAGE_NAME"
  local TARGET_IMAGE="$WORK_DIR/kernel/$IMAGE_NAME"

  if [ ! -f "$ARCHIVED_IMAGE" ]; then
    ABORT "Archived kernel image not found: ${ARCHIVED_IMAGE//$SRC_DIR\//}"
    return 1
  fi

  LOG "- Restoring $IMAGE_NAME from ${ARCHIVED_IMAGE//$SRC_DIR\//}"
  cp -f "$ARCHIVED_IMAGE" "$TARGET_IMAGE" \
    || {
      ABORT "Failed to restore $IMAGE_NAME from archived kernel"
      return 1
    }
}

_REZOSS_RESTORE_ARCHIVED_KERNEL()
{
  local RESTORE_BOOT="$1"
  local RESTORE_INIT_BOOT="$2"

  if [ "$RESTORE_BOOT" = "true" ]; then
    _REZOSS_RESTORE_ARCHIVED_KERNEL_IMAGE "boot.img" || return 1
  fi
  if [ "$RESTORE_INIT_BOOT" = "true" ]; then
    _REZOSS_RESTORE_ARCHIVED_KERNEL_IMAGE "init_boot.img" || return 1
  fi
}

_REZOSS_CHANGE_EDGARS_KERNEL_IMPL()
{
  local TMP_DIR="${REZOSS_KERNEL_TMP_DIR:-$MODPATH/tmp}"
  local RELEASE_JSON ZIP_URL ZIP_NAME IMAGE_GZ MAGISKBOOT MAGISK_APK_URL

  LOG "- Get latest release kernel"
  rm -rf "$TMP_DIR" || return 1
  mkdir -p "$TMP_DIR" || return 1

  # Rezoss: s23-ksu-next-susfs releases/latest (first .zip asset)
  RELEASE_JSON="$(curl -fsSL "https://api.github.com/repos/Rezoss-Reza/s23-ksu-next-susfs/releases/latest")" \
    || return 1
  ZIP_URL="$(echo "$RELEASE_JSON" | jq -r '
    .assets[]
    | select(.name | test("\\.zip$"))
    | .browser_download_url
  ' | head -n1)" || return 1
  if [ ! "$ZIP_URL" ] || [ "$ZIP_URL" = "null" ]; then
    LOGE "Edgars Kernel zip asset not found"
    return 1
  fi

  ZIP_NAME="$(basename "$ZIP_URL")" || return 1
  LOG "- Using Edgars Kernel asset: $ZIP_NAME"
  curl -fL --retry 3 -o "$TMP_DIR/$ZIP_NAME" "$ZIP_URL" || return 1

  LOG "- Extracting Image.gz"
  rm -rf "$TMP_DIR/zip_extract" || return 1
  mkdir -p "$TMP_DIR/zip_extract" || return 1
  unzip -o "$TMP_DIR/$ZIP_NAME" -d "$TMP_DIR/zip_extract" >/dev/null || return 1
  IMAGE_GZ="$(find "$TMP_DIR/zip_extract" -type f -name 'Image.gz' | head -n1)"
  if [ ! -f "$IMAGE_GZ" ]; then
    LOGE "Image.gz not found in Edgars Kernel release zip"
    return 1
  fi

  LOG "- Download magiskboot from magisk github"
  MAGISKBOOT="$TMP_DIR/magiskboot"
  if [[ ! -x "$MAGISKBOOT" ]]; then
    LOG "[*] magiskboot not found, downloading Magisk app to extract it..."

    MAGISK_APK_URL="$(curl -fsSL https://api.github.com/repos/topjohnwu/Magisk/releases/latest \
      | jq -r '.assets[] | select(.name | test("Magisk-v.*\\.apk$")) | .browser_download_url' \
      | head -n1)" || return 1
    if [ ! "$MAGISK_APK_URL" ] || [ "$MAGISK_APK_URL" = "null" ]; then
      LOGE "Magisk APK asset not found"
      return 1
    fi

    curl -fL --retry 3 -o "$TMP_DIR/Magisk.apk" "$MAGISK_APK_URL" || return 1
    unzip -p "$TMP_DIR/Magisk.apk" 'lib/x86_64/libmagiskboot.so' > "$MAGISKBOOT" 2>/dev/null || return 1
    chmod +x "$MAGISKBOOT" || return 1
  fi

  if [ ! -f "$WORK_DIR/kernel/boot.img" ]; then
    LOGE "File not found: ${WORK_DIR//$SRC_DIR\//}/kernel/boot.img"
    return 1
  fi

  # ===== UNPACK BOOT.IMG =====
  LOG "- Copying original boot image..."
  cp -f "$WORK_DIR/kernel/boot.img" "$TMP_DIR/boot.img" || return 1
  (
    LOG "- Unpacking boot.img..."
    cd "$TMP_DIR" || exit 1
    "$MAGISKBOOT" unpack boot.img || exit 1

    # ===== REPLACE KERNEL =====
    # Rezoss: pack Image.gz as-is (do not decompress)
    LOG "- Replacing kernel with new Image.gz..."
    cp -f "$IMAGE_GZ" "$TMP_DIR/kernel" || exit 1

    # ===== REPACK =====
    LOG "- Repacking boot image..."
    "$MAGISKBOOT" repack boot.img || exit 1

    if [[ ! -f new-boot.img ]]; then
      LOG "Repack failed: new-boot.img not generated."
      exit 1
    fi

    cp -f new-boot.img "$WORK_DIR/kernel/boot.img" || exit 1
  ) || return 1

  cd "$SRC_DIR" || return 1
}

_REZOSS_CHANGE_EDGARS_KERNEL()
{
  local STATUS=0

  LOG_STEP_IN "- Change Kernel with Edgars Kernel"
  _REZOSS_CHANGE_EDGARS_KERNEL_IMPL || STATUS=$?
  LOG_STEP_OUT

  return "$STATUS"
}

_REZOSS_PATCH_KSU_NEXT_INIT_BOOT_IMPL()
{
  local TMP_DIR="${REZOSS_KERNEL_TMP_DIR:-$MODPATH/tmp}"
  local KSU_KMI="android13-5.15"
  local KSU_INIT_BOOT="$WORK_DIR/kernel/init_boot.img"
  local KSU_RELEASE_JSON KSU_RELEASE_TAG KSU_HOST_ARCH KSU_KSUD_REGEX
  local KSU_KSUD_URL KSU_MODULE_NAME KSU_MODULE_URL KSU_KSUD KSU_MODULE
  local KSU_PATCHED_INIT_BOOT

  if [ ! -f "$KSU_INIT_BOOT" ]; then
    LOGE "File not found: ${KSU_INIT_BOOT//$SRC_DIR\//}"
    return 1
  fi

  mkdir -p "$TMP_DIR" || return 1

  LOG "- Get latest KernelSU-Next release"
  KSU_RELEASE_JSON="$(curl -fsSL "https://api.github.com/repos/KernelSU-Next/KernelSU-Next/releases/latest")" \
    || return 1
  KSU_RELEASE_TAG="$(echo "$KSU_RELEASE_JSON" | jq -r '.tag_name // empty')" || return 1
  LOG "- Using KernelSU-Next ${KSU_RELEASE_TAG:-latest}"

  KSU_HOST_ARCH="$(uname -m)" || return 1
  case "$KSU_HOST_ARCH" in
    x86_64|amd64)
      KSU_KSUD_REGEX="^ksud-(x86_64|amd64).*linux"
      ;;
    aarch64|arm64)
      KSU_KSUD_REGEX="^ksud-aarch64.*linux"
      ;;
    *)
      LOGE "Unsupported host architecture for KernelSU-Next ksud: $KSU_HOST_ARCH"
      return 1
      ;;
  esac

  KSU_KSUD_URL="$(echo "$KSU_RELEASE_JSON" | jq -r --arg regex "$KSU_KSUD_REGEX" '
    .assets[]
    | select(.name | test($regex))
    | select(.name | test("android") | not)
    | .browser_download_url
  ' | head -n1)" || return 1
  if [ ! "$KSU_KSUD_URL" ] || [ "$KSU_KSUD_URL" = "null" ]; then
    LOGE "KernelSU-Next ksud asset not found for host architecture: $KSU_HOST_ARCH"
    return 1
  fi

  KSU_MODULE_NAME="${KSU_KMI}_kernelsu.ko"
  KSU_MODULE_URL="$(echo "$KSU_RELEASE_JSON" | jq -r --arg name "$KSU_MODULE_NAME" '
    .assets[]
    | select(.name == $name)
    | .browser_download_url
  ' | head -n1)" || return 1
  if [ ! "$KSU_MODULE_URL" ] || [ "$KSU_MODULE_URL" = "null" ]; then
    LOGE "KernelSU-Next module asset not found: $KSU_MODULE_NAME"
    return 1
  fi

  KSU_KSUD="$TMP_DIR/ksud"
  KSU_MODULE="$TMP_DIR/$KSU_MODULE_NAME"

  LOG "- Download ksud"
  curl -fL --retry 3 -o "$KSU_KSUD" "$KSU_KSUD_URL" || return 1
  chmod +x "$KSU_KSUD" || return 1

  LOG "- Download $KSU_MODULE_NAME"
  curl -fL --retry 3 -o "$KSU_MODULE" "$KSU_MODULE_URL" || return 1

  LOG "- Patching init_boot.img for KMI $KSU_KMI"
  cp -f "$KSU_INIT_BOOT" "$TMP_DIR/init_boot.img" || return 1
  (
    cd "$TMP_DIR" || exit 1
    "$KSU_KSUD" boot-patch -b init_boot.img --module "$KSU_MODULE" --kmi "$KSU_KMI"
  ) || return 1

  KSU_PATCHED_INIT_BOOT="$(find "$TMP_DIR" -maxdepth 1 -type f \( -name "*patched*.img" -o -name "new-boot.img" \) -printf "%T@ %p\n" | sort -nr | head -n1 | cut -d " " -f 2-)"
  if [ ! -f "$KSU_PATCHED_INIT_BOOT" ]; then
    KSU_PATCHED_INIT_BOOT="$(find "$TMP_DIR" -maxdepth 1 -type f -name "*.img" ! -name "init_boot.img" -printf "%T@ %p\n" | sort -nr | head -n1 | cut -d " " -f 2-)"
  fi
  if [ ! -f "$KSU_PATCHED_INIT_BOOT" ]; then
    LOGE "KernelSU-Next patched init_boot image was not generated"
    return 1
  fi

  cp -f "$KSU_PATCHED_INIT_BOOT" "$KSU_INIT_BOOT" || return 1
  rm -rf "$TMP_DIR" || LOGW "Failed to remove temporary kernel directory: ${TMP_DIR//$SRC_DIR\//}"
}

_REZOSS_PATCH_KSU_NEXT_INIT_BOOT()
{
  local STATUS=0

  LOG_STEP_IN "- Patch init_boot.img with KernelSU-Next LKM"
  _REZOSS_PATCH_KSU_NEXT_INIT_BOOT_IMPL || STATUS=$?
  LOG_STEP_OUT

  return "$STATUS"
}

# Install KernelSU-Next spoofed manager into system (full ROM only).
# Skipped for fastboot-only staging dirs that have no system tree.
_REZOSS_INSTALL_KSU_NEXT_SPOOFED_MANAGER_IMPL()
{
  local CACHE_DIR="${REZOSS_KERNEL_CACHE_DIR:-$SRC_DIR/out/cache/kernel}"
  local RELEASE_JSON APK_URL APK_NAME APK_PATH TARGET_DIR TARGET_APK

  if [ ! -d "$WORK_DIR/system/system" ]; then
    LOG "- Skip KernelSU-Next manager APK (no system work_dir)"
    return 0
  fi

  if ! command -v SET_METADATA >/dev/null 2>&1; then
    LOGE "SET_METADATA unavailable; cannot install KernelSU-Next manager APK"
    return 1
  fi

  mkdir -p "$CACHE_DIR" || return 1

  LOG "- Get KernelSU-Next spoofed manager APK (latest release)"
  RELEASE_JSON="$(curl -fsSL "https://api.github.com/repos/KernelSU-Next/KernelSU-Next/releases/latest")" \
    || return 1
  APK_URL="$(echo "$RELEASE_JSON" | jq -r '
    .assets[]
    | select(.name | test("spoofed.*\\.apk$"))
    | .browser_download_url
  ' | head -n1)" || return 1
  if [ ! "$APK_URL" ] || [ "$APK_URL" = "null" ]; then
    LOGE "KernelSU-Next spoofed manager APK asset not found"
    return 1
  fi

  APK_NAME="$(basename "$APK_URL")" || return 1
  APK_PATH="$CACHE_DIR/$APK_NAME"
  if [ -f "$APK_PATH" ]; then
    LOG "- Using cached $APK_NAME"
  else
    LOG "- Download $APK_NAME"
    curl -fL --retry 3 -o "$APK_PATH" "$APK_URL" || return 1
  fi

  TARGET_DIR="$WORK_DIR/system/system/app/KernelSU_Next"
  TARGET_APK="$TARGET_DIR/KernelSU_Next.apk"
  mkdir -p "$TARGET_DIR" || return 1
  cp -f "$APK_PATH" "$TARGET_APK" || return 1

  SET_METADATA "system" "system/app/KernelSU_Next" 0 0 755 "u:object_r:system_file:s0" || return 1
  SET_METADATA "system" "system/app/KernelSU_Next/KernelSU_Next.apk" 0 0 644 "u:object_r:system_file:s0" \
    || return 1

  LOG "- Installed KernelSU-Next spoofed manager → system/app/KernelSU_Next"
  return 0
}

_REZOSS_INSTALL_KSU_NEXT_SPOOFED_MANAGER()
{
  local STATUS=0

  LOG_STEP_IN "- Install KernelSU-Next spoofed manager APK"
  _REZOSS_INSTALL_KSU_NEXT_SPOOFED_MANAGER_IMPL || STATUS=$?
  LOG_STEP_OUT

  return "$STATUS"
}

# REZOSS_APPLY_KERNEL_PATCHES [edgars|edgars+ksu|ksu]
# Default edgars+ksu matches Rezoss full ROM (+ spoofed KSU-Next manager on full ROM).
REZOSS_APPLY_KERNEL_PATCHES()
{
  local MODE="${1:-edgars+ksu}"

  _REZOSS_ARCHIVE_KERNEL_IMAGES || LOGW "Failed to archive stock kernel images before replacement"

  case "$MODE" in
    edgars)
      if ! _REZOSS_CHANGE_EDGARS_KERNEL; then
        LOGW "Edgars Kernel replacement failed; restoring archived boot.img and init_boot.img"
        _REZOSS_RESTORE_ARCHIVED_KERNEL "true" "true"
        return 1
      fi
      ;;
    ksu)
      if ! _REZOSS_PATCH_KSU_NEXT_INIT_BOOT; then
        LOGW "KernelSU-Next init_boot patch failed; restoring archived init_boot.img"
        _REZOSS_RESTORE_ARCHIVED_KERNEL "false" "true"
        return 1
      fi
      _REZOSS_INSTALL_KSU_NEXT_SPOOFED_MANAGER \
        || LOGW "KernelSU-Next spoofed manager APK install failed (kernel patch kept)"
      ;;
    edgars+ksu|*)
      if ! _REZOSS_CHANGE_EDGARS_KERNEL; then
        LOGW "Edgars Kernel replacement failed; restoring archived boot.img and init_boot.img"
        _REZOSS_RESTORE_ARCHIVED_KERNEL "true" "true"
        return 1
      elif ! _REZOSS_PATCH_KSU_NEXT_INIT_BOOT; then
        LOGW "KernelSU-Next init_boot patch failed; restoring archived init_boot.img"
        _REZOSS_RESTORE_ARCHIVED_KERNEL "false" "true"
        return 1
      fi
      _REZOSS_INSTALL_KSU_NEXT_SPOOFED_MANAGER \
        || LOGW "KernelSU-Next spoofed manager APK install failed (kernel patch kept)"
      ;;
  esac

  return 0
}
