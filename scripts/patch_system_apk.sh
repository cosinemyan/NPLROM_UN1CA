#!/usr/bin/env bash
# Patch SecSettings.apk or ThemeCenter.apk from extracted firmware (apk-only).
# Does not unpack a flashable ZIP. Do not sideload / MT-install / KSU-overlay the result.
#
# Usage:
#   scripts/patch_system_apk.sh settings --apk SecSettings.apk --framework framework-res.apk
#   scripts/patch_system_apk.sh theme    --apk ThemeCenter.apk --framework framework-res.apk
#   --build-prop FILE         optional apktool tag
#   --intelligence FILE       also patch SecSettingsIntelligence.apk (settings)
#   --output DIR              default: out/settings_patch or out/theme_patch

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

MODE=""
APK_IN=""
FW_IN=""
PROP_IN=""
INTEL_IN=""
OUTPUT_DIR=""
SETTINGS_MOD="$SRC_DIR/unica/mods/settings"
THEME_MOD="$SRC_DIR/unica/mods/cosine/theme_trial"

usage() {
  echo "Usage: $(basename "$0") settings|theme --apk FILE --framework FILE [options]" >&2
  echo "  --build-prop FILE     optional build.prop for apktool tag" >&2
  echo "  --intelligence FILE   also patch SecSettingsIntelligence.apk (settings)" >&2
  echo "  --output DIR          default: out/settings_patch or out/theme_patch" >&2
  exit 1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    settings|theme)
      [ -z "$MODE" ] || usage
      MODE="$1"
      ;;
    --apk)
      shift
      [ -n "${1:-}" ] || usage
      APK_IN="$1"
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
    --intelligence)
      shift
      [ -n "${1:-}" ] || usage
      INTEL_IN="$1"
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
      echo -e "${RED}Unexpected argument: $1${RESET}" >&2
      usage
      ;;
  esac
  shift
done

[ -n "$MODE" ] && [ -n "$APK_IN" ] && [ -n "$FW_IN" ] || usage
[ -f "$APK_IN" ] || { echo -e "${RED}Not found: $APK_IN${RESET}" >&2; exit 1; }
[ -f "$FW_IN" ] || { echo -e "${RED}Not found: $FW_IN${RESET}" >&2; exit 1; }
[ -z "$PROP_IN" ] || [ -f "$PROP_IN" ] || { echo -e "${RED}Not found: $PROP_IN${RESET}" >&2; exit 1; }
[ -z "$INTEL_IN" ] || [ -f "$INTEL_IN" ] || { echo -e "${RED}Not found: $INTEL_IN${RESET}" >&2; exit 1; }

if [ -z "$OUTPUT_DIR" ]; then
  if [ "$MODE" = "settings" ]; then
    OUTPUT_DIR="$OUT_DIR/settings_patch"
  else
    OUTPUT_DIR="$OUT_DIR/theme_patch"
  fi
fi

command -v python3 >/dev/null || { echo -e "${RED}python3 required${RESET}" >&2; exit 1; }
command -v apktool >/dev/null || { echo -e "${RED}apktool required (menu Step 0)${RESET}" >&2; exit 1; }

mkdir -p "$OUT_DIR/tmp" "$OUTPUT_DIR"
WORK=""
cleanup() {
  if [ -n "${WORK:-}" ]; then
    rm -rf "$WORK"
  fi
}
trap cleanup EXIT
WORK="$(mktemp -d "$OUT_DIR/tmp/${MODE}_apk.XXXXXX")"
export _JAVA_OPTIONS="${_JAVA_OPTIONS:--Xmx4g}"
export WORK_DIR="$WORK/work"
mkdir -p "$WORK_DIR/system/system/priv-app/ChoiDujour"

log() { echo -e "  ${CYAN}▶${RESET} $*"; }
ok() { echo -e "  ${GREEN}✔${RESET} $*"; }
warn() { echo -e "  ${YELLOW}⚠${RESET} $*"; }
die() { echo -e "  ${RED}✘ $*${RESET}" >&2; exit 1; }

apktool_tag() {
  local tag="npl"
  if [ -n "$PROP_IN" ]; then
    tag="$(grep '^ro.build.version.incremental=' "$PROP_IN" | head -1 | cut -d= -f2- | tr -d '\r')"
    [ -n "$tag" ] || tag="npl"
  fi
  echo "$tag"
}

decode_apk() {
  local apk="$1"
  local decoded="$2"
  local fwdir="$3"
  local tag="$4"
  local threads
  threads="$(nproc 2>/dev/null || echo 4)"
  rm -rf "$decoded"
  mkdir -p "$(dirname "$decoded")"
  apktool d --no-debug-info -j "$threads" -o "$decoded" -p "$fwdir" -t "$tag" "$apk" \
    || apktool d --no-debug-info -j "$threads" -o "$decoded" -p "$fwdir" "$apk" \
    || die "apktool decode failed: $apk"
}

build_and_sign() {
  local decoded="$1"
  local out_apk="$2"
  local fwdir="$3"
  local name
  name="$(basename "$out_apk")"
  local threads
  threads="$(nproc 2>/dev/null || echo 4)"

  log "apktool b $name"
  mkdir -p "$decoded/build/apk"
  if [ -d "$decoded/original/META-INF" ]; then
    cp -a "$decoded/original/META-INF" "$decoded/build/apk/META-INF"
  fi
  find "$decoded" -type f \( -name "*.orig" -o -name "*.rej" \) -delete
  apktool b -j "$threads" -p "$fwdir" "$decoded" || die "apktool build failed: $name"

  local built="$decoded/dist/$name"
  if [ ! -f "$built" ]; then
    built="$(find "$decoded/dist" -maxdepth 1 -type f -name '*.apk' | head -1 || true)"
  fi
  [ -n "$built" ] && [ -f "$built" ] || die "apktool did not produce dist/*.apk"

  local pem="$SRC_DIR/security/aosp_platform.x509.pem"
  local pk8="$SRC_DIR/security/aosp_platform.pk8"
  if command -v signapk >/dev/null && [ -f "$pem" ] && [ -f "$pk8" ]; then
    log "Signing $name with platform key"
    signapk "$pem" "$pk8" "$built" "$WORK/${name}.signed" || die "signapk failed: $name"
    built="$WORK/${name}.signed"
  else
    warn "platform signapk keys missing — writing unsigned APK (cannot be installed over Samsung)"
  fi
  cp -a "$built" "$out_apk"
  ok "Patched $(du -h "$out_apk" | awk '{print $1}') → $out_apk"
}

echo ""
if [ "$MODE" = "settings" ]; then
  echo -e "  ${BOLD}Patch SecSettings.apk only${RESET}"
  echo -e "  ${DIM}UN1CA Settings overlay + Theme Trial extra-settings toggle.${RESET}"
else
  echo -e "  ${BOLD}Patch ThemeCenter.apk only${RESET}"
  echo -e "  ${DIM}Property-gated trial expiry (Extra settings → Theme Trial).${RESET}"
fi
echo ""

TAG="$(apktool_tag)"
FWDIR="$WORK/apktool/framework"
mkdir -p "$FWDIR"
export APKTOOL_DIR="$WORK/apktool"

log "apktool if framework-res.apk"
apktool if -p "$FWDIR" -t "$TAG" "$FW_IN" >/dev/null \
  || apktool if -p "$FWDIR" "$FW_IN" >/dev/null \
  || die "apktool if framework-res.apk failed"

# shellcheck source=/dev/null
source "$SRC_DIR/scripts/utils/module_utils.sh"
DECODE_APK() { return 0; }

if [ "$MODE" = "theme" ]; then
  APK_NAME="ThemeCenter.apk"
  DECODED="$APKTOOL_DIR/system/priv-app/ThemeCenter/ThemeCenter.apk"
  log "apktool d ThemeCenter.apk"
  decode_apk "$APK_IN" "$DECODED" "$FWDIR" "$TAG"
  python3 "$THEME_MOD/inject_trial_gate.py" "$DECODED" || die "Theme trial inject failed"
  build_and_sign "$DECODED" "$OUTPUT_DIR/ThemeCenter.apk" "$FWDIR"
else
  export MODPATH="$SETTINGS_MOD"
  APK_NAME="SecSettings.apk"
  DECODED="$APKTOOL_DIR/system/priv-app/SecSettings/SecSettings.apk"
  log "apktool d SecSettings.apk"
  decode_apk "$APK_IN" "$DECODED" "$FWDIR" "$TAG"
  # shellcheck source=/dev/null
  . "$SETTINGS_MOD/apply_to_decoded.sh" || die "SecSettings overlay failed"

  TRANSP_PATCH="$SETTINGS_MOD/smali/system/priv-app/SecSettings/SecSettings.apk/0001-Add-Settings-background-transparency-control.patch"
  if [ -f "$TRANSP_PATCH" ]; then
    log "Applying Settings background-transparency patch"
    LC_ALL=C patch -p1 -d "$DECODED" -N --forward -l < "$TRANSP_PATCH" \
      || warn "transparency patch did not apply (already present or smali moved)"
  fi

  build_and_sign "$DECODED" "$OUTPUT_DIR/SecSettings.apk" "$FWDIR"

  if [ -n "$INTEL_IN" ]; then
    INTEL_DECODED="$APKTOOL_DIR/system/priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk"
    log "apktool d SecSettingsIntelligence.apk"
    decode_apk "$INTEL_IN" "$INTEL_DECODED" "$FWDIR" "$TAG"
    # shellcheck source=/dev/null
    . "$SETTINGS_MOD/apply_intelligence_decoded.sh" || die "SecSettingsIntelligence patch failed"
    build_and_sign "$INTEL_DECODED" "$OUTPUT_DIR/SecSettingsIntelligence.apk" "$FWDIR"
  fi
fi

echo ""
echo -e "  ${BOLD}Patched APK:${RESET} ${CYAN}$OUTPUT_DIR/${APK_NAME}${RESET}"
if [ "$MODE" = "settings" ] && [ -f "$OUTPUT_DIR/SecSettingsIntelligence.apk" ]; then
  echo -e "  ${BOLD}Intelligence:${RESET} ${CYAN}$OUTPUT_DIR/SecSettingsIntelligence.apk${RESET}"
fi
echo ""
exit 0
