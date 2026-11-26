package dev.denver.dressroomadvanced;

import java.util.function.Consumer;

/** A failed subscription must not turn a one-time state sample into permanent screen identity. */
final class FoldStateMonitor {
    interface Backend {
        boolean folded() throws Exception;
        void register(Consumer<Boolean> callback) throws Exception;
    }
    private final Backend backend;
    private final Runnable changed;
    private boolean registered;
    private int screen;
    FoldStateMonitor(Backend backend, Runnable changed) {
        this.backend = backend;
        this.changed = changed;
    }
    synchronized int current() throws Exception {
        if (!registered) {
            screen = backend.folded() ? 2 : 1;
            backend.register(this::changed);
            registered = true;
        }
        return screen;
    }
    private synchronized void changed(boolean folded) {
        int next = folded ? 2 : 1;
        if (screen == next) return;
        screen = next;
        changed.run();
    }
}
