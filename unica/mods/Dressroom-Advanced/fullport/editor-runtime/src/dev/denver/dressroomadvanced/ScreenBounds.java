// Native adaptation of BlueOutside / Dressroom-Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

import android.graphics.Rect;
import android.view.View;

/** MotionEvent raw coordinates include the floating window's screen origin. */
final class ScreenBounds {
    static boolean visible(View view, Rect result) {
        if (!view.isShown() || !view.getGlobalVisibleRect(result)) return false;
        int[] origin = new int[2];
        view.getRootView().getLocationOnScreen(origin);
        result.offset(origin[0], origin[1]);
        return !result.isEmpty();
    }
}
