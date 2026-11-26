package io.mesalabs.unica.localsummary;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import android.provider.Settings;
import java.util.concurrent.*;

/** System-side adapter; V73 builds use AIOS, other builds retain the legacy optional engine. */
public final class QwenEngine {
    private static final String ENGINE = "io.mesalabs.rezoss.qwen";
    private static Context app;
    public static void initialize(Context context) { app = context.getApplicationContext(); }
    public static void prioritizeReply() { }
    // Kept for the old smali startup call. Never execute a server in the privileged bridge.
    public static void startServer(ProcessBuilder ignored) { }

    public static String generate(String input, boolean summary, int budgetMs) {
        return generate(input, summary, budgetMs, false);
    }

    public static String generate(String input, boolean summary, int budgetMs, boolean foreground) {
        if (app == null || Looper.myLooper() == Looper.getMainLooper()) return null;
        Context context = app;
        boolean nativeV73 = Settings.Global.getInt(context.getContentResolver(), "rezoss_aios_v73_backend", 0) == 1;
        final String enginePackage = nativeV73 ? "com.samsung.android.aioskernelservice" : ENGINE;
        final String engineService = nativeV73 ? "io.mesalabs.rezoss.nudge.LocalInferenceService" : ENGINE + ".EngineService";
        try {
            if (!trustedEngine(context, enginePackage)) {
                Log.i("RezossAI", "engine status=missing_or_untrusted");
                return null;
            }
        } catch (Exception unavailable) { return null; }
        CountDownLatch done = new CountDownLatch(1);
        final java.util.concurrent.atomic.AtomicReference<Messenger> remote = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();
        String[] result = new String[1];
        long deadline = SystemClock.elapsedRealtime() + budgetMs;
        HandlerThread thread = new HandlerThread("RezossEngineIPC");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        Messenger callback = new Messenger(new Handler(thread.getLooper()) {
            @Override public void handleMessage(Message response) {
                result[0] = response.getData().getString("content");
                Log.i("RezossAI", "engine resultChars=" + (result[0] == null ? 0 : result[0].length()));
                finished.set(true);
                String status = response.getData().getString("status", "unknown");
                if (status.matches("[A-Za-z_]{1,48}")) Log.i("RezossAI", "engine status=" + status);
                done.countDown();
            }
        });
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                synchronized (remote) {
                if (finished.get()) return;
                Messenger server = new Messenger(binder);
                remote.set(server);
                Message request = Message.obtain(null, 1);
                Bundle data = new Bundle();
                data.putString("input", input);
                data.putBoolean("summary", summary);
                data.putBoolean("foreground", foreground);
                data.putLong("deadline", deadline - 200);
                request.setData(data);
                request.replyTo = callback;
                try { server.send(request); }
                catch (RemoteException gone) { done.countDown(); }
            }
            }
            @Override public void onServiceDisconnected(ComponentName name) { done.countDown(); }
            @Override public void onNullBinding(ComponentName name) { done.countDown(); }
            @Override public void onBindingDied(ComponentName name) { done.countDown(); }
        };
        boolean bound = false;
        long identity = Binder.clearCallingIdentity();
        try {
            bound = context.bindService(new Intent().setClassName(enginePackage, engineService),
                    connection, Context.BIND_AUTO_CREATE);
            if (!bound) {
                Log.i("RezossAI", "engine status=not_installed_or_disabled");
                return null;
            }
            if (!done.await(Math.max(1, deadline - SystemClock.elapsedRealtime()), TimeUnit.MILLISECONDS))
                Log.i("RezossAI", "engine status=deadline");
            return result[0];
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            Log.i("RezossAI", "engine status=" + failure.getClass().getSimpleName());
            return null;
        } finally {
            boolean completed;
            synchronized (remote) {
            completed = finished.getAndSet(true);
            Messenger server = remote.get();
            if (!completed && server != null) {
                Message cancel = Message.obtain(null, 2);
                cancel.replyTo = callback;
                try { server.send(cancel); } catch (RemoteException ignored) { }
            }
            }
            Binder.restoreCallingIdentity(identity);
            if (bound) {
                handler.postDelayed(() -> {
                    try { context.unbindService(connection); } catch (IllegalArgumentException ignored) { }
                    thread.quitSafely();
                }, completed ? 30000 : 0);
            } else thread.quitSafely();
        }
    }

    private static boolean trustedEngine(Context context, String enginePackage) {
        PackageManager pm = context.getPackageManager();
        if (pm.checkSignatures(context.getPackageName(), enginePackage) == PackageManager.SIGNATURE_MATCH) return true;
        // The ROM records Samsung's identity for preloads; pin the optional engine's real signer.
        String hash = "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8";
        byte[] expected = new byte[32];
        for (int i = 0; i < expected.length; i++) expected[i] = (byte) Integer.parseInt(hash.substring(i * 2, i * 2 + 2), 16);
        return pm.hasSigningCertificate(enginePackage, expected, PackageManager.CERT_INPUT_SHA256);
    }

    public static String summary(String input) {
        // V73 enforces a 45-second service limit; allow time for its failure callback.
        String result = generate(input, true, 50000);
        if (result != null) return result;
        Log.i("RezossAI", "summary status=unavailable");
        return null;
    }
}
