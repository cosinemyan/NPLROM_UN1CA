// SPDX-License-Identifier: GPL-3.0-or-later
package io.mesalabs.rezoss.memory;

/** Own a native chord from its START through its completion/cancellation. */
public final class SideKeyPolicy {
    public static final int STOCK = 0, CONSUME = 1, LAUNCH = 2;
    private boolean active;
    private boolean owned;

    public int event(int action, boolean eligible) {
        if (action == 1) {
            if (active) return owned ? CONSUME : STOCK;
            active = true;
            owned = eligible;
            return owned ? LAUNCH : STOCK;
        }
        if (action == 2) {
            boolean consume = active && owned;
            active = false;
            owned = false;
            return consume ? CONSUME : STOCK;
        }
        return STOCK;
    }
}
