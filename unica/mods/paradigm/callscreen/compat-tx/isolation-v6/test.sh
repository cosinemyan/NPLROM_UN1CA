#!/usr/bin/env bash
# Copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
HERE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
WORK=$(mktemp -d /tmp/callscreen-v6-test.XXXXXX)
cc -std=c11 -D_GNU_SOURCE -DCS_TEST -Wall -Wextra -Werror \
    -fsanitize=address,undefined -g "$HERE/compat_tx.c" "$HERE/sources.c" "$HERE/test.c" \
    -pthread -ldl -o "$WORK/test"
"$WORK/test"
