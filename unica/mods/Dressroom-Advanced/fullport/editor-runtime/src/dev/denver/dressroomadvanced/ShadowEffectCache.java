package dev.denver.dressroomadvanced;

import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.RenderEffect;
import android.graphics.Shader;

final class ShadowEffectCache {
    private float radius, dx, dy;
    private int color;
    private RenderEffect nativeEffect, result;

    RenderEffect get(
            float nextRadius, float nextDx, float nextDy, int nextColor, RenderEffect nextNative) {
        if (result != null
                && nativeEffect == nextNative
                && color == nextColor
                && Float.compare(radius, nextRadius) == 0
                && Float.compare(dx, nextDx) == 0
                && Float.compare(dy, nextDy) == 0) return result;
        RenderEffect content =
                nextNative == null ? RenderEffect.createOffsetEffect(0, 0) : nextNative;
        RenderEffect silhouette =
                RenderEffect.createColorFilterEffect(
                        new BlendModeColorFilter(nextColor, BlendMode.SRC_IN), content);
        RenderEffect blurred =
                nextRadius > 0
                        ? RenderEffect.createBlurEffect(
                                nextRadius, nextRadius, silhouette, Shader.TileMode.DECAL)
                        : silhouette;
        RenderEffect offset = RenderEffect.createOffsetEffect(nextDx, nextDy, blurred);
        RenderEffect composed =
                RenderEffect.createBlendModeEffect(offset, content, BlendMode.SRC_OVER);
        radius = nextRadius;
        dx = nextDx;
        dy = nextDy;
        color = nextColor;
        nativeEffect = nextNative;
        result = composed;
        return composed;
    }
}
