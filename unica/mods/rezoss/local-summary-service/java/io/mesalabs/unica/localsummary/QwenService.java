package io.mesalabs.unica.localsummary;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import java.util.*;
import java.util.concurrent.*;

public final class QwenService extends Service {
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1));
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<IBinder, Job> jobs = new HashMap<>();
    private final LinkedHashMap<String, CachedReply> cache = new LinkedHashMap<>();
    private Messenger endpoint;
    private static final class CachedReply {
        final String value;
        final long until = SystemClock.elapsedRealtime() + 120000;
        CachedReply(String value) { this.value = value; }
    }
    private static final class Job {
        int uid;
        volatile boolean started;
        volatile boolean cancelled;
        FutureTask<Void> future;
        IBinder.DeathRecipient death;
    }
    @Override public void onCreate() {
        super.onCreate();
        endpoint = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override public void handleMessage(Message request) {
                if (request.replyTo == null || checkPermission(
                        "io.mesalabs.unica.permission.LOCAL_SUMMARY", -1, request.sendingUid)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
                Messenger callback = request.replyTo;
                IBinder binder = callback.getBinder();
                if (request.what == 2) {
                    Job job = jobs.get(binder);
                    if (job != null && job.uid == request.sendingUid) {
                        cancel(job);
                    }
                    return;
                }
                if (request.what != 1 || jobs.containsKey(binder)) return;
                Bundle data = request.getData();
                String input = data.getString("input", "");
                boolean foreground = data.getBoolean("foreground");
                long deadline = Math.min(data.getLong("deadline", 0), SystemClock.elapsedRealtime() + 32000);
                if (input.isEmpty() || input.length() > 12000) { respond(callback, null, "invalid"); return; }
                String key = request.sendingUid + ":" + input;
                cache.entrySet().removeIf(entry -> entry.getValue().until < SystemClock.elapsedRealtime());
                CachedReply cached = cache.get(key);
                if (cached != null) { respond(callback, cached.value, "cached"); return; }
                Job job = new Job();
                job.uid = request.sendingUid;
                job.death = () -> cancel(job);
                job.future = new FutureTask<Void>(() -> {
                    job.started = true;
                    long remaining = deadline - SystemClock.elapsedRealtime();
                    String result = remaining < 1000 ? null : QwenEngine.generate(input, false,
                            (int) Math.min(30000, remaining), foreground);
                    if (!job.cancelled) {
                        main.post(() -> {
                            if (job.cancelled || job.future.isCancelled()) return;
                            if (result != null) {
                                if (cache.size() >= 4) cache.remove(cache.keySet().iterator().next());
                                cache.put(key, new CachedReply(result));
                            }
                            respond(callback, result, result == null ? "unavailable" : "generated");
                        });
                    }
                    return null;
                }) {
                    @Override protected void done() {
                        main.post(() -> {
                            if (jobs.get(binder) == job) jobs.remove(binder);
                            try { binder.unlinkToDeath(job.death, 0); } catch (java.util.NoSuchElementException ignored) { }
                        });
                    }
                };
                try {
                    binder.linkToDeath(job.death, 0);
                    jobs.put(binder, job);
                    worker.execute(job.future);
                } catch (Exception unavailable) {
                    jobs.remove(binder);
                    job.future.cancel(false);
                    respond(callback, null, "busy_or_disconnected");
                }
            }
        });
    }
    private void cancel(Job job) {
        job.cancelled = true;
        if (!job.started) {
            job.future.cancel(false);
            worker.purge();
        }
        Log.i("RezossAI", "reply status=client_cancelled");
    }

    private static void respond(Messenger target, String content, String status) {
        Log.i("RezossAI", "reply status=" + status);
        Message response = Message.obtain(null, 1);
        Bundle data = new Bundle();
        data.putString("content", content);
        data.putString("status", status);
        response.setData(data);
        try { target.send(response); } catch (RemoteException ignored) { }
    }
    @Override public IBinder onBind(Intent intent) { return endpoint.getBinder(); }
    @Override public void onDestroy() {
        for (Job job : jobs.values()) cancel(job);
        worker.shutdown();
        super.onDestroy();
    }
}
