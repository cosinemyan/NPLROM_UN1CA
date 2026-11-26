// Native adaptation of BlueOutside / Dressroom Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

import android.graphics.RenderEffect;
import android.view.View;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class ShadowEffects {
    private record Shadow(float radius, float dx, float dy, int color) {}

    private static final Map<View, Shadow> shadows =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, RenderEffect> nativeEffects =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, ShadowEffectCache> compositions =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> applying = ThreadLocal.withInitial(() -> false);
    private static boolean installed;

    private ShadowEffects() {}

    static RenderEffect nativeEffect(View view, RenderEffect effect) {
        if (applying.get()) return effect;
        if (effect == null) nativeEffects.remove(view); else nativeEffects.put(view,effect);
        Shadow shadow = shadows.get(view);
        return shadow == null ? effect : compose(view,shadow,effect);
    }


    static void apply(View view, boolean enabled, float radius, float dx, float dy, int color) {
        if (android.os.Build.VERSION.SDK_INT < 31) return;
        Shadow old = shadows.get(view);
        if (!enabled && old == null) return;
        Shadow shadow = enabled ? new Shadow(radius, dx, dy, color) : null;
        if (shadow != null && shadow.equals(old)) return;
        if (shadow == null) {
            shadows.remove(view);
            compositions.remove(view);
        } else shadows.put(view, shadow);
        boolean previous = applying.get();
        applying.set(true);
        try {
            view.setRenderEffect(
                    shadow == null
                            ? nativeEffects.get(view)
                            : compose(view, shadow, nativeEffects.get(view)));
        } finally {
            applying.set(previous);
        }
    }

    private static RenderEffect compose(View view, Shadow value, RenderEffect nativeEffect) {
        return compositions
                .computeIfAbsent(view, key -> new ShadowEffectCache())
                .get(value.radius, value.dx, value.dy, value.color, nativeEffect);
    }
}
