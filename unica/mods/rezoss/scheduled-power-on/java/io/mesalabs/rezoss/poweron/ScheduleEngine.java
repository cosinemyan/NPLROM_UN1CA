// SPDX-License-Identifier: GPL-3.0-or-later
package io.mesalabs.rezoss.poweron;

import java.util.UUID;

/** One-shot scheduling policy; Android and hardware effects are injected for host tests. */
final class ScheduleEngine {
    static final class State {
        final long on, off, offset;
        final int boot;
        final String token, error;
        final boolean uncertain;
        State(long on, long off, long offset, int boot, String token, boolean uncertain, String error) {
            this.on=on; this.off=off; this.offset=offset; this.boot=boot;
            this.token=token; this.uncertain=uncertain; this.error=error;
        }
        State withoutOff(String message) { return new State(on,0,offset,boot,"",uncertain,message); }
    }
    interface Ports {
        State load();
        void store(State state) throws Exception;
        long now();
        int boot();
        long[] snapshot() throws Exception;
        void program(long on) throws Exception;
        void checkShutdown() throws Exception;
        void checkExactAlarm() throws Exception;
        void arm(long off, String token) throws Exception;
        void cancelAlarm();
        void shutdown() throws Exception;
    }
    private final Ports io;
    ScheduleEngine(Ports io) { this.io=io; }

    void save(long on, long off, boolean shutdownNow) throws Exception {
        save(on,off,shutdownNow,false);
    }
    private void save(long on,long off,boolean shutdownNow,boolean restoring) throws Exception {
        if (restoring) ScheduleRules.validateRestored(on,off,io.now());
        else ScheduleRules.validate(on,off,io.now());
        if (shutdownNow && off != 0) throw new IllegalArgumentException("Choose immediate or scheduled shutdown.");
        if (shutdownNow || off != 0) io.checkShutdown();
        if (off != 0) io.checkExactAlarm();
        State old=io.load();
        io.cancelAlarm();
        // Clear the automatic action durably before touching the shared hardware slot.
        io.store(new State(old.on,0,old.offset,old.boot,"",true,""));
        long[] clock=io.snapshot();
        io.program(on);
        State next=new State(on,off,clock[0]-clock[1],io.boot(),off==0?"":UUID.randomUUID().toString(),false,"");
        io.store(next);
        if (off != 0) {
            try { io.arm(off,next.token); }
            catch (Exception failure) {
                io.cancelAlarm();
                io.store(next.withoutOff("Power on is saved, but automatic power off could not be scheduled."));
                throw failure;
            }
        }
        if (shutdownNow) io.shutdown();
    }
    void fire(String token) throws Exception {
        State state=io.load();
        // Stale/replayed deliveries must not consume or alter a replacement schedule.
        if (state.off == 0 || state.token.isEmpty() || !state.token.equals(token)) return;
        try {
            long[] clock=io.snapshot();
            if (!ScheduleRules.mayShutdown(state,token,clock[0],clock[1])) {
                io.cancelAlarm();
                io.store(state.withoutOff("Automatic power off was skipped because its time or clock changed. Save a new schedule."));
                return;
            }
            io.checkShutdown();
            // Refresh the power-on slot immediately before the requested shutdown.
            io.program(state.on);
            io.store(state.withoutOff("")); // Consume before the irreversible request.
            io.cancelAlarm();
            io.shutdown();
        } catch (Exception failure) {
            io.cancelAlarm();
            io.store(io.load().withoutOff("Automatic power off failed. Your phone was left running; check the power-on schedule."));
            throw failure;
        }
    }
    void cancel(boolean all) throws Exception {
        State state=io.load();
        io.cancelAlarm();
        io.store(state.withoutOff(""));
        if (all) {
            io.store(new State(state.on,0,state.offset,state.boot,"",true,""));
            io.program(0);
            io.store(new State(0,0,0,io.boot(),"",false,""));
        }
    }
    void restore() throws Exception {
        State state=io.load();
        if (state.on == 0) return;
        long now=io.now();
        if (state.uncertain || state.on-now < 120 || state.on-now > ScheduleRules.WEEK) {
            io.cancelAlarm();
            io.store(state.withoutOff(state.uncertain?"Save the power-on time again before shutting down.":"The previous schedule has ended."));
            return;
        }
        long off=state.off;
        boolean missed=off != 0 && off-now < 120;
        if (missed) off=0;
        // Never catch up a missed power-off event during boot or a clock change.
        save(state.on,off,false,true);
        if (missed) io.store(io.load().withoutOff("The missed power-off time was skipped. Power on is still saved."));
    }
}
