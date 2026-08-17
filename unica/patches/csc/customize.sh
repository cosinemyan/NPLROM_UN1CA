#!/usr/bin/env bash
# CSC Feature Enabler Module (Call recording, Network speed meter, Hiya protection)

LOGI "Applying custom CSC feature tweaks..."

SET_PROP "system" "ro.csc.sales_code" "INS"
SET_PROP "system" "ro.csc.omcnw_code" "INS"
SET_PROP "system" "ril.call.recording" "true"

LOGI "Custom CSC features enabled."
