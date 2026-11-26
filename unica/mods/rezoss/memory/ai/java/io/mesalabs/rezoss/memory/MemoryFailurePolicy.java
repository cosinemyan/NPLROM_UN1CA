/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.rezoss.memory;
import java.util.*;
import java.util.concurrent.CancellationException;
public final class MemoryFailurePolicy {
    /** Restrict fallback to transport/auth availability; never retry refusal or cancellation. */
    public static boolean eligible(Throwable error) {
        boolean retryable = false;
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable e = error; e != null && seen.add(e); e = e.getCause()) {
            boolean timeout = "kotlinx.coroutines.TimeoutCancellationException".equals(e.getClass().getName());
            if ((e instanceof CancellationException && !timeout) || e instanceof InterruptedException) return false;
            String m = (e.getClass().getName() + " " + String.valueOf(e.getMessage())).toLowerCase(Locale.ROOT);
            if (m.contains("refus") || m.contains("safety") || m.contains("blocked") ||
                    m.contains("content_filter") || (m.contains("cancel") && !timeout)) return false;
            retryable |= m.contains("unauthenticated") || m.contains("invalid_grant") ||
                    m.contains("invalid jwt") || m.contains("401 unauthorized") || m.contains("auth_error") || m.contains("authentication") ||
                    m.contains("800010") || m.contains("service_unavailable") ||
                    m.contains("service unavailable") || m.contains("service not available") ||
                    m.contains("service not found") || m.contains("bind service") ||
                    m.contains("timeout") || m.contains("timed out");
        }
        return retryable;
    }

    /** Preserve classified Vertex errors before the delegate discards their cause. */
    public static void throwIfEligible(Throwable error) throws Throwable {
        if (eligible(error)) throw error;
    }

}
