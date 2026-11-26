#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Input: clean stock HoneyBoard decode with ONLY keyboard patch 0001 applied.
# Output: complete replacement for keyboard patch 0005; existing output is refused.
set -euo pipefail
[[ $# == 2 ]] || { echo "Usage: $0 HONEYBOARD_AFTER_0001 OUTPUT_PATCH" >&2; exit 2; }
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
WORK=$(mktemp -d /tmp/shade-keyboard-patch.XXXXXX)
bash "$ROOT/../../../local-summary-service/compile-java.sh" "$ROOT/keyboard-java" "$WORK/smali"
python3 "$ROOT/generate-keyboard-patch.py" "$1" "$WORK/smali" "$2"
