#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Compatibility entry point; all device targets now use the configured API app.
set -euo pipefail
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
exec bash "$ROOT/app/build.sh" "$@"
