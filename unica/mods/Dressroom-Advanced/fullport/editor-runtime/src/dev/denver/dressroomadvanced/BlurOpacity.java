package dev.denver.dressroomadvanced;

final class BlurOpacity {
    static float resolve(
            float explicit,
            boolean forced,
            boolean custom,
            int tint,
            double brightness,
            float nativeValue) {
        if (Float.isFinite(explicit) && explicit >= 0) return Math.min(1, explicit);
        if (forced && custom && (tint >>> 24) == 255 && Double.isFinite(brightness))
            return brightness > 76 ? .35f : .5f;
        return nativeValue;
    }
}
