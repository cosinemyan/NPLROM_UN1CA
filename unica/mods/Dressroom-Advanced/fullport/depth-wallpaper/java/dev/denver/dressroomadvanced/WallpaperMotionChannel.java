// Native port of Dressroom-Advanced by @BlueOutside. See upstream LICENSE.
package dev.denver.dressroomadvanced;

import android.app.WallpaperManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.SharedMemory;
import android.view.View;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Pass the memory handle through WMS once; the renderer writes subsequent frames directly. */
final class WallpaperMotionChannel implements AutoCloseable {
    static final String COMMAND = "dev.denver.dressroomadvanced.MOTION_CHANNEL";
    private final SharedMemory memory;
    private final ByteBuffer reader;
    private final String key = java.util.UUID.randomUUID().toString();
    private final ResultReceiver receiver;

    WallpaperMotionChannel(Runnable changed) throws android.system.ErrnoException {
        memory = SharedMemory.create("Dressroom-wallpaper-motion", WallpaperMotionPacket.SIZE);
        reader = memory.mapReadOnly().order(ByteOrder.nativeOrder());
        ResultReceiver callback =
                new ResultReceiver(new Handler(Looper.getMainLooper())) {
                    @Override
                    protected void onReceiveResult(int resultCode, Bundle resultData) {
                        changed.run();
                    }
                };
        // Bundle otherwise writes the anonymous subclass name, which the wallpaper
        // app (and WMS) cannot load. The framework proxy carries only its Binder.
        android.os.Parcel parcel = android.os.Parcel.obtain();
        try {
            callback.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            receiver = ResultReceiver.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    void send(View view) {
        if (!view.isAttachedToWindow() || view.getWindowToken() == null) return;
        try {
            WallpaperManager.getInstance(view.getContext())
                    .sendWallpaperCommand(view.getWindowToken(), COMMAND, 0, 0, 0, message());
        } catch (RuntimeException ignored) {
            // A preview or a window that does not own the wallpaper keeps its saved mapping.
        }
    }

    Bundle message() {
        Bundle data = new Bundle();
        data.putParcelable("memory", memory);
        data.putParcelable("receiver", receiver);
        data.putString("key", key);
        return data;
    }

    WallpaperMotionPacket.Frame read(int slot) {
        return WallpaperMotionPacket.read(reader, slot);
    }

    @Override
    public void close() {
        SharedMemory.unmap(reader);
        memory.close();
    }

    static final class Sink implements AutoCloseable {
        final String key;
        private final SharedMemory memory;
        private final ByteBuffer writer;
        private final ResultReceiver receiver;

        Sink(Bundle data) throws android.system.ErrnoException {
            key = data.getString("key", "");
            memory = data.getParcelable("memory", SharedMemory.class);
            receiver = data.getParcelable("receiver", ResultReceiver.class);
            if (key.isEmpty()
                    || key.length() > 80
                    || memory == null
                    || receiver == null
                    || memory.getSize() != WallpaperMotionPacket.SIZE) {
                if (memory != null) memory.close();
                throw new IllegalArgumentException("Invalid wallpaper motion channel");
            }
            writer = memory.mapReadWrite().order(ByteOrder.nativeOrder());
        }

        boolean write(WallpaperMotionPacket.Frame frame) {
            return WallpaperMotionPacket.write(writer, frame);
        }

        void signal() {
            receiver.send(0, Bundle.EMPTY);
        }

        @Override
        public void close() {
            SharedMemory.unmap(writer);
            memory.close();
        }
    }
}
