// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once
#include <stdint.h>
struct Hold {
    int64_t since = -1;
    bool update(bool down, int64_t now) {
        if (!down) { since = -1; return false; }
        if (since < 0) since = now;
        return now - since >= 600;
    }
};
