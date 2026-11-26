// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class NativeRuntimeWidgets {
    private final DressroomModule module;
    private final Set<ClassLoader> installed = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Object, State> controllers = new WeakHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.atomic.AtomicBoolean observing =
            new java.util.concurrent.atomic.AtomicBoolean();
    private final DeferredRefresh<Object> pending =
            new DeferredRefresh<>(task -> main.post(task), this::refresh);
    private final Set<Class<?>> legacyControllers = Collections.newSetFromMap(new WeakHashMap<>());

    private static final class State {
        WeakReference<ViewGroup> root = new WeakReference<>(null);
        final RootLayoutWatch layoutWatch = new RootLayoutWatch();
        int type = -1, font = -1, orientation = -1, screen = -1;
        boolean loaded;
        Bundle depthProfile;
        Boolean depthEnabled, maskReady;
        String layout = "";
    }

    NativeRuntimeWidgets(DressroomModule module) {
        this.module = module;
        ScreenIdentity.observe(() -> main.post(() -> refreshAll(true)));
    }





    private void schedule(Object controller, boolean force) {
        WeakReference<Object> reference = new WeakReference<>(controller);
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(
                    () -> {
                        Object value = reference.get();
                        if (value != null) schedule(value, force);
                    });
            return;
        }
        pending.offer(controller, force);
    }



    void refreshAll(boolean force) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> refreshAll(force));
            return;
        }
        if (force) module.widgetEngine.invalidate();
        for (Object controller : new ArrayList<>(controllers.keySet())) refresh(controller, force);
    }



    void clear(Object controller) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> clear(controller));
            return;
        }
        pending.cancel(controller);
        State state = controllers.remove(controller);
        if (state != null) state.layoutWatch.clear();
        ViewGroup root = state == null ? null : state.root.get();
        if (root != null) {
            if (legacyControllers.contains(controller.getClass())) {
                DepthEffects.applyWidgetRoot(root, false, null);
                WidgetPlacement.clear(root);
                return;
            }
            WidgetMediaOcclusion.forget(root);
            DepthEffects.applyWidgetRoot(root, false, null);
            WidgetDepthBlur.blur(root, false);
            WidgetPlacement.clear(root);
        }
    }

    void refresh(Object controller, boolean force) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            WeakReference<Object> reference = new WeakReference<>(controller);
            boolean requested = force;
            main.post(
                    () -> {
                        Object value = reference.get();
                        if (value != null) refresh(value, requested);
                    });
            return;
        }
        try {
            if (legacyControllers.contains(controller.getClass())) {
                refreshLegacy(controller, force);
                return;
            }
            Object container = Reflect.field(controller, "container");
            if (!(container instanceof ViewGroup root)) return;
            Object nativeGrid = Reflect.field(controller, "complicationGridUIController");
            if (!module.widgetEngine.main(Reflect.call(nativeGrid, "getWidgetSizeManager"))) {
                clear(controller);
                return;
            }
            State state = controllers.computeIfAbsent(controller, ignored -> new State());
            int orientation = LayoutOrientation.current(root.getContext());
            int screen = ScreenIdentity.current();
            if (state.orientation != orientation || state.screen != screen) {
                state.screen = screen;
                state.orientation = orientation;
                force = true;
            }
            if (state.root.get() != root) {
                ViewGroup previous = state.root.get();
                if (previous != null) {
                    WidgetMediaOcclusion.forget(previous);
                    DepthEffects.applyWidgetRoot(previous, false, null);
                    WidgetDepthBlur.blur(previous, false);
                    WidgetPlacement.clear(previous);
                }
                state.root = new WeakReference<>(root);
                state.loaded = false;
                watchLayout(controller, root);
            }
            WidgetMediaOcclusion.register(root);
            if (observing.compareAndSet(false, true)) {
                root.getContext()
                        .getContentResolver()
                        .registerContentObserver(
                                SettingsProvider.URI,
                                false,
                                new ContentObserver(main) {
                                    @Override
                                    public void onChange(boolean selfChange) {
                                        refreshAll(true);
                                    }
                                });
            }
            Object provider = Reflect.field(controller, "keyguardClockProvider");
            Object item = Reflect.field(provider, "clockItem");
            if (item == null) return;
            int type = ((Number) Reflect.call(item, "getClockType")).intValue();
            Object preset = Reflect.call(Reflect.call(item, "getFont"), "getPresetFontIndex");
            int font = preset instanceof Number ? ((Number) preset).intValue() : 0;
            if (!force && state.loaded && state.type == type && state.font == font) return;
            Bundle query = new Bundle();
            query.putInt("type", type);
            query.putInt("font", font);
            Bundle saved = SettingsBridge.read(root.getContext(), query);
            if (force) module.widgetEngine.invalidate();
            Bundle widgets = module.widgetEngine.settings(root.getContext());
            if (widgets.getBoolean("layout") || state.layout.startsWith("true:")) {
                Object nativeData =
                        Reflect.call(Reflect.field(controller, "aodDataManager"), "get");
                Object align = Reflect.call(Reflect.field(nativeData, "G"), "getValue");
                Object grid = Reflect.field(controller, "complicationGridUIController");
                if (!widgets.getBoolean("layout"))
                    align = WidgetEngine.enumValue(align.getClass(), "Default");
                if (align != Reflect.field(grid, "widgetAlignType")) {
                    Reflect.call(grid, "changeAlignType", align);
                    try {
                        module.widgetEngine.reflow(grid);
                    } catch (Exception e) {
                        module.report("Widget reflow skipped", e);
                    }
                }
            }
            String layout =
                    widgets.getBoolean("layout")
                            + ":"
                            + widgets.getInt("columns")
                            + ":"
                            + widgets.getInt("rows")
                            + ":"
                            + widgets.getBoolean("vertical")
                            + ":"
                            + widgets.getFloat("scale", 1);
            if (!layout.equals(state.layout)) {
                state.layout = layout;
                module.widgetEngine.refreshAll();
            }
            state.type = type;
            state.font = font;
            state.loaded = true;
            boolean enabled = WidgetPreferences.depth(widgets, saved);
            DepthEffects.applyWidgetRoot(root, enabled, WidgetPreferences.mask(widgets, saved));
            WidgetDepthBlur.blur(root, widgets.getBoolean("blurOff"));
            WidgetPlacement.apply(root, widgets, provider);
        } catch (Exception error) {
            module.report("Native widget depth synchronization unavailable", error);
        }
    }

    private void refreshLegacy(Object controller, boolean force)
            throws ReflectiveOperationException {
        Object container = Reflect.field(controller, "container");
        if (!(container instanceof ViewGroup root)) return;
        Object grid = Reflect.field(controller, "complicationGridUIController");
        if (!module.widgetEngine.main(Reflect.call(grid, "getWidgetSizeManager"))) {
            clear(controller);
            return;
        }
        State state = controllers.computeIfAbsent(controller, ignored -> new State());
        int orientation = LayoutOrientation.current(root.getContext());
        int screen = ScreenIdentity.current();
        if (state.orientation != orientation || state.screen != screen) {
            state.screen = screen;
            state.orientation = orientation;
            force = true;
        }
        if (state.root.get() != root) {
            View previous = state.root.get();
            if (previous != null) {
                DepthEffects.applyWidgetRoot(previous, false, null);
                WidgetPlacement.clear(previous);
            }
            state.root = new WeakReference<>(root);
            state.loaded = false;
            state.type = state.font = -1;
            state.depthProfile = null;
            state.depthEnabled = state.maskReady = null;
            watchLayout(controller, root);
        }
        if (observing.compareAndSet(false, true)) {
            root.getContext()
                    .getContentResolver()
                    .registerContentObserver(
                            SettingsProvider.URI,
                            false,
                            new ContentObserver(main) {
                                @Override
                                public void onChange(boolean selfChange) {
                                    refreshAll(true);
                                }
                            });
        }
        if (force) module.widgetEngine.invalidate();
        Bundle settings = module.widgetEngine.settings(root.getContext());
        boolean alignChanged = false;
        if (settings.getBoolean("layout")) {
            Object align = Reflect.field(grid, "widgetAlignType");
            // On 8.5 phones Samsung leaves the grid alignment unset until it receives
            // an explicit layout. Resolve the enum from its declaration, not that value.
            Object target = WidgetEngine.savedAlignment(grid, settings.getBoolean("vertical"));
            if (align != target) {
                Reflect.call(grid, "changeAlignType", target);
                alignChanged = true;
            }
        }
        String layout = WidgetPreferences.layoutKey(settings);
        if (!state.loaded || alignChanged || !layout.equals(state.layout)) {
            module.widgetEngine.refreshChecked(grid);
            module.widgetEngine.repairPositions(grid);
            state.layout = layout;
            state.loaded = true;
        }
        Object provider = Reflect.field(controller, "keyguardClockProvider");
        Class<?> base =
                Class.forName(
                        "com.samsung.android.uniform.widget.clock.AbsClockView",
                        false,
                        grid.getClass().getClassLoader());
        if (DepthBinding.provider(base)) {
            Object item = Reflect.field(provider, "clockItem");
            if (item != null) {
                int type = ((Number) Reflect.call(item, "getClockType")).intValue();
                Object preset = Reflect.call(Reflect.call(item, "getFont"), "getPresetFontIndex");
                int font = preset instanceof Number ? ((Number) preset).intValue() : 0;
                if (force
                        || type != state.type
                        || font != state.font
                        || state.depthProfile == null) {
                    Bundle query = new Bundle();
                    query.putInt("type", type);
                    query.putInt("font", font);
                    state.depthProfile = SettingsBridge.read(root.getContext(), query);
                    state.type = type;
                    state.font = font;
                }
                boolean enabled = WidgetPreferences.depth(settings, state.depthProfile);
                byte[] mask = WidgetPreferences.mask(settings, state.depthProfile);
                DepthEffects.applyWidgetRoot(root, enabled, mask);
                boolean ready = mask != null;
                if (!Boolean.valueOf(enabled).equals(state.depthEnabled)
                        || !Boolean.valueOf(ready).equals(state.maskReady)) {
                    state.depthEnabled = enabled;
                    state.maskReady = ready;
                    android.util.Log.i(
                            DressroomModule.TAG,
                            "8.5 widget depth: enabled="
                                    + enabled
                                    + " mask="
                                    + ready
                                    + " type="
                                    + type
                                    + " font="
                                    + font
                                    + " owner="
                                    + root.getClass().getSimpleName());
                }
            } else {
                // A provider can briefly have no item while replacing the wallpaper/clock.
                state.type = state.font = -1;
                state.depthProfile = null;
                state.depthEnabled = state.maskReady = false;
                DepthEffects.applyWidgetRoot(root, false, null);
            }
        }
        WidgetPlacement.apply(root, settings, provider);
    }

    private void watchLayout(Object controller, ViewGroup root) {
        WeakReference<Object> reference = new WeakReference<>(controller);
        State state = controllers.get(controller);
        state.layoutWatch.bind(
                root,
                (view, l, t, r, b, oldL, oldT, oldR, oldB) -> {
                    Object owner = reference.get();
                    State current = owner == null ? null : controllers.get(owner);
                    if (current != null
                            && current.root.get() == view
                            && (current.orientation != LayoutOrientation.current(view.getContext())
                                    || current.screen != ScreenIdentity.current()))
                        schedule(owner, true);
                });
    }
}
