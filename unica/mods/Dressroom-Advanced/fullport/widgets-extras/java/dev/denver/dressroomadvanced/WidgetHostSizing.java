// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.appwidget.AppWidgetHostView;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Map;

final class WidgetHostSizing {
    private WidgetHostSizing() {}

    private static final String CONTAINER =
            "com.samsung.android.app.facewidget.complication.ComplicationWidgetHostViewContainer";
    private static final Map<AppWidgetHostView, Float> appliedZoom = new java.util.WeakHashMap<>();
    private static final Map<AppWidgetHostView, Integer> savedIds = new java.util.WeakHashMap<>();

    static java.lang.reflect.Method creation(ClassLoader loader)
            throws ReflectiveOperationException {
        String base = "com.samsung.android.app.facewidget.complication.ComplicationWidgetFactory";
        Class<?> factory = Class.forName(base, false, loader),
                result = Class.forName(base + "$WidgetCreationResult", false, loader);
        Class<?> info =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.module.ModuleInfo",
                        false,
                        loader);
        Class<?> display =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.briefwidget.WidgetDisplayType",
                        false,
                        loader);
        Class<?> background =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.briefwidget.WidgetBackgroundInfo",
                        false,
                        loader);
        Class<?> facade =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.appwidget.IAppWidgetFacade",
                        false,
                        loader);
        NativeContract.method(result, "getDbWidgetId", int.class);
        NativeContract.method(result, "getWidgetView", View.class);
        NativeContract.field(
                factory, "widgetSizeManager", Class.forName(WidgetEngine.SIZE, false, loader));
        try {
            return NativeContract.method(
                    factory, "createWidget", result, info, display, background, facade);
        } catch (NoSuchMethodException legacyMissing) {
            return NativeContract.method(
                    factory,
                    "createWidget",
                    result,
                    android.content.Context.class,
                    info,
                    display,
                    background,
                    facade);
        }
    }

    static void created(Object result) throws ReflectiveOperationException {
        if (result == null) return;
        int savedId = ((Number) Reflect.call(result, "getDbWidgetId")).intValue();
        Map<Integer, AppWidgetHostView> hosts = new HashMap<>();
        collect((View) Reflect.call(result, "getWidgetView"), hosts);
        for (AppWidgetHostView host : hosts.values()) savedIds.put(host, savedId);
    }

    static void validate(ClassLoader loader) throws ReflectiveOperationException {
        creation(loader);
        Class<?> manager = Class.forName(WidgetEngine.SIZE, false, loader);
        Class<?> spec =
                Class.forName(
                        "com.samsung.android.app.facewidget.complication.WidgetSizeSpec",
                        false,
                        loader);
        try {
            NativeContract.method(manager, "calculateWidgetSizeSpec", spec, int.class, int.class);
        } catch (NoSuchMethodException reference) {
            try {
                if (!legacyProvider(loader)) throw reference;
                NativeContract.method(manager, "getWidgetSizeSpec", spec, int.class, int.class);
            } catch (NoSuchMethodException oldestMissing) {
                OneUi9AodBlur.widgetSizing(loader, spec);
            }
        }
        Class<?> type = Class.forName(CONTAINER, false, loader);
        NativeContract.field(
                type, "widgetSizeManager", Class.forName(WidgetEngine.SIZE, false, loader));
        NativeContract.method(type, "initConfigurationValues", void.class);
        NativeContract.method(type, "setCellRoundLayoutParams", void.class, View.class);
    }

    static Map<Integer, AppWidgetHostView> attached(Object grid)
            throws ReflectiveOperationException {
        Map<Integer, AppWidgetHostView> hosts = new HashMap<>();
        java.util.function.Consumer<View> collect = view -> collect(view, hosts);
        Reflect.call(grid, "forEachWidgetView", collect);
        return hosts;
    }

    static Map<Integer, AppWidgetHostView> inView(View root) {
        Map<Integer, AppWidgetHostView> hosts = new HashMap<>();
        collect(root, hosts);
        return hosts;
    }

    private static void collect(View view, Map<Integer, AppWidgetHostView> hosts) {
        if (view instanceof AppWidgetHostView host) {
            hosts.put(host.getAppWidgetId(), host);
            Integer savedId = savedIds.get(host);
            if (savedId != null && savedId != Integer.MIN_VALUE) hosts.put(savedId, host);
            return;
        }
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), hosts);
    }

    static void refresh(AppWidgetHostView host, int columns, int rows, Object manager, float zoom)
            throws ReflectiveOperationException {
        refresh(host, columns, rows, manager, zoom, false);
    }

    static void refresh(
            AppWidgetHostView host,
            int columns,
            int rows,
            Object manager,
            float zoom,
            boolean force)
            throws ReflectiveOperationException {
        // A saved custom grid can arrive after 8.5 created the host with stock metrics.
        // Its viewport must be refreshed even when the user's zoom is exactly 100%.
        if (!force && zoom == 1 && !appliedZoom.containsKey(host)) return;
        float scaled = ((Number) Reflect.field(manager, "_scaleFactor")).floatValue();
        Object spec;
        try {
            Reflect.set(manager, "_scaleFactor", scaled / zoom);
            try {
                spec = Reflect.call(manager, "calculateWidgetSizeSpec", columns, rows);
            } catch (NoSuchMethodException reference) {
                try {
                    // Older 8.5 providers calculate the viewport directly in this getter.
                    // The modern getter is conditional, so it must retain the static fallback.
                    if (!legacyProvider(manager.getClass().getClassLoader())) throw reference;
                    spec = Reflect.call(manager, "getWidgetSizeSpec", columns, rows);
                } catch (NoSuchMethodException oldestMissing) {
                    ClassLoader loader = manager.getClass().getClassLoader();
                    Class<?> specType =
                            Class.forName(
                                    "com.samsung.android.app.facewidget.complication.WidgetSizeSpec",
                                    false,
                                    loader);
                    var method = OneUi9AodBlur.widgetSizing(loader, specType);
                    method.setAccessible(true);
                    spec =
                            method.invoke(
                                    null,
                                    host.getContext(),
                                    columns,
                                    rows,
                                    Reflect.call(manager, "unitSize", columns),
                                    Reflect.call(manager, "unitSize", rows),
                                    false);
                }
                if (spec == null)
                    throw new ReflectiveOperationException(
                            "Native widget size spec is unavailable");
            }
        } finally {
            Reflect.set(manager, "_scaleFactor", scaled);
        }
        spec =
                Reflect.call(
                        spec,
                        "copy",
                        number(spec, "getPxWidth"),
                        number(spec, "getPxHeight"),
                        number(spec, "getScaleX") * zoom,
                        number(spec, "getScaleY") * zoom);
        if (hasNativeResize(host, spec)) {
            Reflect.call(host, "setResizeScale", spec);
        } else {
            ViewGroup.LayoutParams params = host.getLayoutParams();
            params.width = (int) number(spec, "getPxWidth");
            params.height = (int) number(spec, "getPxHeight");
            host.setLayoutParams(params);
            host.setPivotX(
                    host.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? params.width : 0);
            host.setPivotY(0);
            host.setScaleX(number(spec, "getScaleX"));
            host.setScaleY(number(spec, "getScaleY"));
        }
        if (zoom == 1) appliedZoom.remove(host);
        else appliedZoom.put(host, zoom);
        if (!(host.getParent() instanceof ViewGroup container)
                || !container.getClass().getName().equals(CONTAINER)) return;
        Reflect.call(container, "setWidgetCellSpan", columns, rows);
        Object backgroundManager = Reflect.field(container, "widgetSizeManager");
        Reflect.call(
                backgroundManager, "init", Reflect.call(backgroundManager, "getWidgetDisplayType"));
        Reflect.call(container, "initConfigurationValues");
        ViewGroup.LayoutParams params = container.getLayoutParams();
        params.width = ((Number) Reflect.field(container, "containerCellWidth")).intValue();
        params.height = ((Number) Reflect.field(container, "containerCellHeight")).intValue();
        container.setLayoutParams(params);
        int padding = ((Number) Reflect.field(container, "padding")).intValue();
        container.setPadding(padding, padding, padding, padding);
        for (String name : new String[] {"blurView", "dimView", "previewDimView"}) {
            Object child = Reflect.field(container, name);
            if (!(child instanceof View view)) continue;
            Reflect.call(container, "setCellRoundLayoutParams", view);
            if (view.getBackground() instanceof GradientDrawable shape)
                shape.setCornerRadius(((Number) Reflect.field(container, "radius")).floatValue());
        }
    }

    private static boolean legacyProvider(ClassLoader loader) throws ClassNotFoundException {
        return ClockBinding.of(
                        Class.forName(
                                "com.samsung.android.uniform.widget.clock.AbsClockView",
                                false,
                                loader))
                .legacy;
    }

    private static boolean hasNativeResize(View host, Object spec) {
        try {
            NativeContract.method(host.getClass(), "setResizeScale", void.class, spec.getClass());
            return true;
        } catch (NoSuchMethodException legacy) {
            return false;
        }
    }

    private static float number(Object owner, String method) throws ReflectiveOperationException {
        return ((Number) Reflect.call(owner, method)).floatValue();
    }
}
