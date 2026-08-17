LOG_STEP_IN "- ssch71 experimental mods"

# =============================================================================
# ssch71 priv-app
# =============================================================================
LOG "- Adding Collection app"
ADD_TO_WORK_DIR "$MODPATH" "system" \
    "system/priv-app/Collection" 0 0 755 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "$MODPATH" "system" \
    "system/priv-app/Collection/Collection.apk" 0 0 644 "u:object_r:system_file:s0"

LOG "- Adding SocialComposer app"
ADD_TO_WORK_DIR "$MODPATH" "system" \
    "system/priv-app/SocialComposer" 0 0 755 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "$MODPATH" "system" \
    "system/priv-app/SocialComposer/SocialComposer.apk" 0 0 644 "u:object_r:system_file:s0"

LOG "- Adding Voicecaptioning app"
ADD_TO_WORK_DIR "$MODPATH" "system" \
    "system/priv-app/Voicecaptioning" 0 0 755 "u:object_r:system_file:s0"
ADD_TO_WORK_DIR "$MODPATH" "system" \
    "system/priv-app/Voicecaptioning/Voicecaptioning.apk" 0 0 644 "u:object_r:system_file:s0"

LOG_STEP_OUT