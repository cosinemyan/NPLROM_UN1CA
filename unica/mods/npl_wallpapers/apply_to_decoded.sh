#!/usr/bin/env bash
# Inject NPL assets + featured JSON into an apktool-decoded wallpaper-res.apk dir.
# NEVER replaces Wallpaper_001.webp or default_thumb.webp (bootloop).
set -euo pipefail

APK_DIR="${1:-}"
MODPATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ASSETS_DIR="${ASSETS_DIR:-$MODPATH/assets}"

if [ -z "$APK_DIR" ] || [ ! -d "$APK_DIR" ]; then
  echo "usage: apply_to_decoded.sh <apktool-decoded-wallpaper-res-dir>" >&2
  exit 2
fi

if ! type LOG >/dev/null 2>&1; then
  LOG() { echo "  $*"; }
  LOGW() { echo "  WARN $*"; }
  LOGE() { echo "  ERR $*" >&2; }
fi

: "${TARGET_COMMON_SUPPORT_DYN_RESOLUTION_CONTROL:=true}"
: "${TARGET_PRODUCT_SHIPPING_API_LEVEL:=33}"

NPL_WALLPAPER_RESIZE() {
  local RES="2400"
  local dyn="${TARGET_COMMON_SUPPORT_DYN_RESOLUTION_CONTROL:-true}"
  if [ "$dyn" = "true" ]; then
    if [ "$TARGET_PRODUCT_SHIPPING_API_LEVEL" -gt 30 ] && [ "$TARGET_PRODUCT_SHIPPING_API_LEVEL" -lt 34 ]; then
      RES="3088"
    else
      RES="3120"
    fi
  fi
  echo "$RES"
}

NPL_SANITIZE_BASENAME() {
  local name="${1%.*}"
  name="$(tr '[:upper:]' '[:lower:]' <<< "$name")"
  name="$(sed -E 's/[^a-z0-9]+/_/g; s/^_+//; s/_+$//; s/_+/_/g' <<< "$name")"
  [ -n "$name" ] || name="wallpaper"
  echo "npl_${name}"
}

NPL_ENSURE_APKTOOL_ENTRY() {
  local YML="$1"
  local ENTRY="$2"
  [ -f "$YML" ] || return 0
  if ! grep -q -F -- "- $ENTRY" "$YML"; then
    if ! grep -q "^doNotCompress:" "$YML"; then
      printf "\n%s\n" "doNotCompress:" >> "$YML"
    fi
    printf "%s\n" "- $ENTRY" >> "$YML"
  fi
}

NPL_READ_MARKER_LINES() {
  local f="$1"
  [ -f "$f" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    [ -z "$line" ] || [[ "$line" == \#* ]] && continue
    printf '%s\n' "$line"
  done < "$f"
}

DRAWABLE_DIR="$(find "$APK_DIR/res" -type d -name 'drawable-nodpi*' -print -quit)"
JSON_FILE="$APK_DIR/res/raw/resources_info.json"
FEATURE_JSON="$APK_DIR/res/raw/resources_info_feature.json"

if [ ! -d "$DRAWABLE_DIR" ] || [ ! -f "$JSON_FILE" ]; then
  LOGE "Decoded wallpaper-res layout not found (drawable or resources_info.json)"
  exit 1
fi

shopt -s nullglob nocaseglob
ASSET_FILES=()
if [ -f "$ASSETS_DIR/order.txt" ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    [ -z "$line" ] || [[ "$line" == \#* ]] && continue
    [ -f "$ASSETS_DIR/$line" ] && ASSET_FILES+=("$ASSETS_DIR/$line")
  done < "$ASSETS_DIR/order.txt"
fi
if [ "${#ASSET_FILES[@]}" -eq 0 ]; then
  ASSET_FILES=("$ASSETS_DIR"/*.jpg "$ASSETS_DIR"/*.jpeg "$ASSETS_DIR"/*.png "$ASSETS_DIR"/*.webp)
fi
shopt -u nocaseglob

FILTERED=()
for f in "${ASSET_FILES[@]}"; do
  base="$(basename "$f")"
  [[ "$base" == README* ]] && continue
  [[ "$base" == SET_NPL_DEFAULT ]] && continue
  [[ "$base" == order.txt ]] && continue
  [[ "$base" == featured.txt ]] && continue
  [[ "$base" == default.txt ]] && continue
  [[ "$base" == .gitkeep ]] && continue
  [[ "$base" == ATTRIBUTION.md ]] && continue
  FILTERED+=("$f")
done
ASSET_FILES=("${FILTERED[@]}")

if [ "${#ASSET_FILES[@]}" -eq 0 ]; then
  LOGW "No images in unica/mods/npl_wallpapers/assets/"
  exit 0
fi

FEATURED_CATALOG=()
while IFS= read -r line || [ -n "$line" ]; do
  [ -z "$line" ] && continue
  case "$line" in
    npl:*)
      asset_base="${line#npl:}"
      FEATURED_CATALOG+=("$(NPL_SANITIZE_BASENAME "$asset_base").png")
      ;;
  esac
done < <(NPL_READ_MARKER_LINES "$ASSETS_DIR/featured.txt")

export NPL_FEATURED_CATALOG="${FEATURED_CATALOG[*]}"
export NPL_SET_DEFAULT=0

RESIZE="$(NPL_WALLPAPER_RESIZE)"
CATALOG_NAMES=()

LOG "- Injecting ${#ASSET_FILES[@]} NPL wallpaper(s) (resize ${RESIZE}px)"
if [ ${#FEATURED_CATALOG[@]} -gt 0 ]; then
  LOG "- Featured row (JSON only): ${FEATURED_CATALOG[*]}"
  LOG "- Wallpaper_001.webp / default_thumb.webp left stock (no bootloop replace)"
fi

for src in "${ASSET_FILES[@]}"; do
  stem="$(NPL_SANITIZE_BASENAME "$(basename "$src")")"
  out_webp="$DRAWABLE_DIR/${stem}.webp"
  catalog_name="${stem}.png"

  LOG "- Adding ${catalog_name} from assets/$(basename "$src")"

  case "${src##*.}" in
    webp|WEBP)
      if command -v cwebp >/dev/null; then
        cwebp -q 90 -resize "$RESIZE" "$RESIZE" "$src" -o "$out_webp" >/dev/null || exit 1
      else
        cp -a "$src" "$out_webp" || exit 1
      fi
      ;;
    jpg|jpeg|png|JPG|JPEG|PNG)
      command -v cwebp >/dev/null || { LOGE "cwebp is required to convert $(basename "$src")"; exit 1; }
      cwebp -q 90 -resize "$RESIZE" "$RESIZE" "$src" -o "$out_webp" >/dev/null || exit 1
      ;;
    *)
      LOGW "Skipping unsupported type: $(basename "$src")"
      continue
      ;;
  esac

  CATALOG_NAMES+=("$catalog_name")
  NPL_ENSURE_APKTOOL_ENTRY "$APK_DIR/apktool.yml" "${DRAWABLE_DIR#"$APK_DIR"/}/${stem}.webp"
done

if [ "${#CATALOG_NAMES[@]}" -eq 0 ]; then
  LOGW "No wallpapers were converted"
  exit 0
fi

python3 "$MODPATH/inject_catalog.py" "$JSON_FILE" "${CATALOG_NAMES[@]}" || exit 1

if [ ${#FEATURED_CATALOG[@]} -gt 0 ]; then
  if [ ! -f "$FEATURE_JSON" ]; then
    LOGW "resources_info_feature.json missing — creating from catalog template"
    python3 - "$JSON_FILE" "$FEATURE_JSON" <<'PY'
import json, sys
from pathlib import Path
src, dst = Path(sys.argv[1]), Path(sys.argv[2])
data = json.loads(src.read_text(encoding="utf-8"))
out = {k: v for k, v in data.items() if k != "phone"}
out["phone"] = []
dst.write_text(json.dumps(out, indent=4) + "\n", encoding="utf-8")
PY
  fi
  python3 "$MODPATH/inject_feature.py" "$JSON_FILE" "$FEATURE_JSON" "${FEATURED_CATALOG[@]}" || exit 1
fi

mkdir -p "${NPL_WP_VERIFY_DIR:-/tmp}"
if [ -n "${NPL_WP_VERIFY_DIR:-}" ]; then
  cp -a "$JSON_FILE" "$NPL_WP_VERIFY_DIR/resources_info.after.json"
  [ -f "$FEATURE_JSON" ] && cp -a "$FEATURE_JSON" "$NPL_WP_VERIFY_DIR/resources_info_feature.after.json"
  printf '%s\n' "${FEATURED_CATALOG[@]}" > "$NPL_WP_VERIFY_DIR/featured_catalog.txt"
fi
