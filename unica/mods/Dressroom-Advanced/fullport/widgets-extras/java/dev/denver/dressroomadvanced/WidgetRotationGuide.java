// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Hide the phone's two rotation targets without changing native rotation eligibility. */
final class WidgetRotationGuide {
    private final java.util.function.BooleanSupplier suppress;
    private final Map<View, Integer> hidden = new WeakHashMap<>();
    private final Map<android.content.res.Resources, int[]> ids = new WeakHashMap<>();
    private WeakReference<View> anchor = new WeakReference<>(null);
    private WeakReference<View> currentGuide = new WeakReference<>(null);
    private WeakReference<ViewTreeObserver> observer = new WeakReference<>(null);
    private final ViewTreeObserver.OnGlobalLayoutListener listener =
            () -> {
                View view = anchor.get();
                if (view != null) update(view.getRootView());
            };

    WidgetRotationGuide(java.util.function.BooleanSupplier suppress) {
        this.suppress = suppress;
    }

    void clear() {
        ViewTreeObserver previous = observer.get();
        if (previous != null && previous.isAlive()) previous.removeOnGlobalLayoutListener(listener);
        View root = anchor.get();
        if (root != null && root.getViewTreeObserver().isAlive())
            root.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        anchor.clear();
        observer.clear();
        currentGuide.clear();
        hidden.forEach(View::setVisibility);
        hidden.clear();
    }

    void update(View root) {
        anchor = new WeakReference<>(root);
        ViewTreeObserver tree = root.getViewTreeObserver(), previous = observer.get();
        if (tree != previous && tree.isAlive()) {
            if (previous != null && previous.isAlive())
                previous.removeOnGlobalLayoutListener(listener);
            // An unattached view's observer can be merged into its window during attach.
            tree.removeOnGlobalLayoutListener(listener);
            tree.addOnGlobalLayoutListener(listener);
            observer = new WeakReference<>(tree);
        }
        View guide = currentGuide.get();
        if (guide == null || guide.getRootView() != root) {
            var resources = root.getResources();
            String pkg = root.getContext().getPackageName();
            int[] controls =
                    ids.computeIfAbsent(
                            resources,
                            unused ->
                                    new int[] {
                                        resources.getIdentifier(
                                                "assist_guide_box_default", "id", pkg),
                                        resources.getIdentifier("assist_guide_box_left", "id", pkg)
                                    });
            if (controls[0] == 0 || controls[1] == 0) return;
            View first = root.findViewById(controls[0]), second = root.findViewById(controls[1]);
            if (first == null
                    || second == null
                    || !(first.getParent() instanceof View firstParent)
                    || !(second.getParent() instanceof View secondParent)
                    || firstParent == secondParent
                    || firstParent.getParent() != secondParent.getParent()
                    || !(firstParent.getParent() instanceof View found)) return;
            guide = found;
            currentGuide = new WeakReference<>(guide);
        }
        if (!suppress.getAsBoolean()) {
            Integer original = hidden.remove(guide);
            if (original != null) guide.setVisibility(original);
            return;
        }
        if (!hidden.containsKey(guide) || guide.getVisibility() != View.GONE)
            hidden.put(guide, guide.getVisibility());
        if (guide.getVisibility() != View.GONE) guide.setVisibility(View.GONE);
    }
}
