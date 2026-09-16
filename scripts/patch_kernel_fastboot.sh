#!/usr/bin/env bash
# Build Edgars / KernelSU-Next boot+init_boot images only (no full ROM rebuild).
# Output: out/kernel_fastboot/{boot.img,init_boot.img} for fastboot flash tests.
#
# Usage:
#   scripts/patch_kernel_fastboot.sh [--mode edgars|edgars+ksu|ksu] [--source fw|work|PATH]
#
# Requires: Step 1 target init + extracted firmware (or work_dir kernel images).

set -euo pipefail

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export SRC_DIR
export OUT_DIR="${OUT_DIR:-$SRC_DIR/out}"
export FW_DIR="${FW_DIR:-$OUT_DIR/fw}"
export TOOLS_DIR="${TOOLS_DIR:-$OUT_DIR/tools}"
export PATH="$TOOLS_DIR/bin:$PATH"

# shellcheck source=scripts/utils/log_utils.sh
source "$SRC_DIR/scripts/utils/log_utils.sh"

MODE="edgars+ksu"
SOURCE_MODE="fw"
SOURCE_PATH=""
OUTPUT_DIR="$OUT_DIR/kernel_fastboot"

usage() {
  echo "Usage: $(basename "$0") [options]" >&2
  echo "  --mode edgars|edgars+ksu|ksu   default: edgars+ksu" >&2
  echo "  --source fw|work|<dir>         stock fw, work_dir, or folder with boot.img" >&2
  echo "  --output DIR                   default: out/kernel_fastboot" >&2
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --mode) MODE="$2"; shift 2 ;;
    --source) SOURCE_MODE="$2"; shift 2 ;;
    --output) OUTPUT_DIR="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

case "$MODE" in
  edgars|edgars+ksu|ksu) ;;
  *)
    echo "Invalid --mode: $MODE" >&2
    exit 1
    ;;
esac

# Load target from out/config.sh when present
if [ -f "$OUT_DIR/config.sh" ]; then
  # shellcheck disable=SC1091
  set -o allexport
  # shellcheck disable=SC1091
  source "$OUT_DIR/config.sh"
  set +o allexport
fi

TARGET_CODENAME="${TARGET_CODENAME:-${SELECTED_TARGET:-}}"
TARGET_FIRMWARE="${TARGET_FIRMWARE:-}"

resolve_fw_kernel_dir() {
  local model csc
  if [ -n "$TARGET_FIRMWARE" ]; then
    model="$(cut -d/ -f1 <<<"$TARGET_FIRMWARE")"
    csc="$(cut -d/ -f2 <<<"$TARGET_FIRMWARE")"
    if [ -d "$FW_DIR/${model}_${csc}/kernel" ]; then
      echo "$FW_DIR/${model}_${csc}/kernel"
      return 0
    fi
  fi
  # Fallback: first fw/*/kernel with boot.img
  local d
  for d in "$FW_DIR"/*/kernel; do
    [ -f "$d/boot.img" ] || continue
    echo "$d"
    return 0
  done
  return 1
}

SRC_KERNEL_DIR=""
case "$SOURCE_MODE" in
  fw)
    SRC_KERNEL_DIR="$(resolve_fw_kernel_dir)" \
      || { LOGE "No extracted firmware kernel found. Run menu Step 3 first."; exit 1; }
    ;;
  work)
    if [ -z "$TARGET_CODENAME" ]; then
      LOGE "TARGET_CODENAME unset. Run menu Step 1 first."
      exit 1
    fi
    SRC_KERNEL_DIR="$OUT_DIR/target/$TARGET_CODENAME/work_dir/kernel"
    [ -f "$SRC_KERNEL_DIR/boot.img" ] \
      || { LOGE "No work_dir kernel at $SRC_KERNEL_DIR (build ROM once, or use --source fw)"; exit 1; }
    ;;
  *)
    if [ -d "$SOURCE_MODE" ]; then
      SRC_KERNEL_DIR="$SOURCE_MODE"
    elif [ -f "$SOURCE_MODE/boot.img" ]; then
      SRC_KERNEL_DIR="$SOURCE_MODE"
    else
      LOGE "Invalid --source: $SOURCE_MODE"
      exit 1
    fi
    ;;
esac

[ -f "$SRC_KERNEL_DIR/boot.img" ] || { LOGE "boot.img missing in $SRC_KERNEL_DIR"; exit 1; }

# Staging work dir so we never mutate fw/ in place
WORK_DIR="$OUTPUT_DIR/work"
MODPATH="$SRC_DIR/unica/mods/rezoss"
export WORK_DIR MODPATH
export REZOSS_KERNEL_TMP_DIR="$OUTPUT_DIR/tmp"
export REZOSS_ARCHIVED_KERNEL_DIR="$OUTPUT_DIR/stock_archive"
# Keep shared download cache with full ROM builds
export REZOSS_KERNEL_CACHE_DIR="${REZOSS_KERNEL_CACHE_DIR:-$OUT_DIR/cache/kernel}"

rm -rf "$WORK_DIR" "$REZOSS_KERNEL_TMP_DIR"
mkdir -p "$WORK_DIR/kernel" "$OUTPUT_DIR" "$REZOSS_ARCHIVED_KERNEL_DIR" "$REZOSS_KERNEL_CACHE_DIR"

LOG_STEP_IN "- Kernel fastboot patch ($MODE)"
LOG "- Source: ${SRC_KERNEL_DIR//$SRC_DIR\//}"
LOG "- Output: ${OUTPUT_DIR//$SRC_DIR\//}"

cp -f "$SRC_KERNEL_DIR/boot.img" "$WORK_DIR/kernel/boot.img"
if [ -f "$SRC_KERNEL_DIR/init_boot.img" ]; then
  cp -f "$SRC_KERNEL_DIR/init_boot.img" "$WORK_DIR/kernel/init_boot.img"
elif [[ "$MODE" == *ksu* ]]; then
  LOGE "init_boot.img required for mode $MODE"
  exit 1
fi

# Validate Android boot header
if [[ "$(xxd -p -l 8 "$WORK_DIR/kernel/boot.img")" != "414e44524f494421" ]]; then
  LOGE "boot.img is not an Android boot image"
  exit 1
fi

# shellcheck source=unica/mods/rezoss/kernel.sh
source "$MODPATH/kernel.sh"

if ! REZOSS_APPLY_KERNEL_PATCHES "$MODE"; then
  LOGE "Kernel patch failed"
  LOG_STEP_OUT
  exit 1
fi

# Publish flashable copies (+ keep stock copies for compare)
cp -f "$WORK_DIR/kernel/boot.img" "$OUTPUT_DIR/boot.img"
[ -f "$WORK_DIR/kernel/init_boot.img" ] && cp -f "$WORK_DIR/kernel/init_boot.img" "$OUTPUT_DIR/init_boot.img"
# Also copy stock archive for easy restore flash
[ -f "$REZOSS_ARCHIVED_KERNEL_DIR/boot.img" ] && cp -f "$REZOSS_ARCHIVED_KERNEL_DIR/boot.img" "$OUTPUT_DIR/boot.stock.img"
[ -f "$REZOSS_ARCHIVED_KERNEL_DIR/init_boot.img" ] && cp -f "$REZOSS_ARCHIVED_KERNEL_DIR/init_boot.img" "$OUTPUT_DIR/init_boot.stock.img"

{
  echo "# Generated by patch_kernel_fastboot.sh — mode=$MODE"
  echo "# Source: $SRC_KERNEL_DIR"
  echo "fastboot flash boot boot.img"
  if [ -f "$OUTPUT_DIR/init_boot.img" ] && [[ "$MODE" == *ksu* ]]; then
    echo "fastboot flash init_boot init_boot.img"
  fi
  echo "fastboot reboot"
  echo ""
  echo "# Restore stock from this folder:"
  echo "# fastboot flash boot boot.stock.img"
  echo "# fastboot flash init_boot init_boot.stock.img"
} > "$OUTPUT_DIR/flash_fastboot.sh"
chmod +x "$OUTPUT_DIR/flash_fastboot.sh"

LOG "- Wrote ${OUTPUT_DIR//$SRC_DIR\//}/boot.img ($(du -h "$OUTPUT_DIR/boot.img" | awk '{print $1}'))"
[ -f "$OUTPUT_DIR/init_boot.img" ] && \
  LOG "- Wrote ${OUTPUT_DIR//$SRC_DIR\//}/init_boot.img ($(du -h "$OUTPUT_DIR/init_boot.img" | awk '{print $1}'))"
LOG_STEP_OUT

echo ""
echo "  Flash (device in fastboot):"
echo "    cd ${OUTPUT_DIR//$SRC_DIR\//}"
sed 's/^/    /' "$OUTPUT_DIR/flash_fastboot.sh" | grep -v '^    #'
echo ""

exit 0
