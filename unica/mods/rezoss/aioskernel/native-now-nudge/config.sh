# SPDX-License-Identifier: GPL-3.0-or-later
# Device-local default; also accepts an exported environment override.
# Set the dm3q default to false to restore the previous ROM packaging.
if [[ "$TARGET_CODENAME" == "dm3q" ]]; then
    UNICA_ENABLE_NATIVE_NOW_NUDGE="${UNICA_ENABLE_NATIVE_NOW_NUDGE:-true}"
else
    UNICA_ENABLE_NATIVE_NOW_NUDGE="${UNICA_ENABLE_NATIVE_NOW_NUDGE:-false}"
fi

# Local generation only. Explicitly selected API providers are unaffected.
if [[ "$TARGET_CODENAME" == "dm3q" ]]; then
    export UNICA_LOCAL_LLM_BACKEND="${UNICA_LOCAL_LLM_BACKEND:-gemma-gpu}"
else
    export UNICA_LOCAL_LLM_BACKEND="${UNICA_LOCAL_LLM_BACKEND:-qwen}"
fi
case "$UNICA_LOCAL_LLM_BACKEND" in gemma-gpu|qwen) ;; *) echo "Unsupported local LLM backend: $UNICA_LOCAL_LLM_BACKEND" >&2; return 1 ;; esac
export UNICA_ENABLE_NATIVE_NOW_NUDGE
