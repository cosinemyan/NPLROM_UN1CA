#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

PRINT_USAGE()
{
    echo "Usage: rezoss_cyber_jars [options]" >&2
    echo " -o, --output <dir> : Output directory, defaults to out/cyber" >&2
    echo " --modpath <dir> : Rezoss mod directory, defaults to unica/mods/rezoss" >&2
    echo " --no-work-dir-setup : Do not create work dir if it is missing" >&2
    echo " --refresh-work-dir : Run create_work_dir even if work dir already exists" >&2
}

if [[ "${1:-}" == "-h" ]] || [[ "${1:-}" == "--help" ]]; then
    PRINT_USAGE
    exit 0
fi

if [ -z "${SRC_DIR:-}" ]; then
    cd "$REPO_DIR"
    # shellcheck disable=SC1091
    source "$REPO_DIR/buildenv.sh" dm3q || exit 1
fi

cd "$SRC_DIR"

# [
source "$SRC_DIR/scripts/utils/build_utils.sh" || exit 1
source "$SRC_DIR/scripts/utils/module_utils.sh" || exit 1
# ]

MODPATH="$SRC_DIR/unica/mods/rezoss"
OUTPUT_DIR="$OUT_DIR/cyber"
SETUP_WORK_DIR=true
REFRESH_WORK_DIR=false

SOURCE_FIRMWARE_PATH="$(cut -d "/" -f 1 -s <<< "$SOURCE_FIRMWARE")_$(cut -d "/" -f 2 -s <<< "$SOURCE_FIRMWARE")"
TARGET_FIRMWARE_PATH="$(cut -d "/" -f 1 -s <<< "$TARGET_FIRMWARE")_$(cut -d "/" -f 2 -s <<< "$TARGET_FIRMWARE")"

PREPARE_SCRIPT()
{
    while [ "$#" != 0 ]; do
        case "$1" in
            "-o" | "--output")
                shift
                [ "$1" ] || {
                    LOGE "Missing value for --output"
                    PRINT_USAGE
                    exit 1
                }
                OUTPUT_DIR="$1"
                ;;
            "--modpath")
                shift
                [ "$1" ] || {
                    LOGE "Missing value for --modpath"
                    PRINT_USAGE
                    exit 1
                }
                MODPATH="$1"
                ;;
            "--no-work-dir-setup")
                SETUP_WORK_DIR=false
                ;;
            "--refresh-work-dir")
                REFRESH_WORK_DIR=true
                ;;
            "-h" | "--help")
                PRINT_USAGE
                exit 0
                ;;
            *)
                LOGE "Unknown option: $1"
                PRINT_USAGE
                exit 1
                ;;
        esac

        shift
    done
}

ENSURE_EXTRACTED_FIRMWARE()
{
    if [ ! -f "$FW_DIR/$SOURCE_FIRMWARE_PATH/.extracted" ] || \
            [ ! -f "$FW_DIR/$TARGET_FIRMWARE_PATH/.extracted" ]; then
        LOGE "Required firmware is not extracted."
        LOGE "Run this first: unica extract_fw"
        return 1
    fi

    return 0
}

ENSURE_WORK_DIR()
{
    local FRAMEWORK_RES="$WORK_DIR/system/system/framework/framework-res.apk"

    if ! $SETUP_WORK_DIR; then
        if [ ! -f "$FRAMEWORK_RES" ]; then
            LOGE "Work dir is missing: ${FRAMEWORK_RES//$SRC_DIR\//}"
            LOGE "Run without --no-work-dir-setup or create the work dir first."
            return 1
        fi

        return 0
    fi

    if $REFRESH_WORK_DIR || [ ! -f "$FRAMEWORK_RES" ]; then
        ENSURE_EXTRACTED_FIRMWARE || return 1

        LOG_STEP_IN true "Creating work dir"
        "$SRC_DIR/scripts/internal/create_work_dir.sh" || return 1
        LOG_STEP_OUT
    fi
}

PATCH_AND_BUILD_JAR()
{
    local JAR_PATH="$1"
    local JAR_NAME
    local TARGET_JAR
    local DECODE_DIR

    JAR_NAME="$(basename "$JAR_PATH")"
    TARGET_JAR="system/framework/$JAR_NAME"
    DECODE_DIR="$APKTOOL_DIR/system/${TARGET_JAR//system\//}"

    ADD_TO_WORK_DIR "$MODPATH" "system" "$TARGET_JAR" 0 0 644 "u:object_r:system_lib_file:s0" || return 1

    mkdir -p "$(dirname "$DECODE_DIR")"
    "$SRC_DIR/scripts/apktool.sh" d --force "system" "$TARGET_JAR" || return 1

    LOG "- Applying signature patch to /system/$TARGET_JAR"
    APPLY_PATCH "system" "$TARGET_JAR" "$SIGNATURE_PATCH" || return 1

    SMALI_PATCH "system" "$TARGET_JAR" \
        "smali_classes2/com/android/server/pm/InstallPackageHelper.smali" "replace" \
        '<init>(Lcom/android/server/pm/PackageManagerService;Lcom/android/server/pm/AppDataHelper;Lcom/android/server/pm/RemovePackageHelper;Lcom/android/server/pm/DeletePackageHelper;Lcom/android/server/pm/BroadcastHelper;)V' \
        'CONFIG_CUSTOM_PLATFORM_SIGNATURE' \
        "$CERT_SIGNATURE" \
        > /dev/null || return 1

    LOG "- Rebuilding $JAR_NAME with UN1CA apktool flow"
    "$SRC_DIR/scripts/apktool.sh" b "system" "$TARGET_JAR" || return 1

    LOG "- Exporting rebuilt $JAR_NAME to ${OUTPUT_DIR//$SRC_DIR\//}"
    cp -a "$WORK_DIR/system/$TARGET_JAR" "$OUTPUT_DIR/$JAR_NAME"
}

PREPARE_SCRIPT "$@"

if [ ! -d "$MODPATH" ]; then
    ABORT "Folder not found: ${MODPATH//$SRC_DIR\//}"
fi

if [ -f "$MODPATH/disable" ]; then
    ABORT "Rezoss mod is disabled: ${MODPATH//$SRC_DIR\//}/disable"
fi

if [ ! -d "$MODPATH/system/framework" ]; then
    ABORT "Folder not found: ${MODPATH//$SRC_DIR\//}/system/framework"
fi

ENSURE_WORK_DIR || exit 1

SIGNATURE_PATCH="$SRC_DIR/unica/patches/signature/services.jar/0001-Allow-custom-platform-signature.patch"
if [ ! -f "$SIGNATURE_PATCH" ]; then
    ABORT "File not found: ${SIGNATURE_PATCH//$SRC_DIR\//}"
fi

CERT_PREFIX="aosp"
if ${ROM_IS_OFFICIAL:-false}; then
    CERT_PREFIX="unica"
fi

if [ ! -f "$SRC_DIR/security/${CERT_PREFIX}_platform.x509.pem" ]; then
    ABORT "File not found: security/${CERT_PREFIX}_platform.x509.pem"
fi

CERT_SIGNATURE="$(sed "/CERTIFICATE/d" "$SRC_DIR/security/${CERT_PREFIX}_platform.x509.pem" | tr -d "\n" | base64 -d | xxd -p -c 0)"

mkdir -p "$OUTPUT_DIR"

ORIGINAL_APKTOOL_DIR="$APKTOOL_DIR"
APKTOOL_DIR="$OUT_DIR/target/$TARGET_CODENAME/rezoss_cyber_apktool"
export APKTOOL_DIR

shopt -s nullglob
JARS=("$MODPATH/system/framework/"*.jar)
shopt -u nullglob

if [ "${#JARS[@]}" -eq 0 ]; then
    ABORT "No *.jar files found in ${MODPATH//$SRC_DIR\//}/system/framework"
fi

LOG_STEP_IN true "Building Rezoss cyber framework jars"
for JAR in "${JARS[@]}"; do
    PATCH_AND_BUILD_JAR "$JAR" || exit 1
done
LOG_STEP_OUT

APKTOOL_DIR="$ORIGINAL_APKTOOL_DIR"
export APKTOOL_DIR

LOG "- Rebuilt jars are in ${OUTPUT_DIR//$SRC_DIR\//}"

exit 0