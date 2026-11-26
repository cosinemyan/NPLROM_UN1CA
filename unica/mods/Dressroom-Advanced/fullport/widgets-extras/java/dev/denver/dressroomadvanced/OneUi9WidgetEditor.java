// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.net.Uri;
import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;

final class OneUi9WidgetEditor {
    static final String TOOLBAR = "v8.y";
    static final String TOOLBAR_METHOD = "l";
    static final String GRID_WRAPPER = "pc.h";
    static final String PREVIEW = "op.m";
    static final String PREVIEW_METHOD = "b";
    static final String STORE = "sd.q";
    static final String ROTATION_TRIMMED = "rh.a";
    static final String LIFECYCLE = "cg.n";
    static final String LIFECYCLE_ARG = "gf.c";
    static final String ROTATION = "cg.v";
    static final String GUIDE = "sp.y1";
    static final String GRID_METRICS = "sd.h";
    static final String CELL_METRICS = "sd.o";
    static final String PLACEMENT = "sd.m";
    static final String ALIGN_FEEDBACK = "yc.b";
    static final String ADAPTER = "rp.e";
    static final String ADAPTER_METHOD = "b";
    static final String PICKER_SIZE = "sd.d";
    static final String MENU_ITEM = "k6.m";
    static final String PANEL_MENU = "k6.o";

    private OneUi9WidgetEditor() {}

    private record Preview(java.lang.reflect.Field root, java.lang.reflect.Field inner,
            java.lang.reflect.Field data) {}
    private static final ClassValue<Preview> previews = new ClassValue<>() {
        @Override protected Preview computeValue(Class<?> owner) {
            try {
                Class<?> data = entity(owner.getClassLoader(), "item.widget.WidgetData");
                java.lang.reflect.Field root;
                try { root = NativeContract.assignableField(owner, "L", View.class); }
                catch (NoSuchFieldException changed) { root = NativeContract.findField(owner, "K", View.class); }
                java.lang.reflect.Field inner;
                try { inner = NativeContract.assignableField(owner, "H", android.widget.FrameLayout.class); }
                catch (NoSuchFieldException changed) { inner = NativeContract.findField(owner, "G", android.widget.FrameLayout.class); }
                return new Preview(root, inner, NativeContract.findField(owner, "G", data));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Unsupported widget preview ownership", error);
            }
        }
    };
    static View root(Object owner) throws IllegalAccessException { return (View) previews.get(owner.getClass()).root.get(owner); }
    static View inner(Object owner) throws IllegalAccessException { return (View) previews.get(owner.getClass()).inner.get(owner); }
    static Object data(Object owner) throws IllegalAccessException { return previews.get(owner.getClass()).data.get(owner); }


    private static Class<?> entity(ClassLoader loader, String suffix)
            throws ClassNotFoundException {
        return Class.forName(
                "com.samsung.android.lockscreen.editor.item.domain.entity." + suffix,
                false,
                loader);
    }

    static Method toolbar(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> baseActivity =
                Class.forName(
                        "com.samsung.android.app.dressroom.presentation.preview.activity.EditBaseActivity",
                        false,
                        loader);
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls -> NativeContract.hasConstructor(cls, baseActivity, View.class),
                        TOOLBAR);
        return NativeContract.findMethod(owner, TOOLBAR_METHOD, void.class);
    }

    static Class<?> gridWrapper(ClassLoader loader) throws ClassNotFoundException {
        Class<?> uiController =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.appwidget.IWidgetUIController",
                        false,
                        loader);
        return NativeContract.findClass(
                loader,
                cls -> !cls.isInterface() && NativeContract.hasMethod(cls, uiController),
                GRID_WRAPPER);
    }

    static Method getGrid(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> uiController =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.appwidget.IWidgetUIController",
                        false,
                        loader);
        return NativeContract.findMethod(gridWrapper(loader), "d", uiController);
    }

    static Method providerAlign(ClassLoader loader, Class<?> alignType)
            throws ReflectiveOperationException {
        return NativeContract.findMethod(gridWrapper(loader), "a", void.class, alignType);
    }

    static Method create(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> widgetData = entity(loader, "item.widget.WidgetData");
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasField(cls, widgetData)
                                        && NativeContract.hasMethod(cls, View.class),
                        PREVIEW, "lp.l");
        return NativeContract.findMethod(owner, PREVIEW_METHOD, View.class);
    }

    static Method restore(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> widgetData = entity(loader, "item.widget.WidgetData");
        Class<?> profileData = entity(loader, "profile.ProfileData");
        Class<?> uiMode = entity(loader, "preview.UIMode");
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && NativeContract.hasField(cls, Uri.class)
                                        && NativeContract.hasMethod(
                                                cls, widgetData, profileData, uiMode),
                        STORE, "sd.p");
        return NativeContract.findMethod(owner, "b", widgetData, profileData, uiMode);
    }

    static Method portraitPolicy(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> owner = NativeContract.findClass(loader,
                cls -> cls.isInterface() && NativeContract.hasMethod(cls, int.class)
                        && NativeContract.hasMethod(cls, void.class, int.class)
                        && NativeContract.hasMethod(cls, boolean.class), "df.b");
        return NativeContract.findMethod(owner, "a", boolean.class);
    }

    static Method rotationTrimmed(ClassLoader loader, Class<?> alignType)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.getName().startsWith("com.samsung.")
                                        && NativeContract.hasMethod(cls, alignType),
                        ROTATION_TRIMMED);
        return NativeContract.findMethod(owner, "i", alignType);
    }

    static Method lifecycle(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> argType =
                NativeContract.findClass(loader, Class::isEnum, LIFECYCLE_ARG);
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls -> NativeContract.hasMethod(cls, void.class, argType),
                        LIFECYCLE);
        return NativeContract.findMethod(owner, "c", void.class, argType);
    }

    static Method rotate(ClassLoader loader, Class<?> alignType)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls -> NativeContract.hasMethod(cls, boolean.class, alignType),
                        ROTATION, "cg.u");
        return NativeContract.findMethod(owner, "a", boolean.class, alignType);
    }

    static Method guide(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> dataBinding = Class.forName("androidx.databinding.ViewDataBinding", false, loader);
        // A generic binding/no-arg shape matches unrelated panels. Unknown guide owners
        // must not hide an arbitrary editor view or prevent the widget controls loading.
        Class<?> owner = NativeContract.knownClass(loader,
                cls -> dataBinding.isAssignableFrom(cls)
                        && NativeContract.hasMethod(cls, void.class), GUIDE);
        if (owner == null) throw new ClassNotFoundException("Optional widget rotation guide");
        return NativeContract.findMethod(owner, "executeBindings", void.class);
    }

    static Method gridMetrics(ClassLoader loader, Class<?> gridType, Class<?> alignType)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && NativeContract.hasMethodPrefix(
                                                cls, void.class, gridType, alignType),
                        GRID_METRICS, "sd.g");
        return NativeContract.findMethodPrefix(owner, "a", void.class, gridType, alignType);
    }

    static Method cellMetrics(ClassLoader loader, Class<?> gridType, Class<?> sizeType)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && NativeContract.hasMethodPrefix(
                                                cls, void.class, gridType, sizeType),
                        CELL_METRICS, "sd.n");
        return NativeContract.findMethodPrefix(owner, "a", void.class, gridType, sizeType);
    }

    static Method placement(ClassLoader loader, Class<?> widgetDataType, Class<?> widgetAssistType)
            throws ReflectiveOperationException {
        Class<?> clockAssist = entity(loader, "item.clock.ClockAssistData");
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && NativeContract.hasMethodPrefix(
                                                cls,
                                                void.class,
                                                widgetDataType,
                                                widgetAssistType,
                                                clockAssist),
                        PLACEMENT, "sd.l");
        return NativeContract.findMethodPrefix(
                owner, "f", void.class, widgetDataType, widgetAssistType, clockAssist);
    }

    static Method alignFeedback(ClassLoader loader, Class<?> widgetDataType)
            throws ReflectiveOperationException {
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        // The sub-clock repository shares this signature but
                                        // has no boolean state; require it to avoid binding it.
                                        && NativeContract.hasField(cls, boolean.class)
                                        && NativeContract.hasMethod(
                                                cls, void.class, widgetDataType),
                        ALIGN_FEEDBACK);
        return NativeContract.findMethod(owner, "c", void.class, widgetDataType);
    }

    static Class<?> adapter(ClassLoader loader) throws ClassNotFoundException {
        return NativeContract.findClass(
                loader,
                cls ->
                        NativeContract.hasStaticMethod(
                                cls,
                                void.class,
                                View.class,
                                int.class,
                                int.class,
                                float.class,
                                int.class,
                                int.class,
                                boolean.class),
                ADAPTER, "u2.a");
    }

    static Method pickerScope(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> uiMode = entity(loader, "preview.UIMode");
        Class<?> deviceType = entity(loader, "system.DeviceType");
        Class<?> owner =
                NativeContract.findClass(
                        loader,
                        cls ->
                                !cls.isInterface()
                                        && NativeContract.hasMethod(
                                                cls, ArrayList.class, uiMode, deviceType),
                        PICKER_SIZE, "sd.c");
        return NativeContract.findMethod(owner, "a", ArrayList.class, uiMode, deviceType);
    }
}
