#!/usr/bin/env bash
# Native port of BlueOutside / Dressroom-Advanced.
# Integration copyright (c) 2026 Rezoss
# SPDX-License-Identifier: GPL-3.0-or-later
SKIPUNZIP=1

# The mod runner sorts this directory before rezoss. Apply the native stages from
# that mod after its native font/clock dependencies, without changing global order.
if [[ "$TARGET_CODENAME" == "dm3q" && "$TARGET_PLATFORM_SDK_VERSION" == "37" ]]; then
    LOG "- Dressroom-Advanced by @BlueOutside: native stages follow the UN1CA clock/font patches"
    LOG "- https://github.com/BlueOutside/Dressroom-Advanced"
fi
