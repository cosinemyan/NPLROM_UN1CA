// Native port of Dressroom-Advanced by @BlueOutside. See upstream LICENSE.
package dev.denver.dressroomadvanced;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Small shared-memory records; an odd sequence means the renderer is still writing. */
final class WallpaperMotionPacket {
    static final int SLOTS = 16, STRIDE = 64, SIZE = SLOTS * STRIDE;
    private static final VarHandle INTS =
            MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());

    record Frame(
            int revision,
            int wallpaper,
            int which,
            int width,
            int height,
            float scaleX,
            float scaleY,
            float offsetX,
            float offsetY) {
        boolean valid() {
            return wallpaper >= 0
                    && (which & 3) == 2
                    && (which & ~63) == 0
                    && Integer.bitCount(which & 60) <= 1
                    && width > 0
                    && height > 0
                    && Float.isFinite(scaleX)
                    && Float.isFinite(scaleY)
                    && scaleX >= .5f
                    && scaleX <= 2
                    && scaleY >= .5f
                    && scaleY <= 2
                    && Float.isFinite(offsetX)
                    && Float.isFinite(offsetY)
                    && Math.abs(offsetX) <= 2
                    && Math.abs(offsetY) <= 2;
        }

        boolean fits(int id, int w, int h) {
            return valid()
                    && wallpaper == id
                    && w > 0
                    && h > 0
                    && Math.abs((double) width * h / ((double) height * w) - 1) < .01;
        }
    }

    static int slot(int which) {
        int mode = which & 60;
        return (mode == 0 ? 4 : mode) / 4;
    }

    static Frame read(ByteBuffer memory, int slot) {
        if (slot < 0 || slot >= SLOTS) return null;
        int base = slot * STRIDE;
        int sequence = (int) INTS.getVolatile(memory, base);
        if (sequence == 0 || (sequence & 1) != 0) return null;
        Frame result =
                new Frame(
                        sequence,
                        memory.getInt(base + 4),
                        memory.getInt(base + 8),
                        memory.getInt(base + 12),
                        memory.getInt(base + 16),
                        memory.getFloat(base + 20),
                        memory.getFloat(base + 24),
                        memory.getFloat(base + 28),
                        memory.getFloat(base + 32));
        VarHandle.acquireFence();
        return sequence == (int) INTS.getVolatile(memory, base) && result.valid() ? result : null;
    }

    static boolean write(ByteBuffer memory, Frame frame) {
        if (!frame.valid()) return false;
        int slot = slot(frame.which), base = slot * STRIDE;
        Frame previous = read(memory, slot);
        if (previous != null
                && previous.wallpaper == frame.wallpaper
                && previous.which == frame.which
                && previous.width == frame.width
                && previous.height == frame.height
                && previous.scaleX == frame.scaleX
                && previous.scaleY == frame.scaleY
                && previous.offsetX == frame.offsetX
                && previous.offsetY == frame.offsetY) return false;
        int sequence = (int) INTS.getVolatile(memory, base);
        if ((sequence & 1) != 0 || !INTS.compareAndSet(memory, base, sequence, sequence + 1))
            return false;
        sequence += 2;
        if (sequence == 0) sequence = 2;
        memory.putInt(base + 4, frame.wallpaper);
        memory.putInt(base + 8, frame.which);
        memory.putInt(base + 12, frame.width);
        memory.putInt(base + 16, frame.height);
        memory.putFloat(base + 20, frame.scaleX);
        memory.putFloat(base + 24, frame.scaleY);
        memory.putFloat(base + 28, frame.offsetX);
        memory.putFloat(base + 32, frame.offsetY);
        INTS.setVolatile(memory, base, sequence);
        return true;
    }
}
