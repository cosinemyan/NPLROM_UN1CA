#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Same settings/API identity as the full app, without the offline model assets.
set -euo pipefail
: "${SRC_DIR:?Source buildenv.sh dm3q first}"
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
OUTPUT=${1:?Output APK required}
exec bash "$ROOT/../app/build.sh" "$OUTPUT" --stub
