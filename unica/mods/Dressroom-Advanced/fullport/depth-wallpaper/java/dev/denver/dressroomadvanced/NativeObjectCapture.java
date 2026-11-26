package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Fixed direct SDK callbacks preserving upstream per-instance/shared engine ownership. */
public final class NativeObjectCapture {
    private static final ClassValue<State> states = new ClassValue<>() {
        @Override protected State computeValue(Class<?> type) {
            try { return new State(type); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
    };
    private static final class State {
        final Object lock;
        final CaptureInstanceState<Object> instance;
        final Set<Object> owners = Collections.newSetFromMap(new WeakHashMap<>());
        State(Class<?> type) throws ReflectiveOperationException {
            Field field = type.getDeclaredField("CLASS_LOCK");
            field.setAccessible(true);
            lock = field.get(null);
            if (type.getSimpleName().equals("ArcSoftObjectCaptureImpl")) {
                ArcSoftEngineBinding binding = new ArcSoftEngineBinding(type);
                instance = new CaptureInstanceState<>(binding.ready, binding::handle);
            } else instance = null;
        }
    }
    private NativeObjectCapture() {}

    public static void init(Object owner) {
        State state = states.get(owner.getClass());
        synchronized (state.lock) {
            if (state.instance != null) state.instance.prepare(owner);
            try {
                original(owner, "nativeDepthOriginalInit");
                if (state.instance != null) state.instance.opened(owner); else state.owners.add(owner);
            } finally { if (state.instance != null) state.instance.restore(); }
        }
    }

    public static void release(Object owner) {
        State state = states.get(owner.getClass());
        synchronized (state.lock) {
            if (state.instance != null) {
                state.instance.prepare(owner);
                try { original(owner, "nativeDepthOriginalRelease"); }
                finally { state.instance.closed(owner); state.instance.restore(); }
            } else {
                state.owners.remove(owner);
                if (state.owners.isEmpty()) original(owner, "nativeDepthOriginalRelease");
            }
        }
    }

    public static Object capture(Object owner, Bitmap bitmap) {
        State state = states.get(owner.getClass());
        synchronized (state.lock) {
            init(owner);
            return original(owner, "nativeDepthOriginalCapture", bitmap);
        }
    }

    private static Object original(Object owner, String name, Object... arguments) {
        try { return Reflect.call(owner, name, arguments); }
        catch (ReflectiveOperationException failure) {
            Throwable cause = failure instanceof InvocationTargetException ? failure.getCause() : failure;
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Samsung subject detector failed", cause);
        }
    }
}
