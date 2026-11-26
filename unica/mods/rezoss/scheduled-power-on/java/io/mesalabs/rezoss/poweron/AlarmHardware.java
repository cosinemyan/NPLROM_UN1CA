// SPDX-License-Identifier: GPL-3.0-or-later
package io.mesalabs.rezoss.poweron;
import java.io.IOException;
final class AlarmHardware {
    static { System.loadLibrary("rezoss_poweron"); }
    static synchronized native long[] snapshot() throws IOException;
    static synchronized native long program(long epochSeconds) throws IOException;
    private AlarmHardware() {}
}
