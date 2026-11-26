package dev.denver.dressroomadvanced;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;

final class FontAxisSettings {
    private FontAxisSettings() {}

    static boolean nativeStretch(Bundle profile) {
        return profile == null || ((!profile.containsKey("fontNativeStretch") || profile.getBoolean("fontNativeStretch")) && !profile.getBoolean("followDisplayFont") && profile.getString("fontFile", "").isEmpty());
    }

    static Bundle clean(Bundle input) {
        Bundle result = new Bundle();
        if (input == null) return result;
        ArrayList<String> tags = new ArrayList<>(input.keySet());
        Collections.sort(tags);
        for (String tag : tags) {
            if (result.size() == 64) break;
            if (!FontAxes.validTag(tag)) continue;
            Object raw = input.get(tag);
            if (raw instanceof Number number && Float.isFinite(number.floatValue()))
                result.putFloat(tag, number.floatValue());
        }
        return result;
    }

    static Bundle values(Bundle profile) {
        if (profile == null) return new Bundle();
        if (profile.containsKey("fontAxes")) return clean(profile.getBundle("fontAxes"));
        Bundle result = new Bundle();
        float width = profile.getFloat("fontWidth", Float.NaN);
        if (Float.isFinite(width)) result.putFloat("wdth", width);
        return result;
    }

    static String key(Bundle values) {
        Bundle clean = clean(values);
        ArrayList<String> tags = new ArrayList<>(clean.keySet());
        Collections.sort(tags);
        StringBuilder key = new StringBuilder();
        for (String tag : tags)
            key.append(tag)
                    .append('=')
                    .append(Float.floatToIntBits(clean.getFloat(tag)))
                    .append(';');
        return key.toString();
    }
}
