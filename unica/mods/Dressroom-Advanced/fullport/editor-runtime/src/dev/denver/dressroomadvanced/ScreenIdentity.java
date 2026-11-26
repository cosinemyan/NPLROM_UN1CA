package dev.denver.dressroomadvanced;

import android.os.Bundle;
import java.lang.reflect.Method;

/** Fold screen identity is independent of Android display number and orientation. */
final class ScreenIdentity {
    static final String FIELD = "layoutScreen";
    static final int MAIN = 1, SUB = 2;
    private static FoldStateMonitor monitor;
    private static Boolean foldable;
    private static final java.util.List<Runnable> observers = new java.util.concurrent.CopyOnWriteArrayList<>();

    private ScreenIdentity() {}

    static boolean supported(int sdk, int oneUi, String policy) {
        return sdk >= 36 && sdk <= 37 && oneUi >= 90000 && oneUi < 100000
                && policy != null && (policy.contains("LOCKSCREEN") || policy.contains("COVER"));
    }

    static synchronized boolean supported() {
        if (foldable != null) return foldable;
        try {
            Class<?> features = Class.forName("com.samsung.android.feature.SemFloatingFeature");
            Object owner = features.getMethod("getInstance").invoke(null);
            String policy = (String) features.getMethod("getString", String.class)
                    .invoke(owner, "SEC_FLOATING_FEATURE_LOCKSCREEN_CONFIG_SUBDISPLAY_POLICY");
            return foldable = supported(android.os.Build.VERSION.SDK_INT, PlatformGate.oneUiVersion(), policy);
        } catch (ReflectiveOperationException | RuntimeException ignored) { return foldable = false; }
    }

    static int wallpaperWhich(int screen, boolean lock) {
        return (lock ? 2 : 1) | (screen == SUB ? 16 : screen == MAIN ? 4 : 0);
    }

    static synchronized int current() {
        if (!supported()) return 0;
        try {
            if (monitor == null) monitor = createMonitor();
            return monitor.current();
        } catch (Exception error) {
            throw new IllegalStateException("Fold screen identity unavailable", error);
        }
    }

    private static FoldStateMonitor createMonitor() throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.samsung.android.view.SemWindowManager");
        Object manager = type.getMethod("getInstance").invoke(null);
        Method folded = type.getMethod("isFolded");
        Class<?> callback = Class.forName("com.samsung.android.view.SemWindowManager$FoldStateListener");
        Method register = type.getMethod("registerFoldStateListener", callback, android.os.Handler.class);
        return new FoldStateMonitor(new FoldStateMonitor.Backend() {
            // Retain the successful listener for the lifetime of this process.
            private Object listener;
            public boolean folded() throws ReflectiveOperationException {
                return Boolean.TRUE.equals(folded.invoke(manager));
            }
            public void register(java.util.function.Consumer<Boolean> changed) throws ReflectiveOperationException {
                Object candidate = java.lang.reflect.Proxy.newProxyInstance(callback.getClassLoader(),
                        new Class<?>[] {callback}, (p, method, args) -> {
                            if (method.getName().equals("onFoldStateChanged")) changed.accept(Boolean.TRUE.equals(args[0]));
                            else if (method.getName().equals("hashCode")) return System.identityHashCode(p);
                            else if (method.getName().equals("equals")) return p == args[0];
                            else if (method.getName().equals("toString")) return "DressroomFoldListener";
                            return null;
                        });
                register.invoke(manager, candidate, new android.os.Handler(android.os.Looper.getMainLooper()));
                listener = candidate;
            }
        }, () -> { for (Runnable observer : observers) observer.run(); });
    }

    static void observe(Runnable observer) {
        if (supported()) { current(); observers.add(observer); }
    }

    static int value(Bundle data) {
        int value = data.getInt(FIELD);
        return value == MAIN || value == SUB ? value : 0;
    }

    static void stamp(Bundle data) {
        if (!data.containsKey(FIELD)) {
            int screen = current();
            if (screen != 0) data.putInt(FIELD, screen);
        }
    }

    static void copy(Bundle source, Bundle target) {
        if (source.containsKey(FIELD)) target.putInt(FIELD, value(source));
    }

    static String key(String base, Bundle data) {
        return switch (value(data)) {
            case MAIN -> base + "_fold_main";
            case SUB -> base + "_fold_sub";
            default -> base;
        };
    }

    static String draft(Bundle data) {
        return data.getInt(LayoutOrientation.FIELD) + ":" + value(data);
    }
}
