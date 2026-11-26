// Native port of Dressroom-Advanced by @BlueOutside. See upstream LICENSE.
package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import android.graphics.Path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public final class MaskContour {
    private static final class Node {
        final float x, y;
        Node a, b;
        boolean visited;

        Node(float x, float y) {
            this.x = x;
            this.y = y;
        }

        void link(Node node) {
            if (a == null) a = node;
            else if (b == null) b = node;
            else throw new IllegalStateException("Non-manifold contour");
        }
    }

    private final HashMap<Long, Node> edges = new HashMap<>();
    private final ArrayList<Node> nodes = new ArrayList<>();
    private final int width;
    private final float threshold;

    private MaskContour(int width, float threshold) {
        this.width = width;
        this.threshold = threshold;
    }

    public static Path extract(Bitmap bitmap, float threshold) {
        if (bitmap == null
                || bitmap.isRecycled()
                || !Float.isFinite(threshold)
                || threshold <= 0
                || threshold >= 255) throw new IllegalArgumentException("Invalid mask");
        return new MaskContour(bitmap.getWidth(), threshold).trace(bitmap);
    }

    private Node edge(int x, int y, boolean vertical, int first, int second) {
        long key = (((long) (y + 1) * (width + 2) + (x + 1)) << 1) | (vertical ? 1 : 0);
        Node old = edges.get(key);
        if (old != null) return old;
        float t = Math.max(0, Math.min(1, (threshold - first) / (second - first)));
        Node node = new Node(x + .5f + (vertical ? 0 : t), y + .5f + (vertical ? t : 0));
        edges.put(key, node);
        nodes.add(node);
        return node;
    }

    private void join(Node first, Node second) {
        first.link(second);
        second.link(first);
    }

    private Path trace(Bitmap bitmap) {
        int height = bitmap.getHeight();
        int[] top = new int[width], bottom = new int[width];
        bitmap.getPixels(bottom, 0, width, 0, 0, width, 1);
        for (int y = -1; y < height; y++) {
            for (int x = -1; x < width; x++) {
                int tl = x < 0 ? 0 : top[x] >>> 24, tr = x + 1 >= width ? 0 : top[x + 1] >>> 24;
                int bl = x < 0 ? 0 : bottom[x] >>> 24,
                        br = x + 1 >= width ? 0 : bottom[x + 1] >>> 24;
                int code =
                        (tl >= threshold ? 1 : 0)
                                | (tr >= threshold ? 2 : 0)
                                | (br >= threshold ? 4 : 0)
                                | (bl >= threshold ? 8 : 0);
                if (code == 0 || code == 15) continue;
                Node t = ((code & 1) != 0) != ((code & 2) != 0) ? edge(x, y, false, tl, tr) : null;
                Node r =
                        ((code & 2) != 0) != ((code & 4) != 0)
                                ? edge(x + 1, y, true, tr, br)
                                : null;
                Node b =
                        ((code & 8) != 0) != ((code & 4) != 0)
                                ? edge(x, y + 1, false, bl, br)
                                : null;
                Node l = ((code & 1) != 0) != ((code & 8) != 0) ? edge(x, y, true, tl, bl) : null;
                switch (code) {
                    case 1:
                    case 14:
                        join(l, t);
                        break;
                    case 2:
                    case 13:
                        join(t, r);
                        break;
                    case 3:
                    case 12:
                        join(l, r);
                        break;
                    case 4:
                    case 11:
                        join(r, b);
                        break;
                    case 6:
                    case 9:
                        join(t, b);
                        break;
                    case 7:
                    case 8:
                        join(l, b);
                        break;
                    case 5:
                    case 10:
                        double q =
                                (tl - threshold) * (br - threshold)
                                        - (tr - threshold) * (bl - threshold);
                        boolean connected = code == 5 ? q >= 0 : q <= 0;
                        if ((code == 5) == connected) {
                            join(t, r);
                            join(l, b);
                        } else {
                            join(t, l);
                            join(r, b);
                        }
                        break;
                    default:
                        throw new AssertionError(code);
                }
            }
            int[] old = top;
            top = bottom;
            bottom = old;
            if (y + 2 < height) bitmap.getPixels(bottom, 0, width, 0, y + 2, width, 1);
            else Arrays.fill(bottom, 0);
        }
        Path result = new Path();
        result.setFillType(Path.FillType.EVEN_ODD);
        for (Node start : nodes) {
            if (start.visited) continue;
            result.moveTo(start.x, start.y);
            Node previous = null, current = start;
            do {
                if (current.a == null || current.b == null || current.visited)
                    throw new IllegalStateException("Open contour");
                current.visited = true;
                Node next = current.a == previous ? current.b : current.a;
                result.lineTo(next.x, next.y);
                previous = current;
                current = next;
            } while (current != start);
            result.close();
        }
        return result;
    }
}
