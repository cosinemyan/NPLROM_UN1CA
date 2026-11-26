// Native port of Dressroom-Advanced by @BlueOutside. See upstream LICENSE.
package dev.denver.dressroomadvanced;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Objects;

final class DepthAuto {
    private final DressroomModule module;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WeakReference<View> clock = new WeakReference<>(null),
            image = new WeakReference<>(null);
    private Bundle profile;
    private Object stamp, resolved;
    private boolean enabled;
    private long revision;
    private int settlingRetries, detectionRetries;
    private Runnable check, deadline;
    private Request timedRequest;

    private record Request(long revision, long session, WeakReference<View> image, Object stamp) {}

    private final LatestDepthWork<Request> jobs = new LatestDepthWork<>(this::start);

    DepthAuto(DressroomModule module) {
        this.module = module;
        DepthEffects.listenWallpaper(this::wallpaper);
    }

    void reset() {
        cancelDeadline();
        enabled = false;
        revision++;
        settlingRetries = 0;
        detectionRetries = 0;
        stamp = resolved = null;
        profile = null;
        clock.clear();
        image.clear();
        jobs.clear();
        if (check != null) main.removeCallbacks(check);
        check = null;
        DepthEffects.cancelScan();
        DepthEffects.pendingSource(false);
    }

    void bind(View clock, Bundle profile, boolean widgets) {
        if (!DepthEffects.isEditorClock(clock)) return;
        if (!module.depthImageAvailable()) {
            reset();
            DepthEffects.pendingSource(true);
            return;
        }
        boolean changed =
                this.clock.get() != clock
                        || this.profile == null
                        || profile == null
                        || this.profile.getInt("type", -1) != profile.getInt("type", -1)
                        || this.profile.getInt("font", -1) != profile.getInt("font", -1);
        if (changed) {
            cancelDeadline();
            detectionRetries = 0;
            revision++;
            resolved = null;
            jobs.clear();
            if (check != null) {
                main.removeCallbacks(check);
                check = null;
            }
        }
        this.clock = new WeakReference<>(clock);
        this.profile = profile == null ? null : new Bundle(profile);
        boolean active = profile != null && (profile.getBoolean("depthEnabled") || widgets);
        if (!active) {
            if (enabled) {
                cancelDeadline();
                enabled = false;
                revision++;
                resolved = null;
                jobs.clear();
                if (check != null) main.removeCallbacks(check);
                check = null;
                DepthEffects.cancelScan();
                DepthEffects.pendingSource(false);
            }
            return;
        }
        if (!enabled) DepthEffects.pendingSource(true);
        enabled = true;
        View wallpaper = DepthEffects.wallpaper(clock);
        if (EditorWallpaperSource.available(wallpaper)) wallpaper(wallpaper);
    }

    void retry() {
        cancelDeadline();
        detectionRetries = 0;
        resolved = null;
        revision++;
        settlingRetries = 0;
        jobs.clear();
        schedule(0, true);
    }

    void settings(Bundle settings, boolean widgets) {
        View renderer = clock.get();
        if (renderer != null) bind(renderer, settings, widgets);
    }

    private void wallpaper(View view) {
        if (!enabled) return;
        View renderer = clock.get();
        if (renderer == null || renderer.getWindowToken() != view.getWindowToken()) return;
        if (image.get() != view) {
            View previous = image.get();
            if (EditorWallpaperSource.available(previous)
                    && (long) previous.getWidth() * previous.getHeight()
                            > (long) view.getWidth() * view.getHeight()) return;
            if (DepthEffects.wallpaper(renderer) != view) return;
        }
        try {
            Object current = LiveSubject.stamp(view);
            if (image.get() != view || !Objects.equals(stamp, current)) {
                boolean sameWallpaper =
                        image.get() == view && LiveSubject.isSameWallpaper(stamp, current);
                cancelDeadline();
                image = new WeakReference<>(view);
                stamp = current;
                detectionRetries = 0;
                settlingRetries = 0;
                resolved = null;
                revision++;
                jobs.clear();
                DepthEffects.cancelScan();
                if (!sameWallpaper) {
                    DepthEffects.pendingSource(true);
                }
                schedule(350, false);
            } else if (resolved == null && check == null) schedule(350, false);
        } catch (Exception ignored) {
        }
    }

    private void schedule(long delay, boolean force) {
        if (!enabled) return;
        if (check != null) main.removeCallbacks(check);
        long expected = revision;
        check =
                () -> {
                    check = null;
                    if (!enabled || revision != expected) return;
                    View wallpaper = image.get(), renderer = clock.get();
                    if (renderer == null) return;
                    if (!renderer.isAttachedToWindow()
                            || !EditorWallpaperSource.available(wallpaper)
                            || wallpaper.getWidth() <= 0
                            || wallpaper.getHeight() <= 0) {
                        if (settlingRetries < 5) {
                            settlingRetries++;
                            schedule(250, force);
                        }
                        return;
                    }
                    try {
                        String fingerprint = LiveSubject.fingerprint(wallpaper);
                        byte[] live = force ? null : DepthEffects.liveMask(wallpaper);
                        if (live != null) {
                            resolved = stamp;
                            settlingRetries = 0;
                            DepthEffects.pendingSource(false);
                            if (!module.depthDetected(
                                    renderer, live, "Subject reused for this clock style"))
                                retryDetection(
                                        "Clock preview was not ready to receive the subject");
                            return;
                        }
                        if (!force
                                && fingerprint.equals(profile.getString(DepthMetadata.FINGERPRINT))
                                && profile.getByteArray(DepthMetadata.SOURCE) != null) {
                            DepthEffects.restoreSource(wallpaper, profile, fingerprint);
                            resolved = stamp;
                            settlingRetries = 0;
                            DepthEffects.pendingSource(false);
                            if (!module.depthDetected(
                                    renderer,
                                    profile.getByteArray(DepthMetadata.SOURCE),
                                    "Subject restored · follows wallpaper movement"))
                                retryDetection(
                                        "Clock preview was not ready to receive the subject");
                            return;
                        }
                        resolved = stamp;
                        settlingRetries = 0;
                        DepthEffects.pendingSource(true);
                        module.depthStatus("Detecting the full wallpaper subject…");
                        Request request =
                                new Request(
                                        revision,
                                        module.generation(),
                                        new WeakReference<>(wallpaper),
                                        stamp);
                        armDeadline(request);
                        jobs.offer(request);
                    } catch (Exception error) {
                        String msg = error.getMessage();
                        if (settlingRetries < 5
                                && msg != null
                                && (msg.contains("ready") || msg.contains("measured"))) {
                            settlingRetries++;
                            schedule(250, force);
                            return;
                        }
                        module.depthDetectionFailed("Depth detection failed: " + msg);
                    }
                };
        main.postDelayed(check, delay);
    }

    private boolean current(Request request) {
        return enabled
                && request.revision == revision
                && request.session == module.generation()
                && image.get() == request.image.get()
                && Objects.equals(stamp, request.stamp)
                && module.depthImageAvailable();
    }

    private void start(Request request, Runnable complete) {
        View renderer = clock.get();
        if (!current(request) || renderer == null) {
            complete.run();
            return;
        }
        View wallpaper = request.image.get();
        if (wallpaper == null) {
            complete.run();
            return;
        }
        boolean[] answered = {false};
        DepthEffects.scan(
                wallpaper.getContext(),
                renderer,
                wallpaper,
                new DepthEffects.Result() {
                    @Override
                    public void done(byte[] mask, String message) {
                        answered[0] = true;
                        if (!current(request)) return;
                        if (mask != null) {
                            DepthEffects.pendingSource(false);
                            if (module.depthDetected(clock.get(), mask, message)) {
                                detectionRetries = 0;
                            } else
                                retryDetection(
                                        "Clock preview was not ready to receive the subject");
                        } else module.depthDetectionFailed(message);
                    }

                    @Override
                    public void retry(String message) {
                        answered[0] = true;
                        if (current(request)) retryDetection(message);
                    }
                },
                () -> {
                    if (timedRequest == request) cancelDeadline();
                    if (!answered[0] && current(request))
                        retryDetection("Subject scan was superseded before its result was applied");
                    complete.run();
                });
    }

    private void cancelDeadline() {
        if (deadline != null) main.removeCallbacks(deadline);
        deadline = null;
        timedRequest = null;
    }

    private void armDeadline(Request request) {
        cancelDeadline();
        timedRequest = request;
        deadline =
                () -> {
                    deadline = null;
                    if (!current(request)) return;
                    String message =
                            "Samsung's subject detector has not finished. If it stays here, restart"
                                + " Dressroom.";
                    DepthEffects.detectionFailure(message);
                    module.depthStatus(message);
                    // Do not interrupt JNI or start another native thread. A late valid result
                    // may still finish; LatestDepthWork keeps only one waiting request.
                };
        main.postDelayed(deadline, 60_000);
    }

    private void retryDetection(String message) {
        DepthEffects.pendingSource(true);
        if (++detectionRetries <= 2) {
            resolved = null;
            module.depthStatus("Waiting for the wallpaper preview…");
            schedule(350, false);
        } else {
            resolved = stamp;
            String failure = message + ". Tap Detect wallpaper subject to retry.";
            if (DepthEffects.liveMask(image.get()) != null) {
                DepthEffects.detectionFailure(failure);
                module.depthStatus(failure);
            } else module.depthDetectionFailed(failure);
        }
    }
}
