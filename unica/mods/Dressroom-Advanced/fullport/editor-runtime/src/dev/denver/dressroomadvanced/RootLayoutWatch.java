// Native adaptation of BlueOutside / Dressroom Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

import android.view.View;

import java.lang.ref.WeakReference;

final class RootLayoutWatch {
    private WeakReference<View> root = new WeakReference<>(null);
    private View.OnLayoutChangeListener listener;

    void bind(View view, View.OnLayoutChangeListener listener) {
        clear();
        root = new WeakReference<>(view);
        this.listener = listener;
        view.addOnLayoutChangeListener(listener);
    }

    void clear() {
        View view = root.get();
        if (view != null && listener != null) view.removeOnLayoutChangeListener(listener);
        root.clear();
        listener = null;
    }
}
