package io.mesalabs.rezoss.memory;

/** Pure policy shared by the launcher bridge and host tests. */
public final class GesturePolicy {
    public static final int NONE = 0, SCREEN = 1, VOICE = 2, CAMERA = 3;
    private GesturePolicy() {}

    public static boolean enabled(boolean memory, boolean homeUp, boolean secureHomeUp) {
        return memory && !homeUp && !secureHomeUp;
    }

    public static int action(boolean threeFingers, String direction, boolean repeat) {
        if (!threeFingers || repeat) return NONE;
        if ("SWIPE_DOWN".equals(direction)) return SCREEN;
        if ("SWIPE_RIGHT".equals(direction)) return VOICE;
        if ("SWIPE_LEFT".equals(direction)) return CAMERA;
        return NONE;
    }

    public static final class Session {
        private boolean owned, cancelled, fired;
        public void begin(boolean eligible) {
            owned = eligible;
            cancelled = false;
            fired = false;
        }
        public void observe(int pointers, boolean cancel) {
            if (pointers > 3 || cancel) cancelled = true;
        }
        public boolean owns() { return owned; }
        public int claim(boolean enabledNow, boolean threeFingers, String direction, boolean repeat) {
            if (!owned || cancelled || fired || !enabledNow) return NONE;
            int result = action(threeFingers, direction, repeat);
            if (result != NONE) fired = true;
            return result;
        }
    }
}
