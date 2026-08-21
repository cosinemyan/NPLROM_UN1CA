#!/usr/bin/env bash
# Patch wallpaper-res.apk (NPL images + featured JSON) inside an existing flashable ZIP
# without rebuilding the ROM. Unpacks system, edits the APK, repacks with Android labels.
#
# Does NOT replace Wallpaper_001.webp / default_thumb.webp (that bootloops).
#
# Usage:
#   scripts/patch_zip_wallpaper.sh <zip|dir> [options]
#   scripts/patch_zip_wallpaper.sh --apk-only --apk wallpaper-res.apk --framework framework-res.apk
#   --inject           write a new ZIP with system.* replaced
#   --apk-only         stop after wallpaper-res.apk (no system.img / ZIP rewrite)
#   --apk FILE         patch this wallpaper-res.apk (skips system unpack)
#   --framework FILE   framework-res.apk for apktool (required with --apk)
#   --build-prop FILE  optional build.prop (apktool tag)
#   --extra FILE       also replace this zip-root file (repeatable; vendor.* from a prior patch)
#   --output DIR       default: $OUT_DIR/wallpaper_patch

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
SOURCE_DIR=""
DO_INJECT=false
DO_APK_ONLY=false
APK_IN=""
FW_IN=""
PROP_IN=""
OUTPUT_DIR="$OUT_DIR/wallpaper_patch"
EXTRA_FILES=()
SDAT2IMG="$SRC_DIR/scripts/utils/sdat2img.py"
ZIP_REPLACE="$SRC_DIR/scripts/utils/zip_replace_root.py"
WP_MOD="$SRC_DIR/unica/mods/cosine/npl_wallpapers"
BLOCK_SIZE=4096

usage() {
  echo "Usage: $(basename "$0") <NPL.zip | folder> [options]" >&2
  echo "       $(basename "$0") --apk-only --apk wallpaper-res.apk --framework framework-res.apk" >&2
  echo "  --inject       write a new zip with system files replaced (Zip64-safe)" >&2
  echo "  --apk-only     write wallpaper-res.apk only (skip system.img / ZIP rewrite)" >&2
  echo "  --apk FILE     patch this wallpaper-res.apk (no system unpack)" >&2
  echo "  --framework FILE  framework-res.apk for apktool (with --apk)" >&2
  echo "  --build-prop FILE optional build.prop for apktool tag" >&2
  echo "  --extra FILE   also inject this zip-root file (repeatable)" >&2
  echo "  --output DIR   replacement files (default: out/wallpaper_patch)" >&2
  exit 1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --inject) DO_INJECT=true ;;
    --apk-only) DO_APK_ONLY=true ;;
    --apk)
      shift
      [ -n "${1:-}" ] || usage
      APK_IN="$1"
      DO_APK_ONLY=true
      ;;
    --framework)
      shift
      [ -n "${1:-}" ] || usage
      FW_IN="$1"
      ;;
    --build-prop)
      shift
      [ -n "${1:-}" ] || usage
      PROP_IN="$1"
      ;;
    --extra)
      shift
      [ -n "${1:-}" ] || usage
      EXTRA_FILES+=("$1")
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

if [ -n "$APK_IN" ]; then
  [ -f "$APK_IN" ] || { echo -e "${RED}Not found: $APK_IN${RESET}" >&2; exit 1; }
  [ -n "$FW_IN" ] && [ -f "$FW_IN" ] || { echo -e "${RED}--apk needs --framework framework-res.apk${RESET}" >&2; exit 1; }
  [ -z "$PROP_IN" ] || [ -f "$PROP_IN" ] || { echo -e "${RED}Not found: $PROP_IN${RESET}" >&2; exit 1; }
elif [ -n "$INPUT" ]; then
  [ -e "$INPUT" ] || { echo -e "${RED}Not found: $INPUT${RESET}" >&2; exit 1; }
  if [ -d "$INPUT" ]; then
    SOURCE_DIR="$(cd "$INPUT" && pwd)"
  fi
else
  usage
fi

if [ -n "${SELECTED_TARGET:-}" ] && [ -f "$SRC_DIR/target/$SELECTED_TARGET/config.sh" ]; then
  # shellcheck disable=SC1090
  source "$SRC_DIR/target/$SELECTED_TARGET/config.sh"
fi
: "${TARGET_COMMON_SUPPORT_DYN_RESOLUTION_CONTROL:=true}"
: "${TARGET_PRODUCT_SHIPPING_API_LEVEL:=33}"
export TARGET_COMMON_SUPPORT_DYN_RESOLUTION_CONTROL TARGET_PRODUCT_SHIPPING_API_LEVEL

command -v python3 >/dev/null || { echo -e "${RED}python3 required${RESET}" >&2; exit 1; }
command -v apktool >/dev/null || { echo -e "${RED}apktool required (menu Step 0)${RESET}" >&2; exit 1; }
command -v cwebp >/dev/null || { echo -e "${RED}cwebp required${RESET}" >&2; exit 1; }
[ -x "$WP_MOD/apply_to_decoded.sh" ] || chmod +x "$WP_MOD/apply_to_decoded.sh" "$WP_MOD/inject_catalog.py" "$WP_MOD/inject_feature.py" 2>/dev/null || true
if [ -z "$APK_IN" ]; then
  command -v brotli >/dev/null || { echo -e "${RED}brotli required${RESET}" >&2; exit 1; }
  command -v extract.erofs >/dev/null || { echo -e "${RED}extract.erofs required (menu Step 0)${RESET}" >&2; exit 1; }
  [ -f "$SDAT2IMG" ] || { echo -e "${RED}Missing $SDAT2IMG${RESET}" >&2; exit 1; }
  if ! $DO_APK_ONLY; then
    command -v mkfs.erofs >/dev/null || { echo -e "${RED}mkfs.erofs required (menu Step 0)${RESET}" >&2; exit 1; }
    [ -f "$ZIP_REPLACE" ] || { echo -e "${RED}Missing $ZIP_REPLACE${RESET}" >&2; exit 1; }
  fi
fi

mkdir -p "$OUT_DIR/tmp" "$OUTPUT_DIR"
WORK=""
cleanup() {
  if [ -n "${WORK:-}" ]; then
    rm -rf "$WORK"
  fi
}
trap cleanup EXIT
WORK="$(mktemp -d "$OUT_DIR/tmp/wallpaper_patch.XXXXXX")"
export _JAVA_OPTIONS="${_JAVA_OPTIONS:--Xmx4g}"

log() { echo -e "  ${CYAN}▶${RESET} $*"; }
ok() { echo -e "  ${GREEN}✔${RESET} $*"; }
warn() { echo -e "  ${YELLOW}⚠${RESET} $*"; }
die() { echo -e "  ${RED}✘ $*${RESET}" >&2; exit 1; }

if [ -z "$APK_IN" ]; then
  free_gb="$(df -BG --output=avail "$OUT_DIR" | tail -1 | tr -dc '0-9')"
  if [ -n "$free_gb" ] && [ "$free_gb" -lt 25 ]; then
    warn "Only ${free_gb}G free on the out/ filesystem — system unpack needs ~25G+"
  fi
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

  APK_PATH="$(find "$WORK/tree" -path '*/priv-app/wallpaper-res/wallpaper-res.apk' -type f | head -1 || true)"
  [ -n "$APK_PATH" ] && [ -f "$APK_PATH" ] || die "wallpaper-res.apk not found in system image"
  ok "wallpaper-res.apk → $APK_PATH"

  FRAMEWORK_RES="$(find "$WORK/tree" -path '*/framework/framework-res.apk' -type f | head -1 || true)"
  if [ -f "$WORK/tree/system/build.prop" ]; then
    BUILD_PROP="$WORK/tree/system/build.prop"
  elif [ -f "$WORK/tree/build.prop" ]; then
    BUILD_PROP="$WORK/tree/build.prop"
  else
    BUILD_PROP="$(find "$WORK/tree" -name build.prop -type f | head -1 || true)"
  fi
  [ -n "$FRAMEWORK_RES" ] || die "framework-res.apk not found in system image"
  [ -n "$BUILD_PROP" ] || die "build.prop not found in system image"
}

patch_wallpaper_apk() {
  local apk="$APK_PATH"
  local decoded="$WORK/apktool/wallpaper-res.apk"
  local fwdir="$WORK/apktool/framework"
  mkdir -p "$fwdir" "$OUTPUT_DIR"

  local tag
  tag="$(grep '^ro.build.version.incremental=' "$BUILD_PROP" | head -1 | cut -d= -f2- | tr -d '\r')"
  [ -n "$tag" ] || tag="npl"

  log "apktool if framework-res.apk"
  apktool if -p "$fwdir" -t "$tag" "$FRAMEWORK_RES" >/dev/null \
    || apktool if -p "$fwdir" "$FRAMEWORK_RES" >/dev/null \
    || die "apktool if framework-res.apk failed"

  log "apktool d wallpaper-res.apk (no resource-path shortening on rebuild)"
  rm -rf "$decoded"
  local threads
  threads="$(nproc 2>/dev/null || echo 4)"
  apktool d --no-debug-info -j "$threads" -o "$decoded" -p "$fwdir" -t "$tag" "$apk" \
    || apktool d --no-debug-info -j "$threads" -o "$decoded" -p "$fwdir" "$apk" \
    || die "apktool decode failed"

  cp -a "$decoded/res/raw/resources_info.json" "$OUTPUT_DIR/resources_info.before.json" 2>/dev/null || true
  [ -f "$decoded/res/raw/resources_info_feature.json" ] \
    && cp -a "$decoded/res/raw/resources_info_feature.json" "$OUTPUT_DIR/resources_info_feature.before.json"

  export NPL_WP_VERIFY_DIR="$OUTPUT_DIR"
  "$WP_MOD/apply_to_decoded.sh" "$decoded" || die "wallpaper inject failed"

  if [ -f "$decoded/res/drawable-nodpi/Wallpaper_001.webp" ] || [ -f "$decoded/res/drawable-nodpi-v4/Wallpaper_001.webp" ]; then
    ok "Wallpaper_001.webp still present (not replaced)"
  fi

  log "apktool b wallpaper-res.apk (without -srp)"
  mkdir -p "$decoded/build/apk"
  if [ -d "$decoded/original/META-INF" ]; then
    cp -a "$decoded/original/META-INF" "$decoded/build/apk/META-INF"
  fi
  apktool b -j "$threads" -p "$fwdir" "$decoded" || die "apktool build failed"
  local built="$decoded/dist/wallpaper-res.apk"
  if [ ! -f "$built" ]; then
    built="$(find "$decoded/dist" -maxdepth 1 -type f -name '*.apk' | head -1 || true)"
  fi
  [ -n "$built" ] && [ -f "$built" ] || die "apktool did not produce dist/*.apk"

  local pem="$SRC_DIR/security/aosp_platform.x509.pem"
  local pk8="$SRC_DIR/security/aosp_platform.pk8"
  if command -v signapk >/dev/null && [ -f "$pem" ] && [ -f "$pk8" ]; then
    log "Signing wallpaper-res.apk with platform key"
    signapk "$pem" "$pk8" "$built" "$WORK/wallpaper-res.signed.apk" \
      || die "signapk wallpaper-res.apk failed"
    built="$WORK/wallpaper-res.signed.apk"
  else
    warn "platform signapk keys missing — installing unsigned APK (may be rejected)"
  fi

  if [ -n "${apk:-}" ] && [ -f "$apk" ] && [ -z "$APK_IN" ]; then
    log "Writing patched APK back into the system tree"
    cat "$built" > "$apk"
  fi
  cp -a "$built" "$OUTPUT_DIR/wallpaper-res.apk"
  ok "Patched $(du -h "$OUTPUT_DIR/wallpaper-res.apk" | awk '{print $1}') → $OUTPUT_DIR/wallpaper-res.apk"
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
    die "New system.img ($newsz) is larger than original ($ORIG_SIZE). Use fewer/smaller wallpapers."
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
  local outzip="$OUTPUT_DIR/${base}_wallpaper.zip"
  if [ -f "$outzip" ]; then
    warn "Removing leftover $(basename "$outzip")"
    rm -f "$outzip"
  fi

  local replace=("$OUTPUT_DIR/system.new.dat.br" "$OUTPUT_DIR/system.transfer.list" "$OUTPUT_DIR/system.patch.dat")
  local extra
  for extra in "${EXTRA_FILES[@]+"${EXTRA_FILES[@]}"}"; do
    [ -f "$extra" ] || die "extra file missing: $extra"
    replace+=("$extra")
  done

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
APK_PATH=""
FRAMEWORK_RES=""
BUILD_PROP=""
ANDROID_FC=""
ANDROID_FSC=""

echo ""
if $DO_APK_ONLY; then
  echo -e "  ${BOLD}Patch wallpaper-res.apk only${RESET}"
else
  echo -e "  ${BOLD}Patch wallpaper-res in existing ZIP${RESET}"
fi
echo -e "  ${DIM}Featured JSON only — Wallpaper_001.webp is not replaced.${RESET}"
echo ""

if [ -n "$APK_IN" ]; then
  APK_PATH="$WORK/wallpaper-res.apk"
  FRAMEWORK_RES="$WORK/framework-res.apk"
  cp -a "$APK_IN" "$APK_PATH"
  cp -a "$FW_IN" "$FRAMEWORK_RES"
  if [ -n "$PROP_IN" ]; then
    BUILD_PROP="$WORK/build.prop"
    cp -a "$PROP_IN" "$BUILD_PROP"
  else
    BUILD_PROP="$WORK/build.prop"
    printf 'ro.build.version.incremental=npl\n' > "$BUILD_PROP"
  fi
else
  extract_system_dat "$INPUT"
  unpack_system_tree
fi

patch_wallpaper_apk

echo ""
echo -e "  ${BOLD}Featured catalog${RESET}"
if [ -f "$OUTPUT_DIR/featured_catalog.txt" ]; then
  sed 's/^/    /' "$OUTPUT_DIR/featured_catalog.txt"
else
  echo -e "    ${DIM}(none)${RESET}"
fi
echo -e "  ${DIM}JSON: $OUTPUT_DIR/resources_info_feature.after.json${RESET}"
echo ""

if $DO_APK_ONLY; then
  echo -e "  ${BOLD}Patched APK:${RESET} ${CYAN}$OUTPUT_DIR/wallpaper-res.apk${RESET}"
  echo ""
  exit 0
fi

repack_system

if $DO_INJECT; then
  inject_zip
fi

echo ""
if $DO_INJECT; then
  echo -e "  ${BOLD}Flash the *_wallpaper.zip this script just wrote.${RESET}"
  echo -e "  ${DIM}Do not zip the folder again by hand.${RESET}"
else
  echo -e "  ${BOLD}Drop these into the working ZIP (replace same names at zip root):${RESET}"
  echo -e "    ${CYAN}system.new.dat.br${RESET}"
  echo -e "    ${CYAN}system.transfer.list${RESET}"
  echo -e "    ${CYAN}system.patch.dat${RESET}"
fi
echo ""
