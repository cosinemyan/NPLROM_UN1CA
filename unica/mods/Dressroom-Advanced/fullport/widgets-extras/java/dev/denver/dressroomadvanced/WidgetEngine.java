// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

final class WidgetEngine {
    static final String GRID =
            "com.samsung.android.app.facewidget.complication.ComplicationGridUIController";
    static final String SIZE =
            "com.samsung.android.app.facewidget.complication.ComplicationWidgetSizeManager";
    private final DressroomModule module;
    private int runtimeScreen;
    private final Set<ClassLoader> installed = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<Object> grids = Collections.newSetFromMap(new WeakHashMap<>());
    private final ThreadLocal<Boolean> nativeMetrics = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Object> nativeArrangement = new ThreadLocal<>();
    private final java.util.Map<Object, Bundle> previewManagers = new WeakHashMap<>();
    private Bundle runtime;
    private int runtimeOrientation;
    private int runtimeWallpaper = -1;
    private Context context;
    private boolean refreshing;

    private record GridRefresh(Method method, boolean forceArgument) {}

    private static final ClassValue<GridRefresh> refreshBindings =
            new ClassValue<>() {
                @Override
                protected GridRefresh computeValue(Class<?> type) {
                    try {
                        try {
                            return new GridRefresh(
                                    NativeContract.method(
                                            type,
                                            "reconfigureGridLayout",
                                            void.class,
                                            boolean.class),
                                    true);
                        } catch (NoSuchMethodException ignored) {
                            return new GridRefresh(
                                    NativeContract.method(
                                            type, "reconfigureGridLayout", void.class),
                                    false);
                        }
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Unsupported native grid refresh", error);
                    }
                }
            };
    private final Set<ClassLoader> legacyProviders = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<ClassLoader> scaleInstalled = Collections.newSetFromMap(new WeakHashMap<>());

    WidgetEngine(DressroomModule module) {
        this.module = module;
    }

    Bundle settings(Context c) {
        if (module.editorWidgets != null && module.editorWidgets.active()) return module.editorWidgets.settings(c);
        int orientation = LayoutOrientation.current(c);
        int screen = ScreenIdentity.current();
        if (runtime == null || runtimeOrientation != orientation || runtimeScreen != screen) {
            runtime = WidgetPreferences.read(c, "");
            runtimeOrientation = orientation;
            runtimeScreen = screen;
            runtimeWallpaper = runtime.getInt("wallpaperId", -1);
        }
        return runtime;
    }

    void invalidate() {
        runtime = null;
    }

    boolean wallpaperChanged(Context c) {
        if (runtime == null || SettingsBridge.wallpaperId(c) == runtimeWallpaper) return false;
        invalidate();
        return true;
    }

    boolean main(Object manager) {
        try {
            Object display = Reflect.call(manager, "getWidgetDisplayType");
            return !(Boolean) Reflect.call(display, "isCover")
                    && !(Boolean) Reflect.call(display, "isInformative");
        } catch (Exception e) {
            return false;
        }
    }

    void nativePreview(Object manager, Bundle draft) {
        if (draft == null) { nativePreviewEnd(manager,false); }
        else previewManagers.put(manager, new Bundle(draft));
    }

    void nativePreviewEnd(Object manager,boolean committed) {
        WidgetProviderForms.endPreview(manager,committed);
        previewManagers.remove(manager);invalidate();
    }

    private Bundle config(Object manager) throws ReflectiveOperationException {
        Bundle preview = previewManagers.get(manager);
        if (preview != null) return preview;
        return settings((Context) Reflect.field(manager, "context"));
    }









    boolean fits(Object grid, Bundle candidate) throws ReflectiveOperationException {
        Object list = Reflect.call(grid, "getModuleInfo");
        if (!(list instanceof java.util.List<?> modules)) return false;
        int[][] spans = new int[modules.size()][2];
        for (int i = 0; i < modules.size(); i++) {
            spans[i][0] = ((Number) Reflect.call(modules.get(i), "getWidth")).intValue();
            spans[i][1] = ((Number) Reflect.call(modules.get(i), "getHeight")).intValue();
        }
        return WidgetLayout.fits(candidate.getInt("columns"), candidate.getInt("rows"), spans);
    }

    void refreshAll() {
        if (refreshing) return;
        refreshing = true;
        try {
            for (Object grid : new java.util.ArrayList<>(grids)) refresh(grid);
        } finally {
            refreshing = false;
        }
    }

    void refresh(Object grid) {
        try {
            refreshChecked(grid);
        } catch (Exception e) {
            module.report("Widget grid refresh failed", e);
        }
    }

    void refreshChecked(Object grid) throws ReflectiveOperationException {
        Object manager = Reflect.call(grid, "getWidgetSizeManager");
        if (!main(manager)) return;
        GridRefresh refresh = refreshBindings.get(grid.getClass());
        if (refresh.forceArgument()) {
            refresh.method().invoke(grid, true);
            notifyLegacyProviders(grid);
        } else {
            Reflect.call(manager, "init", Reflect.call(manager, "getWidgetDisplayType"));
            refresh.method().invoke(grid);
            notifyLegacyProviders(grid);
        }
    }

    static void validateLegacyGrid(Class<?> grid) throws ReflectiveOperationException {
        NativeContract.method(
                grid, "forEachWidgetView", void.class, java.util.function.Consumer.class);
        NativeContract.method(
                grid, "sendAppWidgetOptions", void.class, int.class, int.class, int.class);
    }

    private void notifyLegacyProviders(Object grid) throws ReflectiveOperationException {
        var attached = WidgetHostSizing.attached(grid);
        Object current = Reflect.call(grid, "getModuleInfo");
        if (!(current instanceof java.util.List<?> items)) return;
        for (Object item : items) {
            String uri = (String) Reflect.call(item, "getUri");
            int separator = uri == null ? -1 : uri.indexOf(';');
            if (separator <= 0) continue;
            int id;
            try {
                id = Integer.parseInt(uri.substring(0, separator));
            } catch (NumberFormatException ignored) {
                continue;
            }
            var host = attached.get(id);
            if (host == null) continue;
            int columns = ((Number) Reflect.call(item, "getWidth")).intValue();
            int rows = ((Number) Reflect.call(item, "getHeight")).intValue();
            Object manager = Reflect.call(grid, "getWidgetSizeManager");
            if(previewManagers.containsKey(manager))WidgetProviderForms.preview(manager,host,columns,rows);
            else WidgetProviderForms.refresh(host,columns,rows);
            WidgetHostSizing.refresh(host,columns,rows,manager,config(manager).getFloat("scale",1),
                    config(manager).getBoolean("layout"));
            Reflect.call(grid, "sendAppWidgetOptions", id, columns, rows);
        }
    }

    boolean repairPositions(Object grid) throws ReflectiveOperationException {
        Object manager = Reflect.call(grid, "getWidgetSizeManager");
        if (!main(manager) || !config(manager).getBoolean("layout")) return false;
        Object current = Reflect.call(grid, "getModuleInfo");
        if (!(current instanceof java.util.List<?> items)) return false;
        boolean vertical =
                Boolean.TRUE.equals(
                        Reflect.call(Reflect.field(grid, "widgetAlignType"), "isVertical"));
        int[][] cells = new int[items.size()][4];
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            cells[i][0] =
                    ((Number) Reflect.call(item, vertical ? "getSecondaryX" : "getX")).intValue();
            cells[i][1] =
                    ((Number) Reflect.call(item, vertical ? "getSecondaryY" : "getY")).intValue();
            cells[i][2] = ((Number) Reflect.call(item, "getWidth")).intValue();
            cells[i][3] = ((Number) Reflect.call(item, "getHeight")).intValue();
        }
        if (WidgetLayout.positionsValid(
                ((Number) Reflect.call(manager, "getColumn")).intValue(),
                ((Number) Reflect.call(manager, "getRow")).intValue(),
                cells)) return false;
        reflow(grid);
        return true;
    }

    void reflow(Object grid) throws ReflectiveOperationException {
        Object containers = Reflect.field(grid, "containerManager");
        Object id = Reflect.call(containers, "getContainerId");
        if (id == null) return;
        Object manager = Reflect.call(grid, "getGridManager");
        Object container = Reflect.call(manager, "b", id);
        if (container == null) return;
        Object items = Reflect.call(container, "f");
        Method rearrange =
                NativeContract.findMethod(
                        container.getClass(),
                        "o",
                        boolean.class,
                        java.util.List.class,
                        java.util.Set.class,
                        boolean.class);
        if (!Boolean.TRUE.equals(rearrange.invoke(container, items, Collections.emptySet(), true)))
            throw new IllegalStateException(
                    "Samsung could not fit these widgets. Increase the grid size.");
    }

    static Object savedAlignment(Object grid, boolean vertical)
            throws ReflectiveOperationException {
        Class<?> type =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.briefwidget.WidgetAlignType",
                        false,
                        grid.getClass().getClassLoader());
        NativeContract.field(grid.getClass(), "widgetAlignType", type);
        return enumValue(type, vertical ? "Vertical" : "Default");
    }

    static Object enumValue(Class<?> type, String name) {
        for (Object value : type.getEnumConstants())
            if (((Enum<?>) value).name().equals(name)) return value;
        throw new IllegalArgumentException("Missing native enum " + name);
    }

    private record MetricScope(boolean previous,Object alignment) {}
    Object nativeMetricsBegin(Object manager) {
        boolean previous=nativeMetrics.get();nativeMetrics.set(true);Object alignment=null;
        try {if(main(manager)&&config(manager).getBoolean("layout")){alignment=Reflect.field(manager,"widgetAlignType");Reflect.set(manager,"widgetAlignType",enumValue(alignment.getClass(),"Default"));}}
        catch(Exception failure){module.report("Widget metric scope",failure);}
        return new MetricScope(previous,alignment);
    }
    void nativeMetricsEnd(Object manager,Object token,boolean success) {
        MetricScope scope=(MetricScope)token;
        try {if(success)nativeInit(manager);}finally {try{if(scope.alignment()!=null)Reflect.set(manager,"widgetAlignType",scope.alignment());}catch(Exception failure){module.report("Widget alignment restore",failure);}nativeMetrics.set(scope.previous());}
    }
    Object arrangementBegin(Object requested) {Object previous=nativeArrangement.get();nativeArrangement.set(requested);return previous;}
    void arrangementEnd(Object previous) {if(previous==null)nativeArrangement.remove();else nativeArrangement.set(previous);}
    boolean nativeRefresh(Object layouts,Object requested) {
        try {Object grid=Reflect.field(layouts,"complicationGridUIController"),manager=Reflect.call(grid,"getWidgetSizeManager");if(!main(manager)||!config(manager).getBoolean("layout"))return false;
            Object actual=nativeArrangement.get();Reflect.call(grid,"changeAlignType",actual==null?requested:actual);reflow(grid);return true;
        }catch(Exception failure){module.report("Widget arrangement",failure);return false;}
    }
    void nativeCreated(Object factory,Object result) {
        try {WidgetHostSizing.created(result);if(result==null)return;Object manager=Reflect.field(factory,"widgetSizeManager");if(!main(manager))return;
            Object info=Reflect.call(result,"getWidgetInfo");for(var host:new java.util.HashSet<>(WidgetHostSizing.inView((android.view.View)Reflect.call(result,"getWidgetView")).values()))WidgetHostSizing.refresh(host,((Number)Reflect.call(info,"getWidth")).intValue(),((Number)Reflect.call(info,"getHeight")).intValue(),manager,config(manager).getFloat("scale",1));
        }catch(Exception failure){module.report("Widget host scaling",failure);}
    }
    void nativeGrid(Object grid, android.view.View root) {
        grids.add(grid);
        if (root != null) context = root.getContext().getApplicationContext();
    }
    int nativeDimension(Object manager, int original, boolean column) {
        try {
            if (nativeMetrics.get() || !main(manager)) return original;
            Bundle b = config(manager);
            if (!b.getBoolean("layout")) return original;
            boolean vertical = (Boolean) Reflect.call(Reflect.field(manager, "widgetAlignType"), "isVertical");
            int[] dims = WidgetLayout.dimensions(b.getInt("columns"), b.getInt("rows"), b.getBoolean("vertical"), vertical);
            return dims[column ? 0 : 1];
        } catch (Exception error) { module.report("Widget grid metric", error); return original; }
    }
    boolean nativeBigger(Object manager, boolean original) {
        try { return original || (!nativeMetrics.get() && main(manager) && config(manager).getBoolean("layout")); }
        catch (Exception error) { return original; }
    }
    void nativeInit(Object manager) {
        try {
            if (main(manager)) {
                float base = ((Number) Reflect.field(manager, "_scaleFactor")).floatValue();
                Reflect.set(manager, "_scaleFactor", base * config(manager).getFloat("scale", 1));
            }
        } catch (Exception error) { module.report("Widget scale", error); }
    }
    void beginMetrics() { nativeMetrics.set(true); }
    void endMetrics() { nativeMetrics.set(false); }
}
