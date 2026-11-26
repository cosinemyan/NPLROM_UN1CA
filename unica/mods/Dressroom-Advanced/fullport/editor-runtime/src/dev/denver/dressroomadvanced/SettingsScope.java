package dev.denver.dressroomadvanced;

final class SettingsScope {
    private SettingsScope() {}

    static String key(int wallpaper, int type, int font) {
        return "wallpaper_" + wallpaper + "_" + type + "_" + font;
    }

    static int orientation(int value) {
        return value == 1 || value == 2 ? value : 0;
    }

    static int orientation(int width, int height) {
        return width <= 0 || height <= 0 || width == height ? 0 : width > height ? 2 : 1;
    }

    static String oriented(String key, int orientation) {
        return switch (orientation(orientation)) {
            case 1 -> key + "_portrait";
            case 2 -> key + "_landscape";
            default -> key;
        };
    }

    static boolean canImportLegacy(int wallpaper, boolean alreadyImported) {
        return wallpaper >= 0 && !alreadyImported;
    }

    static boolean sceneMatches(String requested, String stored) {
        return requested.isEmpty() || stored.isEmpty() || requested.equals(stored);
    }
}
