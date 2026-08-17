#!/usr/bin/env bash
# Copyright (c) 2025 Salvo Giangreco
# SPDX-License-Identifier: GPL-3.0-or-later

set -e

# [
source "$SRC_DIR/scripts/utils/build_utils.sh" || exit 1

FRAMEWORK_DIR="$TOOLS_DIR/apktool/framework"
FRAMEWORK_TAG="$(GET_PROP "system" "ro.build.version.incremental")"


FORCE=false
PARTITION=""
FILE=""

INPUT_FILE=""
OUTPUT_PATH=""

# UN1CA patches apktool with -srp (shorten resource paths). Stock 3.x does not.
APKTOOL_SUPPORTS_SRP=false
if apktool b --help 2>&1 | grep -q 'shorten-res-paths'; then
    APKTOOL_SUPPORTS_SRP=true
fi

THREAD_COUNT=$(awk -v max="$(nproc)" '/MemTotal/ {
  tc = int(($2 + 1048575) / 2097152);
  print (tc < 1 ? 1 : (tc > max ? max : tc));
}' /proc/meminfo)

[ -n "$GITHUB_ACTIONS" ] && THREAD_COUNT=1

PREPARE_SMARTSUGGESTIONS_PACKAGING()
{
    local YML="$OUTPUT_PATH/apktool.yml"
    local RES_DIR="$OUTPUT_PATH/res"
    local ENTRY
    local ADDED=0

    [ -f "$YML" ] || return 0
    [ -d "$RES_DIR" ] || return 0

    if ! grep -q "^doNotCompress:" "$YML"; then
        printf "\n%s\n" "doNotCompress:" >> "$YML"
    fi

    while IFS= read -r ENTRY; do
        if ! grep -q -F -- "- $ENTRY" "$YML"; then
            printf "%s\n" "- $ENTRY" >> "$YML"
            ADDED=$((ADDED + 1))
        fi
    done < <(find "$RES_DIR" -type f -path "*/raw*/alias_index.7z" -printf "res/%P\n" | sort)

    if (( ADDED > 0 )); then
        LOG "- Keeping $ADDED SmartSuggestions alias_index.7z resources uncompressed"
    fi
}

BUILD()
{
    if [ ! -d "$OUTPUT_PATH" ]; then
        LOGE "Folder not found: ${OUTPUT_PATH//$SRC_DIR\//}"
        exit 1
    fi

    local SHORTEN_RESOURCE_PATHS=true

    case "$PARTITION:$FILE" in
        "system:system/priv-app/SamsungSmartSuggestions/SamsungSmartSuggestions.apk")
            if (( THREAD_COUNT > 4 )); then
                LOG "- Limiting apktool threads for ${INPUT_FILE//$WORK_DIR/} to 4 to avoid JVM heap exhaustion"
                THREAD_COUNT=4
            fi
            SHORTEN_RESOURCE_PATHS=false
            PREPARE_SMARTSUGGESTIONS_PACKAGING
            ;;
        "system:system/priv-app/wallpaper-res/wallpaper-res.apk")
            # Samsung wallpaper-res looks up drawables by original filename
            # (Wallpaper_001.webp, resources_info.json). -srp renames them to
            # res/Xx.webp and SystemUI fails to set the boot wallpaper.
            SHORTEN_RESOURCE_PATHS=false
            LOG "- Building wallpaper-res.apk without resource path shortening"
            ;;
    esac

    LOG "- Building ${INPUT_FILE//$WORK_DIR/}"

    # Copy original META-INF
    mkdir -p "$OUTPUT_PATH/build/apk"
    cp -a "$OUTPUT_PATH/original/META-INF" "$OUTPUT_PATH/build/apk/META-INF"

    # Most APKs are rebuilt with shortened resource paths. SmartSuggestions is
    # kept expanded because its known working CHN build uses expanded res paths.
    find "$OUTPUT_PATH" -type f \( -name "*.orig" -o -name "*.rej" \) -delete
    REBALANCE_DEX
    if $SHORTEN_RESOURCE_PATHS && $APKTOOL_SUPPORTS_SRP; then
        EVAL "apktool b -j \"$THREAD_COUNT\" -p \"$FRAMEWORK_DIR\" -srp \"$OUTPUT_PATH\"" || exit 1
    else
        if $SHORTEN_RESOURCE_PATHS && ! $APKTOOL_SUPPORTS_SRP; then
            LOGW "apktool has no -srp (need UN1CA patched apktool); building without resource path shortening"
        fi
        EVAL "apktool b -j \"$THREAD_COUNT\" -p \"$FRAMEWORK_DIR\" \"$OUTPUT_PATH\"" || exit 1
    fi

    local FILE_NAME
    FILE_NAME="$(basename "$INPUT_FILE")"

    if [[ "$INPUT_FILE" == *".apk" ]]; then
        local CERT_PREFIX="aosp"
        $ROM_IS_OFFICIAL && CERT_PREFIX="unica"

        LOG "- Signing ${INPUT_FILE//$WORK_DIR/}"
        EVAL "signapk \"$SRC_DIR/security/${CERT_PREFIX}_platform.x509.pem\" \"$SRC_DIR/security/${CERT_PREFIX}_platform.pk8\" \"$OUTPUT_PATH/dist/$FILE_NAME\" \"$OUTPUT_PATH/dist/temp.apk\"" || exit 1
        mv -f "$OUTPUT_PATH/dist/temp.apk" "$OUTPUT_PATH/dist/$FILE_NAME"
    else
        LOG "- Zipaligning ${INPUT_FILE//$WORK_DIR/}"
        EVAL "zipalign -p 4 \"$OUTPUT_PATH/dist/$FILE_NAME\" \"$OUTPUT_PATH/dist/temp\"" || exit 1
        mv -f "$OUTPUT_PATH/dist/temp" "$OUTPUT_PATH/dist/$FILE_NAME"
    fi

    mkdir -p "$(dirname "$INPUT_FILE")"
    mv -f "$OUTPUT_PATH/dist/$FILE_NAME" "$INPUT_FILE"
    rm -rf "$OUTPUT_PATH/build" && rm -rf "$OUTPUT_PATH/dist"

    if [ -d "${INPUT_FILE%/*}/oat" ]; then
        DELETE_FROM_WORK_DIR "$PARTITION" "${FILE%/*}/oat"
    fi
    if [ -f "${INPUT_FILE%/*}/$FILE_NAME.prof" ]; then
        DELETE_FROM_WORK_DIR "$PARTITION" "${FILE%/*}/$FILE_NAME.prof"
    fi
    if [ -f "${INPUT_FILE%/*}/$FILE_NAME.bprof" ]; then
        DELETE_FROM_WORK_DIR "$PARTITION" "${FILE%/*}/$FILE_NAME.bprof"
    fi
}

REBALANCE_DEX()
{
    case "$PARTITION:$FILE" in
        "system:system/framework/framework.jar")
            local FROM="$OUTPUT_PATH/smali/android/drm"
            local TO="$OUTPUT_PATH/smali_classes8/android/drm"
            if [ -d "$FROM" ] && [ ! -d "$TO" ]; then
                LOG "- Moving android/drm to classes8.dex to keep framework.jar below the dex method limit"
                mkdir -p "$(dirname "$TO")"
                mv -f "$FROM" "$TO"
            fi
            ;;
        "system_ext:priv-app/SystemUI/SystemUI.apk")
            local FROM_DIR="$OUTPUT_PATH/smali_classes3/com/android/systemui/settings/brightness"
            local TO_DIR="$OUTPUT_PATH/smali_classes6/com/android/systemui/settings/brightness"
            if [ -d "$FROM_DIR" ]; then
                LOG "- Moving brightness settings package to classes6.dex to keep SystemUI classes3 below the dex method limit"
                mkdir -p "$TO_DIR"
                find "$FROM_DIR" -mindepth 1 -maxdepth 1 -exec mv -f -t "$TO_DIR" {} +
                rmdir "$FROM_DIR" 2>/dev/null || true
            fi
            ;;
    esac
}

DECODE()
{
    if [ ! -f "$INPUT_FILE" ]; then
        LOGE "File not found: ${INPUT_FILE//$WORK_DIR/}"
        exit 1
    elif [ -d "$OUTPUT_PATH" ]; then
        if $FORCE; then
            rm -rf "$OUTPUT_PATH"
        else
            LOGE "Output directory already exists (${OUTPUT_PATH//$SRC_DIR\//}). Use --force flag if you want to overwrite it."
            exit 1
        fi
    fi

    if [[ "$(READ_BYTES_AT "$INPUT_FILE" "0" "4")" != "04034b50" ]]; then
        LOGE "File not valid: ${INPUT_FILE//$WORK_DIR/}"
        exit 1
    fi

    LOG "- Decoding ${INPUT_FILE//$WORK_DIR/}"

    # Decode APK with --no-debug-info, which will disassemble DEX file with the following flags:
    # - Disabled synthetic accessors comments
    # - Disabled debug info
    # - Use .locals directive instead of the .registers one
    # - Use a sequential numbering scheme for labels
    EVAL "apktool d --no-debug-info -j \"$THREAD_COUNT\" -o \"$OUTPUT_PATH\" -p \"$FRAMEWORK_DIR\" -t \"$FRAMEWORK_TAG\" \"$INPUT_FILE\"" || exit 1
}

PREPARE_SCRIPT()
{
    if [[ "$#" == 0 ]]; then
        PRINT_USAGE
        exit 1
    fi

    ACTION="$1"
    if [[ "$ACTION" != "decode" ]] && [[ "$ACTION" != "d" ]] && \
            [[ "$ACTION" != "build" ]] && [[ "$ACTION" != "b" ]]; then
        PRINT_USAGE
        exit 1
    fi

    shift

    if [[ "$1" == "--force" ]] || [[ "$1" == "-f" ]]; then
        FORCE=true
        shift
    fi

    PARTITION="$1"
    if [ ! "$PARTITION" ]; then
        PRINT_USAGE
        exit 1
    elif ! IS_VALID_PARTITION_NAME "$PARTITION"; then
        LOGE "\"$PARTITION\" is not a valid partition name"
        exit 1
    fi

    shift

    if [ ! "$1" ]; then
        PRINT_USAGE
        exit 1
    fi

    FILE="$1"
    while [[ "${FILE:0:1}" == "/" ]]; do
        FILE="${FILE:1}"
    done

    local FILE_PATH="$WORK_DIR"
    case "$PARTITION" in
        "system_ext")
            if $TARGET_OS_BUILD_SYSTEM_EXT_PARTITION; then
                FILE_PATH+="/system_ext"
            else
                FILE_PATH+="/system/system/system_ext"
            fi
            ;;
        *)
            FILE_PATH+="/$PARTITION"
            ;;
    esac
    FILE_PATH+="/$FILE"

    INPUT_FILE="$FILE_PATH"
    OUTPUT_PATH="$APKTOOL_DIR/$PARTITION/${FILE//system\//}"
}

PRINT_USAGE()
{
    echo "Usage: apktool d[ecode]/b[uild] [options] <partition> <file>" >&2
    echo " -f, --force : Force delete output directory" >&2
}
# ]

ACTION=""

PREPARE_SCRIPT "$@"

if [ ! "$FRAMEWORK_TAG" ]; then
    LOGE "Work dir needs to be set up before using this script"
    exit 1
elif [ ! -f "$FRAMEWORK_DIR/1-$FRAMEWORK_TAG.apk" ]; then
    LOGW "framework-res.apk for \"$FRAMEWORK_TAG\" not found, installing"
    EVAL "apktool if -p \"$FRAMEWORK_DIR\" -t \"$FRAMEWORK_TAG\" \"$WORK_DIR/system/system/framework/framework-res.apk\"" || exit 1
fi

case "$ACTION" in
    "d" | "decode")
        DECODE
        ;;
    "b" | "build")
        BUILD
        ;;
esac

exit 0
