package dev.denver.dressroomadvanced;

import android.view.View;

final class OneUi85WidgetEditor {
    static final String PREVIEW = "ib.l0";
    static final String ADAPTER = "lb.c";
    static final String ADAPTER_METHOD = "r";
    static final String TOOLBAR = "V3.v";
    static final String TOOLBAR_METHOD = "H";
    static final String MENU_ITEM = "T2.H";
    static final String GRID_CONTROLLER = "O5.i";
    static final String GRID_METHOD = "r";
    static final String PANEL_MENU = "T2.K";

    private OneUi85WidgetEditor() {}

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
                ADAPTER);
    }
}
