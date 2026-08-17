#!/usr/bin/env bash
# Play Integrity & Fingerprint Spoofing Module

LOGI "Applying Play Integrity & Device Fingerprint Spoofing..."

SET_PROP "system" "ro.build.fingerprint" "google/husky/husky:14/UD1A.230805.004/10800000:user/release-keys"
SET_PROP "product" "ro.product.model" "Pixel 8 Pro"

LOGI "Play Integrity spoofing applied."
