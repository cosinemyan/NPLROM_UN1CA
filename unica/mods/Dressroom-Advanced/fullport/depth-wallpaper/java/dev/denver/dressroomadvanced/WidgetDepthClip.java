// Native port of Dressroom-Advanced by @BlueOutside. See upstream LICENSE.
package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewGroup;
import java.util.Map;
import java.util.WeakHashMap;

/** Clip each widget surface once, retaining Samsung's native blur information. */
final class WidgetDepthClip {
    private static final String CONTAINER =
            "com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer";
    private static final Map<View, ViewGeometry> geometry = new WeakHashMap<>();

    private WidgetDepthClip() {}

    static void apply(View root, boolean enabled) {
        WidgetContentClip.apply(root, false);
        if (!(root instanceof ViewGroup group)) return;
        if (root.getClass().getName().equals(CONTAINER)) {
            container(group, enabled);
            return;
        }
        for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i), enabled);
    }

    static void container(ViewGroup container, boolean enabled) {
        WidgetContentClip.apply(container, false);
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            boolean changed = WidgetContentClip.apply(child, enabled);
            if (!enabled) {
                geometry.remove(child);
                continue;
            }
            ViewGeometry state = geometry.computeIfAbsent(child, key -> new ViewGeometry());
            boolean moved = state.update(child, DepthEffects.subjectIdentity(child), WidgetMediaOcclusion.source(child));
            if (changed || moved) WidgetContentClip.refresh(child);
        }
    }
}
