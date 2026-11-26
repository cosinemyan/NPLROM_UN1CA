package dev.denver.dressroomadvanced;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

final class IdentityLocks {
    private static final class Entry {
        final WeakReference<Object> key;
        final List<Float> heights;

        Entry(Object key, List<Float> heights) {
            this.key = new WeakReference<>(key);
            this.heights = new ArrayList<>(heights);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    synchronized List<Float> get(Object key) {
        entries.removeIf(e -> e.key.get() == null);
        for (Entry e : entries) if (e.key.get() == key) return new ArrayList<>(e.heights);
        return null;
    }

    synchronized void put(Object key, List<Float> heights) {
        remove(key);
        entries.add(new Entry(key, heights));
    }

    synchronized void remove(Object key) {
        entries.removeIf(e -> e.key.get() == null || e.key.get() == key);
    }

    synchronized void clear() {
        entries.clear();
    }
}
