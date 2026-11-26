// Native port of Dressroom-Advanced by @BlueOutside. See upstream LICENSE.
package dev.denver.dressroomadvanced;

final class DepthSourcePolicy {
    static boolean visible(boolean interactive, boolean aodWallpaper) {
        return interactive || aodWallpaper;
    }

    private String key;

    boolean observe(String next) {
        boolean changed = key != null && !key.equals(next);
        key = next;
        return changed;
    }

    void reset() {
        key = null;
    }

    String key() {
        return key;
    }

    static boolean invalidProfile(
            boolean known, boolean image, boolean reset, String currentKey, String savedKey) {
        if (known && !image) return true;
        boolean stamped = savedKey != null && !savedKey.isEmpty();
        boolean current = known && image && stamped && savedKey.equals(currentKey);
        if (known && stamped && !current) return true;
        return reset && !current;
    }

    static boolean image(int type, int parent) {
        return still(type) && (parent <= 0 || still(parent));
    }

    private static boolean still(int type) {
        return type == 0 || type == 19 || type == 23 || type == 401 || type == 402 || type == 408;
    }
}
