// Native adaptation of BlueOutside / Dressroom-Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

/** Gesture policy shared by Clock Advanced and Widgets/Extras Advanced. */
final class OutsidePanelTap {
    private boolean downOutside;
    private float startX, startY;
    void reset() { downOutside = false; }
    boolean event(int action, float x, float y, boolean inside, boolean childOwnsTouch, float slop) {
        if (childOwnsTouch) { reset(); return false; }
        if (action == 4) { reset(); return !inside; } // ACTION_OUTSIDE has no UP.
        if (action == 0) {
            downOutside = !inside; startX = x; startY = y;
        } else if (action == 2) {
            if (Math.hypot(x-startX, y-startY) > slop) reset();
        } else if (action == 1) {
            boolean close = downOutside && !inside; reset(); return close;
        } else if (action == 3 || action == 5) reset(); // CANCEL / POINTER_DOWN.
        return false;
    }
}
