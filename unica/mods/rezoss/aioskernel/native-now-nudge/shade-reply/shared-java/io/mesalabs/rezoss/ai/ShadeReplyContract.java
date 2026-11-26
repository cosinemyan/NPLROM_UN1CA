package io.mesalabs.rezoss.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared, Android-independent notification reply identity and session rules. */
public final class ShadeReplyContract {
    public static final String PREFIX = "rezoss.shade.";
    public static final String KEY = PREFIX + "key";
    public static final String PACKAGE = PREFIX + "package";
    public static final String FINGERPRINT = PREFIX + "fingerprint";
    public static final String TOKEN = PREFIX + "token";
    public static final String BLOCKED = PREFIX + "blocked";
    public static final String BRIDGE = PREFIX + "bridge";
    public static final String STARTED = PREFIX + "started";
    public static final String PERMISSION = "com.samsung.android.permission.SUGGESTED_REPLIES";
    private static final long TTL = 60_000_000_000L;
    private ShadeReplyContract() {}

    public static String fingerprint(String text, long timestampMillis) {
        if (text == null || text.isEmpty() || text.length() > 32768 || timestampMillis <= 0) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(text.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Long.toString(timestampMillis).getBytes(StandardCharsets.US_ASCII));
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { return null; }
    }

    private static boolean clean(String s, int max) {
        if (s == null || s.isEmpty() || s.length() > max) return false;
        for (int i = 0; i < s.length(); i++) if (Character.isISOControl(s.charAt(i))) return false;
        return true;
    }

    /** Android owns the tag segment: preserve it verbatim, including newlines and pipes. */
    public static String packageFromKey(String key) {
        if (key == null || key.isEmpty() || key.length() > 2048) return null;
        int first = key.indexOf('|'), second = key.indexOf('|', first + 1);
        int third = key.indexOf('|', second + 1), last = key.lastIndexOf('|');
        if (first <= 0 || second <= first + 1 || third <= second + 1 || last <= third) return null;
        String pkg = key.substring(first + 1, second);
        if (!key.substring(0, first).matches("-?[0-9]{1,10}")
                || !clean(pkg, 255) || !pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
                || !key.substring(second + 1, third).matches("-?[0-9]{1,11}")
                || !key.substring(last + 1).matches("[0-9]{1,10}")) return null;
        return pkg;
    }

    public static boolean valid(String key, String pkg, String fp) {
        return pkg != null && pkg.equals(packageFromKey(key))
                && fp != null && fp.matches("[0-9a-f]{64}");
    }

    /** Call from one thread. Times must use the same monotonic nanosecond clock. */
    public static final class State {
        private static final class Entry {
            long started;
            List<String> replies;
            Entry(long started, List<String> replies) { this.started = started; this.replies = replies; }
        }
        private final Map<String, Entry> cache = new LinkedHashMap<>();
        private final Map<String, Entry> generations = new LinkedHashMap<>();
        private final Map<String, String> generationFingerprints = new LinkedHashMap<>();
        private final Set<String> blocked = new LinkedHashSet<>();
        private String active;
        private String token;
        private long cutoff = Long.MIN_VALUE;
        private boolean disabled;
        private static String prefix(String key) { return key == null ? "null:" : key.length() + ":" + key + "\n"; }
        private static String identity(String key, String fp) { return prefix(key) + fp; }
        private static boolean fresh(long started, long now) { return started > 0 && now >= started && now - started <= TTL; }

        public void open(String key, String pkg, String fp, String token, long nowNanos) {
            if (active != null && (!active.equals(identity(key, fp)) || !java.util.Objects.equals(this.token, token))) {
                close(nowNanos);
            }
            active = null;
            this.token = null;
            if (disabled || !valid(key, pkg, fp) || !clean(token, 256) || nowNanos <= 0) {
                close(nowNanos);
                return;
            }
            active = identity(key, fp);
            this.token = token;
        }
        public void close(long nowNanos) {
            active = null;
            token = null;
            cutoff = Math.max(cutoff, nowNanos);
            cache.clear();
            generations.clear();
            generationFingerprints.clear();
        }
        public void typed(long nowNanos) {
            if (active == null) return;
            blocked.add(active);
            cache.remove(active);
            if (blocked.size() > 128) { disabled = true; cache.clear(); active = null; token = null; }
        }
        public boolean receive(String key, String pkg, String fp, long startedNanos, List<String> replies, long nowNanos) {
            if (disabled || !valid(key, pkg, fp) || replies == null || !fresh(startedNanos, nowNanos)
                    || startedNanos <= cutoff) return false;
            String id = identity(key, fp);
            Entry generation = generations.get(key);
            if (generation != null && (generation.started > startedNanos
                    || (generation.started == startedNanos && !fp.equals(generationFingerprints.get(key))))) return false;
            Entry old = cache.get(id);
            if (old != null && old.started > startedNanos) return false;
            // A duplicate pending event must not erase an already completed result.
            if (old != null && old.started == startedNanos && old.replies != null) return false;
            List<String> safe = null;
            if (!replies.isEmpty()) {
                if (replies.size() > 3) return false;
                ArrayList<String> copy = new ArrayList<>();
                for (String reply : replies) {
                    if (!clean(reply, 1024) || reply.trim().isEmpty()) return false;
                    copy.add(reply);
                }
                safe = Collections.unmodifiableList(copy);
            }
            // Never evict a live generation watermark: doing so could admit an older completion.
            if (old == null && generation == null && cache.size() >= 16) {
                cache.entrySet().removeIf(e -> !fresh(e.getValue().started, nowNanos));
                if (cache.size() >= 16) return false;
            }
            if (generation == null && generations.size() >= 16) {
                java.util.Iterator<Map.Entry<String, Entry>> it = generations.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Entry> item = it.next();
                    if (!fresh(item.getValue().started, nowNanos)) {
                        String expiredKey = item.getKey();
                        it.remove();
                        generationFingerprints.remove(expiredKey);
                        cache.keySet().removeIf(cached -> cached.startsWith(prefix(expiredKey)));
                    }
                }
                if (generations.size() >= 16) return false;
            }
            if (generation == null || generation.started < startedNanos) {
                cache.keySet().removeIf(cached -> cached.startsWith(prefix(key)));
            }
            generations.put(key, new Entry(startedNanos, null));
            generationFingerprints.put(key, fp);
            if (blocked.contains(id)) return false;
            cache.put(id, new Entry(startedNanos, safe));
            return true;
        }
        public List<String> current(long nowNanos) {
            if (disabled || active == null || token == null || blocked.contains(active)) return Collections.emptyList();
            Entry entry = cache.get(active);
            return entry != null && entry.replies != null && fresh(entry.started, nowNanos)
                    ? entry.replies : Collections.emptyList();
        }
    }
}
