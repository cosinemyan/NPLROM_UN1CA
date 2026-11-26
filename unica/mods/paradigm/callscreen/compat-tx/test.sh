#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail
ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
WORK=$(mktemp -d /tmp/callscreen-compat-test.XXXXXX)
cc -std=c11 -D_GNU_SOURCE -DCS_TEST -Wall -Wextra -Werror -fsanitize=address,undefined -g \
    "$ROOT/compat_tx.c" "$ROOT/test_compat_tx.c" -pthread -ldl -o "$WORK/test"
"$WORK/test"
