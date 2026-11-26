// Native port of Dressroom-Advanced by @BlueOutside. See upstream LICENSE.
package dev.denver.dressroomadvanced;

import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;

import java.lang.ref.WeakReference;

final class DepthGeometry {
    private record Cut(
            WeakReference<Path> subject, Path.FillType fill, float[] original, Path result) {}

    private static final java.util.ArrayDeque<Cut> cuts = new java.util.ArrayDeque<>();
    private static final java.util.Map<Path, Boolean> detailed = new java.util.WeakHashMap<>();

    private DepthGeometry() {}

    static void outline(android.graphics.Outline outline, Path visible) {
        if (visible.isEmpty()) outline.setRect(-2, -2, -1, -1);
        else outline.setPath(visible);
    }

    static Path visibleBackground(float width, float height, float radius, Path subject) {
        Path visible = new Path();
        visible.addRoundRect(new RectF(0, 0, width, height), radius, radius, Path.Direction.CW);
        return subtract(visible, subject);
    }

    static synchronized Path subtract(Path original, Path subject) {
        if (!original.isInverseFillType() && !subject.isInverseFillType()) {
            RectF contentBounds = new RectF(), subjectBounds = new RectF();
            original.computeBounds(contentBounds, true);
            subject.computeBounds(subjectBounds, true);
            if (original.isEmpty()
                    || subject.isEmpty()
                    || !RectF.intersects(contentBounds, subjectBounds)) return new Path(original);
        }
        float[] shape = original.approximate(.1f);
        for (java.util.Iterator<Cut> iterator = cuts.iterator(); iterator.hasNext(); ) {
            Cut cut = iterator.next();
            Path cachedSubject = cut.subject.get();
            if (cachedSubject == null) {
                iterator.remove();
                continue;
            }
            if (cachedSubject == subject
                    && cut.fill == original.getFillType()
                    && java.util.Arrays.equals(shape, cut.original)) return new Path(cut.result);
        }
        Path visible = new Path(original);
        if (detailed.computeIfAbsent(subject, p -> p.approximate(.5f).length > 768)
                || !visible.op(subject, Path.Op.DIFFERENCE))
            visible = subtractRegion(original, subject);
        visible = simplify(visible, .3f);
        if (cuts.size() >= 24) cuts.removeFirst();
        cuts.addLast(
                new Cut(
                        new WeakReference<>(subject),
                        original.getFillType(),
                        shape,
                        new Path(visible)));
        return visible;
    }

    static Path simplify(Path source, float tolerance) {
        float[] points = source.approximate(.1f);
        Path result = new Path();
        result.setFillType(source.getFillType());
        int start = 0, count = points.length / 3;
        for (int i = 1; i <= count; i++) {
            boolean boundary =
                    i == count
                            || (points[i * 3] == points[(i - 1) * 3]
                                    && (points[i * 3 + 1] != points[(i - 1) * 3 + 1]
                                            || points[i * 3 + 2] != points[(i - 1) * 3 + 2]));
            if (!boundary) continue;
            boolean[] keep = new boolean[i - start];
            keep[0] = keep[keep.length - 1] = true;
            java.util.ArrayDeque<int[]> work = new java.util.ArrayDeque<>();
            work.add(new int[] {start, i - 1});
            while (!work.isEmpty()) {
                int[] pair = work.removeLast();
                int a = pair[0], b = pair[1], best = -1;
                float ax = points[a * 3 + 1],
                        ay = points[a * 3 + 2],
                        dx = points[b * 3 + 1] - ax,
                        dy = points[b * 3 + 2] - ay;
                float length = dx * dx + dy * dy, distance = tolerance * tolerance;
                for (int j = a + 1; j < b; j++) {
                    float x = points[j * 3 + 1] - ax, y = points[j * 3 + 2] - ay;
                    float t =
                            length == 0 ? 0 : Math.max(0, Math.min(1, (x * dx + y * dy) / length));
                    float d = (x - t * dx) * (x - t * dx) + (y - t * dy) * (y - t * dy);
                    if (d > distance) {
                        distance = d;
                        best = j;
                    }
                }
                if (best >= 0) {
                    keep[best - start] = true;
                    work.add(new int[] {a, best});
                    work.add(new int[] {best, b});
                }
            }
            result.moveTo(points[start * 3 + 1], points[start * 3 + 2]);
            for (int j = start + 1; j < i; j++)
                if (keep[j - start]) result.lineTo(points[j * 3 + 1], points[j * 3 + 2]);
            result.close();
            start = i;
        }
        return result;
    }

    static Path subtractRegion(Path original, Path subject) {
        Matrix scale = new Matrix();
        scale.setScale(2, 2);
        Path glyph = new Path(), mask = new Path();
        original.transform(scale, glyph);
        subject.transform(scale, mask);
        RectF bounds = new RectF();
        glyph.computeBounds(bounds, true);
        Region clip =
                new Region(
                        (int) Math.floor(bounds.left) - 1,
                        (int) Math.floor(bounds.top) - 1,
                        (int) Math.ceil(bounds.right) + 1,
                        (int) Math.ceil(bounds.bottom) + 1);
        Region content = new Region(), hidden = new Region();
        content.setPath(glyph, clip);
        hidden.setPath(mask, clip);
        content.op(hidden, Region.Op.DIFFERENCE);
        Path result = content.getBoundaryPath();
        scale.setScale(.5f, .5f);
        result.transform(scale);
        return result;
    }
}
