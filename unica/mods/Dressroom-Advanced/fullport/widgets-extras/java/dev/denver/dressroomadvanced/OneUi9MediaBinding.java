// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.reflect.Method;

final class OneUi9MediaBinding {
    static final String HOLDER = "aod.fa2",
            COORDINATOR = "aod.ag4",
            ANIMATOR_LISTENER = "aod.o8",
            EXPAND_LAMBDA = "aod.n82",
            PILL_BOUNDS = "aod.di1";

    private OneUi9MediaBinding() {}

    static Class<?> holder(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> page =
                Class.forName(
                        "com.samsung.android.app.facewidget.pages.music.FaceWidgetMusicPage",
                        false,
                        loader);
        Class<?> model =
                Class.forName(
                        "com.samsung.android.app.facewidget.pages.music.FaceWidgetMusicViewModel",
                        false,
                        loader);
        Class<?> mask =
                Class.forName(
                        "com.samsung.android.uniform.nowbar.MediaMaskingFrameLayout",
                        false,
                        loader);
        return NativeContract.findClass(
                loader,
                type ->
                        NativeContract.hasField(type, page)
                                && NativeContract.hasField(type, model)
                                && NativeContract.hasField(type, mask),
                HOLDER, "aod.u92");
    }

    static Class<?> coordinator(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> lighting =
                Class.forName(
                        "com.samsung.android.uniform.nowbar.MediaLightingView", false, loader);
        Class<?> mask =
                Class.forName(
                        "com.samsung.android.uniform.nowbar.MediaMaskingFrameLayout",
                        false,
                        loader);
        return NativeContract.findClass(
                loader,
                type ->
                        NativeContract.hasConstructor(
                                        type, Context.class, Handler.class, boolean.class)
                                && NativeContract.hasMethod(
                                        type,
                                        void.class,
                                        FrameLayout.class,
                                        lighting,
                                        mask,
                                        ImageView.class,
                                        ImageView.class),
                COORDINATOR, "aod.wf4");
    }

    static Method animatorUpdate(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> listener =
                NativeContract.findClass(
                        loader,
                        type ->
                                ValueAnimator.AnimatorUpdateListener.class.isAssignableFrom(type)
                                        && NativeContract.hasConstructor(
                                                type, int.class, Object.class),
                        ANIMATOR_LISTENER, "aod.k8");
        return listener.getDeclaredMethod("onAnimationUpdate", ValueAnimator.class);
    }

    static Method expandInvoke(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> lambda =
                NativeContract.findClass(
                        loader,
                        type ->
                                NativeContract.hasMethod(type, Object.class)
                                        && NativeContract.hasConstructor(
                                                type,
                                                Object.class,
                                                Object.class,
                                                Object.class,
                                                Object.class,
                                                int.class),
                        EXPAND_LAMBDA, "aod.a82");
        return NativeContract.findMethod(lambda, "invoke", Object.class);
    }

    static Method pillBounds(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> dbItem =
                Class.forName(
                        "com.samsung.android.app.aodservice.common.provider.DB.LockWidgetDBItem",
                        false,
                        loader);
        Class<?> helper =
                NativeContract.findClass(
                        loader,
                        type ->
                                NativeContract.hasStaticMethod(
                                                type, boolean.class, View.class, int.class, int.class)
                                        && NativeContract.hasStaticMethod(
                                                type, dbItem, String.class),
                        PILL_BOUNDS, "aod.dp1");
        return NativeContract.findStaticMethod(
                helper, "D", boolean.class, View.class, int.class, int.class);
    }
}
