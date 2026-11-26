package dev.denver.dressroomadvanced;

import android.os.Bundle;

final class DepthSafety {
    static void clearMask(Bundle data) {
        if (data == null) return;
        for (String field : DepthMetadata.BLOBS) {
            data.remove(field);
            data.remove(field + "Ref");
        }
        data.remove(DepthMetadata.FINGERPRINT);
        data.remove("depthSourceVersion");
    }

    static void disable(Bundle data) {
        if (data == null) return;
        clearMask(data);
        data.putBoolean("depthEnabled", false);
        if (data.getBoolean("depthOwnsManual")) {
            data.putBoolean("manual", false);
            data.remove("manualHeights");
            data.remove("manualProgress");
        }
        data.putBoolean("depthOwnsManual", false);
    }
}
