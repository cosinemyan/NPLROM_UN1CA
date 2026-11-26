// Native port of Dressroom-Advanced by @BlueOutside. See upstream LICENSE.
package dev.denver.dressroomadvanced;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

final class ObjectCaptureBackend {
    private static final String SDK = "com.samsung.android.app.sdk.deepsky.objectcapture.";

    private ObjectCaptureBackend() {}

    static boolean available(ClassLoader loader) {
        for (String name :
                new String[] {"impl.SribObjectCaptureImpl", "impl.ArcSoftObjectCaptureImpl"}) {
            try {
                Class.forName(SDK + name, false, loader).getConstructor(Context.class);
                return true;
            } catch (ReflectiveOperationException | LinkageError ignored) {
            }
        }
        try {
            Class.forName(SDK + "ObjectCaptureProvider", false, loader)
                    .getMethod("getInstance", Context.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    static Object open(Context context, ClassLoader loader) throws Exception {
        List<Callable<?>> factories = new ArrayList<>();
        factories.add(
                () ->
                        Class.forName(SDK + "ObjectCaptureProvider", false, loader)
                                .getMethod("getInstance", Context.class)
                                .invoke(null, context));
        for (String name :
                new String[] {"impl.SribObjectCaptureImpl", "impl.ArcSoftObjectCaptureImpl"}) {
            factories.add(
                    () ->
                            Class.forName(SDK + name, false, loader)
                                    .getConstructor(Context.class)
                                    .newInstance(context));
        }
        Object detector =
                firstUsable(factories, new ArrayList<>(), ObjectCaptureBackend::tryInitArcSoft);
        android.util.Log.i(
                "Dressroom-Advanced",
                "Subject detector selected: " + detector.getClass().getSimpleName());
        return detector;
    }

    static Object firstUsable(Iterable<? extends Callable<?>> factories, List<Object> failed)
            throws Exception {
        return firstUsable(factories, failed, detector -> false);
    }

    static Object firstUsable(
            Iterable<? extends Callable<?>> factories,
            List<Object> failed,
            java.util.function.Predicate<Object> recover)
            throws Exception {
        IllegalStateException failure =
                new IllegalStateException("No Samsung subject detector initialized");
        java.util.Set<Class<?>> attempted = new java.util.HashSet<>();
        List<String> reasons = new ArrayList<>();
        for (Callable<?> factory : factories) {
            Object detector = null;
            try {
                detector = factory.call();
                if (detector == null
                        || detector.getClass().getSimpleName().equals("StubObjectCaptureImpl")
                        || !attempted.add(detector.getClass())) continue;
                // Support-report APIs contain model/SoC policy gates. Native init is the
                // authority here; do not fake successful initialization or touch its state.
                Reflect.call(detector, "init");
                return detector;
            } catch (Exception | LinkageError error) {
                Throwable cause = error;
                while (cause instanceof ReflectiveOperationException && cause.getCause() != null)
                    cause = cause.getCause();
                String backend =
                        detector == null
                                ? "SDK factory"
                                : detector.getClass()
                                        .getSimpleName()
                                        .replace("ObjectCaptureImpl", "");
                String detail = cause.getMessage();
                if (detail == null || detail.isBlank()) detail = cause.getClass().getSimpleName();
                reasons.add(backend + ": " + detail);
                failure.addSuppressed(cause);
                if (detector != null) {
                    // Preserve the established ArcSoft recovery before loading another native
                    // backend.
                    if (recover.test(detector)) return detector;
                    failed.add(detector);
                    try {
                        Reflect.call(detector, "release");
                    } catch (Exception | LinkageError cleanup) {
                        failure.addSuppressed(cleanup);
                    }
                }
            }
        }
        if (!reasons.isEmpty())
            throw new IllegalStateException(
                    failure.getMessage() + ". " + String.join("; ", reasons), failure);
        throw failure;
    }

    private static boolean tryInitArcSoft(Object detector) {
        if (detector == null || !detector.getClass().getName().contains("ArcSoft")) return false;
        try {
            Object wrapper = null;
            try {
                wrapper = Reflect.call(detector, "getInstance");
            } catch (Exception ignored) {
                try {
                    Object delegate = Reflect.field(detector, "instance$delegate");
                    if (delegate != null) wrapper = Reflect.call(delegate, "getValue");
                } catch (Exception ignored2) {
                }
            }
            if (wrapper == null) return false;
            Object jniInstance = Reflect.field(wrapper, "instance");
            if (jniInstance == null) return false;
            Object lock;
            try {
                java.lang.reflect.Field lockField =
                        detector.getClass().getDeclaredField("CLASS_LOCK");
                lockField.setAccessible(true);
                lock = lockField.get(null);
            } catch (NoSuchFieldException missingOnOlderSdk) {
                lock = detector.getClass();
            }
            synchronized (lock) {
                int[] platformIds = {5, 0, 4, 1, 2, 6};
                for (int id : platformIds) {
                    try {
                        Object ret = Reflect.call(jniInstance, "OBJECT_CAPTURE_Init", id);
                        if (ret instanceof Number n && n.intValue() == 0) {
                            java.lang.reflect.Field initStateField =
                                    detector.getClass().getDeclaredField("IS_INIT_STATE");
                            initStateField.setAccessible(true);
                            java.util.concurrent.atomic.AtomicBoolean initState =
                                    (java.util.concurrent.atomic.AtomicBoolean)
                                            initStateField.get(null);
                            if (initState != null) initState.set(true);
                            android.util.Log.i(
                                    "Dressroom-Advanced",
                                    "ArcSoft object capture initialized with fallback platformId="
                                            + id);
                            return true;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception | LinkageError error) {
            android.util.Log.w(
                    "Dressroom-Advanced", "ArcSoft fallback initialization failed", error);
        }
        return false;
    }
}
