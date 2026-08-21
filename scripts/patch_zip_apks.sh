#!/usr/bin/env bash
# Replace patched system APKs inside an existing flashable ZIP (no ROM rebuild).
#
# Usage:
#   scripts/patch_zip_apks.sh <zip|dir> --apk FILE:priv-app/.../name.apk [--apk ...]
#   --inject       write a new ZIP with system.* replaced (Zip64-safe)
#   --output DIR   default: $OUT_DIR/apk_inject

set -euo pipefail

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export SRC_DIR
export OUT_DIR="${OUT_DIR:-$SRC_DIR/out}"
export TOOLS_DIR="${TOOLS_DIR:-$OUT_DIR/tools}"
export PATH="$TOOLS_DIR/bin:$PATH"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
RESET='\033[0m'

INPUT=""
DO_INJECT=false
OUTPUT_DIR="$OUT_DIR/apk_inject"
REPLACE_APKS=()
SDAT2IMG="$SRC_DIR/scripts/utils/sdat2img.py"
ZIP_REPLACE="$SRC_DIR/scripts/utils/zip_replace_root.py"
BLOCK_SIZE=4096

usage() {
  echo "Usage: $(basename "$0") <NPL.zip | folder> --apk FILE:priv-app/.../name.apk [--apk ...]" >&2
  echo "  --inject     write a new zip with system files replaced (Zip64-safe)" >&2
  echo "  --output DIR replacement files (default: out/apk_inject)" >&2
  exit 1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --inject) DO_INJECT=true ;;
    --apk)
      shift
      [ -n "${1:-}" ] || usage
      [[ "$1" == *:* ]] || { echo -e "${RED}--apk needs FILE:relpath${RESET}" >&2; usage; }
      REPLACE_APKS+=("$1")
      ;;
    --output)
      shift
      [ -n "${1:-}" ] || usage
      OUTPUT_DIR="$1"
      ;;
    -h|--help) usage ;;
    -*)
      echo -e "${RED}Unknown option: $1${RESET}" >&2
      usage
      ;;
    *)
      if [ -z "$INPUT" ]; then
        INPUT="$1"
      else
        echo -e "${RED}Unexpected argument: $1${RESET}" >&2
        usage
      fi
      ;;
  esac
  shift
done

[ -n "$INPUT" ] && [ -e "$INPUT" ] || usage
[ "${#REPLACE_APKS[@]}" -gt 0 ] || { echo -e "${RED}Need at least one --apk FILE:relpath${RESET}" >&2; usage; }

command -v python3 >/dev/null || { echo -e "${RED}python3 required${RESET}" >&2; exit 1; }
command -v brotli >/dev/null || { echo -e "${RED}brotli required${RESET}" >&2; exit 1; }
command -v extract.erofs >/dev/null || { echo -e "${RED}extract.erofs required (menu Step 0)${RESET}" >&2; exit 1; }
command -v mkfs.erofs >/dev/null || { echo -e "${RED}mkfs.erofs required (menu Step 0)${RESET}" >&2; exit 1; }
[ -f "$SDAT2IMG" ] || { echo -e "${RED}Missing $SDAT2IMG${RESET}" >&2; exit 1; }
[ -f "$ZIP_REPLACE" ] || { echo -e "${RED}Missing $ZIP_REPLACE${RESET}" >&2; exit 1; }

for spec in "${REPLACE_APKS[@]}"; do
  src="${spec%%:*}"
  [ -f "$src" ] || { echo -e "${RED}Not found: $src${RESET}" >&2; exit 1; }
done

mkdir -p "$OUT_DIR/tmp" "$OUTPUT_DIR"
WORK=""
cleanup() {
  if [ -n "${WORK:-}" ]; then
    rm -rf "$WORK"
  fi
}
trap cleanup EXIT
WORK="$(mktemp -d "$OUT_DIR/tmp/apk_inject.XXXXXX")"

log() { echo -e "  ${CYAN}▶${RESET} $*"; }
ok() { echo -e "  ${GREEN}✔${RESET} $*"; }
warn() { echo -e "  ${YELLOW}⚠${RESET} $*"; }
die() { echo -e "  ${RED}✘ $*${RESET}" >&2; exit 1; }

free_gb="$(df -BG --output=avail "$OUT_DIR" | tail -1 | tr -dc '0-9')"
if [ -n "$free_gb" ] && [ "$free_gb" -lt 25 ]; then
  warn "Only ${free_gb}G free on the out/ filesystem — system unpack needs ~25G+"
fi

find_extract_cfg() {
  local name="$1"
  local f
  f="$(find "$WORK/tree" -path "*/config/$name" -type f 2>/dev/null | head -1)"
  [ -n "$f" ] && [ -f "$f" ] && echo "$f"
}

save_android_pack_config() {
  local fc fsc
  fc="$(find_extract_cfg system_file_contexts || true)"
  [ -z "$fc" ] && fc="$(find "$WORK/tree" -path '*/config/*file_contexts' -type f 2>/dev/null | head -1 || true)"
  fsc="$(find_extract_cfg system_fs_config || true)"
  [ -z "$fsc" ] && fsc="$(find "$WORK/tree" -path '*/config/*fs_config' -type f 2>/dev/null | head -1 || true)"
  ANDROID_FC=""
  ANDROID_FSC=""
  if [ -n "$fc" ] && [ -s "$fc" ]; then
    if grep -qE 'user_home_t|unconfined_u' "$fc"; then
      die "extract.erofs file_contexts has Fedora labels (user_home_t). Use the original working ZIP."
    fi
    if ! grep -qE 'u:object_r:(system_file|system_app_data_file|apk_data_file)' "$fc"; then
      die "extract.erofs file_contexts is not Android system labels. Use the original working ZIP."
    fi
    ANDROID_FC="$WORK/android_file_contexts"
    cp -a "$fc" "$ANDROID_FC"
    ok "Android file_contexts: $(wc -l < "$ANDROID_FC") labels (from source image)"
  fi
  if [ -n "$fsc" ] && [ -s "$fsc" ]; then
    ANDROID_FSC="$WORK/android_fs_config"
    cp -a "$fsc" "$ANDROID_FSC"
  fi
}

image_has_host_selinux() {
  python3 - "$1" <<'PY'
import sys
from pathlib import Path
data = Path(sys.argv[1]).read_bytes()
sys.exit(0 if b"user_home_t" in data or b"unconfined_u" in data else 1)
PY
}

image_fs() {
  python3 - "$1" <<'PY'
import sys
from pathlib import Path
p = Path(sys.argv[1])
data = p.read_bytes()
if len(data) >= 1082 and data[1080:1082] == b"\x53\xef":
    print("ext4")
elif len(data) >= 1028 and data[1024:1028] == b"\x10\x20\xf5\xf2":
    print("f2fs")
elif len(data) >= 1028 and data[1024:1028] == b"\xe2\xe1\xf5\xe0":
    print("erofs")
else:
    print("unknown")
PY
}

extract_system_dat() {
  local src="$1"
  local dest="$WORK/in"
  mkdir -p "$dest"

  if [ -f "$src" ] && [[ "$src" == *.zip ]]; then
    log "Extracting system.* from $(basename "$src")  (this is large)"
    unzip -o -j "$src" \
      "system.new.dat.br" "system.new.dat" "system.transfer.list" "system.patch.dat" \
      -d "$dest" >/dev/null 2>&1 || true
    SOURCE_ZIP="$src"
  elif [ -d "$src" ]; then
    log "Using system files in $src"
    for f in system.new.dat.br system.new.dat system.transfer.list system.patch.dat system.img; do
      [ -f "$src/$f" ] && cp -a "$src/$f" "$dest/"
    done
  else
    die "Unsupported input: $src"
  fi

  if [ -f "$dest/system.img" ] && [ ! -f "$dest/system.transfer.list" ]; then
    cp -a "$dest/system.img" "$WORK/system.img"
    return 0
  fi

  [ -f "$dest/system.transfer.list" ] || die "system.transfer.list not found"
  if [ -f "$dest/system.new.dat.br" ] && [ ! -f "$dest/system.new.dat" ]; then
    log "Decompressing system.new.dat.br"
    brotli -d -o "$dest/system.new.dat" "$dest/system.new.dat.br"
  fi
  [ -f "$dest/system.new.dat" ] || die "system.new.dat / system.new.dat.br not found"

  log "Converting DAT → system.img"
  python3 "$SDAT2IMG" "$dest/system.transfer.list" "$dest/system.new.dat" "$WORK/system.img"
  rm -f "$dest/system.new.dat" "$dest/system.new.dat.br"
}

unpack_system_tree() {
  local img="$WORK/system.img"
  local fs
  fs="$(image_fs "$img")"
  echo "$fs" > "$WORK/fs_type"
  log "Filesystem: $fs  size=$(stat -c%s "$img") bytes"
  ORIG_SIZE="$(stat -c%s "$img")"
  [ "$fs" = "erofs" ] || die "Unsupported system filesystem: $fs (need erofs)"

  mkdir -p "$WORK/tree"
  log "extract.erofs system (several GB — wait)"
  if ! extract.erofs -x -i "$img" -o "$WORK/tree" \
    && ! extract.erofs -i "$img" -o "$WORK/tree"; then
    die "extract.erofs failed"
  fi
  chmod -R u+w "$WORK/tree" 2>/dev/null || true
  save_android_pack_config
}

replace_apks() {
  local spec src rel dest dir base
  for spec in "${REPLACE_APKS[@]}"; do
    src="${spec%%:*}"
    rel="${spec#*:}"
    rel="${rel#/}"
    dest="$(find "$WORK/tree" -path "*/$rel" -type f | head -1 || true)"
    [ -n "$dest" ] && [ -f "$dest" ] || die "Not in system image: $rel"
    log "Replacing $rel"
    cat "$src" > "$dest"
    dir="$(dirname "$dest")"
    base="$(basename "$dest")"
    rm -rf "$dir/oat"
    rm -f "$dir/${base}.prof" "$dir/${base}.bprof"
    ok "$rel ($(du -h "$dest" | awk '{print $1}'))"
  done
}

repack_system() {
  command -v mkfs.erofs >/dev/null || die "mkfs.erofs not in PATH"
  local img="$WORK/system.img"
  local src="$WORK/tree"
  local mount_point="/"

  if grep -qE '^/system/system/' "$ANDROID_FC" 2>/dev/null; then
    mount_point="/"
    src="$WORK/tree"
  elif grep -qE '^/system/priv-app' "$ANDROID_FC" 2>/dev/null; then
    mount_point="/system"
    if [ -d "$WORK/tree/system/priv-app" ] && [ ! -d "$WORK/tree/priv-app" ]; then
      src="$WORK/tree/system"
    else
      src="$WORK/tree"
    fi
  fi

  [ -n "${ANDROID_FC:-}" ] && [ -f "$ANDROID_FC" ] || die "No Android file_contexts from extract.erofs"

  log "mkfs.erofs system (mount-point $mount_point, keep original image size)"
  rm -f "$WORK/system.new.img"
  local mkfs_args=(-z "lz4hc,9" -b 4096 --mount-point "$mount_point" -T "1640995200" --file-contexts "$ANDROID_FC")
  if [ -n "${ANDROID_FSC:-}" ] && [ -f "$ANDROID_FSC" ]; then
    mkfs_args+=(--fs-config-file "$ANDROID_FSC")
  fi
  mkfs.erofs "${mkfs_args[@]}" "$WORK/system.new.img" "$src" || die "mkfs.erofs failed"
  if image_has_host_selinux "$WORK/system.new.img"; then
    die "system.img contains Fedora SELinux (user_home_t). Do not flash this."
  fi
  ok "system.img has Android labels, no Fedora user_home_t"

  local newsz
  newsz="$(stat -c%s "$WORK/system.new.img")"
  if [ "$newsz" -gt "$ORIG_SIZE" ]; then
    die "New system.img ($newsz) is larger than original ($ORIG_SIZE)."
  fi
  if [ "$newsz" -lt "$ORIG_SIZE" ]; then
    log "Padding system.img $newsz → $ORIG_SIZE"
    truncate -s "$ORIG_SIZE" "$WORK/system.new.img"
  fi
  mv -f "$WORK/system.new.img" "$img"

  local blocks=$((ORIG_SIZE / BLOCK_SIZE))
  [ $((ORIG_SIZE % BLOCK_SIZE)) -eq 0 ] || die "system.img size is not a 4096-byte multiple"

  log "Writing transfer.list + system.new.dat"
  {
    echo "4"
    echo "$blocks"
    echo "0"
    echo "0"
    echo "erase 2,0,$blocks"
    echo "new 2,0,$blocks"
  } > "$OUTPUT_DIR/system.transfer.list"
  cp -a "$img" "$OUTPUT_DIR/system.new.dat"
  : > "$OUTPUT_DIR/system.patch.dat"

  log "Compressing system.new.dat.br (slow — several GB)"
  rm -f "$OUTPUT_DIR/system.new.dat.br"
  brotli --quality=6 --force --output="$OUTPUT_DIR/system.new.dat.br" "$OUTPUT_DIR/system.new.dat"
  rm -f "$OUTPUT_DIR/system.new.dat"
  ok "Wrote $OUTPUT_DIR/system.new.dat.br"
  ok "Wrote $OUTPUT_DIR/system.transfer.list"
  ok "Wrote $OUTPUT_DIR/system.patch.dat  (empty — keep it)"
}

inject_zip() {
  local src="${SOURCE_ZIP:-}"
  [ -n "$src" ] && [ -f "$src" ] || die "--inject needs a .zip input"
  [ -f "$OUTPUT_DIR/system.new.dat.br" ] || die "system.new.dat.br missing in $OUTPUT_DIR"
  local base
  base="$(basename "$src" .zip)"
  local outzip="$OUTPUT_DIR/${base}_apks.zip"
  if [ -f "$outzip" ]; then
    warn "Removing leftover $(basename "$outzip")"
    rm -f "$outzip"
  fi

  local replace=("$OUTPUT_DIR/system.new.dat.br" "$OUTPUT_DIR/system.transfer.list" "$OUTPUT_DIR/system.patch.dat")
  log "Writing $(basename "$outzip") (Zip64 rewrite)"
  python3 "$ZIP_REPLACE" "$src" "$outzip" "${replace[@]}" || die "zip rewrite failed"

  local pem="$SRC_DIR/security/aosp_testkey.x509.pem"
  local pk8="$SRC_DIR/security/aosp_testkey.pk8"
  if command -v signapk >/dev/null && [ -f "$pem" ] && [ -f "$pk8" ]; then
    log "Re-signing zip"
    local signed="${outzip%.zip}-sign.zip"
    if signapk -w "$pem" "$pk8" "$outzip" "$signed"; then
      mv -f "$signed" "$outzip"
    else
      warn "signapk failed — unsigned zip kept (TWRP can still flash it)"
      rm -f "$signed"
    fi
  else
    warn "signapk not found — zip signatures are stale. TWRP usually still flashes it."
  fi
  ok "Patched zip: $outzip"
}

SOURCE_ZIP=""
ORIG_SIZE=0
ANDROID_FC=""
ANDROID_FSC=""

echo ""
echo -e "  ${BOLD}Replace APKs in existing ZIP${RESET}"
echo -e "  ${DIM}Does not rebuild the ROM. Unpacks system, swaps APKs, repacks.${RESET}"
echo ""

extract_system_dat "$INPUT"
unpack_system_tree
replace_apks
repack_system

if $DO_INJECT; then
  inject_zip
fi

echo ""
if $DO_INJECT; then
  echo -e "  ${BOLD}Flash this zip:${RESET} ${CYAN}$OUTPUT_DIR/*_apks.zip${RESET}"
  echo -e "  ${DIM}Do not zip the folder again by hand.${RESET}"
else
  echo -e "  ${BOLD}Drop these into the working ZIP (replace same names at zip root):${RESET}"
  echo -e "    ${CYAN}system.new.dat.br${RESET}"
  echo -e "    ${CYAN}system.transfer.list${RESET}"
  echo -e "    ${CYAN}system.patch.dat${RESET}"
fi
echo ""
exit 0
