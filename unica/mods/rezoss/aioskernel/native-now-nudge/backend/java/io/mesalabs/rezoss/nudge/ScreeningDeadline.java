package io.mesalabs.rezoss.nudge;

import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** One terminal outcome for a screening request, including time spent queued. */
final class ScreeningDeadline {
    static final long BUDGET_MS=9000;
    private final long expires;
    private final AtomicBoolean terminal=new AtomicBoolean();
    ScreeningDeadline(long submitted){expires=submitted+BUDGET_MS;}
    long expires(){return expires;}
    boolean done(){return terminal.get();}
    boolean finish(){return terminal.compareAndSet(false,true);}
    boolean complete(long now){return now<expires&&finish();}
    long remaining(long now)throws InterruptedIOException {
        long left=expires-now;
        if(done()||left<=0)throw new InterruptedIOException("Call Screening deadline");
        return left;
    }
}
