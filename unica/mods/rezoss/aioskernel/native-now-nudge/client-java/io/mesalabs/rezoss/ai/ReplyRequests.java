package io.mesalabs.rezoss.ai;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Owns Samsung resolver resources by result identity, never by the latest request. */
public final class ReplyRequests {
    private static final Object LOCK = new Object();
    private static final Map<AtomicReference<?>, State> requests = new IdentityHashMap<>();
    private static final Map<Object, Integer> memoryLeases = new IdentityHashMap<>();
    private static volatile Method scopeActive;

    private static final class State {
        final CountDownLatch latch;
        final Object scope;
        Object processor, memory;
        boolean cancelled, completed, finished;
        State(CountDownLatch latch, Object scope) { this.latch = latch; this.scope = scope; }
    }

    private ReplyRequests() {}

    public static void begin(AtomicReference<?> result, CountDownLatch latch, Object scope) {
        synchronized (LOCK) {
            if (requests.containsKey(result)) throw new IllegalStateException("Duplicate reply request");
            requests.put(result, new State(latch, scope));
        }
    }

    private static boolean active(Object scope) {
        if (scope == null) return true;
        try {
            Method method = scopeActive;
            if (method == null) {
                // Exact public coroutine predicate used by the inspected Samsung resolver.
                method = Class.forName("kotlinx.coroutines.d").getMethod("z", Class.forName("kotlinx.coroutines.x"));
                scopeActive = method;
            }
            return (Boolean) method.invoke(null, scope);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot check reply coroutine ownership", e);
        }
    }

    public static void processor(AtomicReference<?> result, Object processor) {
        synchronized (LOCK) { require(result).processor = processor; }
    }

    public static Object processor(AtomicReference<?> result) {
        synchronized (LOCK) {
            State state = require(result);
            if (!active(state.scope)) cancel(state);
            return state.cancelled ? null : state.processor;
        }
    }

    public static void memory(AtomicReference<?> result, Object memory) {
        synchronized (LOCK) {
            State state = require(result);
            if (state.memory != null) throw new IllegalStateException("Duplicate reply memory lease");
            state.memory = memory;
            Integer count = memoryLeases.get(memory);
            memoryLeases.put(memory, count == null ? 1 : count + 1);
        }
    }

    public static CountDownLatch latch(AtomicReference<?> result) {
        synchronized (LOCK) {
            State state = requests.get(result);
            return state == null || state.cancelled ? null : state.latch;
        }
    }

    public static void await(AtomicReference<?> result, long seconds) throws InterruptedException {
        State state;
        synchronized (LOCK) { state = require(result); }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        try {
            for (;;) {
                synchronized (LOCK) {
                    if (!active(state.scope)) cancel(state);
                    if (state.cancelled || state.finished || state.completed) return;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return;
                state.latch.await(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS);
            }
        } catch (InterruptedException e) {
            synchronized (LOCK) { cancel(state); }
            throw e;
        }
    }

    public static void publish(AtomicReference<Object> result, Object value) {
        synchronized (LOCK) {
            State state = requests.get(result);
            if (state == null || state.finished || state.completed || state.cancelled) return;
            if (!active(state.scope)) { cancel(state); return; }
            result.set(value);
            state.completed = true;
            state.latch.countDown();
        }
    }

    /** Called on success, timeout and exceptional unwind; late callbacks are ignored. */
    public static CountDownLatch finish(AtomicReference<?> result) {
        State state;
        synchronized (LOCK) {
            state = requests.remove(result);
            if (state == null) return null;
            state.finished = true;
            // Cancellation at the completion boundary must not deliver a stale reply.
            try { if (!active(state.scope)) cancel(state); }
            catch (RuntimeException e) { cancel(state); System.err.println("RezossReplyRequests: " + e); }
            state.latch.countDown();
        }
        try { release(state.processor, "close"); }
        finally {
            synchronized (LOCK) {
                if (state.memory != null) {
                    int count = memoryLeases.get(state.memory) - 1;
                    if (count == 0) {
                        memoryLeases.remove(state.memory);
                        release(state.memory, "release");
                    } else memoryLeases.put(state.memory, count);
                }
            }
        }
        return state.cancelled ? null : state.latch;
    }

    private static State require(AtomicReference<?> result) {
        State state = requests.get(result);
        if (state == null) throw new IllegalStateException("Unknown reply request");
        return state;
    }

    private static void cancel(State state) { state.cancelled = true; state.latch.countDown(); }

    private static void release(Object resource, String method) {
        if (resource == null) return;
        try { resource.getClass().getMethod(method).invoke(resource); }
        catch (ReflectiveOperationException e) { System.err.println("RezossReplyRequests: " + method + " failed: " + e); }
    }
}
