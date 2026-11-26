#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
[[ $# == 2 ]] || { echo "Usage: $0 vendor_boot.img msm_kgsl.ko" >&2; exit 1; }
HERE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd "$HERE/../../../.." && pwd)
AVB="$ROOT/external/android-tools/vendor/avb/libavb"
CHECK_DIR=$(mktemp -d /tmp/unica-gpu-preflight.XXXXXX)
trap 'rm -f "$CHECK_DIR/sha.o" "$CHECK_DIR/check"; rmdir "$CHECK_DIR"' EXIT
cc -O2 -DAVB_COMPILATION -I"$AVB" -I"$AVB/crypto" -c "$AVB/crypto/sha256_impl.c" -o "$CHECK_DIR/sha.o"
c++ -std=c++17 -O2 -DAVB_COMPILATION -I"$AVB" -I"$AVB/crypto" -Wall -Wextra -Werror \
    "$HERE/verify-firmware.cpp" "$CHECK_DIR/sha.o" -o "$CHECK_DIR/check"
"$CHECK_DIR/check" "$1" "$2"
