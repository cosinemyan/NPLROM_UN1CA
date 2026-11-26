// SPDX-License-Identifier: GPL-3.0-or-later
package io.mesalabs.rezoss.poweron;

final class ScheduleRules {
    static final long WEEK = 7 * 86400L;
    static final long MIN_LEAD = 5 * 60L;
    static final long MIN_GAP = 3 * 60L;
    static void validate(long on, long off, long now) {
        validate(on,off,now,MIN_LEAD);
    }
    // Already saved events may be restored close to their due time. Keep the
    // existing two-minute driver margin; the five-minute rule governs NEW saves.
    static void validateRestored(long on,long off,long now) {
        validate(on,off,now,120);
    }
    private static void validate(long on,long off,long now,long lead) {
        if (on < 1577836800L || on > 4102444740L || now < 1577836800L || now > 4102444740L)
            throw new IllegalArgumentException("Correct the phone's date and time first.");
        if (on % 60 != 0 || on - now < lead || on - now > WEEK)
            throw new IllegalArgumentException("Choose a power-on time at least "+(lead/60)+" minutes ahead, within 7 days.");
        if (off != 0 && (off < 1577836800L || off > 4102444740L
                || off % 60 != 0 || off - now < lead || on - off < MIN_GAP))
            throw new IllegalArgumentException("Choose power off at least "+(lead/60)+" minutes ahead, and power on at least 3 minutes after power off.");
    }
    static long pickerMinimum(boolean powerOn,boolean separate,long off,long now) {
        long minimum=now+MIN_LEAD;
        if (powerOn && separate) minimum=Math.max(minimum,off+MIN_GAP);
        return (minimum+59)/60*60; // Picker precision is one minute; round UP.
    }
    static void validateSelection(long selected,boolean powerOn,boolean separate,long off,long now) {
        long minimum=pickerMinimum(powerOn,separate,off,now);
        long maximum=now+WEEK-(powerOn?0:MIN_GAP);
        if (selected<minimum || selected>maximum || selected%60!=0)
            throw new IllegalArgumentException(powerOn&&separate?
                    "Power on must be at least 5 minutes ahead and at least 3 minutes after power off.":
                    "Choose a time at least 5 minutes ahead, within 7 days.");
    }
    static boolean mayShutdown(ScheduleEngine.State state, String token, long now, long rtc) {
        return !state.uncertain && !state.token.isEmpty() && state.token.equals(token)
                && state.off > 0 && now >= state.off && now - state.off <= 60
                && state.on - now >= 120 && state.on - now <= WEEK
                && Math.abs((now - rtc) - state.offset) <= 10;
    }
    private ScheduleRules() {}
}
