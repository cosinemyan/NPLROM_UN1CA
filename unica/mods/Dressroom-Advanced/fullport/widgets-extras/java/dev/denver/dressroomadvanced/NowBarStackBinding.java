// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

/** Checked entry points for the stack engines; obfuscated names live with their provider. */
final class NowBarStackBinding {
    record Names(
            String model,
            String states,
            String manager,
            String stackSize,
            String keys,
            String key,
            String items,
            String restricted,
            String mode,
            String screenOn,
            String dragging,
            String reordering,
            String dismissing,
            String expanding,
            String animationManager,
            String animationsEnabled,
            String animate,
            boolean countedAnimate,
            String bindItem,
            String hideContents,
            String wave,
            String resources) {}

    final Class<?> stack, card, model;
    final Field items, restricted, mode, screenOn, dragging, reordering, dismissing, expanding;
    final Field animationManager, animationsEnabled, state, animator;
    final Method refresh,
            animate,
            expanded,
            content,
            data,
            item,
            key,
            blur,
            bindItem,
            hideContents,
            wave;
    final Constructor<?> stackSize;
    final String resources;

    static NowBarStackBinding of(Class<?> clock) throws ReflectiveOperationException {
        ClockBinding provider = ClockBinding.of(clock);
        return new NowBarStackBinding(
                clock.getClassLoader(),
                provider.legacy
                        ? (provider.clockStyle ? OneUi85Stack.names() : OneUi85AodStack.names())
                        : (provider.clockStyle
                                ? OneUi9ClockStyleStack.names()
                                : OneUi9Stack.names()));
    }

    NowBarStackBinding(ClassLoader loader, Names names) throws ReflectiveOperationException {
        stack =
                Class.forName(
                        "com.samsung.android.app.facewidget.utils.NowBarCardStackView",
                        false,
                        loader);
        Class<?> itemType =
                Class.forName(
                        "com.android.systemui.plugins.keyguardstatusview.NowBarItem",
                        false,
                        loader);
        card =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasMethod(cls, ViewGroup.class)
                                        && NativeContract.hasMethod(cls, itemType),
                        "com.samsung.android.app.facewidget.presentation.a");
        model =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasField(cls, Animator.class)
                                        && NativeContract.hasMethod(card, cls),
                        names.model, "aod.kx4");
        Class<?> manager =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasField(stack, cls)
                                        && (NativeContract.hasMethod(
                                                        cls,
                                                        void.class,
                                                        View.class,
                                                        model,
                                                        int.class)
                                                || NativeContract.hasMethod(
                                                        cls, void.class, View.class, model)),
                        names.manager, "aod.cy6");
        items = NativeContract.findField(stack, names.items, ArrayList.class);
        restricted = NativeContract.field(stack, names.restricted, boolean.class);
        mode = NativeContract.findField(stack, names.mode, Enum.class);
        screenOn = NativeContract.field(stack, names.screenOn, boolean.class);
        dragging = NativeContract.field(stack, names.dragging, boolean.class);
        reordering = NativeContract.field(stack, names.reordering, boolean.class);
        dismissing = NativeContract.field(stack, names.dismissing, boolean.class);
        expanding = NativeContract.field(stack, names.expanding, boolean.class);
        animationManager = NativeContract.findField(stack, names.animationManager, manager);
        animationsEnabled =
                NativeContract.findField(manager, names.animationsEnabled, boolean.class);
        state = NativeContract.findField(model, "a", Enum.class);
        animator = NativeContract.findField(model, "f", Animator.class);
        refresh = NativeContract.findMethod(stack, "q", void.class, CopyOnWriteArrayList.class);
        animate =
                names.countedAnimate
                        ? NativeContract.findMethod(
                                manager, names.animate, void.class, View.class, model, int.class)
                        : NativeContract.findMethod(
                                manager, names.animate, void.class, View.class, model);
        expanded = NativeContract.findMethod(stack, "k", boolean.class);
        content = NativeContract.findMethod(card, "getNormalContentRootView", ViewGroup.class);
        data = NativeContract.findMethod(card, "getShowHideAnimationData", model);
        item = NativeContract.findMethod(card, "getNowbarItem", itemType);
        blur = NativeContract.findMethod(card, "getBlurContainer", View.class);
        bindItem =
                NativeContract.findMethod(
                        card, names.bindItem, void.class, itemType, Integer.class);
        hideContents =
                NativeContract.findMethod(
                        card, names.hideContents, void.class, int.class, boolean.class);
        key = resolveKey(loader, itemType, names);
        Class<?> stackSizeClass =
                NativeContract.findClass(
                        loader,
                        cls ->
                                NativeContract.hasConstructor(cls, stack, int.class)
                                        && NativeContract.hasMethod(cls, Object.class),
                        names.stackSize, "aod.px4");
        stackSize = stackSizeClass.getDeclaredConstructor(stack, int.class);
        stackSize.setAccessible(true);
        Method mediaWave = null;
        if (names.wave != null) {
            Class<?> media = Class.forName(
                    "com.samsung.android.app.facewidget.presentation.NowBarMediaCardView", false, loader);
            try { mediaWave = NativeContract.method(media, names.wave, void.class); }
            catch (NoSuchMethodException renamed) {
                mediaWave = NativeContract.findMethod(media, "w0", void.class);
            }
        }
        wave = mediaWave;
        resources = names.resources;
    }

    private static Method resolveKey(ClassLoader loader, Class<?> itemType, Names names)
            throws ReflectiveOperationException {
        Class<?> known =
                NativeContract.knownClass(
                        loader,
                        type -> NativeContract.hasStaticMethod(type, String.class, itemType),
                        names.keys);
        if (known != null)
            return NativeContract.findStaticMethod(known, names.key, String.class, itemType);
        var matches = new java.util.LinkedHashSet<Method>();
        Method getter = NativeContract.method(itemType, "getNowBarKey", String.class);
        String item = "L" + itemType.getName().replace('.', '/') + ";";
        try {
            for (DexSignatures.Body body : DexSignatures.callers(getter)) {
                if ((body.access() & 8) == 0
                        || !body.result().equals("Ljava/lang/String;")
                        || !body.parameters().equals(java.util.List.of(item))) continue;
                var getters = new java.util.HashSet<String>();
                for (DexSignatures.Call call : body.calls())
                    if (!call.isStatic()
                            && call.owner().equals(item)
                            && call.parameters().isEmpty()
                            && call.result().equals("Ljava/lang/String;")) getters.add(call.name());
                // Card identity combines these three values. Package names and debug
                // labels have the same outer signature but read a different set.
                if (getters.containsAll(
                        java.util.Set.of("getNotiID", "getNowBarKey", "getNowBarPackage")))
                    matches.add(body.method(loader));
            }
        } catch (java.io.IOException invalidDex) {
            throw new ReflectiveOperationException(
                    "Could not read Now Bar identity signature", invalidDex);
        }
        if (matches.size() != 1)
            throw new NoSuchMethodException("Ambiguous Now Bar identity: " + matches.size());
        return matches.iterator().next();
    }
}
