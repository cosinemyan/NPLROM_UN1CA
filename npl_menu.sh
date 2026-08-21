#!/usr/bin/env bash
# NPL ROM Builder - Interactive Step-by-Step TUI Menu

set -eo pipefail

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export SRC_DIR
export OUT_DIR="$SRC_DIR/out"
export FW_DIR="$OUT_DIR/fw"
export ODIN_DIR="$OUT_DIR/odin"
export TMP_DIR="$OUT_DIR/tmp"
export WORK_DIR="$OUT_DIR/work"
export APKTOOL_DIR="$OUT_DIR/apktool"
# UN1CA layout: built tools live under out/tools (not tools/)
export TOOLS_DIR="$OUT_DIR/tools"
export SECURITY_DIR="$SRC_DIR/security"
export ROM_IS_OFFICIAL=false

# Auto-fix permissions on all scripts silently at startup
find "$SRC_DIR/scripts" -name "*.sh" -exec chmod +x {} \; 2>/dev/null || true
chmod +x "$SRC_DIR/npl_menu.sh" "$SRC_DIR/buildenv.sh" "$SRC_DIR/tools/setup.sh" 2>/dev/null || true

mkdir -p "$TOOLS_DIR/bin"
export PATH="$TOOLS_DIR/bin:$PATH"
export LD_LIBRARY_PATH="${TOOLS_DIR}/lib:${LD_LIBRARY_PATH:-}"
export PYTHONUNBUFFERED=1

# ─── Colors ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
RESET='\033[0m'

# ─── Load version ──────────────────────────────────────────────────────────────
NPL_VERSION="1.0-STABLE"
NPL_MAINTAINER="Cosine"
[ -f "$SRC_DIR/unica/configs/version.sh" ] && source "$SRC_DIR/unica/configs/version.sh" || true

# ─── State Tracking ────────────────────────────────────────────────────────────
SELECTED_TARGET=""
FW_MODE="qssi"   # qssi = UN1CA shared base; native = SOURCE=TARGET (per-device)
STEP_DEPS=false
STEP_ENV=false
STEP_FW_DOWNLOADED=false
STEP_FW_EXTRACTED=false
STEP_ROM_BUILT=false

STATE_FILE=""

save_state() {
  [ -z "$STATE_FILE" ] && return
  if [ -z "${SELECTED_TARGET:-}" ]; then
    STEP_ENV=false
  fi
  cat > "$STATE_FILE" <<EOF
SELECTED_TARGET="$SELECTED_TARGET"
FW_MODE="$FW_MODE"
STEP_DEPS=$STEP_DEPS
STEP_ENV=$STEP_ENV
STEP_FW_DOWNLOADED=$STEP_FW_DOWNLOADED
STEP_FW_EXTRACTED=$STEP_FW_EXTRACTED
STEP_ROM_BUILT=$STEP_ROM_BUILT
EOF
}

# After buildenv/gen_config: apply QSSI base vs native SOURCE=TARGET
apply_fw_mode() {
  local mode="${1:-$FW_MODE}"
  FW_MODE="$mode"
  [ -f "$OUT_DIR/config.sh" ] || return 1

  local target_fw qssi_fw
  target_fw="$(grep '^TARGET_FIRMWARE=' "$OUT_DIR/config.sh" | head -1 | cut -d= -f2- | tr -d '"')"
  qssi_fw="$(grep '^SOURCE_FIRMWARE=' "$SRC_DIR/unica/configs/qssi.sh" 2>/dev/null | head -1 | cut -d= -f2- | tr -d '"')"
  [ -z "$qssi_fw" ] && qssi_fw="SM-S911B/EUX/352404911234563"

  case "$FW_MODE" in
    native)
      # Per-device: SOURCE mirrors TARGET (firmware + flags) — no QSSI base overlay
      while IFS= read -r line; do
        [[ "$line" =~ ^TARGET_([A-Za-z0-9_]+)=(.*)$ ]] || continue
        local key="${BASH_REMATCH[1]}"
        local val="${BASH_REMATCH[2]}"
        if grep -q "^SOURCE_${key}=" "$OUT_DIR/config.sh"; then
          sed -i "s|^SOURCE_${key}=.*|SOURCE_${key}=${val}|" "$OUT_DIR/config.sh"
        fi
      done < "$OUT_DIR/config.sh"
      export SOURCE_FIRMWARE="$target_fw"
      ;;
    qssi|*)
      FW_MODE="qssi"
      sed -i "s|^SOURCE_FIRMWARE=.*|SOURCE_FIRMWARE=\"${qssi_fw}\"|" "$OUT_DIR/config.sh"
      export SOURCE_FIRMWARE="$qssi_fw"
      ;;
  esac

  # Reload exported vars used by download/extract
  set -o allexport
  # shellcheck disable=SC1091
  source "$OUT_DIR/config.sh"
  set +o allexport
  return 0
}

# buildenv.sh unsets SELECTED_TARGET (UN1CA behavior) — keep menu state
init_target() {
  local target="$1"
  if [ -z "$target" ] || [ ! -d "$SRC_DIR/target/$target" ]; then
    return 1
  fi
  source "$SRC_DIR/buildenv.sh" "$target" || return 1
  SELECTED_TARGET="$target"
  apply_fw_mode "$FW_MODE" || return 1
  return 0
}

fw_odin_path() {
  # MODEL/CSC/IMEI → ODIN_DIR/MODEL_CSC
  local fw="$1"
  local m c
  m="$(cut -d '/' -f 1 <<< "$fw")"
  c="$(cut -d '/' -f 2 <<< "$fw")"
  echo "$ODIN_DIR/${m}_${c}"
}

fw_has_odin() {
  local p
  p="$(fw_odin_path "$1")"
  [ -f "$p/.downloaded" ] && compgen -G "$p/AP_*.md5" > /dev/null
}

migrate_legacy_odin() {
  local fw="$1"
  local codename="${2:-}"
  local odin_path model csc legacy
  odin_path="$(fw_odin_path "$fw")"
  model="$(cut -d '/' -f 1 <<< "$fw")"
  csc="$(cut -d '/' -f 2 <<< "$fw")"
  [ -n "$codename" ] || return 0
  legacy="$OUT_DIR/fw/$codename"
  if [ -f "$odin_path/.downloaded" ]; then
    return 0
  fi
  if [ ! -d "$legacy" ] || ! compgen -G "$legacy/AP_*.md5" > /dev/null; then
    return 0
  fi
  echo -e "  ${CYAN}▶ Migrating:${RESET} fw/$codename → odin/${model}_${csc}"
  mkdir -p "$odin_path"
  for f in "$legacy"/BL_*.md5 "$legacy"/AP_*.md5 "$legacy"/CP_*.md5 \
           "$legacy"/CSC_*.md5 "$legacy"/HOME_CSC_*.md5; do
    [ -f "$f" ] || continue
    mv -n "$f" "$odin_path/" 2>/dev/null || true
  done
  local pda cscver
  pda="$(basename "$(ls "$odin_path"/AP_*.md5 2>/dev/null | head -1)" | sed -E 's/^AP_([^_]+)_.*/\1/')"
  cscver="$(basename "$(ls "$odin_path"/CSC_*.md5 2>/dev/null | head -1)" | sed -E 's/^CSC_[^_]+_([^_]+)_.*/\1/')"
  [ -z "$cscver" ] && cscver="$pda"
  if [ -n "$pda" ]; then
    printf '%s' "${pda}/${cscver}/${pda}" > "$odin_path/.downloaded"
  fi
  [ -L "$FW_DIR/${model}_${csc}" ] && rm -f "$FW_DIR/${model}_${csc}"
}

load_state() {
  STATE_FILE="$SRC_DIR/out/.npl_build_state"
  mkdir -p "$SRC_DIR/out"
  [ -f "$STATE_FILE" ] && source "$STATE_FILE" || true
  FW_MODE="${FW_MODE:-qssi}"

  if [ -z "${SELECTED_TARGET:-}" ] && [ -f "$OUT_DIR/config.sh" ]; then
    SELECTED_TARGET="$(grep '^TARGET_CODENAME=' "$OUT_DIR/config.sh" 2>/dev/null | head -1 | cut -d= -f2- | tr -d '"')"
  fi

  if [ -n "${SELECTED_TARGET:-}" ]; then
    if init_target "$SELECTED_TARGET"; then
      STEP_ENV=true
    else
      SELECTED_TARGET=""
      STEP_ENV=false
    fi
    save_state
  elif $STEP_ENV; then
    STEP_ENV=false
    save_state
  fi
}

# ─── Helpers ───────────────────────────────────────────────────────────────────
clear_screen() { clear 2>/dev/null || true; }


print_header() {
  echo -e "${CYAN}${BOLD}"
  echo "  ███╗   ██╗██████╗ ██╗     ██████╗  ██████╗ ███╗   ███╗"
  echo "  ████╗  ██║██╔══██╗██║     ██╔══██╗██╔═══██╗████╗ ████║"
  echo "  ██╔██╗ ██║██████╔╝██║     ██████╔╝██║   ██║██╔████╔██║"
  echo "  ██║╚██╗██║██╔═══╝ ██║     ██╔══██╗██║   ██║██║╚██╔╝██║"
  echo "  ██║ ╚████║██║     ███████╗██║  ██║╚██████╔╝██║ ╚═╝ ██║"
  echo "  ╚═╝  ╚═══╝╚═╝     ╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚═╝     ╚═╝"
  echo -e "${RESET}"
  echo -e "  ${DIM}ROM Builder v${NPL_VERSION} · Maintainer: ${NPL_MAINTAINER}${RESET}"
  echo -e "  ${DIM}─────────────────────────────────────────────────────────${RESET}"
  echo ""
}

step_status() {
  local done=$1
  if $done; then
    echo -e "${GREEN}✔${RESET}"
  else
    echo -e "${DIM}○${RESET}"
  fi
}

print_steps() {
  local target_display="${SELECTED_TARGET:-not selected}"
  local mode_label
  if [[ "$FW_MODE" == "native" ]]; then
    mode_label="native (per-device SOURCE=TARGET)"
  else
    mode_label="qssi (UN1CA shared S23 base)"
  fi
  echo -e "  ${BOLD}Current Target:${RESET} ${CYAN}${target_display}${RESET}"
  echo -e "  ${BOLD}Firmware mode:${RESET} ${CYAN}${mode_label}${RESET}"
  if [ -n "${SOURCE_FIRMWARE:-}" ]; then
    echo -e "  ${DIM}SOURCE=${SOURCE_FIRMWARE}${RESET}"
    echo -e "  ${DIM}TARGET=${TARGET_FIRMWARE:-}${RESET}"
  fi
  echo ""
  echo -e "  $(step_status $STEP_DEPS)    ${BOLD}Step 0${RESET}  Check & install dependencies"
  echo -e "  $(step_status $STEP_ENV)    ${BOLD}Step 1${RESET}  Select target device & init environment"
  local step2_extra=""
  if [ -n "${SOURCE_FIRMWARE:-}" ] && [ -n "$(fw_partial_enc4 "$SOURCE_FIRMWARE" 2>/dev/null)" ]; then
    step2_extra="  ${YELLOW}(partial — resume)${RESET}"
  elif [ -n "${TARGET_FIRMWARE:-}" ] && [ -n "$(fw_partial_enc4 "$TARGET_FIRMWARE" 2>/dev/null)" ]; then
    step2_extra="  ${YELLOW}(partial — resume)${RESET}"
  fi
  echo -e "  $(step_status $STEP_FW_DOWNLOADED)    ${BOLD}Step 2${RESET}  Download stock firmware${step2_extra}"
  echo -e "  $(step_status $STEP_FW_EXTRACTED)    ${BOLD}Step 3${RESET}  Extract firmware"
  echo -e "  $(step_status $STEP_ROM_BUILT)    ${BOLD}Step 4${RESET}  Build ROM ZIP"
  echo ""
  local wp_count
  wp_count="$(npl_wallpaper_count 2>/dev/null || echo 0)"
  if npl_wallpapers_enabled; then
    echo -e "  ${GREEN}APK patch:${RESET} wallpapers ${CYAN}ON${RESET}  ${DIM}($wp_count image(s) in assets/)${RESET}"
  else
    echo -e "  ${YELLOW}APK patch:${RESET} wallpapers ${DIM}OFF (stock pack)${RESET}"
  fi
  echo ""
}

# Extra ENTER / keys typed during a long download would otherwise become the next menu choice.
drain_stdin() {
  local _junk
  while IFS= read -r -t 0.05 -n 1024 _junk; do :; done || true
}

press_enter() {
  echo ""
  echo -e "  ${DIM}Press ENTER to continue...${RESET}"
  read -r || true
  drain_stdin
}

latest_flashable_zip() {
  find "$OUT_DIR" -maxdepth 1 -type f \( -name 'NPL_*.zip' -o -name 'UN1CA_*.zip' \) ! -name '*target_files*' \
    -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2-
}

latest_target_files_zip() {
  find "$OUT_DIR" -maxdepth 1 -type f -name '*-target_files.zip' \
    -printf '%T@ %p\n' 2>/dev/null | sort -rn | head -1 | cut -d' ' -f2-
}

print_build_artifacts() {
  local codename="${SELECTED_TARGET:-}"
  local flash tf wp wp_size flash_size tf_size

  flash="$(latest_flashable_zip)"
  tf="$(latest_target_files_zip)"
  if [ -n "$codename" ]; then
    wp="$OUT_DIR/target/$codename/work_dir/system/system/priv-app/wallpaper-res/wallpaper-res.apk"
  fi

  echo ""
  echo -e "  ${BOLD}Output files${RESET}"
  echo -e "  ${DIM}──────────────────────────────────────────${RESET}"

  if [ -n "$flash" ] && [ -f "$flash" ]; then
    flash_size="$(du -h "$flash" 2>/dev/null | awk '{print $1}')"
    echo -e "  ${GREEN}Share in community:${RESET} ${flash#$SRC_DIR/}"
    echo -e "  ${DIM}${flash_size} — signed flashable ZIP for Odin / recovery${RESET}"
  else
    echo -e "  ${YELLOW}No flashable ZIP found in out/ yet.${RESET}"
  fi

  if [ -n "$tf" ] && [ -f "$tf" ]; then
    tf_size="$(du -h "$tf" 2>/dev/null | awk '{print $1}')"
    echo -e "  ${DIM}Dev / rebuild only:${RESET} ${tf#$SRC_DIR/} (${tf_size})"
  fi

  if [ -n "${wp:-}" ] && [ -f "$wp" ]; then
    wp_size="$(du -h "$wp" 2>/dev/null | awk '{print $1}')"
    echo -e "  ${CYAN}Wallpapers only:${RESET} ${wp#$SRC_DIR/}"
    echo -e "  ${DIM}${wp_size} — replace stock wallpaper-res.apk (root + remount)${RESET}"
  fi
}

run_step() {
  export SRC_DIR OUT_DIR FW_DIR TMP_DIR WORK_DIR APKTOOL_DIR TOOLS_DIR SECURITY_DIR ODIN_DIR
  export TARGET_CODENAME TARGET_MODEL TARGET_DEFAULT_CSC TARGET_OS_SINGLE_SYSTEM_IMAGE
  export SOURCE_FIRMWARE TARGET_FIRMWARE SOURCE_EXTRA_FIRMWARES TARGET_EXTRA_FIRMWARES
  export ROM_IS_OFFICIAL NPL_VERSION NPL_CODENAME NPL_MAINTAINER NPL_BUILD_TYPE FW_MODE
  echo -e "\n  ${CYAN}▶ Running:${RESET} ${BOLD}$*${RESET}\n"
  local rc
  set +e
  "$@"
  rc=$?
  set -e
  return "$rc"
}

fw_partial_enc4() {
  local p
  p="$(fw_odin_path "$1")"
  [ -d "$p" ] || return 0
  find "$p" -maxdepth 1 -type f -name '*.enc4' 2>/dev/null | head -1 || true
}

fw_show_partial() {
  local fw="$1" label="$2" f sz
  f="$(fw_partial_enc4 "$fw")"
  [ -n "$f" ] || return 1
  sz="$(du -h "$f" 2>/dev/null | awk '{print $1}')"
  echo -e "  ${YELLOW}⚠ ${label} partial download${RESET}  ${sz}  ${DIM}${f#$SRC_DIR/}${RESET}"
  return 0
}

# Keep the TUI alive on FUS drops; resume from *.enc4 unless the user discards.
download_until_done() {
  local args=("$@")
  while true; do
    if run_step "$SRC_DIR/scripts/download_fw.sh" "${args[@]}"; then
      return 0
    fi
    local filtered=() a
    for a in "${args[@]}"; do
      [[ "$a" == "-f" || "$a" == "--force" ]] && continue
      filtered+=("$a")
    done
    args=("${filtered[@]}")

    echo ""
    echo -e "  ${YELLOW}Download interrupted (Samsung CDN reset). The menu is still running.${RESET}"
    local f
    f="$(find "$ODIN_DIR" -maxdepth 2 -type f -name '*.enc4' 2>/dev/null | head -1)"
    if [ -n "$f" ]; then
      echo -e "  ${DIM}$(du -h "$f" | awk '{print $1}') already on disk — resume will continue, not restart.${RESET}"
    fi
    echo ""
    echo -e "  ${BOLD}[1]${RESET}  Resume now  ${DIM}(default)${RESET}"
    echo -e "  ${BOLD}[2]${RESET}  Back to menu"
    echo -e "  ${BOLD}[3]${RESET}  Discard partial file and start over"
    echo ""
    echo -e -n "  ${BOLD}Choice [1]:${RESET} "
    local c
    read -r c
    case "$c" in
      2)
        echo -e "\n  ${DIM}Partial download kept. Open Step 2 again and choose Resume.${RESET}"
        return 1
        ;;
      3)
        find "$ODIN_DIR" -maxdepth 2 -type f -name '*.enc4' -delete 2>/dev/null || true
        echo -e "  ${YELLOW}Partial file removed.${RESET}"
        ;;
      *) ;;
    esac
  done
}


# ─── Step Functions ────────────────────────────────────────────────────────────
step_check_deps() {
  clear_screen
  print_header
  echo -e "  ${BOLD}Step 0: Check & Install Dependencies${RESET}"
  echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
  echo ""

  local missing_apt=()
  local all_ok=true

  # Host packages (apktool comes from out/tools — do not use system 2.10)
  for pkg in java xxd curl unzip lz4 python3 rsync jq 7z bc clang cmake ffmpeg cwebp protoc getfattr git pkg-config make; do
    if command -v "$pkg" &>/dev/null; then
      echo -e "  ${GREEN}✔${RESET}  $pkg"
    else
      echo -e "  ${RED}✘${RESET}  $pkg  ${DIM}(missing)${RESET}"
      missing_apt+=("$pkg")
      all_ok=false
    fi
  done

  # cmake/pkg-config names (lz4 CLI ≠ liblz4 headers)
  if command -v pkg-config &>/dev/null; then
    for pc in liblz4 libbrotlicommon zlib; do
      if pkg-config --exists "$pc" 2>/dev/null; then
        echo -e "  ${GREEN}✔${RESET}  $pc (pkg-config)"
      else
        echo -e "  ${RED}✘${RESET}  $pc  ${DIM}(headers missing)${RESET}"
        missing_apt+=("$pc")
        all_ok=false
      fi
    done
  fi

  echo ""
  if [ -x "$TOOLS_DIR/bin/apktool" ]; then
    echo -e "  ${GREEN}✔${RESET}  apktool (out/tools)"
  else
    echo -e "  ${YELLOW}⚠${RESET}  apktool  ${DIM}(will install into out/tools)${RESET}"
    all_ok=false
  fi
  if [ -e "$TOOLS_DIR/bin/mkuserimg_mke2fs" ]; then
    echo -e "  ${GREEN}✔${RESET}  mkuserimg_mke2fs"
  else
    echo -e "  ${YELLOW}⚠${RESET}  mkuserimg_mke2fs  ${DIM}(needed for APEX/ext4)${RESET}"
    all_ok=false
  fi
  if command -v samloader &>/dev/null || [ -x "$TOOLS_DIR/bin/samloader" ]; then
    echo -e "  ${GREEN}✔${RESET}  samloader"
  else
    echo -e "  ${YELLOW}⚠${RESET}  samloader  ${DIM}(not installed)${RESET}"
    all_ok=false
  fi

  echo ""
  echo -e "  ${DIM}This runs ./tools/setup.sh — host packages (dnf/apt/pacman/zypper), submodules, then UN1CA tools.${RESET}"
  echo ""

  if $all_ok; then
    echo -e -n "  ${BOLD}Refresh toolchain anyway? [Y/n]:${RESET} "
  else
    echo -e "  ${YELLOW}Some dependencies are missing.${RESET}"
    echo -e -n "  ${BOLD}Install / refresh now? [Y/n]:${RESET} "
  fi
  read -r confirm
  if [[ "$confirm" =~ ^[Nn]$ ]]; then
    if $all_ok; then
      STEP_DEPS=true
      save_state
    fi
    press_enter
    return
  fi

  echo -e "\n  ${CYAN}▶ ./tools/setup.sh${RESET}"
  set +e
  "$SRC_DIR/tools/setup.sh"
  local setup_rc=$?
  set -e
  if [ "$setup_rc" -ne 0 ]; then
    echo -e "\n  ${YELLOW}setup.sh exited ${setup_rc} — some tools may still be usable. Menu stays open.${RESET}"
    press_enter
    return
  fi

  echo -e "\n  ${GREEN}✔ Dependencies ready.${RESET}"
  STEP_DEPS=true
  save_state
  press_enter
}

step_select_target() {
  clear_screen
  print_header
  echo -e "  ${BOLD}Step 1: Select Target Device & Firmware Mode${RESET}"
  echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
  echo ""

  local targets=()
  while IFS= read -r t; do
    [[ -n "$t" ]] && targets+=("$t")
  done < <(find "$SRC_DIR/target" -mindepth 1 -maxdepth 1 -type d -printf "%f\n" 2>/dev/null | sort)

  local i=1
  for t in "${targets[@]}"; do
    local name=""
    if [ -f "$SRC_DIR/target/$t/config.sh" ]; then
      name="$(source "$SRC_DIR/target/$t/config.sh" 2>/dev/null && echo "${TARGET_NAME:-$t}")"
    fi
    echo -e "  ${BOLD}[$i]${RESET}  $t  ${DIM}${name}${RESET}"
    ((i++)) || true
  done

  echo ""
  echo -e -n "  ${BOLD}Select device [1-${#targets[@]}]:${RESET} "
  read -r choice

  local idx=$((choice - 1))
  if [[ $idx -lt 0 || $idx -ge ${#targets[@]} ]]; then
    echo -e "\n  ${RED}Invalid selection.${RESET}"
    press_enter
    return
  fi

  local chosen="${targets[$idx]}"

  echo ""
  echo -e "  ${BOLD}Firmware mode${RESET} (UN1CA-style SOURCE/TARGET overlay):"
  echo -e "  ${BOLD}[1]${RESET}  QSSI base  ${DIM}— shared system from unica/configs/qssi.sh (SM-S911B); TARGET = this device${RESET}"
  echo -e "      ${DIM}Best for multi-model ROM (S23 / S23+ / Ultra share one system base)${RESET}"
  echo -e "  ${BOLD}[2]${RESET}  Native / separate  ${DIM}— SOURCE = TARGET (this model's own firmware only)${RESET}"
  echo -e "      ${DIM}Max per-device compatibility; no cross-model base overlay${RESET}"
  echo ""
  echo -e -n "  ${BOLD}Mode [1]:${RESET} "
  read -r mode_choice
  case "$mode_choice" in
    2) FW_MODE="native" ;;
    *) FW_MODE="qssi" ;;
  esac

  if init_target "$chosen"; then
    STEP_ENV=true
    STEP_FW_DOWNLOADED=false
    STEP_FW_EXTRACTED=false
    STEP_ROM_BUILT=false
    save_state
    echo -e "\n  ${GREEN}✔ Target:${RESET} ${BOLD}${SELECTED_TARGET}${RESET}"
    echo -e "  ${GREEN}✔ Mode:${RESET}   ${BOLD}${FW_MODE}${RESET}"
    echo -e "  ${DIM}SOURCE_FIRMWARE=${SOURCE_FIRMWARE}${RESET}"
    echo -e "  ${DIM}TARGET_FIRMWARE=${TARGET_FIRMWARE}${RESET}"
  else
    SELECTED_TARGET=""
    STEP_ENV=false
    save_state
    echo -e "\n  ${RED}Failed to init environment for ${chosen}.${RESET}"
  fi
  press_enter
}

step_download_fw() {
  clear_screen
  print_header
  echo -e "  ${BOLD}Step 2: Download Stock Firmware${RESET}"
  echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
  echo ""

  if [ -z "$SELECTED_TARGET" ]; then
    echo -e "  ${RED}⚠  Please complete Step 1 first.${RESET}"
    press_enter
    return
  fi

  if ! init_target "$SELECTED_TARGET"; then
    echo -e "  ${RED}buildenv failed${RESET}"
    press_enter
    return
  fi

  migrate_legacy_odin "$TARGET_FIRMWARE" "$SELECTED_TARGET"
  if [[ "$SOURCE_FIRMWARE" != "$TARGET_FIRMWARE" ]]; then
    migrate_legacy_odin "$SOURCE_FIRMWARE" "dm1q"
  fi

  echo -e "  ${BOLD}Mode:${RESET} ${FW_MODE}"
  echo -e "  ${DIM}SOURCE=${SOURCE_FIRMWARE}${RESET}"
  echo -e "  ${DIM}TARGET=${TARGET_FIRMWARE}${RESET}"
  echo ""

  local src_ok=false tgt_ok=false
  fw_has_odin "$SOURCE_FIRMWARE" && src_ok=true
  fw_has_odin "$TARGET_FIRMWARE" && tgt_ok=true

  if $src_ok; then
    echo -e "  ${GREEN}✔ SOURCE Odin present${RESET}  $(fw_odin_path "$SOURCE_FIRMWARE" | sed "s|$SRC_DIR/||")"
  else
    echo -e "  ${YELLOW}○ SOURCE Odin missing${RESET}  $(cut -d/ -f1-2 <<< "$SOURCE_FIRMWARE" | tr / _)"
    fw_show_partial "$SOURCE_FIRMWARE" "SOURCE" || true
  fi
  if [[ "$SOURCE_FIRMWARE" == "$TARGET_FIRMWARE" ]]; then
    echo -e "  ${DIM}(TARGET same as SOURCE — one package set)${RESET}"
  elif $tgt_ok; then
    echo -e "  ${GREEN}✔ TARGET Odin present${RESET}  $(fw_odin_path "$TARGET_FIRMWARE" | sed "s|$SRC_DIR/||")"
  else
    echo -e "  ${YELLOW}○ TARGET Odin missing${RESET}  $(cut -d/ -f1-2 <<< "$TARGET_FIRMWARE" | tr / _)"
    fw_show_partial "$TARGET_FIRMWARE" "TARGET" || true
  fi
  echo ""

  local has_partial=false
  [ -n "$(fw_partial_enc4 "$SOURCE_FIRMWARE")" ] && has_partial=true
  if [[ "$SOURCE_FIRMWARE" != "$TARGET_FIRMWARE" ]] && [ -n "$(fw_partial_enc4 "$TARGET_FIRMWARE")" ]; then
    has_partial=true
  fi

  if $has_partial; then
    echo -e "  ${BOLD}[1]${RESET}  Resume interrupted download  ${DIM}(default — continue from partial *.enc4)${RESET}"
  else
    echo -e "  ${BOLD}[1]${RESET}  Download what this build needs  ${DIM}(SOURCE + TARGET from config)${RESET}"
  fi
  echo -e "  ${BOLD}[2]${RESET}  Download all S23 models  ${DIM}(dm1q + dm2q + dm3q — max multi-device cache)${RESET}"
  echo -e "  ${BOLD}[3]${RESET}  Force re-download for this build  ${DIM}(deletes completed packages; keeps partial unless you discard)${RESET}"
  if $src_ok && { [[ "$SOURCE_FIRMWARE" == "$TARGET_FIRMWARE" ]] || $tgt_ok; }; then
    echo -e "  ${BOLD}[4]${RESET}  Skip — keep existing packages"
  fi
  echo -e "  ${BOLD}[b]${RESET}  Back to menu"
  echo ""
  local default_choice="1"
  echo -e -n "  ${BOLD}Choice [${default_choice}]:${RESET} "
  read -r dl_choice
  [ -z "$dl_choice" ] && dl_choice="$default_choice"

  local ok=false
  case "$dl_choice" in
    b|B)
      return
      ;;
    4)
      if $src_ok && { [[ "$SOURCE_FIRMWARE" == "$TARGET_FIRMWARE" ]] || $tgt_ok; }; then
        STEP_FW_DOWNLOADED=true
        save_state
        echo -e "\n  ${GREEN}✔ Using existing firmware — continue to Step 3.${RESET}"
        press_enter
        return
      fi
      echo -e "  ${YELLOW}Packages incomplete — downloading what is needed.${RESET}"
      download_until_done && ok=true
      ;;
    2)
      echo -e "\n  ${CYAN}▶ Downloading QSSI base + every target firmware…${RESET}"
      local base_fw
      base_fw="$(grep '^SOURCE_FIRMWARE=' "$SRC_DIR/unica/configs/qssi.sh" | head -1 | cut -d= -f2- | tr -d '"')"
      download_until_done --ignore-source --ignore-target "$base_fw" || true
      local t cfg tf
      for t in dm1q dm2q dm3q; do
        cfg="$SRC_DIR/target/$t/config.sh"
        [ -f "$cfg" ] || continue
        tf="$(bash -c "source '$cfg' >/dev/null 2>&1; echo \"\$TARGET_FIRMWARE\"")"
        [ -n "$tf" ] || continue
        echo -e "\n  ${CYAN}▶ $t → $tf${RESET}"
        download_until_done --ignore-source --ignore-target "$tf" || true
      done
      fw_has_odin "$SOURCE_FIRMWARE" && { [[ "$SOURCE_FIRMWARE" == "$TARGET_FIRMWARE" ]] || fw_has_odin "$TARGET_FIRMWARE"; } && ok=true
      ;;
    3)
      download_until_done -f && ok=true
      ;;
    *)
      download_until_done && ok=true
      ;;
  esac

  if $ok && fw_has_odin "$SOURCE_FIRMWARE" && \
     { [[ "$SOURCE_FIRMWARE" == "$TARGET_FIRMWARE" ]] || fw_has_odin "$TARGET_FIRMWARE"; }; then
    STEP_FW_DOWNLOADED=true
    save_state
    echo -e "\n  ${GREEN}✔ Firmware ready.${RESET}"
  else
    STEP_FW_DOWNLOADED=false
    save_state
    echo -e "\n  ${YELLOW}Firmware not complete yet. Use Step 2 → Resume when you are ready.${RESET}"
  fi
  press_enter
}


step_extract_fw() {
  clear_screen
  print_header
  echo -e "  ${BOLD}Step 3: Extract Firmware${RESET}"
  echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
  echo ""

  if [ -z "$SELECTED_TARGET" ]; then
    echo -e "  ${RED}⚠  Please complete Step 1 first.${RESET}"
    press_enter
    return
  fi

  if ! init_target "$SELECTED_TARGET"; then
    echo -e "  ${RED}buildenv failed${RESET}"
    press_enter
    return
  fi

  if ! fw_has_odin "$SOURCE_FIRMWARE"; then
    echo -e "  ${RED}⚠  SOURCE not downloaded ($(cut -d/ -f1-2 <<< "$SOURCE_FIRMWARE")) — run Step 2.${RESET}"
    press_enter
    return
  fi
  if [[ "$SOURCE_FIRMWARE" != "$TARGET_FIRMWARE" ]] && ! fw_has_odin "$TARGET_FIRMWARE"; then
    echo -e "  ${RED}⚠  TARGET not downloaded ($(cut -d/ -f1-2 <<< "$TARGET_FIRMWARE")) — run Step 2.${RESET}"
    press_enter
    return
  fi

  local src_path tgt_path
  src_path="$FW_DIR/$(cut -d/ -f1 <<< "$SOURCE_FIRMWARE")_$(cut -d/ -f2 <<< "$SOURCE_FIRMWARE")"
  tgt_path="$FW_DIR/$(cut -d/ -f1 <<< "$TARGET_FIRMWARE")_$(cut -d/ -f2 <<< "$TARGET_FIRMWARE")"

  echo -e "  ${DIM}Will extract SOURCE → ${src_path#$SRC_DIR/}${RESET}"
  if [[ "$SOURCE_FIRMWARE" != "$TARGET_FIRMWARE" ]]; then
    echo -e "  ${DIM}Will extract TARGET → ${tgt_path#$SRC_DIR/}${RESET}"
  fi
  echo ""

  if [ -f "$src_path/.extracted" ] && [ ! -L "$src_path" ] && \
     { [[ "$SOURCE_FIRMWARE" == "$TARGET_FIRMWARE" ]] || { [ -f "$tgt_path/.extracted" ] && [ ! -L "$tgt_path" ]; }; }; then
    echo -e "  ${GREEN}✔ Already extracted${RESET}"
    echo -e "  ${BOLD}[1]${RESET}  Keep existing extract"
    echo -e "  ${BOLD}[2]${RESET}  Re-extract  ${DIM}(--force)${RESET}"
    echo -e -n "  ${BOLD}Choice [1]:${RESET} "
    read -r ex_choice
    if [[ "$ex_choice" != "2" ]]; then
      STEP_FW_DOWNLOADED=true
      STEP_FW_EXTRACTED=true
      save_state
      press_enter
      return
    fi
    run_step "$SRC_DIR/scripts/extract_fw.sh" -f || {
      echo -e "\n  ${YELLOW}Extract failed. Menu stays open — fix and retry Step 3.${RESET}"
      press_enter
      return
    }
  else
    [ -L "$src_path" ] && rm -f "$src_path"
    [ -L "$tgt_path" ] && rm -f "$tgt_path"
    run_step "$SRC_DIR/scripts/extract_fw.sh" || {
      echo -e "\n  ${YELLOW}Extract failed. Menu stays open — fix and retry Step 3.${RESET}"
      press_enter
      return
    }
  fi

  STEP_FW_DOWNLOADED=true
  STEP_FW_EXTRACTED=true
  save_state
  press_enter
}

step_build_rom() {
  local build_log="$OUT_DIR/.npl_last_build.log"
  local build_rc=0

  while true; do
    clear_screen
    print_header
    echo -e "  ${BOLD}Step 4: Build ROM ZIP${RESET}"
    echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
    echo ""

    if ! $STEP_FW_EXTRACTED; then
      echo -e "  ${RED}⚠  Please complete Step 3 first.${RESET}"
      press_enter
      return
    fi

    if [ -z "${SELECTED_TARGET:-}" ]; then
      echo -e "  ${RED}⚠  Please complete Step 1 first.${RESET}"
      press_enter
      return
    fi

    # Ensure buildenv vars (WORK_DIR, APKTOOL_DIR, PATH→out/tools) are loaded
    if ! init_target "$SELECTED_TARGET"; then
      echo -e "  ${RED}buildenv failed${RESET}"
      press_enter
      return
    fi

    echo -e "  ${DIM}FW mode=${FW_MODE}  SOURCE=${SOURCE_FIRMWARE}${RESET}"
    echo -e "  ${DIM}TARGET=${TARGET_FIRMWARE}${RESET}"
    echo ""
    echo -e "  ${BOLD}[1]${RESET}  Normal build  ${DIM}(make_rom -z; skips if no skin/target changes)${RESET}"
    echo -e "  ${BOLD}[2]${RESET}  Force rebuild  ${DIM}(-f -z; wipes apktool + .completed)${RESET}"
    echo -e "  ${BOLD}[3]${RESET}  Wipe apktool only, then force rebuild"
    echo -e "  ${BOLD}[0]${RESET}  Back to main menu"
    echo ""
    echo -e -n "  ${BOLD}Choice [1]:${RESET} "
    read -r build_choice

    case "$build_choice" in
      0|q|Q|b|B) return ;;
    esac

    local flags=("-z")
    case "$build_choice" in
      2) flags=("-f" "-z") ;;
      3)
        echo -e "\n  ${CYAN}▶ rm -rf out/target/${SELECTED_TARGET}/apktool …/.completed${RESET}"
        rm -rf "$OUT_DIR/target/$SELECTED_TARGET/apktool"
        rm -f "$OUT_DIR/target/$SELECTED_TARGET/work_dir/.completed"
        flags=("-f" "-z")
        ;;
    esac

    echo -e "\n  ${CYAN}▶ source buildenv.sh ${SELECTED_TARGET} && npl make_rom ${flags[*]}${RESET}"
    echo -e "  ${DIM}Logging to out/.npl_last_build.log${RESET}\n"

    mkdir -p "$OUT_DIR"
    export SRC_DIR OUT_DIR FW_DIR TMP_DIR WORK_DIR APKTOOL_DIR TOOLS_DIR SECURITY_DIR ODIN_DIR
    export TARGET_CODENAME TARGET_MODEL TARGET_DEFAULT_CSC TARGET_OS_SINGLE_SYSTEM_IMAGE
    export SOURCE_FIRMWARE TARGET_FIRMWARE SOURCE_EXTRA_FIRMWARES TARGET_EXTRA_FIRMWARES
    export ROM_IS_OFFICIAL NPL_VERSION NPL_CODENAME NPL_MAINTAINER NPL_BUILD_TYPE FW_MODE

    set +e
    "$SRC_DIR/scripts/make_rom.sh" "${flags[@]}" 2>&1 | tee "$build_log"
    build_rc="${PIPESTATUS[0]}"
    set -e

    echo ""
    if [ "$build_rc" -eq 0 ]; then
      STEP_ROM_BUILT=true
      save_state
      echo -e "  ${GREEN}✔ ROM build finished.${RESET}"
    else
      echo -e "  ${YELLOW}Build failed. Log saved — fix errors above, then retry.${RESET}"
    fi

    print_build_artifacts
    echo -e "  ${DIM}Full log: out/.npl_last_build.log${RESET}"
    echo ""
    echo -e "  ${BOLD}[Enter]${RESET}  Return to main menu"
    echo -e "  ${BOLD}[b]${RESET}     Build again (stay on Step 4)"
    echo ""
    echo -e -n "  ${BOLD}Choice:${RESET} "
    read -r post_choice
    [[ "$post_choice" == "b" || "$post_choice" == "B" ]] && continue
    return
  done
}


NPL_COSINE_MOD="$SRC_DIR/unica/mods/cosine"
NPL_WP_MOD="$NPL_COSINE_MOD/npl_wallpapers"
NPL_WP_ASSETS="$NPL_WP_MOD/assets"
NPL_WP_DISABLE="$NPL_WP_MOD/disable"
NPL_WP_FEATURED="$NPL_WP_ASSETS/featured.txt"

npl_wallpapers_enabled() {
  [ -d "$NPL_WP_MOD" ] && [ ! -f "$NPL_WP_DISABLE" ] && [ ! -f "$NPL_COSINE_MOD/disable" ]
}

npl_wallpaper_count() {
  find "$NPL_WP_ASSETS" -maxdepth 1 -type f \( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.webp' \) 2>/dev/null | wc -l
}

# Basenames in picker order (order.txt first, then leftover images).
npl_wallpaper_list() {
  local f base seen=""
  if [ -f "$NPL_WP_ASSETS/order.txt" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
      line="${line#"${line%%[![:space:]]*}"}"
      line="${line%"${line##*[![:space:]]}"}"
      [ -z "$line" ] || [[ "$line" == \#* ]] && continue
      [ -f "$NPL_WP_ASSETS/$line" ] || continue
      printf '%s\n' "$line"
      seen="$seen|$line|"
    done < "$NPL_WP_ASSETS/order.txt"
  fi
  shopt -s nullglob nocaseglob
  for f in "$NPL_WP_ASSETS"/*.jpg "$NPL_WP_ASSETS"/*.jpeg "$NPL_WP_ASSETS"/*.png "$NPL_WP_ASSETS"/*.webp; do
    [ -f "$f" ] || continue
    base="$(basename "$f")"
    [[ "$seen" == *"|$base|"* ]] && continue
    printf '%s\n' "$base"
  done
  shopt -u nocaseglob
}

npl_featured_count() {
  local n
  n="$(grep -c '^npl:' "$NPL_WP_FEATURED" 2>/dev/null || true)"
  echo "${n:-0}"
}

# Shared ZIP/folder picker for vendor and wallpaper APK patches.
# Sets PATCH_INPUT, PATCH_WANT_INJECT, PATCH_WANT_REPACK. Returns 1 on cancel.
# Pass "apk-only" to skip the rewrite-zip / repack prompt.
ask_patch_source() {
  local mode="${1:-}"
  PATCH_INPUT=""
  PATCH_WANT_INJECT=false
  PATCH_WANT_REPACK=false
  mkdir -p "$OUT_DIR/vendor_in" "$OUT_DIR/vendor_patch" "$OUT_DIR/wallpaper_patch" "$OUT_DIR/apk_inject" "$OUT_DIR/tmp"

  local latest zip_choice input="" def_zip="1"
  latest="$(latest_flashable_zip)"

  echo -e "  ${DIM}vendor.patch.dat / system.patch.dat are normally empty on a full ZIP — keep them.${RESET}"
  echo ""
  echo -e "  ${BOLD}[1]${RESET}  A flashable ZIP  ${DIM}(NPL_*.zip)${RESET}"
  echo -e "  ${BOLD}[2]${RESET}  Folder of DAT files  ${DIM}(vendor.* and/or system.*)${RESET}"
  if [ -n "$latest" ]; then
    echo -e "  ${BOLD}[3]${RESET}  Latest zip in out/  ${DIM}${latest#$SRC_DIR/}${RESET}"
    def_zip="3"
  fi
  if [ -f "$OUT_DIR/vendor_in/vendor.new.dat.br" ] || [ -f "$OUT_DIR/vendor_in/vendor.transfer.list" ]; then
    echo -e "  ${BOLD}[4]${RESET}  Drop folder  ${DIM}out/vendor_in/${RESET}"
  fi
  echo ""
  echo -e -n "  ${BOLD}Choice [$def_zip]:${RESET} "
  read -r zip_choice
  zip_choice="${zip_choice:-$def_zip}"

  case "$zip_choice" in
    3)
      [ -n "$latest" ] || { echo -e "  ${RED}No NPL_*.zip in out/${RESET}"; return 1; }
      input="$latest"
      ;;
    4)
      input="$OUT_DIR/vendor_in"
      ;;
    2)
      echo -e -n "  ${BOLD}Folder path:${RESET} "
      read -r input
      input="${input/#\~/$HOME}"
      ;;
    *)
      echo -e -n "  ${BOLD}ZIP path:${RESET} "
      read -r input
      input="${input/#\~/$HOME}"
      ;;
  esac

  if [ -z "$input" ] || [ ! -e "$input" ]; then
    echo -e "\n  ${RED}Not found: ${input:-empty}${RESET}"
    return 1
  fi

  PATCH_INPUT="$input"
  if [[ "$mode" == "apk-only" ]]; then
    return 0
  fi
  if [[ "$input" == *.zip ]]; then
    echo ""
    echo -e "  ${BOLD}Automatically write a new flashable ZIP?${RESET}  ${DIM}(Zip64 rewrite of a copy)${RESET}"
    echo -e -n "  ${BOLD}[Y/n]:${RESET} "
    read -r inj
    [[ ! "$inj" =~ ^[Nn]$ ]] && PATCH_WANT_INJECT=true
  elif [ -d "$input" ] && [ -d "$input/META-INF" ]; then
    echo ""
    echo -e "  ${BOLD}Automatically zip this folder after patching?${RESET}"
    echo -e -n "  ${BOLD}[Y/n]:${RESET} "
    read -r inj
    [[ ! "$inj" =~ ^[Nn]$ ]] && PATCH_WANT_REPACK=true
  fi
  return 0
}

step_apk_patch() {
  while true; do
    clear_screen
    print_header
    echo -e "  ${BOLD}APK patch${RESET}"
    echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
    echo ""
    echo -e "  Decode / inject into system APKs from Step 3 extract, or swap patched"
    echo -e "  APKs into an existing flashable ZIP."
    echo ""
    echo -e "  ${BOLD}[1]${RESET}  Wallpaper  ${DIM}(wallpaper-res.apk — catalog, featured row)${RESET}"
    echo -e "  ${BOLD}[2]${RESET}  Settings   ${DIM}(SecSettings — UN1CA + Theme Trial toggle)${RESET}"
    echo -e "  ${BOLD}[3]${RESET}  Theme      ${DIM}(ThemeCenter — trial expiry gate)${RESET}"
    echo -e "  ${BOLD}[4]${RESET}  Inject into existing ZIP  ${DIM}(replace patched APKs only)${RESET}"
    echo -e "  ${BOLD}[0]${RESET}  Back"
    echo ""
    echo -e -n "  ${BOLD}Choice:${RESET} "
    read -r apk_choice
    case "$apk_choice" in
      1) step_apk_wallpaper ;;
      2) step_apk_settings ;;
      3) step_apk_theme ;;
      4) step_apk_inject_zip ;;
      0|"") return ;;
    esac
  done
}

step_apk_inject_zip() {
  clear_screen
  print_header
  echo -e "  ${BOLD}Inject patched APKs into an existing ZIP${RESET}"
  echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
  echo ""
  echo -e "  Does ${BOLD}not${RESET} rebuild the ROM. Unpacks ${CYAN}system.new.dat.br${RESET}, replaces"
  echo -e "  selected APKs, writes a new flashable ZIP. Source must be the original"
  echo -e "  working ZIP."
  echo ""
  echo -e "  ${YELLOW}Several GB and 20–60+ min.${RESET}"
  echo ""

  local -a labels=() files=() rels=() mark=()
  local i n tok choice file rel label rest
  for spec in \
    "$OUT_DIR/wallpaper_patch/wallpaper-res.apk|priv-app/wallpaper-res/wallpaper-res.apk|wallpaper-res.apk" \
    "$OUT_DIR/settings_patch/SecSettings.apk|priv-app/SecSettings/SecSettings.apk|SecSettings.apk" \
    "$OUT_DIR/settings_patch/SecSettingsIntelligence.apk|priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk|SecSettingsIntelligence.apk" \
    "$OUT_DIR/theme_patch/ThemeCenter.apk|priv-app/ThemeCenter/ThemeCenter.apk|ThemeCenter.apk"
  do
    file="${spec%%|*}"
    rest="${spec#*|}"
    rel="${rest%%|*}"
    label="${rest#*|}"
    [ -f "$file" ] || continue
    labels+=("$label")
    files+=("$file")
    rels+=("$rel")
    mark+=("1")
  done
  n="${#files[@]}"
  if [ "$n" -eq 0 ]; then
    echo -e "  ${YELLOW}No patched APKs yet.${RESET} Use Wallpaper / Settings / Theme first."
    press_enter
    return
  fi

  while true; do
    clear_screen
    print_header
    echo -e "  ${BOLD}Inject patched APKs into an existing ZIP${RESET}"
    echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
    echo ""
    echo -e "  Toggle numbers (space-separated). Selected APKs replace the copies in the ZIP."
    echo ""
    for i in "${!files[@]}"; do
      if [ "${mark[$i]}" = 1 ]; then
        echo -e "  ${GREEN}[x]${RESET}  ${BOLD}$((i + 1))${RESET}  ${labels[$i]}"
      else
        echo -e "  ${DIM}[ ]${RESET}  ${BOLD}$((i + 1))${RESET}  ${labels[$i]}"
      fi
      echo -e "      ${DIM}${files[$i]#$SRC_DIR/}${RESET}"
    done
    echo ""
    echo -e "  ${BOLD}[a]${RESET} All   ${BOLD}[n]${RESET} None   ${BOLD}[s]${RESET} Continue   ${BOLD}[0]${RESET} Cancel"
    echo ""
    echo -e -n "  ${BOLD}Toggle / command:${RESET} "
    read -r choice
    case "$choice" in
      0|"") return ;;
      a|A)
        for i in "${!files[@]}"; do mark[$i]=1; done
        ;;
      n|N)
        for i in "${!files[@]}"; do mark[$i]=0; done
        ;;
      s|S) break ;;
      *)
        for tok in $choice; do
          [[ "$tok" =~ ^[0-9]+$ ]] || continue
          i=$((tok - 1))
          [ "$i" -ge 0 ] && [ "$i" -lt "$n" ] || continue
          if [ "${mark[$i]}" = 1 ]; then
            mark[$i]=0
          else
            mark[$i]=1
          fi
        done
        ;;
    esac
  done

  local selected=0
  for i in "${!files[@]}"; do
    [ "${mark[$i]}" = 1 ] && selected=$((selected + 1))
  done
  if [ "$selected" -eq 0 ]; then
    echo -e "\n  ${YELLOW}Nothing selected.${RESET}"
    press_enter
    return
  fi

  echo ""
  if ! ask_patch_source; then
    press_enter
    return
  fi

  chmod +x "$SRC_DIR/scripts/patch_zip_apks.sh" \
    "$SRC_DIR/scripts/utils/sdat2img.py" "$SRC_DIR/scripts/utils/zip_replace_root.py" 2>/dev/null || true

  local args=()
  for i in "${!files[@]}"; do
    [ "${mark[$i]}" = 1 ] || continue
    args+=(--apk "${files[$i]}:${rels[$i]}")
  done
  if $PATCH_WANT_INJECT && [[ "$PATCH_INPUT" == *.zip ]]; then
    args+=(--inject)
  fi

  echo ""
  if "$SRC_DIR/scripts/patch_zip_apks.sh" "$PATCH_INPUT" "${args[@]}"; then
    if $PATCH_WANT_INJECT; then
      echo -e "\n  ${GREEN}✔ Flash this zip:${RESET} ${CYAN}out/apk_inject/*_apks.zip${RESET}"
    else
      echo -e "\n  ${GREEN}✔ System files:${RESET} ${CYAN}out/apk_inject/${RESET}"
      echo -e "  Replace ${BOLD}system.new.dat.br${RESET} + transfer.list + patch.dat at the zip root."
    fi
  else
    echo -e "\n  ${RED}APK inject failed.${RESET} Need system.new.dat.br + transfer.list, and erofs tools from Step 0."
  fi
  press_enter
}

step_apk_settings() {
  clear_screen
  print_header
  echo -e "  ${BOLD}APK patch — Settings${RESET}"
  echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
  echo ""
  echo -e "  Overlay: ${CYAN}unica/mods/settings/${RESET}  ${DIM}(UN1CA Settings + Theme Trial toggle)${RESET}"
  echo -e "  Writes ${CYAN}out/settings_patch/SecSettings.apk${RESET}."
  echo ""

  local work_apk work_fw work_intel prop
  work_apk="$(npl_find_work_apk "priv-app/SecSettings/SecSettings.apk" || true)"
  work_fw="$(npl_find_work_framework_apk || true)"
  work_intel="$(npl_find_work_apk "priv-app/SecSettingsIntelligence/SecSettingsIntelligence.apk" || true)"

  if [ -z "$work_apk" ] || [ -z "$work_fw" ]; then
    echo -e "  ${YELLOW}No extracted firmware SecSettings.apk.${RESET} Run Step 3 first."
    press_enter
    return
  fi

  echo -e "  Using extracted firmware:"
  echo -e "    ${DIM}${work_apk#$SRC_DIR/}${RESET}"
  [ -n "$work_intel" ] && echo -e "    ${DIM}${work_intel#$SRC_DIR/}${RESET}"
  echo ""
  echo -e -n "  ${BOLD}Patch now? [Y/n]:${RESET} "
  read -r go
  [[ "$go" =~ ^[Nn]$ ]] && return

  chmod +x "$SRC_DIR/scripts/patch_system_apk.sh" 2>/dev/null || true
  local args=(settings --apk "$work_apk" --framework "$work_fw")
  prop="$(npl_find_work_build_prop || true)"
  [ -n "$prop" ] && args+=(--build-prop "$prop")
  [ -n "$work_intel" ] && args+=(--intelligence "$work_intel")
  echo ""
  if "$SRC_DIR/scripts/patch_system_apk.sh" "${args[@]}"; then
    echo -e "\n  ${GREEN}✔ Patched APK:${RESET} ${CYAN}out/settings_patch/SecSettings.apk${RESET}"
    [ -f "$OUT_DIR/settings_patch/SecSettingsIntelligence.apk" ] && \
      echo -e "  ${GREEN}✔ Intelligence:${RESET} ${CYAN}out/settings_patch/SecSettingsIntelligence.apk${RESET}"
    echo -e "  ${DIM}Theme Trial also needs ThemeCenter from APK patch → Theme.${RESET}"
  else
    echo -e "\n  ${RED}Settings APK patch failed.${RESET} Need apktool (Step 0)."
  fi
  press_enter
}

step_apk_theme() {
  clear_screen
  print_header
  echo -e "  ${BOLD}APK patch — Theme${RESET}"
  echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
  echo ""
  echo -e "  Inject: ${CYAN}unica/mods/cosine/theme_trial/${RESET}"
  echo -e "  Writes ${CYAN}out/theme_patch/ThemeCenter.apk${RESET}."
  echo -e "  Gate is off until Extra settings → ${BOLD}Theme Trial${RESET} is on"
  echo -e "  ${DIM}(needs patched SecSettings from APK patch → Settings, or Step 4).${RESET}"
  echo ""

  local work_apk work_fw prop
  work_apk="$(npl_find_work_apk "priv-app/ThemeCenter/ThemeCenter.apk" || true)"
  work_fw="$(npl_find_work_framework_apk || true)"

  if [ -z "$work_apk" ] || [ -z "$work_fw" ]; then
    echo -e "  ${YELLOW}No extracted firmware ThemeCenter.apk.${RESET} Run Step 3 first."
    press_enter
    return
  fi

  echo -e "  Using extracted firmware:"
  echo -e "    ${DIM}${work_apk#$SRC_DIR/}${RESET}"
  echo ""
  echo -e -n "  ${BOLD}Patch now? [Y/n]:${RESET} "
  read -r go
  [[ "$go" =~ ^[Nn]$ ]] && return

  chmod +x "$SRC_DIR/scripts/patch_system_apk.sh" \
    "$SRC_DIR/unica/mods/cosine/theme_trial/inject_trial_gate.py" 2>/dev/null || true
  local args=(theme --apk "$work_apk" --framework "$work_fw")
  prop="$(npl_find_work_build_prop || true)"
  [ -n "$prop" ] && args+=(--build-prop "$prop")
  echo ""
  if "$SRC_DIR/scripts/patch_system_apk.sh" "${args[@]}"; then
    echo -e "\n  ${GREEN}✔ Patched APK:${RESET} ${CYAN}out/theme_patch/ThemeCenter.apk${RESET}"
  else
    echo -e "\n  ${RED}Theme APK patch failed.${RESET} Need apktool (Step 0)."
  fi
  press_enter
}

step_apk_wallpaper() {
  while true; do
    clear_screen
    print_header
    echo -e "  ${BOLD}APK patch — Wallpaper${RESET}"
    echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
    echo ""
    echo -e "  Images: ${CYAN}unica/mods/cosine/npl_wallpapers/assets/${RESET}"
    echo -e "  Featured row is JSON only — ${BOLD}Wallpaper_001.webp is never replaced${RESET}."
    echo ""

    local count featured_n
    count="$(npl_wallpaper_count)"
    featured_n="$(npl_featured_count)"
    if npl_wallpapers_enabled; then
      echo -e "  Step 4 inject: ${GREEN}ON${RESET}  ${DIM}($count image(s), $featured_n featured)${RESET}"
    else
      echo -e "  Step 4 inject: ${YELLOW}OFF${RESET}  ${DIM}(stock pack; [4] still patches an APK)${RESET}"
    fi
    if [ -f "$NPL_WP_FEATURED" ]; then
      echo -e "  Featured:"
      grep '^npl:' "$NPL_WP_FEATURED" 2>/dev/null | sed 's/^npl:/    /' || echo -e "    ${DIM}(none)${RESET}"
    fi
    echo ""
    local work_apk
    work_apk="$(npl_find_work_wallpaper_apk || true)"
    if [ -n "$work_apk" ]; then
      echo -e "  Patch source: ${GREEN}extracted firmware${RESET}"
      echo -e "    ${DIM}${work_apk#$SRC_DIR/}${RESET}"
    else
      echo -e "  Patch source: ${YELLOW}no extract yet${RESET}  ${DIM}run Step 3, or a ZIP is used${RESET}"
    fi
    echo ""
    echo -e "  ${BOLD}[1]${RESET}  Enable  ${DIM}(bake into ROM on Step 4)${RESET}"
    echo -e "  ${BOLD}[2]${RESET}  Disable ${DIM}(stock wallpaper-res until you turn it on)${RESET}"
    echo -e "  ${BOLD}[3]${RESET}  Select featured images  ${DIM}(multi-select)${RESET}"
    if [ -n "$work_apk" ]; then
      echo -e "  ${BOLD}[4]${RESET}  Patch wallpaper-res  ${DIM}(from extract → out/wallpaper_patch/)${RESET}"
    else
      echo -e "  ${BOLD}[4]${RESET}  Patch wallpaper-res  ${DIM}(ZIP — unpacks system)${RESET}"
    fi
    echo -e "  ${BOLD}[0]${RESET}  Back"
    echo ""
    echo -e -n "  ${BOLD}Choice:${RESET} "
    read -r wp_choice
    case "$wp_choice" in
      1)
        rm -f "$NPL_WP_DISABLE"
        echo -e "\n  ${GREEN}✔ Wallpaper inject ON.${RESET} Force-rebuild Step 4 to bake images + featured JSON."
        press_enter
        ;;
      2)
        mkdir -p "$NPL_WP_MOD"
        printf '%s\n' "# Skip NPL wallpaper injection (stock wallpaper-res.apk)." > "$NPL_WP_DISABLE"
        echo -e "\n  ${YELLOW}✔ Wallpaper inject OFF.${RESET} Force-rebuild Step 4 for a stock wallpaper pack."
        press_enter
        ;;
      3) step_apk_wallpaper_featured ;;
      4|5) step_apk_wallpaper_patch ;;
      0|"") return ;;
    esac
  done
}

step_apk_wallpaper_featured() {
  local files=() i n choice tok
  mapfile -t files < <(npl_wallpaper_list)
  n="${#files[@]}"
  if [ "$n" -eq 0 ]; then
    echo -e "\n  ${YELLOW}No images in assets/.${RESET} Drop JPG/PNG/WebP first."
    press_enter
    return
  fi

  local -a mark=()
  for i in "${!files[@]}"; do
    mark[$i]=0
    if [ -f "$NPL_WP_FEATURED" ] && grep -qx "npl:${files[$i]}" "$NPL_WP_FEATURED" 2>/dev/null; then
      mark[$i]=1
    fi
  done

  while true; do
    clear_screen
    print_header
    echo -e "  ${BOLD}Featured wallpaper row${RESET}"
    echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
    echo ""
    echo -e "  Toggle numbers (space-separated), left → right in the picker."
    echo -e "  ${DIM}Catalog JSON only — does not replace Wallpaper_001.webp.${RESET}"
    echo ""
    for i in "${!files[@]}"; do
      if [ "${mark[$i]}" = 1 ]; then
        echo -e "  ${GREEN}[x]${RESET}  ${BOLD}$((i + 1))${RESET}  ${files[$i]}"
      else
        echo -e "  ${DIM}[ ]${RESET}  ${BOLD}$((i + 1))${RESET}  ${files[$i]}"
      fi
    done
    echo ""
    echo -e "  ${BOLD}[a]${RESET} All   ${BOLD}[n]${RESET} None   ${BOLD}[s]${RESET} Save   ${BOLD}[0]${RESET} Cancel"
    echo ""
    echo -e -n "  ${BOLD}Toggle / command:${RESET} "
    read -r choice
    case "$choice" in
      0|"") return ;;
      a|A)
        for i in "${!files[@]}"; do mark[$i]=1; done
        ;;
      n|N)
        for i in "${!files[@]}"; do mark[$i]=0; done
        ;;
      s|S)
        mkdir -p "$NPL_WP_ASSETS"
        {
          echo "# Featured row in the Samsung wallpaper picker (left to right)."
          echo "# Catalog-only — Wallpaper_001.webp is never replaced (that bootloops)."
          for i in "${!files[@]}"; do
            [ "${mark[$i]}" = 1 ] && printf 'npl:%s\n' "${files[$i]}"
          done
        } > "$NPL_WP_FEATURED"
        echo -e "\n  ${GREEN}✔ Saved ${NPL_WP_FEATURED#$SRC_DIR/}${RESET}"
        echo -e "  ${DIM}Rebuild Step 4, or Wallpaper → [4] Patch wallpaper-res.${RESET}"
        press_enter
        return
        ;;
      *)
        for tok in $choice; do
          [[ "$tok" =~ ^[0-9]+$ ]] || continue
          i=$((tok - 1))
          [ "$i" -ge 0 ] && [ "$i" -lt "$n" ] || continue
          if [ "${mark[$i]}" = 1 ]; then
            mark[$i]=0
          else
            mark[$i]=1
          fi
        done
        ;;
    esac
  done
}

step_apk_wallpaper_patch_zip() {
  clear_screen
  print_header
  echo -e "  ${BOLD}Patch wallpaper-res in an existing ZIP${RESET}"
  echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
  echo ""
  echo -e "  Does ${BOLD}not${RESET} rebuild the ROM. Unpacks ${CYAN}system.new.dat.br${RESET} and injects"
  echo -e "  assets + featured JSON. Source must be the original working ZIP."
  echo ""
  echo -e "  ${YELLOW}Several GB and 20–60+ min.${RESET} Wallpaper_001.webp is not replaced."
  echo ""

  if ! ask_patch_source; then
    press_enter
    return
  fi

  echo ""
  chmod +x "$SRC_DIR/scripts/patch_zip_wallpaper.sh" \
    "$SRC_DIR/scripts/utils/sdat2img.py" "$SRC_DIR/scripts/utils/zip_replace_root.py" \
    "$NPL_WP_MOD/apply_to_decoded.sh" "$NPL_WP_MOD/inject_catalog.py" \
    "$NPL_WP_MOD/inject_feature.py" 2>/dev/null || true

  local wargs=()
  if $PATCH_WANT_INJECT && [[ "$PATCH_INPUT" == *.zip ]]; then
    wargs+=(--inject)
  fi
  if "$SRC_DIR/scripts/patch_zip_wallpaper.sh" "$PATCH_INPUT" "${wargs[@]}"; then
    if $PATCH_WANT_INJECT; then
      echo -e "\n  ${GREEN}✔ Flash this zip:${RESET} ${CYAN}out/wallpaper_patch/*_wallpaper.zip${RESET}"
      echo -e "  ${DIM}Do not zip the folder again by hand.${RESET}"
    else
      echo -e "\n  ${GREEN}✔ System files:${RESET} ${CYAN}out/wallpaper_patch/${RESET}"
      echo -e "  Replace ${BOLD}system.new.dat.br${RESET} + transfer.list + patch.dat at the zip root."
    fi
  else
    echo -e "\n  ${RED}Wallpaper patch failed.${RESET} Need system.new.dat.br + system.transfer.list, apktool, cwebp, and erofs tools from Step 0."
  fi
  press_enter
}

# Prefer Step 3 extracted firmware (out/fw/MODEL_CSC), then Step 4 work_dir.
npl_apk_source_roots() {
  local d spec seen="|"
  _npl_emit_root() {
    [ -d "$1" ] || return 0
    [[ "$seen" == *"|$1|"* ]] && return 0
    seen="${seen}${1}|"
    printf '%s\n' "$1"
  }
  for spec in "${SOURCE_FIRMWARE:-}" "${TARGET_FIRMWARE:-}"; do
    [ -n "$spec" ] || continue
    _npl_emit_root "$FW_DIR/$(cut -d/ -f1 <<< "$spec")_$(cut -d/ -f2 <<< "$spec")"
  done
  if [ -d "$FW_DIR" ]; then
    for d in "$FW_DIR"/*; do
      [ -d "$d/system" ] && _npl_emit_root "$d"
    done
  fi
  [ -n "${WORK_DIR:-}" ] && _npl_emit_root "$WORK_DIR"
  if [ -n "${SELECTED_TARGET:-}" ]; then
    _npl_emit_root "$OUT_DIR/target/$SELECTED_TARGET/work_dir"
  fi
  if [ -d "$OUT_DIR/target" ]; then
    for d in "$OUT_DIR/target"/*/work_dir; do
      _npl_emit_root "$d"
    done
  fi
}

npl_find_work_apk() {
  local rel="$1" root p
  while IFS= read -r root; do
    [ -n "$root" ] || continue
    for p in "$root/system/system/$rel" "$root/system/$rel"; do
      [ -f "$p" ] && echo "$p" && return 0
    done
  done < <(npl_apk_source_roots)
  return 1
}

npl_find_work_wallpaper_apk() {
  npl_find_work_apk "priv-app/wallpaper-res/wallpaper-res.apk"
}

npl_find_work_framework_apk() {
  npl_find_work_apk "framework/framework-res.apk"
}

npl_find_work_build_prop() {
  local root p
  while IFS= read -r root; do
    [ -n "$root" ] || continue
    for p in \
      "$root/system/system/build.prop" \
      "$root/system/build.prop"; do
      [ -f "$p" ] && echo "$p" && return 0
    done
  done < <(npl_apk_source_roots)
  return 1
}

step_apk_wallpaper_patch() {
  clear_screen
  print_header
  echo -e "  ${BOLD}Patch wallpaper-res.apk${RESET}"
  echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
  echo ""
  echo -e "  Writes ${CYAN}out/wallpaper_patch/wallpaper-res.apk${RESET}."
  echo -e "  Uses Step 3 extract when present — no ZIP unpack."
  echo -e "  Wallpaper_001.webp is not replaced."
  echo ""

  chmod +x "$SRC_DIR/scripts/patch_zip_wallpaper.sh" \
    "$SRC_DIR/scripts/utils/sdat2img.py" \
    "$NPL_WP_MOD/apply_to_decoded.sh" "$NPL_WP_MOD/inject_catalog.py" \
    "$NPL_WP_MOD/inject_feature.py" 2>/dev/null || true

  local work_apk work_fw prop
  work_apk="$(npl_find_work_wallpaper_apk || true)"
  work_fw="$(npl_find_work_framework_apk || true)"

  if [ -n "$work_apk" ] && [ -n "$work_fw" ]; then
    echo -e "  Using extracted firmware (framework-res is decode-only, not patched):"
    echo -e "    ${DIM}${work_apk#$SRC_DIR/}${RESET}"
    echo ""
    local apk_args=(--apk-only --apk "$work_apk" --framework "$work_fw")
    prop="$(npl_find_work_build_prop || true)"
    [ -n "$prop" ] && apk_args+=(--build-prop "$prop")
    if "$SRC_DIR/scripts/patch_zip_wallpaper.sh" "${apk_args[@]}"; then
      echo -e "\n  ${GREEN}✔ Patched APK:${RESET} ${CYAN}out/wallpaper_patch/wallpaper-res.apk${RESET}"
    else
      echo -e "\n  ${RED}APK patch failed.${RESET} Need apktool and cwebp."
    fi
    press_enter
    return
  fi

  echo -e "  No extracted firmware. Using a flashable ZIP / system DAT instead."
  echo -e "  ${YELLOW}Unpacks system (several GB); only the APK is kept.${RESET}"
  echo ""
  if ! ask_patch_source apk-only; then
    press_enter
    return
  fi
  echo ""
  if "$SRC_DIR/scripts/patch_zip_wallpaper.sh" "$PATCH_INPUT" --apk-only; then
    echo -e "\n  ${GREEN}✔ Patched APK:${RESET} ${CYAN}out/wallpaper_patch/wallpaper-res.apk${RESET}"
  else
    echo -e "\n  ${RED}APK patch failed.${RESET} Need system.new.dat.br + transfer.list, apktool, cwebp, extract.erofs."
  fi
  press_enter
}

step_patch_vendor() {
  clear_screen
  print_header
  echo -e "  ${BOLD}Patch vendor in an existing ZIP${RESET}"
  echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
  echo ""
  echo -e "  Does ${BOLD}not${RESET} rebuild the ROM. Unpacks vendor, edits ${CYAN}build.prop${RESET} / fstab."
  echo -e "  ${DIM}Wallpaper ZIP patch lives under APK patch → Wallpaper.${RESET}"
  echo -e "  Source must be the original working ZIP ${DIM}(not a Fedora-packed image).${RESET}"
  echo ""

  if ! ask_patch_source; then
    echo -e "  ${DIM}Copy vendor.new.dat.br + vendor.transfer.list into out/vendor_in/ and pick [4].${RESET}"
    press_enter
    return
  fi

  local args=()
  local input="$PATCH_INPUT"

  echo ""
  echo -e "  ${BOLD}What to write into vendor/build.prop${RESET}"
  local def_prop=""
  if [ -n "${SELECTED_TARGET:-}" ] && [ -f "$SRC_DIR/target/$SELECTED_TARGET/patches/displayconfig/vendor.prop" ]; then
    def_prop="$SRC_DIR/target/$SELECTED_TARGET/patches/displayconfig/vendor.prop"
    echo -e "  ${BOLD}[1]${RESET}  Apply $SELECTED_TARGET displayconfig  ${DIM}${def_prop#$SRC_DIR/}${RESET}"
  else
    echo -e "  ${BOLD}[1]${RESET}  Apply a vendor.prop file  ${DIM}(you will be asked for the path)${RESET}"
  fi
  echo -e "  ${BOLD}[2]${RESET}  Edit build.prop in \$EDITOR  ${DIM}(${EDITOR:-nano})${RESET}"
  echo -e "  ${BOLD}[3]${RESET}  Apply props, then edit"
  echo ""
  echo -e -n "  ${BOLD}Choice [2]:${RESET} "
  read -r mode
  mode="${mode:-2}"

  case "$mode" in
    1)
      if [ -z "$def_prop" ]; then
        echo -e -n "  ${BOLD}vendor.prop path:${RESET} "
        read -r def_prop
        def_prop="${def_prop/#\~/$HOME}"
      fi
      [ -f "$def_prop" ] || { echo -e "  ${RED}No such file${RESET}"; press_enter; return; }
      args+=(--prop "$def_prop")
      ;;
    3)
      if [ -z "$def_prop" ]; then
        echo -e -n "  ${BOLD}vendor.prop path:${RESET} "
        read -r def_prop
        def_prop="${def_prop/#\~/$HOME}"
      fi
      [ -f "$def_prop" ] || { echo -e "  ${RED}No such file${RESET}"; press_enter; return; }
      args+=(--prop "$def_prop" --edit)
      ;;
    *)
      args+=(--edit)
      ;;
  esac

  local default_fstab=""
  if [ -n "${SELECTED_TARGET:-}" ] && [ -f "$SRC_DIR/target/$SELECTED_TARGET/patches/dfe/vendor/etc/fstab.qcom" ]; then
    default_fstab="$SRC_DIR/target/$SELECTED_TARGET/patches/dfe/vendor/etc/fstab.qcom"
  elif [ -f "$SRC_DIR/target/dm1q/patches/dfe/vendor/etc/fstab.qcom" ]; then
    default_fstab="$SRC_DIR/target/dm1q/patches/dfe/vendor/etc/fstab.qcom"
  fi
  if [ -n "$default_fstab" ]; then
    echo ""
    echo -e "  Replace ${BOLD}vendor/etc/fstab.qcom${RESET} with ${CYAN}${default_fstab#$SRC_DIR/}${RESET}?"
    echo -e "  ${DIM}Same DFE module as a full ROM build (/data encryptable for recovery decrypt).${RESET}"
    echo -e -n "  ${BOLD}Replace fstab? [Y/n]:${RESET} "
    read -r fstab_choice
    if [[ ! "$fstab_choice" =~ ^[Nn]$ ]]; then
      args+=(--fstab "$default_fstab")
    fi
  fi

  echo ""
  chmod +x "$SRC_DIR/scripts/patch_zip_vendor.sh" \
    "$SRC_DIR/scripts/utils/sdat2img.py" "$SRC_DIR/scripts/utils/zip_replace_root.py" 2>/dev/null || true

  if $PATCH_WANT_INJECT; then
    args+=(--inject)
  elif $PATCH_WANT_REPACK; then
    args+=(--repack)
  fi
  if "$SRC_DIR/scripts/patch_zip_vendor.sh" "$input" "${args[@]}"; then
    if $PATCH_WANT_INJECT; then
      echo -e "\n  ${GREEN}✔ Flash this zip:${RESET} ${CYAN}out/vendor_patch/*_vendorpatch.zip${RESET}"
    elif $PATCH_WANT_REPACK; then
      echo -e "\n  ${GREEN}✔ Flashable zip written next to the folder.${RESET}"
    else
      echo -e "\n  ${GREEN}✔ Vendor files:${RESET} ${CYAN}out/vendor_patch/${RESET}"
    fi
  else
    echo -e "\n  ${RED}Vendor patch failed.${RESET} Need vendor.new.dat.br + vendor.transfer.list (and brotli / erofs tools from Step 0)."
  fi
  press_enter
}

step_reset() {
  echo -e "\n  ${YELLOW}Reset build state? This won't delete downloaded firmware. [y/N]:${RESET} "
  read -r confirm
  if [[ "$confirm" =~ ^[Yy]$ ]]; then
    STEP_ENV=false
    STEP_FW_DOWNLOADED=false
    STEP_FW_EXTRACTED=false
    STEP_ROM_BUILT=false
    SELECTED_TARGET=""
    FW_MODE="qssi"
    save_state
    echo -e "  ${GREEN}✔ State reset.${RESET}"
    press_enter
  fi
}

# ─── Main Menu ─────────────────────────────────────────────────────────────────
main_menu() {
  load_state
  while true; do
    drain_stdin
    clear_screen
    print_header
    print_steps

    echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
    echo -e "  ${BOLD}[0]${RESET}  Check & install dependencies"
    echo -e "  ${BOLD}[1]${RESET}  Select target device & init environment"
    echo -e "  ${BOLD}[2]${RESET}  Download stock firmware"
    echo -e "  ${BOLD}[3]${RESET}  Extract firmware"
    echo -e "  ${BOLD}[4]${RESET}  Build ROM ZIP"
    echo -e "  ${DIM}──────────────────────────────────────────${RESET}"
    echo -e "  ${BOLD}[5]${RESET}  Run all steps  ${DIM}(0→1→2→3→4)${RESET}"
    echo -e "  ${BOLD}[a]${RESET}  APK patch  ${DIM}(wallpaper / settings / theme)${RESET}"
    echo -e "  ${BOLD}[p]${RESET}  Patch vendor in existing ZIP  ${DIM}(build.prop / fstab — no ROM rebuild)${RESET}"
    echo -e "  ${BOLD}[r]${RESET}  Reset build state"
    echo -e "  ${BOLD}[q]${RESET}  Quit"
    echo ""
    echo -e -n "  ${BOLD}Choose:${RESET} "
    read -r choice || exit 0
    choice="${choice//$'\r'/}"
    choice="${choice#"${choice%%[![:space:]]*}"}"
    choice="${choice%"${choice##*[![:space:]]}"}"
    [ -z "$choice" ] && continue

    case "$choice" in
      0) step_check_deps ;;
      1) step_select_target ;;
      2) step_download_fw ;;
      3) step_extract_fw ;;
      4) step_build_rom ;;
      5)
        step_check_deps
        step_select_target
        step_download_fw
        step_extract_fw
        step_build_rom
        ;;
      a|A) step_apk_patch ;;
      w|W) step_apk_wallpaper ;;
      p|P) step_patch_vendor ;;
      r|R) step_reset ;;
      q|Q)
        echo -e "\n  ${DIM}Goodbye.${RESET}\n"
        exit 0
        ;;
      *)
        echo -e "\n  ${RED}Invalid choice.${RESET}"
        sleep 1
        ;;
    esac
  done
}

main_menu
