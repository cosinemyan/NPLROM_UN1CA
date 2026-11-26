// Native port of Dressroom-Advanced by @BlueOutside. See upstream LICENSE.
package dev.denver.dressroomadvanced;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToLongFunction;

/** ArcSoft has one native engine per JNI object, despite the SDK's class-wide ready flag. */
final class CaptureInstanceState<T> {
    private final AtomicBoolean ready;
    private final ToLongFunction<T> handle;
    private final Set<T> owners = Collections.newSetFromMap(new WeakHashMap<>());

    CaptureInstanceState(AtomicBoolean ready, ToLongFunction<T> handle) {
        this.ready = ready;
        this.handle = handle;
    }

    void prepare(T owner) {
        ready.set(handle.applyAsLong(owner) != 0);
    }

    void opened(T owner) {
        if (handle.applyAsLong(owner) == 0)
            throw new IllegalStateException("ArcSoft initialized without a native engine");
        owners.add(owner);
    }

    void closed(T owner) {
        owners.remove(owner);
    }

    void restore() {
        ready.set(!owners.isEmpty());
    }
}
