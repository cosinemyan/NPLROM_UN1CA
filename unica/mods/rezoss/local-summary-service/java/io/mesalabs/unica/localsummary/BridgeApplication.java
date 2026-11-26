package io.mesalabs.unica.localsummary;

public final class BridgeApplication extends android.app.Application {
    @Override public void onCreate() {
        super.onCreate();
        QwenEngine.initialize(this);
    }
}
