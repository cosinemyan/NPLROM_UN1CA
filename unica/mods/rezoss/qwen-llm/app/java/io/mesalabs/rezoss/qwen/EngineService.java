package io.mesalabs.rezoss.qwen;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import java.util.concurrent.*;

/** Single-flight inference with authenticated cancellation and a thermal watchdog. */
public final class EngineService extends Service {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ApiEngine engine;
    private Messenger endpoint;
    private volatile Job active;
    private volatile boolean destroyed;
    private final Runnable idle = () -> { if (!destroyed) watchdog.execute(() -> { if (active == null) engine.cancel(); }); };

    private final class Job {
        final Messenger callback;
        final int uid;
        final String input;
        final String requestId = java.util.UUID.randomUUID().toString();
        final boolean summary;
        final boolean foreground;
        final long deadline;
        volatile String cancellation;
        volatile Thread runner;
        IBinder.DeathRecipient death;
        Job(Message message) {
            callback = message.replyTo;
            uid = message.sendingUid;
            Bundle data = message.getData();
            input = data.getString("input", "");
            summary = data.getBoolean("summary");
            foreground = data.getBoolean("foreground");
            deadline = Math.min(data.getLong("deadline", 0), SystemClock.elapsedRealtime() + 30000);
        }
        void cancel(String reason) {
            if (cancellation != null) return;
            cancellation = reason;
            engine.cancel(requestId);
            Thread running = runner;
            if (running != null) running.interrupt();
            Log.i("RezossLLM", "request status=cancel_pending reason=" + reason);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        engine = new ApiEngine(this);
        watchdog.scheduleAtFixedRate(() -> {
            Job job = active;
            if (job == null) return;
            String reason = ThermalGuard.blocked(this);
            if (reason == null && SystemClock.elapsedRealtime() >= job.deadline) reason = "deadline";
            if (reason != null) job.cancel(reason);
        }, 0, 1, TimeUnit.SECONDS);
        endpoint = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override public void handleMessage(Message message) {
                if (message.replyTo == null) return;
                try {
                    int bridge = getPackageManager().getApplicationInfo("io.mesalabs.unica.localsummary", 0).uid;
                    if (message.sendingUid != bridge || !trustedBridge()) {
                        Log.w("RezossLLM", "request status=caller_denied");
                        return;
                    }
                    if (message.what == 2) {
                        Job job = active;
                        if (job != null && job.uid == message.sendingUid
                                && job.callback.getBinder().equals(message.replyTo.getBinder())) job.cancel("client_cancelled");
                        return;
                    }
                    if (message.what != 1) return;
                    Job job = new Job(message);
                    String blocked = ThermalGuard.blocked(EngineService.this);
                    if (blocked != null) { respond(job.callback, null, blocked); return; }
                    if (active != null) {
                        respond(job.callback, null, "busy"); return;
                    }
                    if (job.input.isEmpty() || job.input.length() > 12000
                            || job.deadline <= SystemClock.elapsedRealtime()) {
                        respond(job.callback, null, "invalid_or_expired"); return;
                    }
                    job.death = () -> job.cancel("client_died");
                    job.callback.getBinder().linkToDeath(job.death, 0);
                    main.removeCallbacks(idle);
                    active = job;
                    worker.execute(() -> runJob(job));
                } catch (Exception invalid) {
                    respond(message.replyTo, null, "request_error");
                }
            }
        });
    }

    private void runJob(Job job) {
        job.runner = Thread.currentThread();
        String result = null;
        String status = "unavailable";
        try {
            if (job.cancellation == null) {
                result = engine.generate(job.input, job.summary, job.deadline, job.requestId);
                status = result == null ? "rejected_output" : "generated";
            }
        } catch (Exception failure) {
            status = failure instanceof ApiClient.ApiException
                    ? ((ApiClient.ApiException) failure).status : failure.getClass().getSimpleName();
        } finally {
            if (job.cancellation != null) { result = null; status = job.cancellation; }
            job.runner = null;
            Thread.interrupted();
            final String content = result;
            final String reason = status;
            main.post(() -> {
                try { job.callback.getBinder().unlinkToDeath(job.death, 0); } catch (java.util.NoSuchElementException ignored) { }
                if (destroyed) return;
                if (active == job) active = null;
                respond(job.callback, content, reason);
                main.postDelayed(idle, 300000);
            });
        }
    }

    private static void respond(Messenger target, String content, String status) {
        Log.i("RezossLLM", "request status=" + status);
        Message response = Message.obtain(null, 1);
        Bundle data = new Bundle();
        data.putString("content", content);
        data.putString("status", status);
        response.setData(data);
        try { target.send(response); } catch (RemoteException ignored) { }
    }
    private boolean trustedBridge() throws PackageManager.NameNotFoundException {
        String pkg = "io.mesalabs.unica.localsummary";
        if ((getPackageManager().getApplicationInfo(pkg, 0).flags
                & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) return false;
        if (getPackageManager().checkSignatures(pkg, getPackageName()) == PackageManager.SIGNATURE_MATCH) return true;
        byte[] expected = new byte[32];
        String hash = "34df0e7a9f1cf1892e45c056b4973cd81ccf148a4050d11aea4ac5a65f900a42";
        for (int i = 0; i < expected.length; i++) expected[i] = (byte) Integer.parseInt(hash.substring(i * 2, i * 2 + 2), 16);
        return getPackageManager().hasSigningCertificate(pkg, expected, PackageManager.CERT_INPUT_SHA256);
    }

    @Override public IBinder onBind(Intent intent) { return endpoint.getBinder(); }
    @Override public void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        if (active != null) active.cancel("service_stopped");
        worker.shutdownNow();
        engine.close();
        watchdog.shutdownNow();
        super.onDestroy();
    }
}
