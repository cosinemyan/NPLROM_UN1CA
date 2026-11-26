package dev.denver.dressroomadvanced;

/** A saved shape cannot grant a font a capability or cross font-file selections. */
final class StretchRestorePolicy {
    private StretchRestorePolicy() {}

    static boolean eligible(boolean samsungStretchFont, int selectedFont, int savedFont,
            String selectedPath, String savedPath, boolean externalFont) {
        return samsungStretchFont && !externalFont && selectedFont == savedFont
                && java.util.Objects.equals(selectedPath, savedPath);
    }
}
