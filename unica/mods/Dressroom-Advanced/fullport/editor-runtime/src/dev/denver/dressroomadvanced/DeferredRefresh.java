// Native adaptation of BlueOutside / Dressroom Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

final class DeferredRefresh<T> {
    private static final class Ticket {
        boolean force;

        Ticket(boolean force) {
            this.force = force;
        }
    }

    private final Map<T, Ticket> pending = new WeakHashMap<>();
    private final Executor executor;
    private final BiConsumer<T, Boolean> refresh;

    DeferredRefresh(Executor executor, BiConsumer<T, Boolean> refresh) {
        this.executor = executor;
        this.refresh = refresh;
    }

    void offer(T owner, boolean force) {
        Ticket existing = pending.get(owner);
        if (existing != null) {
            existing.force |= force;
            return;
        }
        Ticket ticket = new Ticket(force);
        pending.put(owner, ticket);
        WeakReference<T> reference = new WeakReference<>(owner);
        executor.execute(
                () -> {
                    T value = reference.get();
                    if (value == null || pending.get(value) != ticket) return;
                    pending.remove(value);
                    refresh.accept(value, ticket.force);
                });
    }

    void cancel(T owner) {
        pending.remove(owner);
    }
}
