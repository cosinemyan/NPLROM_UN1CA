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
  echo -e "  $(step_status $STEP_FW_DOWNLOADED)    ${BOLD}Step 2${RESET}  Download stock firmware"
  echo -e "  $(step_status $STEP_FW_EXTRACTED)    ${BOLD}Step 3${RESET}  Extract firmware"
  echo -e "  $(step_status $STEP_ROM_BUILT)    ${BOLD}Step 4${RESET}  Build ROM ZIP"
  echo ""
}

press_enter() {
  echo ""
  echo -e "  ${DIM}Press ENTER to continue...${RESET}"
  read -r
}

run_step() {
  export SRC_DIR OUT_DIR FW_DIR TMP_DIR WORK_DIR APKTOOL_DIR TOOLS_DIR SECURITY_DIR ODIN_DIR
  export TARGET_CODENAME TARGET_MODEL TARGET_DEFAULT_CSC TARGET_OS_SINGLE_SYSTEM_IMAGE
  export SOURCE_FIRMWARE TARGET_FIRMWARE SOURCE_EXTRA_FIRMWARES TARGET_EXTRA_FIRMWARES
  export ROM_IS_OFFICIAL NPL_VERSION NPL_CODENAME NPL_MAINTAINER NPL_BUILD_TYPE FW_MODE
  echo -e "\n  ${CYAN}▶ Running:${RESET} ${BOLD}$*${RESET}\n"
  "$@"
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
  for pkg in java xxd curl unzip lz4 python3 rsync jq 7z bc clang cmake ffmpeg cwebp protoc getfattr git; do
    if command -v "$pkg" &>/dev/null; then
      echo -e "  ${GREEN}✔${RESET}  $pkg"
    else
      echo -e "  ${RED}✘${RESET}  $pkg  ${DIM}(missing)${RESET}"
      missing_apt+=("$pkg")
      all_ok=false
    fi
  done

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
  echo -e "  ${DIM}This step runs: git submodule update --init --recursive && ./tools/setup.sh${RESET}"
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

  echo ""

  if [ ${#missing_apt[@]} -gt 0 ]; then
    local PM=""
    if command -v dnf &>/dev/null; then
      PM="dnf"
    elif command -v apt-get &>/dev/null; then
      PM="apt-get"
    elif command -v apt &>/dev/null; then
      PM="apt"
    elif command -v yum &>/dev/null; then
      PM="yum"
    elif command -v pacman &>/dev/null; then
      PM="pacman"
    elif command -v zypper &>/dev/null; then
      PM="zypper"
    fi

    local sys_pkgs=()
    for p in "${missing_apt[@]}"; do
      case "$PM" in
        dnf|yum)
          case "$p" in
            java) sys_pkgs+=("java-latest-openjdk") ;;
            xxd) sys_pkgs+=("vim-common") ;;
            7z) sys_pkgs+=("p7zip") ;;
            cwebp) sys_pkgs+=("libwebp-tools") ;;
            protoc) sys_pkgs+=("protobuf-compiler") ;;
            getfattr) sys_pkgs+=("attr") ;;
            *) sys_pkgs+=("$p") ;;
          esac
          ;;
        pacman)
          case "$p" in
            java) sys_pkgs+=("jdk-openjdk") ;;
            xxd) sys_pkgs+=("vim") ;;
            7z) sys_pkgs+=("p7zip") ;;
            cwebp) sys_pkgs+=("libwebp") ;;
            protoc) sys_pkgs+=("protobuf") ;;
            getfattr) sys_pkgs+=("attr") ;;
            *) sys_pkgs+=("$p") ;;
          esac
          ;;
        *)
          case "$p" in
            java) sys_pkgs+=("openjdk-17-jdk") ;;
            7z) sys_pkgs+=("p7zip-full") ;;
            cwebp) sys_pkgs+=("webp") ;;
            protoc) sys_pkgs+=("protobuf-compiler") ;;
            getfattr) sys_pkgs+=("attr") ;;
            *) sys_pkgs+=("$p") ;;
          esac
          ;;
      esac
    done

    if [ -n "$PM" ] && [ ${#sys_pkgs[@]} -gt 0 ]; then
      echo -e "  ${CYAN}▶ Installing system packages using $PM (sudo required)...${RESET}"
      case "$PM" in
        pacman) sudo pacman -S --noconfirm "${sys_pkgs[@]}" ;;
        zypper) sudo zypper install -y "${sys_pkgs[@]}" ;;
        *) sudo "$PM" install -y "${sys_pkgs[@]}" ;;
      esac
    fi
  fi

  echo -e "\n  ${CYAN}▶ git submodule update --init --recursive${RESET}"
  if [ -d "$SRC_DIR/.git" ]; then
    git -C "$SRC_DIR" submodule update --init --recursive || \
      echo -e "  ${YELLOW}⚠ submodule update failed — setup will use fast-path tools only${RESET}"
  else
    echo -e "  ${YELLOW}⚠ not a git checkout — skipping submodules${RESET}"
  fi

  echo -e "\n  ${CYAN}▶ ./tools/setup.sh  ${DIM}(apktool 3.x + mkuserimg → out/tools)${RESET}"
  "$SRC_DIR/tools/setup.sh"

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
  fi
  if [[ "$SOURCE_FIRMWARE" == "$TARGET_FIRMWARE" ]]; then
    echo -e "  ${DIM}(TARGET same as SOURCE — one package set)${RESET}"
  elif $tgt_ok; then
    echo -e "  ${GREEN}✔ TARGET Odin present${RESET}  $(fw_odin_path "$TARGET_FIRMWARE" | sed "s|$SRC_DIR/||")"
  else
    echo -e "  ${YELLOW}○ TARGET Odin missing${RESET}  $(cut -d/ -f1-2 <<< "$TARGET_FIRMWARE" | tr / _)"
  fi
  echo ""

  echo -e "  ${BOLD}[1]${RESET}  Download what this build needs  ${DIM}(SOURCE + TARGET from config)${RESET}"
  echo -e "  ${BOLD}[2]${RESET}  Download all S23 models  ${DIM}(dm1q + dm2q + dm3q — max multi-device cache)${RESET}"
  echo -e "  ${BOLD}[3]${RESET}  Force re-download for this build  ${DIM}(--force)${RESET}"
  if $src_ok && { [[ "$SOURCE_FIRMWARE" == "$TARGET_FIRMWARE" ]] || $tgt_ok; }; then
    echo -e "  ${BOLD}[4]${RESET}  Skip — keep existing packages"
  fi
  echo ""
  echo -e -n "  ${BOLD}Choice [1]:${RESET} "
  read -r dl_choice

  case "$dl_choice" in
    4)
      if $src_ok && { [[ "$SOURCE_FIRMWARE" == "$TARGET_FIRMWARE" ]] || $tgt_ok; }; then
        STEP_FW_DOWNLOADED=true
        save_state
        echo -e "\n  ${GREEN}✔ Using existing firmware — continue to Step 3.${RESET}"
        press_enter
        return
      fi
      echo -e "  ${YELLOW}Packages incomplete — downloading what is needed.${RESET}"
      run_step "$SRC_DIR/scripts/download_fw.sh"
      ;;
    2)
      echo -e "\n  ${CYAN}▶ Downloading QSSI base + every target firmware…${RESET}"
      # Base from qssi
      local base_fw
      base_fw="$(grep '^SOURCE_FIRMWARE=' "$SRC_DIR/unica/configs/qssi.sh" | head -1 | cut -d= -f2- | tr -d '"')"
      run_step "$SRC_DIR/scripts/download_fw.sh" --ignore-source --ignore-target "$base_fw" || true
      local t cfg tf
      for t in $(find "$SRC_DIR/target" -mindepth 1 -maxdepth 1 -type d -printf "%f\n" | sort); do
        cfg="$SRC_DIR/target/$t/config.sh"
        [ -f "$cfg" ] || continue
        tf="$(bash -c "source '$cfg' >/dev/null 2>&1; echo \"\$TARGET_FIRMWARE\"")"
        [ -n "$tf" ] || continue
        echo -e "\n  ${CYAN}▶ $t → $tf${RESET}"
        run_step "$SRC_DIR/scripts/download_fw.sh" --ignore-source --ignore-target "$tf" || true
      done
      ;;
    3)
      run_step "$SRC_DIR/scripts/download_fw.sh" -f
      ;;
    *)
      run_step "$SRC_DIR/scripts/download_fw.sh"
      ;;
  esac

  STEP_FW_DOWNLOADED=true
  save_state
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
    run_step "$SRC_DIR/scripts/extract_fw.sh" -f
  else
    [ -L "$src_path" ] && rm -f "$src_path"
    [ -L "$tgt_path" ] && rm -f "$tgt_path"
    run_step "$SRC_DIR/scripts/extract_fw.sh"
  fi

  STEP_FW_DOWNLOADED=true
  STEP_FW_EXTRACTED=true
  save_state
  press_enter
}

step_build_rom() {
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
  echo ""
  echo -e -n "  ${BOLD}Choice [1]:${RESET} "
  read -r build_choice

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

  echo -e "\n  ${CYAN}▶ source buildenv.sh ${SELECTED_TARGET} && npl make_rom ${flags[*]}${RESET}\n"
  run_step "$SRC_DIR/scripts/make_rom.sh" "${flags[@]}"
  STEP_ROM_BUILT=true
  save_state
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
    echo -e "  ${BOLD}[r]${RESET}  Reset build state"
    echo -e "  ${BOLD}[q]${RESET}  Quit"
    echo ""
    echo -e -n "  ${BOLD}Choose:${RESET} "
    read -r choice || exit 0


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
