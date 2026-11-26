// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.graphics.Matrix;
import android.graphics.Path;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

final class WidgetMediaOcclusion {
    private static final Map<ViewGroup, Path> covers = new WeakHashMap<>();
    private static final Map<View, Boolean> widgets = new WeakHashMap<>();

    private record Local(Path source, Matrix inverse, Path path) {}

    private static final Map<View, Local> local = new WeakHashMap<>();

    static void register(View root) {
        widgets.put(root, true);
    }

    static void forget(View root) {
        widgets.remove(root);
        local.remove(root);
    }

    static Path source(View view) {
        if (view.getWindowToken() == null) return null;
        boolean registered = false;
        for (View parent = view;
                parent != null;
                parent = parent.getParent() instanceof View next ? next : null)
            if (widgets.containsKey(parent)) {
                registered = true;
                break;
            }
        if (!registered) return null;
        for (Map.Entry<ViewGroup, Path> item : covers.entrySet())
            if (item.getKey().getWindowToken() == view.getWindowToken()) return item.getValue();
        return null;
    }

    static boolean active(View view) {
        return source(view) != null;
    }

    static Path localPath(View view) {
        Path source = source(view);
        if (source == null) return null;
        Matrix global = new Matrix(), inverse = new Matrix();
        view.transformMatrixToGlobal(global);
        if (!global.invert(inverse)) return null;
        Local old = local.get(view);
        if (old != null && old.source == source && old.inverse.equals(inverse)) return old.path;
        Path result = new Path();
        source.transform(inverse, result);
        local.put(view, new Local(source, new Matrix(inverse), result));
        return result;
    }

    static void publish(ViewGroup root, Path cover) {
        if (cover == null) {
            if (covers.remove(root) == null) return;
        } else {
            Matrix global = new Matrix();
            root.transformMatrixToGlobal(global);
            Path screen = new Path();
            cover.transform(global, screen);
            covers.put(root, screen);
        }
        for (View widget : new ArrayList<>(widgets.keySet()))
            if (widget != null
                    && widget.isAttachedToWindow()
                    && widget.getWindowToken() == root.getWindowToken()) {
                boolean depth = DepthEffects.hasVisibleSubject(widget);
                WidgetDepthBlur.clipWidgets(widget, depth);
                WidgetContentClip.refresh(widget);
                WidgetDepthBlur.watch(widget, depth || cover != null);
                WidgetDepthBlur.mediaChanged(widget);
            }
    }
}
