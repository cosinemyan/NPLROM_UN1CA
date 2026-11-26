// Native adaptation of BlueOutside / Dressroom Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

import java.util.ArrayList;
import java.util.List;

final class SavedStretch {
    private SavedStretch() {}

    static List<Float> heights(float[] values) {
        if (values == null || values.length == 0) return null;
        List<Float> result = new ArrayList<>();
        for (float value : values) {
            if (!Float.isFinite(value) || value <= 0) return null;
            result.add(value);
        }
        return result;
    }

    static Object font(Object original, List<Float> heights) throws ReflectiveOperationException {
        if (heights == null || heights.equals(Reflect.call(original, "getFontHeights")))
            return original;
        return copy(original, heights);
    }

    static Object copy(Object original, List<Float> heights) throws ReflectiveOperationException {
        return Reflect.call(
                original,
                "copy",
                Reflect.call(original, "getFontType"),
                Reflect.call(original, "getFontPath"),
                Reflect.call(original, "getFontWeight"),
                Reflect.call(original, "getFontRoundedCorners"),
                new ArrayList<>(heights));
    }
}
