// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.view.View;

import java.lang.ref.WeakReference;

final class WidgetScalePreview {
    private WeakReference<View> root = new WeakReference<>(null);
    private float originalX, originalY, appliedX, appliedY, startingZoom;

    void show(View view, float savedZoom, float requestedZoom) {
        if (root.get() != view) {
            clear();
            root = new WeakReference<>(view);
            originalX = view.getScaleX();
            originalY = view.getScaleY();
            startingZoom = savedZoom;
        }
        float ratio = requestedZoom / startingZoom;
        appliedX = originalX * ratio;
        appliedY = originalY * ratio;
        view.setScaleX(appliedX);
        view.setScaleY(appliedY);
    }

    void clear() {
        View view = root.get();
        root.clear();
        if (view == null) return;
        if (Math.abs(view.getScaleX() - appliedX) < .0001f) view.setScaleX(originalX);
        if (Math.abs(view.getScaleY() - appliedY) < .0001f) view.setScaleY(originalY);
    }
}
