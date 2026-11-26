// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.ArrayList;

final class RuntimeExtras {
    private static Bundle cached, lastKnown;
    private static int screen = -1;
    private static boolean foldObserved;
    private static final java.util.Map<Integer, Bundle> committed = new java.util.HashMap<>();
    private static final Bundle defaults = ExtrasPreferences.clean(null);
    private static Context context;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final ArrayList<Runnable> listeners = new ArrayList<>();
    private static boolean observing, retryPending;
    private static long retryAt;
    private static int failures;

    private RuntimeExtras() {}

    static synchronized Bundle get(Context c) {
        if (context == null) {
            context = c.getApplicationContext();
            if (context == null) context = c;
        }
        if (!foldObserved) {
            foldObserved = true;
            ScreenIdentity.observe(() -> main.post(() -> {
                synchronized (RuntimeExtras.class) { switchScreen(); load(); }
                changed();
            }));
        }
        switchScreen();
        load();
        return cached != null ? cached : lastKnown != null ? lastKnown : defaults;
    }

    private static void switchScreen() {
        int next = ScreenIdentity.current();
        if (next == screen) return;
        screen = next;
        cached = null;
        lastKnown = committed.get(next);
        retryAt = 0;
        failures = 0;
    }

    private static void load() {
        switchScreen();
        if (cached != null || context == null || SystemClock.uptimeMillis() < retryAt) return;
        try {
            if (!observing) {
                context.getContentResolver()
                        .registerContentObserver(
                                SettingsProvider.URI,
                                false,
                                new ContentObserver(main) {
                                    @Override
                                    public void onChange(boolean selfChange) {
                                        synchronized (RuntimeExtras.class) {
                                            cached = null;
                                            retryAt = 0;
                                            failures = 0;
                                            load();
                                        }
                                        changed();
                                    }
                                });
                observing = true;
            }
            Bundle value = ExtrasPreferences.read(context);
            cached = value;
            lastKnown = value;
            committed.put(screen, value);
            failures = 0;
            retryAt = 0;
        } catch (RuntimeException error) {
            failures++;
            long delay = failures <= 5 ? Math.min(4000, 250L << (failures - 1)) : 30000;
            retryAt = SystemClock.uptimeMillis() + delay;
            if (failures == 1)
                android.util.Log.w(
                        DressroomModule.TAG,
                        "Extra provider restarting; retaining committed settings",
                        error);
            if (!retryPending && failures <= 5) {
                retryPending = true;
                main.postDelayed(
                        () -> {
                            synchronized (RuntimeExtras.class) {
                                retryPending = false;
                                load();
                            }
                            changed();
                        },
                        delay);
            }
        }
    }

    private static void changed() {
        for (Runnable listener : new ArrayList<>(listeners))
            try {
                listener.run();
            } catch (RuntimeException error) {
                android.util.Log.w(DressroomModule.TAG, "Extra refresh listener failed", error);
            }
    }

    static void userUnlocked() {
        synchronized(RuntimeExtras.class){cached=null;retryAt=0;failures=0;load();}
        changed();
    }
    static boolean enabled(Context c, String key) {
        return get(c).getBoolean(key);
    }

    static void listen(Runnable listener) {
        listeners.add(listener);
    }
}
