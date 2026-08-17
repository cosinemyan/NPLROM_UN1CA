#!/usr/bin/env bash
# KnoxPatch integration module for Knox, Samsung Pass, Secure Folder, and Samsung Health

LOGI "Applying KnoxPatch security patches..."

# Set Knox bypass props
SET_PROP "system" "ro.config.knox" "v30"
SET_PROP "system" "ro.boot.knox.v30" "0"
SET_PROP "system" "ro.boot.warranty_bit" "0"
SET_PROP "system" "ro.warranty_bit" "0"
SET_PROP "system" "ro.build.selinux" "1"

LOGI "KnoxPatch properties applied successfully."
