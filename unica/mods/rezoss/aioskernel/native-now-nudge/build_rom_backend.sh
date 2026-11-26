#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Build the default local GPU backend; retain an explicit Qwen source rollback.
set -euo pipefail
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
OUTPUT=${1:?Usage: build_rom_backend.sh NEW_OUTPUT_APK}
[[ ! -e "$OUTPUT" ]] || { echo "Refusing existing output: $OUTPUT" >&2; exit 1; }
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
case "${UNICA_LOCAL_LLM_BACKEND:-gemma-gpu}" in
    gemma-gpu)
        STAGE=$(mktemp -d /tmp/unica-gemma-rom.XXXXXX)
        bash "$ROOT/gemma/build.sh" "$STAGE/build"
        mkdir -p "$(dirname -- "$OUTPUT")"
        cp "$STAGE/build/AIOS-Gemma4-E2B-GPU.apk" "$OUTPUT"
        cp "$STAGE/build/AIOS-Gemma4-E2B-GPU.apk.version" "$OUTPUT.version"
        ;;
    qwen) exec bash "$ROOT/build_qwen_backend.sh" "$OUTPUT" ;;
    *) echo "Unsupported local LLM backend: $UNICA_LOCAL_LLM_BACKEND" >&2; exit 1 ;;
esac
