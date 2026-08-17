#!/usr/bin/env bash
# Inject custom images from assets/ into the target device's wallpaper-res.apk.
# Keeps stock S23 wallpapers; adds npl_* entries to the Samsung picker catalog.

WALLPAPER_APK="system/priv-app/wallpaper-res/wallpaper-res.apk"
ASSETS_DIR="$MODPATH/assets"

# [
NPL_WALLPAPER_RESIZE()
{
    local RES="2400"

    if $TARGET_COMMON_SUPPORT_DYN_RESOLUTION_CONTROL; then
        if [ "$TARGET_PRODUCT_SHIPPING_API_LEVEL" -gt "30" ] && \
                [ "$TARGET_PRODUCT_SHIPPING_API_LEVEL" -lt "34" ]; then
            RES="3088"
        else
            RES="3120"
        fi
    fi

    echo "$RES"
}

NPL_SANITIZE_BASENAME()
{
    local name="${1%.*}"
    name="$(tr '[:upper:]' '[:lower:]' <<< "$name")"
    name="$(sed -E 's/[^a-z0-9]+/_/g; s/^_+//; s/_+$//; s/_+/_/g' <<< "$name")"
    [ -n "$name" ] || name="wallpaper"
    echo "npl_${name}"
}

NPL_ENSURE_APKTOOL_ENTRY()
{
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
# ]

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

# Ignore README / marker files
FILTERED=()
for f in "${ASSET_FILES[@]}"; do
    base="$(basename "$f")"
    [[ "$base" == README* ]] && continue
    [[ "$base" == SET_NPL_DEFAULT ]] && continue
    [[ "$base" == order.txt ]] && continue
    [[ "$base" == .gitkeep ]] && continue
    FILTERED+=("$f")
done
ASSET_FILES=("${FILTERED[@]}")

if [ "${#ASSET_FILES[@]}" -eq 0 ]; then
    LOGW "No images in unica/mods/npl_wallpapers/assets/ — add JPG/PNG/WebP and rebuild"
    return 0
fi

if [ ! -f "$WORK_DIR/system/system/priv-app/wallpaper-res/wallpaper-res.apk" ]; then
    LOGE "Target wallpaper-res.apk missing from work dir"
    return 1
fi

LOG "- Injecting ${#ASSET_FILES[@]} NPL wallpaper(s) into /system/system/priv-app/wallpaper-res.apk"

DECODE_APK "system" "$WALLPAPER_APK" || return 1

APK_DIR="$APKTOOL_DIR/system/priv-app/wallpaper-res/wallpaper-res.apk"
DRAWABLE_DIR="$(find "$APK_DIR/res" -type d -name 'drawable-nodpi*' -print -quit)"
JSON_FILE="$APK_DIR/res/raw/resources_info.json"

if [ ! -d "$DRAWABLE_DIR" ] || [ ! -f "$JSON_FILE" ]; then
    LOGE "Decoded wallpaper-res layout not found (drawable or resources_info.json)"
    return 1
fi

RESIZE="$(NPL_WALLPAPER_RESIZE)"
CATALOG_NAMES=()

for src in "${ASSET_FILES[@]}"; do
    stem="$(NPL_SANITIZE_BASENAME "$(basename "$src")")"
    out_webp="$DRAWABLE_DIR/${stem}.webp"
    catalog_name="${stem}.png"

    LOG "- Adding ${catalog_name} from assets/$(basename "$src")"

    case "${src##*.}" in
        webp|WEBP)
            if command -v cwebp >/dev/null; then
                EVAL "cwebp -q 100 -resize \"$RESIZE\" \"$RESIZE\" \"$src\" -o \"$out_webp\"" || return 1
            else
                EVAL "cp -a \"$src\" \"$out_webp\"" || return 1
            fi
            ;;
        jpg|jpeg|png|JPG|JPEG|PNG)
            if ! command -v cwebp >/dev/null; then
                LOGE "cwebp is required to convert $(basename "$src")"
                return 1
            fi
            EVAL "cwebp -q 100 -resize \"$RESIZE\" \"$RESIZE\" \"$src\" -o \"$out_webp\"" || return 1
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
    return 0
fi

if [ -f "$ASSETS_DIR/SET_NPL_DEFAULT" ]; then
    export NPL_SET_DEFAULT=1
    LOG "- SET_NPL_DEFAULT: first NPL wallpaper will be the default home wallpaper"
else
    export NPL_SET_DEFAULT=0
fi

python3 "$MODPATH/inject_catalog.py" "$JSON_FILE" "${CATALOG_NAMES[@]}" || return 1

unset RESIZE CATALOG_NAMES FILTERED ASSET_FILES APK_DIR DRAWABLE_DIR JSON_FILE
unset -f NPL_WALLPAPER_RESIZE NPL_SANITIZE_BASENAME NPL_ENSURE_APKTOOL_ENTRY
