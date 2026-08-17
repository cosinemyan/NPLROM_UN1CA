#!/usr/bin/env bash
# Gradle 8.x (apktool) and some Android tools need JDK 17–24 to *build*.
# Fedora 44 often ships Java 25/26 only; Gradle then fails with a bare version
# string ("25.0.4"). This script points JAVA_HOME at a portable Temurin 21 JRE+JDK
# under out/tools/jdk-21 (downloaded once, no sudo).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JDK_DIR="${BUILD_JDK_DIR:-$PROJECT_ROOT/out/tools/jdk-21}"

_java_major() {
  "$1" -version 2>&1 | awk -F '[ ".]+' '/version/ {print $4; exit}'
}

# Already on a Gradle-friendly JDK (17–24)?
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  major="$(_java_major "$JAVA_HOME/bin/java")"
  if [ "$major" -ge 17 ] && [ "$major" -le 24 ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
    return 0 2>/dev/null || exit 0
  fi
fi
if command -v java >/dev/null; then
  major="$(_java_major java)"
  if [ "$major" -ge 17 ] && [ "$major" -le 24 ]; then
    return 0 2>/dev/null || exit 0
  fi
fi

# Prefer a distro JDK in the supported range.
for _jdk in \
  /usr/lib/jvm/java-21-openjdk \
  /usr/lib/jvm/java-17-openjdk \
  /usr/lib/jvm/java-21 \
  /usr/lib/jvm/java-17; do
  if [ -x "$_jdk/bin/java" ]; then
    major="$(_java_major "$_jdk/bin/java")"
    if [ "$major" -ge 17 ] && [ "$major" -le 24 ]; then
      export JAVA_HOME="$_jdk"
      export PATH="$JAVA_HOME/bin:$PATH"
      unset _jdk major
      return 0 2>/dev/null || exit 0
    fi
  fi
done
unset _jdk

if [ ! -x "$JDK_DIR/bin/java" ]; then
  echo "[>] Downloading Temurin JDK 21 for Gradle builds (one-time, ~190 MB)..."
  mkdir -p "$(dirname "$JDK_DIR")"
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse?project=jdk" \
    -o /tmp/npl-temurin21.tar.gz
  rm -rf "$JDK_DIR"
  mkdir -p "$JDK_DIR"
  tar -xzf /tmp/npl-temurin21.tar.gz -C "$JDK_DIR" --strip-components=1
  rm -f /tmp/npl-temurin21.tar.gz
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
unset major JDK_DIR
