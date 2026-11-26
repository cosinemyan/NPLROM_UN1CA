// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;

final class WidgetEditorBinding {
    static final String REPOSITORIES =
            "com.samsung.android.lockscreen.editor.item.data.repository.item.widget.";
    final Method toolbar, create, getGrid, metrics, position;
    private final Class<?> menuItem;

    WidgetEditorBinding(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> widgetData = entity(loader, "item.widget.WidgetData");
        Class<?> preview =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasField(cls, widgetData)
                                        && NativeContract.hasMethod(cls, View.class),
                        OneUi85WidgetEditor.PREVIEW);
        create = NativeContract.findMethod(preview, "d", View.class);
        NativeContract.findField(preview, "I", widgetData);
        NativeContract.findField(preview, "J", ViewGroup.class);
        NativeContract.findField(preview, "N", View.class);
        // The old rotation-host pair was never used here. Rotation is validated
        // against the actual preview's use case by OneUi85WidgetLayout.
        NativeContract.method(
                Class.forName(
                        "com.samsung.android.app.dressroom.presentation.preview.activity.EditMainActivity",
                        false,
                        loader),
                "dispatchTouchEvent",
                boolean.class,
                android.view.MotionEvent.class);
        Class<?> adapter = OneUi85WidgetEditor.adapter(loader);
        NativeContract.findStaticMethod(
                adapter,
                OneUi85WidgetEditor.ADAPTER_METHOD,
                void.class,
                View.class,
                int.class,
                int.class,
                float.class,
                int.class,
                int.class,
                boolean.class);
        Class.forName("androidx.databinding.ViewDataBinding", false, loader)
                .getDeclaredMethod("getBinding", View.class);
        menuItem =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasConstructor(
                                        cls, String.class, int.class, Runnable.class, String.class),
                        OneUi85WidgetEditor.MENU_ITEM);
        menuItem.getDeclaredConstructor(String.class, int.class, Runnable.class, String.class);
        Class<?> menuArray = Array.newInstance(menuItem, 0).getClass();
        Class<?> toolbarClass =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasField(cls, Context.class)
                                        && NativeContract.hasField(cls, menuArray)
                                        && NativeContract.hasConstructor(
                                                cls, android.app.Activity.class, View.class),
                        OneUi85WidgetEditor.TOOLBAR);
        toolbar = toolbarRenderer(loader, toolbarClass, menuArray);
        Class<?> uiController =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.appwidget.IWidgetUIController",
                        false,
                        loader);
        Class<?> gridController =
                NativeContract.findClass(
                        loader,
                        cls -> !cls.isInterface() && NativeContract.hasMethod(cls, uiController),
                        OneUi85WidgetEditor.GRID_CONTROLLER);
        getGrid =
                NativeContract.findMethod(
                        gridController, OneUi85WidgetEditor.GRID_METHOD, uiController);
        Class<?> gridType = entity(loader, "item.widget.WidgetGridData");
        Class<?> sizeType = entity(loader, "item.widget.WidgetSizeData");
        Class<?> previewScale = entity(loader, "system.PreviewScale");
        Class<?> widgetAssist = entity(loader, "item.widget.WidgetAssistData");
        Class<?> screenSize = entity(loader, "system.ScreenSize");
        Class<?> deviceType = entity(loader, "system.DeviceType");
        Class<?> uiMode = entity(loader, "preview.UIMode");
        Class<?> moduleRect = entity(loader, "item.common.ModuleRectInfo");
        Class<?> clockAssist = entity(loader, "item.clock.ClockAssistData");

        Class<?> metricsClass =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && !cls.getName().startsWith(REPOSITORIES + "sublauncher.")
                                        && NativeContract.hasMethod(
                                                cls,
                                                void.class,
                                                gridType,
                                                sizeType,
                                                boolean.class,
                                                previewScale,
                                                boolean.class,
                                                widgetAssist,
                                                boolean.class,
                                                screenSize,
                                                deviceType,
                                                boolean.class,
                                                uiMode,
                                                moduleRect),
                        REPOSITORIES + "K");
        metrics =
                NativeContract.findMethod(
                        metricsClass,
                        "a",
                        void.class,
                        gridType,
                        sizeType,
                        boolean.class,
                        previewScale,
                        boolean.class,
                        widgetAssist,
                        boolean.class,
                        screenSize,
                        deviceType,
                        boolean.class,
                        uiMode,
                        moduleRect);
        NativeContract.findField(
                metrics.getDeclaringClass(), "d", Class.forName("kotlin.Lazy", false, loader));

        Class<?> positionClass =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && !cls.getName().startsWith(REPOSITORIES + "sublauncher.")
                                        && NativeContract.hasMethod(
                                                cls,
                                                void.class,
                                                widgetData,
                                                widgetAssist,
                                                clockAssist,
                                                uiMode,
                                                boolean.class,
                                                screenSize,
                                                boolean.class,
                                                moduleRect),
                        REPOSITORIES + "z");
        position =
                NativeContract.findMethod(
                        positionClass,
                        "b",
                        void.class,
                        widgetData,
                        widgetAssist,
                        clockAssist,
                        uiMode,
                        boolean.class,
                        screenSize,
                        boolean.class,
                        moduleRect);
    }

    private static Class<?> entity(ClassLoader loader, String suffix)
            throws ClassNotFoundException {
        return Class.forName(
                "com.samsung.android.lockscreen.editor.item.domain.entity." + suffix,
                false,
                loader);
    }

    private static Method toolbarRenderer(ClassLoader loader, Class<?> toolbar, Class<?> menuArray)
            throws ReflectiveOperationException {
        var methods = new java.util.LinkedHashSet<Method>();
        String owner = "L" + toolbar.getName().replace('.', '/') + ";";
        String array = "[L" + menuArray.getComponentType().getName().replace('.', '/') + ";";
        try {
            for (Class<?> container :
                    new Class<?>[] {android.widget.LinearLayout.class, ViewGroup.class})
                for (DexSignatures.Body body :
                        DexSignatures.callers(loader, container, "removeAllViews", void.class)) {
                    if (!body.owner().equals(owner) || !body.matches()) continue;
                    for (DexSignatures.Read read : body.reads())
                        if (read.field().type().equals(array)) {
                            methods.add(body.method(loader));
                            break;
                        }
                }
        } catch (java.io.IOException invalidDex) {
            throw new ReflectiveOperationException(
                    "Could not read widget toolbar renderer", invalidDex);
        }
        if (methods.size() != 1)
            throw new NoSuchMethodException("Ambiguous widget toolbar renderer: " + methods.size());
        return methods.iterator().next();
    }

    Object data(Object owner) throws ReflectiveOperationException {
        return Reflect.call(repo(owner), "getData");
    }

    Object repo(Object owner) throws ReflectiveOperationException {
        return Reflect.field(Reflect.field(owner, "n"), "a");
    }

    Object grid(Object owner) throws ReflectiveOperationException {
        return getGrid.invoke(Reflect.call(Reflect.field(owner, "a"), "get"));
    }

    boolean main(Object owner) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(
                Reflect.call(
                        Reflect.call(Reflect.call(Reflect.field(owner, "c"), "b"), "getData"),
                        "isLockScreen"));
    }

    void recalculate(Object owner) throws ReflectiveOperationException {
        Reflect.call(Reflect.field(owner, "B"), "a");
    }

    void finalizeList(Object owner) throws ReflectiveOperationException {
        Reflect.call(owner, "b");
    }

    void addMenu(Object owner, Runnable open, String tag) throws ReflectiveOperationException {
        Object current = Reflect.field(owner, "f");
        if (current == null || Array.getLength(current) == 0) return;
        ArrayList<Object> items = new ArrayList<>();
        for (int i = 0; i < Array.getLength(current); i++) {
            Object item = Array.get(current, i);
            if (tag.equals(Reflect.call(item, "j"))) return;
            items.add(item);
        }
        Context context = (Context) Reflect.field(owner, "a");
        int icon =
                context.getResources()
                        .getIdentifier(
                                "preview_menu_icon_effect", "drawable", context.getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_menu_manage;
        Class<?> itemType = current.getClass().getComponentType();
        if (itemType == null) itemType = menuItem;
        items.add(
                itemType.getDeclaredConstructor(
                                String.class, int.class, Runnable.class, String.class)
                        .newInstance("Advanced", icon, open, tag));
        Object updated = Array.newInstance(itemType, items.size());
        for (int i = 0; i < items.size(); i++) Array.set(updated, i, items.get(i));
        Reflect.set(owner, "f", updated);
    }

    static void metrics(Object size, Object grid, Object manager)
            throws ReflectiveOperationException {
        WidgetBoundaryMetrics.apply(size, grid, manager,
                ((Number) Reflect.call(manager, "getColumn")).intValue(),
                ((Number) Reflect.call(manager, "getRow")).intValue());
    }
}
