// Native port of Dressroom-Advanced by @BlueOutside. See upstream LICENSE.
package dev.denver.dressroomadvanced;

import android.graphics.Path;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class WidgetDepthBlur {
    private static final String CONTAINER =
            "com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer";

    private record Watch(
            java.lang.ref.WeakReference<ViewTreeObserver> observer,
            ViewTreeObserver.OnPreDrawListener listener) {}

    private static final Map<View, Watch> roots = new WeakHashMap<>();
    private static final Map<View, ViewGeometry> geometry = new WeakHashMap<>();
    private static final Map<View, Object> originals = new WeakHashMap<>();
    private static final Map<View, Boolean> blurDisabled = new WeakHashMap<>();
    private static final Set<View> verified = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<View> hidden = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean separateSurfaces;

    static void clipWidgets(View root, boolean depth) {
        separateSurfaces = ScreenIdentity.supported();
        if (separateSurfaces) WidgetDepthClip.apply(root, depth);
        else WidgetContentClip.apply(root, depth);
    }

    private WidgetDepthBlur() {}

    /** Direct replacement of each Samsung complication semSetBlurInfo callsite. */
    static Object submission(View view, Object original) {
        separateSurfaces = ScreenIdentity.supported();
        if (!isBackground(view)) return original;
        if (original == null) originals.remove(view); else originals.put(view, original);
        Object submitted = original;
        try {
            if (original != null && view.getWidth() > 0 && view.getHeight() > 0) {
                Path subject = separateSurfaces ? null : DepthEffects.localSubjectPath(view);
                Path cover = WidgetMediaOcclusion.localPath(view);
                if (subject != null || cover != null) {
                    WidgetBlurInfo copies = new WidgetBlurInfo(original.getClass());
                    Path visible = copies.visible(original, view.getWidth(), view.getHeight(), subject);
                    if (cover != null) visible = DepthGeometry.subtract(visible, cover);
                    if (visible != null) submitted = copies.submission(original, visible);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            android.util.Log.w("Dressroom-Advanced", "Widget blur path unavailable", error);
        }
        return blurOff(view) ? null : submitted;
    }

    private static boolean isBackground(View view) {
        return "BlurView".equals(view.getTag())
                || (view.getParent() instanceof View parent
                        && parent.getClass().getName().equals(CONTAINER)
                        && !(view instanceof ViewGroup));
    }

    static void blur(View root, boolean off) {
        boolean previous = Boolean.TRUE.equals(blurDisabled.get(root));
        if (off) blurDisabled.put(root, true);
        else blurDisabled.remove(root);
        if (off != previous) resubmitBlur(root);
    }

    private static boolean blurOff(View view) {
        for (View current = view;
                current != null;
                current = current.getParent() instanceof View parent ? parent : null)
            if (Boolean.TRUE.equals(blurDisabled.get(current))) return true;
        return false;
    }

    private static void resubmitBlur(View view) {
        Object original = originals.get(view);
        if (original != null)
            try {
                Reflect.call(view, "semSetBlurInfo", submission(view, original));
            } catch (ReflectiveOperationException e) {
                android.util.Log.w("Dressroom-Advanced", "Widget blur restoration failed", e);
            }
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) resubmitBlur(group.getChildAt(i));
        view.invalidate();
    }

    static void watch(View widgets, boolean enabled) {
        separateSurfaces = ScreenIdentity.supported();
        if (!enabled) {
            Watch watch = roots.remove(widgets);
            ViewTreeObserver oldObserver = watch == null ? null : watch.observer.get();
            if (oldObserver != null && oldObserver.isAlive())
                oldObserver.removeOnPreDrawListener(watch.listener);
            if (separateSurfaces) WidgetDepthClip.apply(widgets, false);
            refreshTree(widgets, true);
            geometry.remove(widgets);
            return;
        }
        ViewTreeObserver observer = widgets.getViewTreeObserver();
        Watch previous = roots.get(widgets);
        ViewTreeObserver oldObserver = previous == null ? null : previous.observer.get();
        if (oldObserver == observer && observer.isAlive()) return;
        if (oldObserver != null && oldObserver.isAlive())
            oldObserver.removeOnPreDrawListener(previous.listener);
        geometry.remove(widgets);
        java.lang.ref.WeakReference<View> reference = new java.lang.ref.WeakReference<>(widgets);
        ViewTreeObserver.OnPreDrawListener listener =
                () -> {
                    View root = reference.get();
                    if (root != null) {
                        if (separateSurfaces) {
                            clipWidgets(root, DepthEffects.hasVisibleSubject(root));
                        } else {
                            boolean outlineChanged = WidgetContentClip.apply(root, true);
                            if (geometryChanged(root) || outlineChanged) WidgetContentClip.refresh(root);
                        }
                        refreshTree(root, false);
                    }
                    return true;
                };
        roots.put(widgets, new Watch(new java.lang.ref.WeakReference<>(observer), listener));
        observer.addOnPreDrawListener(listener);
        widgets.invalidate();
    }

    static void afterPlacement(View root) {
        for (var entry : roots.entrySet()) {
            View current = entry.getKey();
            while (current != null && current != root)
                current = current.getParent() instanceof View parent ? parent : null;
            if (current == null) continue;
            Watch watch = entry.getValue();
            ViewTreeObserver observer = watch.observer.get();
            if (observer != null && observer.isAlive()) {
                // Sample the root and nested blur surfaces after free placement,
                // otherwise this frame uses the previous widget location.
                observer.removeOnPreDrawListener(watch.listener);
                observer.addOnPreDrawListener(watch.listener);
            }
        }
    }

    static void mediaChanged(View root) {
        refreshTree(root, false);
    }

    private static void refreshTree(View view, boolean clear) {
        if (isBackground(view)) {
            boolean active =
                    !clear && (DepthEffects.hasVisibleSubject(view) || WidgetMediaOcclusion.active(view));
            boolean outlineChanged = WidgetContentClip.apply(view, active);
            Object original = originals.get(view);
            if (view.getWidth() > 0 && view.getHeight() > 0)
                try {
                    if (clear) {
                        if (geometry.remove(view) == null) return;
                    } else {
                        if (!geometryChanged(view) && !outlineChanged) return;
                        WidgetContentClip.refresh(view);
                    }
                    if (original != null) Reflect.call(view, "semSetBlurInfo", submission(view, original));
                } catch (Exception error) {
                    android.util.Log.w(
                            "Dressroom-Advanced", "Complication depth resubmit unavailable", error);
                }
        }
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) refreshTree(group.getChildAt(i), clear);
    }

    private static boolean geometryChanged(View view) {
        ViewGeometry state = geometry.computeIfAbsent(view, key -> new ViewGeometry());
        return state.update(
                view, DepthEffects.subjectIdentity(view), WidgetMediaOcclusion.source(view));
    }
}
