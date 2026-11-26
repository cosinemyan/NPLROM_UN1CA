// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.net.Uri;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class OneUi85WidgetLayout {
    final Field rotationOwner;
    final Method rotate, restore, editable, alignValue, picker, tablet, gridMetrics;

    OneUi85WidgetLayout(ClassLoader loader, Class<?> preview) throws ReflectiveOperationException {
        Class<?> align =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.briefwidget.WidgetAlignType",
                        false,
                        loader);
        Field found = null;
        for (Field field : preview.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || !NativeContract.hasMethod(field.getType(), boolean.class, align)) continue;
            if (found != null)
                throw new NoSuchFieldException("Ambiguous widget rotation use cases");
            found = field;
        }
        if (found == null) throw new NoSuchFieldException("Widget rotation use case missing");
        found.setAccessible(true);
        rotationOwner = found;
        rotate = NativeContract.findMethod(found.getType(), null, boolean.class, align);
        Class<?> gridData =
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.widget.WidgetGridData",
                        false,
                        loader);
        Class<?> gridRules =
                NativeContract.findClassInPackage(
                        loader,
                        WidgetEditorBinding.REPOSITORIES,
                        type ->
                                !type.isInterface()
                                        && !java.lang.reflect.Modifier.isAbstract(
                                                type.getModifiers())
                                        && !type.getName()
                                                .startsWith(
                                                        WidgetEditorBinding.REPOSITORIES
                                                                + "sublauncher.")
                                        && NativeContract.hasMethod(
                                                type,
                                                void.class,
                                                gridData,
                                                align,
                                                boolean.class,
                                                boolean.class));
        gridMetrics =
                NativeContract.findMethod(
                        gridRules, null, void.class, gridData, align, boolean.class, boolean.class);
        Class<?> model =
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.widget.WidgetData",
                        false,
                        loader);
        Class<?> profile =
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.profile.ProfileData",
                        false,
                        loader);
        Class<?> mode =
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.preview.UIMode",
                        false,
                        loader);
        Class<?> converter =
                NativeContract.findClass(
                        loader,
                        type ->
                                NativeContract.hasField(type, Uri.class)
                                        && NativeContract.hasMethod(type, model, profile, mode),
                        WidgetEditorBinding.REPOSITORIES + "O");
        restore = NativeContract.findMethod(converter, "c", model, profile, mode);
        editable = NativeContract.method(model, "isEditableAlign", boolean.class);
        Class<?> assist =
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockAssistData",
                        false,
                        loader);
        alignValue = NativeContract.method(assist, "getWidgetAlignTypeValue", align);
        Class<?> device =
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.system.DeviceType",
                        false,
                        loader);
        Class<?> eligibility =
                NativeContract.findClass(
                        loader,
                        type ->
                                !type.isInterface()
                                        && NativeContract.hasMethod(
                                                type,
                                                java.util.List.class,
                                                mode,
                                                device,
                                                boolean.class),
                        WidgetEditorBinding.REPOSITORIES + "h");
        picker =
                NativeContract.findMethod(
                        eligibility, "a", java.util.List.class, mode, device, boolean.class);
        tablet = NativeContract.method(device, "isTablet", boolean.class);
    }

    Object rotation(Object owner) throws IllegalAccessException {
        return rotationOwner.get(owner);
    }
}
