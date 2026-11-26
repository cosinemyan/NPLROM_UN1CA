package dev.denver.dressroomadvanced;

final class PlatformGate {
    private static Boolean supported;

    private PlatformGate() {}

    static synchronized boolean supported() {
        if (supported == null) supported = compute();
        return supported;
    }

    static String description() {
        int oneUi = oneUiVersion();
        return "Android "
                + android.os.Build.VERSION.RELEASE
                + " (API "
                + android.os.Build.VERSION.SDK_INT
                + "), One UI "
                + (oneUi > 0 ? String.valueOf(oneUi) : "unknown");
    }

    private static boolean compute() {
        int sdk = android.os.Build.VERSION.SDK_INT;
        if (sdk < 36 || sdk > 37) return false;
        int oneUi = oneUiVersion();
        if (oneUi <= 0) return true;
        int major = oneUi / 10000;
        return (major == 8 && oneUi >= 80500) || (major == 9 && oneUi >= 90000);
    }

    static int oneUiVersion() {
        try {
            Object value =
                    Class.forName("android.os.SystemProperties")
                            .getMethod("get", String.class)
                            .invoke(null, "ro.build.version.oneui");
            String text = String.valueOf(value).trim();
            return text.isEmpty() ? -1 : Integer.parseInt(text);
        } catch (Exception | LinkageError ignored) {
            return -1;
        }
    }
}
