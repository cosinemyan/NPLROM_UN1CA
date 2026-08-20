#!/usr/bin/env bash
# Patch vendor/build.prop inside an existing flashable ZIP (or vendor.* DAT files)
# without rebuilding the ROM. Output replacement files to drop back into the ZIP.
#
# Usage:
#   scripts/patch_zip_vendor.sh <zip|dir|vendor.new.dat.br> [options]
#   --prop FILE     apply key=value lines (empty value deletes)
#   --edit          pause so you can edit the extracted build.prop
#   --inject        write a new ZIP with vendor files replaced
#   --inject-only   reuse already-converted vendor.* (skip unpack/repack)
#   --output DIR    default: $OUT_DIR/vendor_patch

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
PROP_FILES=()
FSTAB_FILE=""
DO_EDIT=false
DO_INJECT=false
DO_INJECT_ONLY=false
DO_REPACK=false
OUTPUT_DIR="$OUT_DIR/vendor_patch"
SDAT2IMG="$SRC_DIR/scripts/utils/sdat2img.py"
ZIP_REPLACE="$SRC_DIR/scripts/utils/zip_replace_root.py"
BLOCK_SIZE=4096

usage() {
  echo "Usage: $(basename "$0") <NPL.zip | folder | vendor.new.dat.br> [options]" >&2
  echo "  --prop FILE   apply vendor.prop (repeatable)" >&2
  echo "  --fstab FILE  replace vendor/etc/fstab.qcom (keeps inode/SELinux)" >&2
  echo "  --edit        pause to edit build.prop" >&2
  echo "  --inject      write a new zip with vendor files replaced (Zip64-safe)" >&2
  echo "  --inject-only skip convert; inject already-built vendor.* into the zip" >&2
  echo "  --repack      copy vendor.* into the extracted ROM folder and zip it" >&2
  echo "  --output DIR  replacement files (default: out/vendor_patch)" >&2
  exit 1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --prop)
      shift
      [ -n "${1:-}" ] || usage
      PROP_FILES+=("$1")
      ;;
    --fstab)
      shift
      [ -n "${1:-}" ] || usage
      FSTAB_FILE="$1"
      ;;
    --edit) DO_EDIT=true ;;
    --inject) DO_INJECT=true ;;
    --inject-only) DO_INJECT=true; DO_INJECT_ONLY=true ;;
    --repack) DO_REPACK=true ;;
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

[ -n "$INPUT" ] || usage
[ -e "$INPUT" ] || { echo -e "${RED}Not found: $INPUT${RESET}" >&2; exit 1; }
if [ -d "$INPUT" ]; then
  SOURCE_DIR="$(cd "$INPUT" && pwd)"
fi

if ! $DO_INJECT_ONLY && [ ${#PROP_FILES[@]} -eq 0 ] && ! $DO_EDIT && [ -z "$FSTAB_FILE" ]; then
  echo -e "${RED}Pass --prop FILE, --fstab FILE, and/or --edit${RESET}" >&2
  exit 1
fi

command -v python3 >/dev/null || { echo -e "${RED}python3 required${RESET}" >&2; exit 1; }
[ -f "$ZIP_REPLACE" ] || { echo -e "${RED}Missing $ZIP_REPLACE${RESET}" >&2; exit 1; }
if ! $DO_INJECT_ONLY; then
  command -v brotli >/dev/null || { echo -e "${RED}brotli required${RESET}" >&2; exit 1; }
  [ -f "$SDAT2IMG" ] || { echo -e "${RED}Missing $SDAT2IMG${RESET}" >&2; exit 1; }
fi

mkdir -p "$OUT_DIR/tmp" "$OUTPUT_DIR" "$OUT_DIR/vendor_in"
WORK=""
MOUNTED=false
cleanup() {
  if ${MOUNTED:-false} && [ -n "${WORK:-}" ]; then
    sudo umount "$WORK/mnt" 2>/dev/null || true
  fi
  if [ -n "${WORK:-}" ]; then
    rm -rf "$WORK"
  fi
}
trap cleanup EXIT
if ! $DO_INJECT_ONLY; then
  WORK="$(mktemp -d "$OUT_DIR/tmp/vendor_patch.XXXXXX")"
fi

log() { echo -e "  ${CYAN}▶${RESET} $*"; }
ok() { echo -e "  ${GREEN}✔${RESET} $*"; }
warn() { echo -e "  ${YELLOW}⚠${RESET} $*"; }
die() { echo -e "  ${RED}✘ $*${RESET}" >&2; exit 1; }

# extract.erofs writes Android labels here from the *source image*, not the host.
# Pack with these so Fedora (SELinux disabled) still gets u:object_r:vendor_file:s0.
find_extract_cfg() {
  local name="$1"
  local f
  f="$(find "$WORK/tree" -path "*/config/$name" -type f 2>/dev/null | head -1)"
  [ -n "$f" ] && [ -f "$f" ] && echo "$f"
}

save_android_pack_config() {
  local fc fsc
  fc="$(find_extract_cfg vendor_file_contexts || true)"
  fsc="$(find_extract_cfg vendor_fs_config || true)"
  ANDROID_FC=""
  ANDROID_FSC=""
  if [ -n "$fc" ] && [ -s "$fc" ]; then
    if grep -qE 'user_home_t|unconfined_u' "$fc"; then
      die "extract.erofs file_contexts has Fedora labels (user_home_t). Use the original working ZIP, not a previous Fedora pack."
    fi
    if ! grep -q 'u:object_r:vendor_file' "$fc"; then
      die "extract.erofs file_contexts is not Android vendor labels. Use the original working ZIP."
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

apply_prop_file() {
  local propfile="$1"
  local buildprop="$2"
  local key val key_esc
  while IFS= read -r l || [ -n "$l" ]; do
    [[ "$l" =~ ^[[:space:]]*# ]] && continue
    [[ -z "${l// }" ]] && continue
    [[ "$l" == *"="* ]] || die "Malformed line in $propfile: $l"
    key="${l%%=*}"
    val="${l#*=}"
    key_esc="$(printf '%s' "$key" | sed 's/[][().*^$\/]/\\&/g')"
    if grep -q "^${key_esc}=" "$buildprop"; then
      if [ -z "$val" ]; then
        log "Deleting $key"
        sed -i "/^${key_esc}=/d" "$buildprop"
      else
        log "Setting $key=$val"
        sed -i "s|^${key_esc}=.*|${key}=${val}|" "$buildprop"
      fi
    elif [ -n "$val" ]; then
      log "Adding $key=$val"
      printf '%s=%s\n' "$key" "$val" >> "$buildprop"
    fi
  done < "$propfile"
}

find_build_prop() {
  local root="$1"
  if [ -f "$root/build.prop" ]; then
    echo "$root/build.prop"
  elif [ -f "$root/vendor/build.prop" ]; then
    echo "$root/vendor/build.prop"
  else
    find "$root" -name build.prop -print -quit
  fi
}

write_over() {
  local src="$1" dst="$2"
  if $MOUNTED; then
    sudo tee "$dst" < "$src" >/dev/null
  else
    cat "$src" > "$dst"
  fi
}

find_fstab_qcom() {
  local root="$1"
  find "$root" \( -path '*/etc/fstab.qcom' -o -name 'fstab.qcom' \) -type f 2>/dev/null | sort
}

apply_fstab() {
  local src="$1"
  [ -f "$src" ] || die "fstab not found: $src"
  local dests=()
  mapfile -t dests < <(find_fstab_qcom "$VENDOR_ROOT")
  if [ ${#dests[@]} -eq 0 ]; then
    if [ -d "$VENDOR_ROOT/etc" ]; then
      dests=("$VENDOR_ROOT/etc/fstab.qcom")
      warn "No existing fstab.qcom — installing $VENDOR_ROOT/etc/fstab.qcom"
      if $MOUNTED; then
        sudo cp -a "$src" "${dests[0]}"
      else
        cp -a "$src" "${dests[0]}"
      fi
    else
      die "No vendor/etc/fstab.qcom inside the image"
    fi
  fi
  mkdir -p "$OUTPUT_DIR"
  cp -a "${dests[0]}" "$OUTPUT_DIR/fstab.qcom.before"
  printf '%s\n' "${dests[@]}" > "$WORK/fstab_targets.txt"
  local d
  for d in "${dests[@]}"; do
    log "Replacing ${d#"$VENDOR_ROOT"/} with $(basename "$src")"
    write_over "$src" "$d"
  done
  cp -a "$src" "$OUTPUT_DIR/fstab.qcom.after"
  ok "fstab.qcom replaced (${#dests[@]} file(s))"
}

data_line() {
  grep -E '/data[[:space:]]|userdata' "$1" 2>/dev/null | grep -v '^#' | head -3 | sed 's/^/    /' || echo "    (no /data line)"
}

extract_vendor_dat() {
  local src="$1"
  local dest="$WORK/in"
  mkdir -p "$dest"

  if [ -f "$src" ] && [[ "$src" == *.zip ]]; then
    log "Extracting vendor.* from $(basename "$src")"
    unzip -o -j "$src" \
      "vendor.new.dat.br" "vendor.new.dat" "vendor.transfer.list" "vendor.patch.dat" \
      -d "$dest" >/dev/null 2>&1 || true
    SOURCE_ZIP="$src"
  elif [ -d "$src" ]; then
    log "Using vendor files in $src"
    for f in vendor.new.dat.br vendor.new.dat vendor.transfer.list vendor.patch.dat vendor.img; do
      [ -f "$src/$f" ] && cp -a "$src/$f" "$dest/"
    done
  elif [ -f "$src" ]; then
    log "Using $(basename "$src")"
    cp -a "$src" "$dest/$(basename "$src")"
    local dir
    dir="$(cd "$(dirname "$src")" && pwd)"
    for f in vendor.transfer.list vendor.patch.dat vendor.new.dat vendor.new.dat.br; do
      [ -f "$dir/$f" ] && cp -a "$dir/$f" "$dest/"
    done
  else
    die "Unsupported input: $src"
  fi

  if [ -f "$dest/vendor.img" ] && [ ! -f "$dest/vendor.transfer.list" ]; then
    cp -a "$dest/vendor.img" "$WORK/vendor.img"
    return 0
  fi

  [ -f "$dest/vendor.transfer.list" ] || die "vendor.transfer.list not found (need it next to the .dat)"

  if [ -f "$dest/vendor.new.dat.br" ] && [ ! -f "$dest/vendor.new.dat" ]; then
    log "Decompressing vendor.new.dat.br"
    brotli -d -o "$dest/vendor.new.dat" "$dest/vendor.new.dat.br"
  fi
  [ -f "$dest/vendor.new.dat" ] || die "vendor.new.dat / vendor.new.dat.br not found"

  log "Converting DAT → vendor.img"
  python3 "$SDAT2IMG" "$dest/vendor.transfer.list" "$dest/vendor.new.dat" "$WORK/vendor.img"
}

unpack_vendor_tree() {
  local img="$WORK/vendor.img"
  local fs
  fs="$(image_fs "$img")"
  echo "$fs" > "$WORK/fs_type"
  log "Filesystem: $fs  size=$(stat -c%s "$img") bytes"
  ORIG_SIZE="$(stat -c%s "$img")"

  case "$fs" in
    ext4)
      mkdir -p "$WORK/mnt"
      if sudo -n true 2>/dev/null || sudo -v; then
        sudo umount "$WORK/mnt" 2>/dev/null || true
        sudo mount -o loop,rw "$img" "$WORK/mnt" || die "loop mount failed"
        MOUNTED=true
        BUILD_PROP="$WORK/mnt/build.prop"
        [ -f "$BUILD_PROP" ] || BUILD_PROP="$(find_build_prop "$WORK/mnt")"
      else
        die "sudo required to mount ext4 vendor.img"
      fi
      ;;
    erofs)
      mkdir -p "$WORK/tree"
      if command -v extract.erofs >/dev/null; then
        log "extract.erofs (no full ROM rebuild)"
        if ! extract.erofs -x -i "$img" -o "$WORK/tree" 2>/dev/null \
          && ! extract.erofs -i "$img" -o "$WORK/tree" 2>/dev/null \
          && ! extract.erofs "$img" "$WORK/tree" 2>/dev/null; then
          warn "extract.erofs failed — trying fuse.erofs"
          unpack_erofs_fuse "$img"
        fi
        if [ -z "$(find_build_prop "$WORK/tree")" ]; then
          warn "extract.erofs produced no build.prop — trying fuse.erofs"
          unpack_erofs_fuse "$img"
        fi
      else
        unpack_erofs_fuse "$img"
      fi
      chmod -R u+w "$WORK/tree" 2>/dev/null || true
      BUILD_PROP="$(find_build_prop "$WORK/tree")"
      MOUNTED=false
      ;;
    *)
      die "Unsupported vendor filesystem: $fs (need ext4 or erofs)"
      ;;
  esac

  [ -n "${BUILD_PROP:-}" ] && [ -f "$BUILD_PROP" ] || die "build.prop not found inside vendor image"
  cp -a "$BUILD_PROP" "$WORK/build.prop.orig"
  ok "build.prop → $BUILD_PROP"
  if [ -d "$WORK/mnt" ] && [ -f "$WORK/mnt/build.prop" ]; then
    VENDOR_ROOT="$WORK/mnt"
  elif [ -d "$WORK/tree" ]; then
    if [ -f "$WORK/tree/build.prop" ]; then
      VENDOR_ROOT="$WORK/tree"
    else
      VENDOR_ROOT="$(dirname "$BUILD_PROP")"
    fi
  else
    VENDOR_ROOT="$(dirname "$BUILD_PROP")"
  fi
  if [ -d "$WORK/tree" ]; then
    save_android_pack_config
  fi
}

unpack_erofs_fuse() {
  local img="$1"
  command -v fuse.erofs >/dev/null || die "fuse.erofs / extract.erofs not in PATH (run menu Step 0)"
  mkdir -p "$WORK/mnt"
  if ! sudo -n true 2>/dev/null; then
    sudo -v || die "sudo required for fuse.erofs"
  fi
  sudo umount "$WORK/mnt" 2>/dev/null || true
  sudo env "PATH=$PATH" fuse.erofs "$img" "$WORK/mnt" || die "fuse.erofs failed"
  sudo cp -a -T "$WORK/mnt" "$WORK/tree"
  sudo chown -hR "$(whoami):$(whoami)" "$WORK/tree"
  sudo umount "$WORK/mnt"
}

repack_vendor() {
  local fs
  fs="$(cat "$WORK/fs_type")"
  local img="$WORK/vendor.img"

  case "$fs" in
    ext4)
      $MOUNTED && sudo umount "$WORK/mnt"
      MOUNTED=false
      sync
      ;;
    erofs)
      command -v mkfs.erofs >/dev/null || die "mkfs.erofs not in PATH (run menu Step 0)"
      local src="$WORK/tree"
      [ -f "$src/build.prop" ] || src="$(dirname "$BUILD_PROP")"
      log "mkfs.erofs (keep original image size so the zip op_list still matches)"
      rm -f "$WORK/vendor.new.img"
      local mkfs_args=(
        -z "lz4hc,9" -b 4096 --mount-point "/vendor" -T "1640995200"
      )
      if [ -n "${ANDROID_FC:-}" ] && [ -f "$ANDROID_FC" ]; then
        log "file_contexts: $ANDROID_FC"
        mkfs_args+=(--file-contexts "$ANDROID_FC")
      else
        die "No Android file_contexts from extract.erofs. Use the original working ZIP (not a zip packed with SELinux disabled)."
      fi
      if [ -n "${ANDROID_FSC:-}" ] && [ -f "$ANDROID_FSC" ]; then
        log "fs_config: $ANDROID_FSC"
        mkfs_args+=(--fs-config-file "$ANDROID_FSC")
      fi
      mkfs.erofs "${mkfs_args[@]}" "$WORK/vendor.new.img" "$src" || die "mkfs.erofs failed"
      if image_has_host_selinux "$WORK/vendor.new.img"; then
        die "vendor.img contains Fedora SELinux (user_home_t). Do not flash this."
      fi
      ok "vendor.img has Android labels, no Fedora user_home_t"
      local newsz
      newsz="$(stat -c%s "$WORK/vendor.new.img")"
      if [ "$newsz" -gt "$ORIG_SIZE" ]; then
        die "New vendor.img ($newsz) is larger than original ($ORIG_SIZE). Remove some props or files."
      fi
      if [ "$newsz" -lt "$ORIG_SIZE" ]; then
        log "Padding vendor.img $newsz → $ORIG_SIZE"
        truncate -s "$ORIG_SIZE" "$WORK/vendor.new.img"
      fi
      mv -f "$WORK/vendor.new.img" "$img"
      ;;
  esac

  local blocks=$((ORIG_SIZE / BLOCK_SIZE))
  [ $((ORIG_SIZE % BLOCK_SIZE)) -eq 0 ] || die "vendor.img size is not a 4096-byte multiple"

  log "Writing transfer.list + vendor.new.dat"
  {
    echo "4"
    echo "$blocks"
    echo "0"
    echo "0"
    echo "erase 2,0,$blocks"
    echo "new 2,0,$blocks"
  } > "$OUTPUT_DIR/vendor.transfer.list"
  cp -a "$img" "$OUTPUT_DIR/vendor.new.dat"
  : > "$OUTPUT_DIR/vendor.patch.dat"

  log "Compressing vendor.new.dat.br (this is the slow step)"
  rm -f "$OUTPUT_DIR/vendor.new.dat.br"
  brotli --quality=6 --force --output="$OUTPUT_DIR/vendor.new.dat.br" "$OUTPUT_DIR/vendor.new.dat"
  rm -f "$OUTPUT_DIR/vendor.new.dat"
  ok "Wrote $OUTPUT_DIR/vendor.new.dat.br"
  ok "Wrote $OUTPUT_DIR/vendor.transfer.list"
  ok "Wrote $OUTPUT_DIR/vendor.patch.dat  (empty — keep it)"
}

inject_zip() {
  local src="${SOURCE_ZIP:-}"
  [ -n "$src" ] && [ -f "$src" ] || die "--inject needs a .zip input"
  [ -f "$OUTPUT_DIR/vendor.new.dat.br" ] || die "vendor.new.dat.br missing in $OUTPUT_DIR"
  [ -f "$OUTPUT_DIR/vendor.transfer.list" ] || die "vendor.transfer.list missing in $OUTPUT_DIR"
  [ -f "$OUTPUT_DIR/vendor.patch.dat" ] || die "vendor.patch.dat missing in $OUTPUT_DIR"
  local base
  base="$(basename "$src" .zip)"
  local outzip="$OUTPUT_DIR/${base}_vendorpatch.zip"
  if [ -f "$outzip" ]; then
    warn "Removing leftover $(basename "$outzip") (7z cannot update Zip64; that copy was unpatched)"
    rm -f "$outzip"
  fi
  log "Writing $(basename "$outzip") (Zip64 rewrite — 7z u is not implemented on this archive)"
  python3 "$ZIP_REPLACE" "$src" "$outzip" \
    "$OUTPUT_DIR/vendor.new.dat.br" \
    "$OUTPUT_DIR/vendor.transfer.list" \
    "$OUTPUT_DIR/vendor.patch.dat" \
    || die "zip rewrite failed"

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

repack_folder() {
  local dir="${SOURCE_DIR:-}"
  [ -n "$dir" ] && [ -d "$dir" ] || die "--repack needs the extracted ROM folder as input"
  [ -d "$dir/META-INF" ] || die "Not a full ROM folder (missing META-INF): $dir"
  [ -f "$OUTPUT_DIR/vendor.new.dat.br" ] || die "vendor.new.dat.br missing in $OUTPUT_DIR"

  log "Copying patched vendor.* into $(basename "$dir")"
  cp -a "$OUTPUT_DIR/vendor.new.dat.br" "$dir/vendor.new.dat.br"
  cp -a "$OUTPUT_DIR/vendor.transfer.list" "$dir/vendor.transfer.list"
  cp -a "$OUTPUT_DIR/vendor.patch.dat" "$dir/vendor.patch.dat"

  local parent name outzip
  parent="$(cd "$(dirname "$dir")" && pwd)"
  name="$(basename "$dir")"
  outzip="$parent/${name}_vendorpatch.zip"
  rm -f "$outzip"
  command -v 7z >/dev/null || die "7z required to zip the folder"
  log "Zipping folder contents → $(basename "$outzip")  (store, zip-root = folder contents)"
  (cd "$dir" && 7z a -tzip -mx=0 -mmt="$(nproc)" "$outzip" -r '*' -x!'*.zip') || die "7z failed"

  local pem="$SRC_DIR/security/aosp_testkey.x509.pem"
  local pk8="$SRC_DIR/security/aosp_testkey.pk8"
  if command -v signapk >/dev/null && [ -f "$pem" ] && [ -f "$pk8" ]; then
    log "Signing zip (TWRP does not require this)"
    local signed="${outzip%.zip}-sign.zip"
    if signapk -w "$pem" "$pk8" "$outzip" "$signed"; then
      mv -f "$signed" "$outzip"
    else
      warn "signapk failed — unsigned zip kept (TWRP can still flash it)"
      rm -f "$signed"
    fi
  else
    warn "Unsigned zip — TWRP will flash it; stock recovery may not"
  fi
  ok "Flashable zip: $outzip"
}

SOURCE_ZIP=""
ORIG_SIZE=0
BUILD_PROP=""
VENDOR_ROOT=""
ANDROID_FC=""
ANDROID_FSC=""

echo ""
echo -e "  ${BOLD}Patch vendor in existing ZIP${RESET}"
echo -e "  ${DIM}Does not rebuild the ROM. Patches vendor build.prop and/or fstab.qcom.${RESET}"
echo ""

if $DO_INJECT_ONLY; then
  SOURCE_ZIP="$INPUT"
  [[ "$SOURCE_ZIP" == *.zip ]] || die "--inject-only needs the original .zip as input"
  inject_zip
  echo ""
  echo -e "  ${BOLD}Flash the *_vendorpatch.zip this script just wrote.${RESET}"
  echo -e "  ${DIM}Do not zip the folder again by hand.${RESET}"
  echo ""
  exit 0
fi

extract_vendor_dat "$INPUT"
unpack_vendor_tree

for pf in "${PROP_FILES[@]+"${PROP_FILES[@]}"}"; do
  [ -f "$pf" ] || die "Prop file not found: $pf"
  echo -e "  ${BOLD}Applying${RESET} $pf"
  apply_prop_file "$pf" "$BUILD_PROP"
done

if [ -n "$FSTAB_FILE" ]; then
  apply_fstab "$FSTAB_FILE"
fi

if $DO_EDIT; then
  echo ""
  echo -e "  ${BOLD}Edit this file, then press ENTER to continue:${RESET}"
  echo -e "  ${CYAN}$BUILD_PROP${RESET}"
  echo -e "  ${DIM}Original saved as $WORK/build.prop.orig${RESET}"
  ${EDITOR:-nano} "$BUILD_PROP" || true
  echo -e -n "  ${BOLD}Press ENTER when build.prop is saved:${RESET} "
  read -r
fi

if cmp -s "$WORK/build.prop.orig" "$BUILD_PROP"; then
  warn "build.prop is unchanged"
fi

show_prop() {
  local file="$1" key="$2"
  grep "^${key}=" "$file" 2>/dev/null || echo "${key}=(not set)"
}

mkdir -p "$OUTPUT_DIR"
cp -a "$WORK/build.prop.orig" "$OUTPUT_DIR/build.prop.before"
cp -a "$BUILD_PROP" "$OUTPUT_DIR/build.prop.after"

print_verify() {
  local file="$1"
  echo ""
  echo -e "  ${BOLD}Verify before converting${RESET}  ${DIM}(nothing has been recompressed yet)${RESET}"
  echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
  echo -e "  ${BOLD}Open these in the editor:${RESET}"
  echo -e "    original: ${CYAN}$OUTPUT_DIR/build.prop.before${RESET}"
  echo -e "    edited:   ${CYAN}$OUTPUT_DIR/build.prop.after${RESET}"
  if [ -f "$OUTPUT_DIR/fstab.qcom.before" ]; then
    echo -e "    fstab in:  ${CYAN}$OUTPUT_DIR/fstab.qcom.before${RESET}"
    echo -e "    fstab out: ${CYAN}$OUTPUT_DIR/fstab.qcom.after${RESET}"
  fi
  echo ""
  echo -e "  ${BOLD}ro.product.vendor.device${RESET}"
  echo -e "    before: ${YELLOW}$(show_prop "$OUTPUT_DIR/build.prop.before" "ro.product.vendor.device")${RESET}"
  echo -e "    after:  ${GREEN}$(show_prop "$file" "ro.product.vendor.device")${RESET}"
  echo ""
  echo -e "  Other device lines after edit:"
  grep -E '^ro\.product\.(vendor\.)?(device|name|model)=' "$file" 2>/dev/null | sed 's/^/    /' || true
  echo ""
  echo -e "  Lines still containing ${BOLD}dm1q${RESET} (not dm1qxxx):"
  if grep -n "dm1q" "$file" | grep -v "dm1qxxx" > "$WORK/dm1q_left" 2>/dev/null; then
    sed 's/^/    /' "$WORK/dm1q_left"
  else
    echo -e "    ${DIM}none${RESET}"
  fi
  if [ -f "$OUTPUT_DIR/fstab.qcom.after" ]; then
    echo ""
    echo -e "  ${BOLD}/data line in fstab.qcom${RESET}"
    echo -e "    before:"
    data_line "$OUTPUT_DIR/fstab.qcom.before"
    echo -e "    after:"
    data_line "$OUTPUT_DIR/fstab.qcom.after"
    echo -e "  ${DIM}encryptable (no fileencryption=) is the decrypt-oriented flag.${RESET}"
  fi
  echo ""
  echo -e "  ${DIM}You can edit build.prop.after / fstab.qcom.after now; they are copied back before convert.${RESET}"
}

print_verify "$OUTPUT_DIR/build.prop.after"

echo ""
echo -e -n "  ${BOLD}Looks correct? Convert vendor.new.dat.br now? [y/N]:${RESET} "
read -r confirm_convert
if [[ ! "$confirm_convert" =~ ^[Yy]$ ]]; then
  echo -e "\n  ${YELLOW}Stopped before convert.${RESET} Edited file kept at:"
  echo -e "  ${CYAN}$OUTPUT_DIR/build.prop.after${RESET}"
  echo -e "  ${DIM}The working ZIP was not changed.${RESET}"
  exit 0
fi

# Manual edits to the persistent previews win over the temp tree copy.
if [ -f "$OUTPUT_DIR/build.prop.after" ]; then
  write_over "$OUTPUT_DIR/build.prop.after" "$BUILD_PROP"
fi
if [ -f "$OUTPUT_DIR/fstab.qcom.after" ] && [ -f "$WORK/fstab_targets.txt" ]; then
  while IFS= read -r d; do
    [ -n "$d" ] || continue
    write_over "$OUTPUT_DIR/fstab.qcom.after" "$d"
  done < "$WORK/fstab_targets.txt"
fi

repack_vendor

if $DO_INJECT; then
  inject_zip
fi
if $DO_REPACK; then
  repack_folder
fi

echo ""
if $DO_INJECT || $DO_REPACK; then
  echo -e "  ${BOLD}Flash the *_vendorpatch.zip this script just wrote.${RESET}"
  echo -e "  ${DIM}Do not zip the folder again by hand.${RESET}"
else
  echo -e "  ${BOLD}Drop these into the working ZIP (replace same names at zip root):${RESET}"
  echo -e "    ${CYAN}vendor.new.dat.br${RESET}"
  echo -e "    ${CYAN}vendor.transfer.list${RESET}"
  echo -e "    ${CYAN}vendor.patch.dat${RESET}"
  echo -e "  ${DIM}Leave system/product/odm alone. Do not change dynamic_partitions_op_list${RESET}"
  echo -e "  ${DIM}(image size was kept).${RESET}"
fi
echo ""
