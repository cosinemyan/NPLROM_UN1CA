#!/usr/bin/env bash
# NPL toolchain bootstrap (UN1CA-compatible layout under out/tools).
# Prefer: scripts/build_dependencies.sh after git submodule update --init --recursive
# This script is a fast path for apktool 3.x + mkuserimg when submodules are not built yet.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_TOOLS="$PROJECT_ROOT/out/tools"
BIN_DIR="$OUT_TOOLS/bin"
LIB_DIR="$OUT_TOOLS/lib"
VENV_DIR="$OUT_TOOLS/venv"

APKTOOL_VERSION="3.0.2"

echo "=========================================="
echo " Setting up NPL ROM Builder Toolchain..."
echo " (tools live in out/tools — same as UN1CA)"
echo "=========================================="

mkdir -p "$BIN_DIR" "$LIB_DIR"

# Prefer full UN1CA-style build when submodules exist
if [ -f "$PROJECT_ROOT/scripts/build_dependencies.sh" ]; then
  if [ -d "$PROJECT_ROOT/external/android-tools/.git" ] || [ -f "$PROJECT_ROOT/external/android-tools/CMakeLists.txt" ]; then
    echo "[>] Submodules present — running build_dependencies.sh..."
    bash "$PROJECT_ROOT/scripts/build_dependencies.sh" || \
      echo "[!] build_dependencies failed; continuing with fast-path tools."
  else
    echo "[!] external/* submodules not initialized."
    echo "    Run: git submodule update --init --recursive"
    echo "    Then: scripts/build_dependencies.sh"
    echo "    Continuing with apktool + mkuserimg fast path..."
  fi
fi

# Apktool >= 3.0.2 required for DEX 041 multi-dex (One UI 8 services.jar)
need_apktool=false
if [ ! -f "$BIN_DIR/apktool" ] || [ ! -f "$BIN_DIR/apktool.jar" ]; then
  need_apktool=true
elif ! java -jar "$BIN_DIR/apktool.jar" -version 2>/dev/null | grep -qE '^3\.'; then
  echo "[!] Existing apktool is not 3.x — upgrading to ${APKTOOL_VERSION}"
  need_apktool=true
fi

if $need_apktool; then
  echo "[>] Downloading apktool ${APKTOOL_VERSION} into $BIN_DIR..."
  curl -fsSL "https://raw.githubusercontent.com/iBotPeaches/Apktool/master/scripts/linux/apktool" -o "$BIN_DIR/apktool"
  curl -fsSL "https://github.com/iBotPeaches/Apktool/releases/download/v${APKTOOL_VERSION}/apktool_${APKTOOL_VERSION}.jar" -o "$BIN_DIR/apktool.jar"
  chmod +x "$BIN_DIR/apktool"
  echo "[✓] apktool ${APKTOOL_VERSION} installed"
else
  echo "[✓] apktool 3.x already in $BIN_DIR"
fi

# mkuserimg_mke2fs (Bluetooth APEX / ext4 images) — wrapper from UN1CA external/ext4_utils
if [ ! -e "$BIN_DIR/mkuserimg_mke2fs" ]; then
  if [ -f "$PROJECT_ROOT/external/ext4_utils/mkuserimg_mke2fs.py" ]; then
    cp -a "$PROJECT_ROOT/external/ext4_utils/mkuserimg_mke2fs.py" "$BIN_DIR/mkuserimg_mke2fs.py"
    ln -sfn "$BIN_DIR/mkuserimg_mke2fs.py" "$BIN_DIR/mkuserimg_mke2fs"
    [ -f "$PROJECT_ROOT/external/ext4_utils/mke2fs.conf" ] && \
      cp -a "$PROJECT_ROOT/external/ext4_utils/mke2fs.conf" "$BIN_DIR/mke2fs.conf"
    echo "[✓] mkuserimg_mke2fs installed (needs mke2fs.android from android-tools for full APEX builds)"
  else
    echo "[!] external/ext4_utils missing — copy from UN1CA or init submodules"
  fi
else
  echo "[✓] mkuserimg_mke2fs already present"
fi

# Zipalign (if missing)
if ! command -v zipalign &>/dev/null && [ ! -f "$BIN_DIR/zipalign" ]; then
  echo "[>] Downloading zipalign..."
  curl -fsSL "https://android.googlesource.com/platform/prebuilts/sdk/+/refs/heads/main/tools/linux/bin/zipalign?format=TEXT" | base64 -d > "$BIN_DIR/zipalign"
  curl -fsSL "https://android.googlesource.com/platform/prebuilts/sdk/+/refs/heads/main/tools/linux/lib64/libc++.so?format=TEXT" | base64 -d > "$LIB_DIR/libc++.so" || true
  chmod +x "$BIN_DIR/zipalign"
  echo "[✓] zipalign installed"
fi

# Signapk
if [ ! -f "$BIN_DIR/signapk" ] || [ ! -f "$BIN_DIR/signapk.jar" ]; then
  echo "[>] Downloading signapk.jar..."
  curl -fsSL "https://android.googlesource.com/platform/prebuilts/sdk/+/refs/heads/main/tools/lib/signapk.jar?format=TEXT" | base64 -d > "$BIN_DIR/signapk.jar"
  cat > "$BIN_DIR/signapk" << 'EOF'
#!/bin/bash
exec java -jar "$(dirname "$0")/signapk.jar" "$@"
EOF
  chmod +x "$BIN_DIR/signapk"
  echo "[✓] signapk installed"
fi

# EROFS utils
_is_real_elf() { file "$1" 2>/dev/null | grep -q "ELF"; }
if ! _is_real_elf "$BIN_DIR/fsck.erofs" || ! _is_real_elf "$BIN_DIR/mkfs.erofs"; then
  echo "[>] Downloading erofs-utils..."
  _EROFS_URL="$(curl -fsSL "https://api.github.com/repos/sekaiacg/erofs-tools/releases/latest" | \
    python3 -c "import sys,json; r=json.load(sys.stdin); \
    [print(a['browser_download_url']) for a in r.get('assets',[]) \
    if 'Linux_x86_64' in a['name']]" 2>/dev/null | head -1)"
  if [ -n "${_EROFS_URL:-}" ]; then
    curl -fsSL "$_EROFS_URL" -o /tmp/_erofs_utils.zip
    rm -rf /tmp/_erofs_utils
    mkdir -p /tmp/_erofs_utils
    unzip -o /tmp/_erofs_utils.zip -d /tmp/_erofs_utils/ >/dev/null
    for _bin in fsck.erofs mkfs.erofs dump.erofs extract.erofs; do
      [ -f "/tmp/_erofs_utils/$_bin" ] && cp "/tmp/_erofs_utils/$_bin" "$BIN_DIR/$_bin" && chmod +x "$BIN_DIR/$_bin"
    done
    rm -rf /tmp/_erofs_utils.zip /tmp/_erofs_utils
    echo "[✓] erofs-utils installed"
  else
    echo "[!] Could not fetch erofs-utils URL"
  fi
  unset _EROFS_URL _bin
else
  echo "[✓] erofs-utils already installed"
fi
unset -f _is_real_elf

# samloader venv under out/tools
if [ ! -d "$VENV_DIR" ]; then
  echo "[>] Creating Python venv..."
  python3 -m venv "$VENV_DIR" 2>/dev/null || true
fi
if [ -f "$VENV_DIR/bin/pip" ]; then
  "$VENV_DIR/bin/pip" install -q "git+https://github.com/ananjaser1211/samloader.git" >/dev/null 2>&1 || true
  [ -f "$VENV_DIR/bin/samloader" ] && ln -sfn "$VENV_DIR/bin/samloader" "$BIN_DIR/samloader"
fi
[ -f "$BIN_DIR/samloader" ] && echo "[✓] samloader at $BIN_DIR/samloader"

echo "=========================================="
echo " Toolchain setup completed!"
echo " PATH should include: $BIN_DIR"
echo " (source buildenv.sh <target> sets this automatically)"
echo "=========================================="
