// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

final class WidgetLayout {
    private WidgetLayout() {}

    static int[] dimensions(int columns, int rows, boolean storedVertical, boolean nativeVertical) {
        return storedVertical == nativeVertical
                ? new int[] {columns, rows}
                : new int[] {rows, columns};
    }

    static int effectiveAnchor(int anchor, boolean custom, boolean vertical, boolean foldable) {
        return anchor == 0 && custom && vertical && foldable ? 4 : anchor;
    }

    static boolean matchesOrientation(int columns, int rows, boolean vertical) {
        return vertical ? rows > columns : columns >= rows;
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static float finite(float value, float fallback, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    static float[] place(
            int anchor,
            float left,
            float top,
            float right,
            float bottom,
            float width,
            float height,
            float screenWidth,
            float screenHeight,
            float gap,
            float x,
            float y) {
        return place(
                anchor,
                left,
                top,
                right,
                bottom,
                width,
                height,
                screenWidth,
                screenHeight,
                gap,
                x,
                y,
                0);
    }

    static float[] place(
            int anchor,
            float left,
            float top,
            float right,
            float bottom,
            float width,
            float height,
            float screenWidth,
            float screenHeight,
            float gap,
            float x,
            float y,
            float offset) {
        float px = (left + right - width) / 2, py = bottom + gap;
        if (anchor == 2) py = top - gap - height;
        if (anchor == 3 || anchor == 4) {
            px = anchor == 3 ? left - gap - width : right + gap;
            py = (top + bottom - height) / 2;
        }
        if (anchor == 1 || anchor == 2) px += offset;
        if (anchor == 3 || anchor == 4) py += offset;
        if (anchor == 5) {
            px = x * Math.max(0, screenWidth - width);
            py = y * Math.max(0, screenHeight - height);
        }
        return new float[] {
            Math.max(0, Math.min(Math.max(0, screenWidth - width), px)),
            Math.max(0, Math.min(Math.max(0, screenHeight - height), py))
        };
    }

    static boolean positionsValid(int columns, int rows, int[][] cells) {
        if (columns < 1 || columns > 8 || rows < 1 || rows > 8 || columns * rows > 32) return false;
        boolean[][] used = new boolean[rows][columns];
        for (int[] cell : cells) {
            if (cell == null || cell.length != 4) return false;
            int x = cell[0], y = cell[1], width = cell[2], height = cell[3];
            if (x < 0
                    || y < 0
                    || width < 1
                    || height < 1
                    || width > columns
                    || height > rows
                    || x > columns - width
                    || y > rows - height) return false;
            for (int yy = y; yy < y + height; yy++)
                for (int xx = x; xx < x + width; xx++) {
                    if (used[yy][xx]) return false;
                    used[yy][xx] = true;
                }
        }
        return true;
    }

    static boolean fits(int columns, int rows, int[][] spans) {
        if (columns < 1 || columns > 8 || rows < 1 || rows > 8 || columns * rows > 32) return false;
        boolean[][] used = new boolean[rows][columns];
        int[][] sorted = spans.clone();
        java.util.Arrays.sort(sorted, (a, b) -> Integer.compare(b[0] * b[1], a[0] * a[1]));
        for (int[] span : sorted) {
            int w = span[0], h = span[1];
            if (w < 1 || h < 1 || w > columns || h > rows) return false;
            boolean placed = false;
            for (int y = 0; y + h <= rows && !placed; y++)
                for (int x = 0; x + w <= columns; x++) {
                    boolean empty = true;
                    for (int yy = y; yy < y + h; yy++)
                        for (int xx = x; xx < x + w; xx++) if (used[yy][xx]) empty = false;
                    if (!empty) continue;
                    for (int yy = y; yy < y + h; yy++)
                        for (int xx = x; xx < x + w; xx++) used[yy][xx] = true;
                    placed = true;
                    break;
                }
            if (!placed) return false;
        }
        return true;
    }
}
