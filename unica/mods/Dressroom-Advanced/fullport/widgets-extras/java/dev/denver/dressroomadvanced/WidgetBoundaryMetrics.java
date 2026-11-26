// Native adaptation of BlueOutside / Dressroom-Advanced.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

/** Applies the resolved draft grid, independently of a stale provider capacity. */
final class WidgetBoundaryMetrics {
    static void apply(Object size, Object grid, Object manager, int columns, int rows)
            throws ReflectiveOperationException {
        if (columns < 1 || rows < 1) throw new IllegalArgumentException("Invalid widget grid");
        int unit = (int) Math.ceil(((Number) Reflect.call(manager, "getUnitSize")).floatValue());
        int padding = ((Number) Reflect.call(manager, "getPadding")).intValue();
        float scale = ((Number) Reflect.call(manager, "getScaleFactor")).floatValue();
        Reflect.call(grid, "setCol", columns);
        Reflect.call(grid, "setRow", rows);
        Reflect.call(size, "setWidgetUnitSize", unit);
        Reflect.call(size, "setWidgetInnerSizePadding", padding);
        Reflect.call(size, "setWidgetSideMargin", padding);
        Reflect.call(size, "setScaleFactor", scale);
        Reflect.call(size, "setPreviewLayoutScale", scale);
        int width = columns * unit + (columns - 1) * padding * 2;
        int height = rows * unit + (rows - 1) * padding * 2;
        Reflect.call(size, "setContainerWidth", width);
        Reflect.call(size, "setContainerHeight", height);
        Reflect.call(size, "setDisableWidth", width);
        Reflect.call(size, "setDisableHeight", height);
        Reflect.call(size, "setDisableExpandWidth", width);
        float radius = ((Number) Reflect.call(manager, "radius", 1)).floatValue();
        Reflect.call(size, "setRadius", radius);
        Reflect.call(size, "setPreviewRadius", radius / scale);
    }
}
