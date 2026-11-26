package dev.denver.dressroomadvanced;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.util.LinkedHashMap;

/** Explicit callbacks from Samsung's layered renderer and wallpaper command endpoint. */
public final class NativeWallpaper {
    private static final ThreadLocal<Pass> pass = new ThreadLocal<>();
    private static final LinkedHashMap<String, WallpaperMotionChannel.Sink> clients = new LinkedHashMap<>();
    private static final WallpaperMotionPacket.Frame[] latest = new WallpaperMotionPacket.Frame[WallpaperMotionPacket.SLOTS];
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static boolean signalled;
    private static final Runnable settled = () -> { signalled = false; signal(); };
    private static final class Pass {
        final int which, wallpaper;
        final Pass previous;
        boolean captured;
        Pass(int which, int wallpaper, Pass previous) {
            this.which = which;
            this.wallpaper = wallpaper;
            this.previous = previous;
        }
    }
    private NativeWallpaper() {}

    public static void begin(Object engine) {
        Pass previous = pass.get();
        pass.set(new Pass(0, -1, previous));
        try {
            int flags = ((Number) engine.getClass().getMethod("getWhich").invoke(engine)).intValue();
            int which = (flags & 2) == 0 ? flags : (flags & ~3) | 2;
            if ((which & 3) != 2) return;
            Context context = (Context) engine.getClass().getMethod("getAppContext").invoke(engine);
            int source = ((Number) engine.getClass().getMethod("getSourceWhich").invoke(engine)).intValue();
            int wallpaper = WallpaperManager.getInstance(context).getWallpaperId(source);
            pass.set(new Pass(which, wallpaper, previous));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            android.util.Log.w("Dressroom-Advanced", "Wallpaper frame identity unavailable", failure);
        }
    }

    public static void end() {
        Pass current = pass.get();
        if (current == null || current.previous == null) pass.remove();
        else pass.set(current.previous);
    }

    /** Called immediately before the layered bitmap Drawable.draw(canvas). */
    @SuppressWarnings("deprecation")
    public static void bitmap(Canvas canvas) {
        Pass identity = pass.get();
        if (identity == null || identity.captured || identity.which == 0 || canvas == null) return;
        try {
            Matrix matrix = new Matrix();
            canvas.getMatrix(matrix);
            float[] values = new float[9];
            matrix.getValues(values);
            if (values[1] != 0 || values[3] != 0 || values[6] != 0 || values[7] != 0 || values[8] != 1
                    || canvas.getWidth() <= 0 || canvas.getHeight() <= 0) return;
            WallpaperMotionPacket.Frame frame = new WallpaperMotionPacket.Frame(0, identity.wallpaper,
                    identity.which, canvas.getWidth(), canvas.getHeight(), values[0], values[4],
                    values[2] / canvas.getWidth(), values[5] / canvas.getHeight());
            if (frame.valid()) publish(frame);
        } catch (RuntimeException failure) {
            android.util.Log.w("Dressroom-Advanced", "Wallpaper matrix unavailable", failure);
        }
        // Only the wallpaper image contributes the transform; effect layers are excluded.
        identity.captured = true;
    }

    public static boolean command(String action, Bundle data) {
        if (!WallpaperMotionChannel.COMMAND.equals(action)) return false;
        if (data != null) try {
            accept(new WallpaperMotionChannel.Sink(data));
        } catch (Exception | LinkageError failure) {
            android.util.Log.w("Dressroom-Advanced", "Wallpaper motion channel rejected", failure);
        }
        return true;
    }

    private static synchronized void accept(WallpaperMotionChannel.Sink sink) {
        WallpaperMotionChannel.Sink old = clients.remove(sink.key);
        if (old != null) old.close();
        if (clients.size() >= 4) clients.remove(clients.keySet().iterator().next()).close();
        clients.put(sink.key, sink);
        for (WallpaperMotionPacket.Frame frame : latest) if (frame != null) sink.write(frame);
        sink.signal();
    }

    private static synchronized void signal() {
        for (WallpaperMotionChannel.Sink sink : clients.values()) sink.signal();
    }

    private static synchronized void publish(WallpaperMotionPacket.Frame frame) {
        latest[WallpaperMotionPacket.slot(frame.which())] = frame;
        boolean changed = false;
        for (WallpaperMotionChannel.Sink sink : clients.values()) changed |= sink.write(frame);
        if (!changed) return;
        main.post(() -> {
            if (!signalled) { signalled = true; signal(); }
            main.removeCallbacks(settled);
            main.postDelayed(settled, 100);
        });
    }
}
