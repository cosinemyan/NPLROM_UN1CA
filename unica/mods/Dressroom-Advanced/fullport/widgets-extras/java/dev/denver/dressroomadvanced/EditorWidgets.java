// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class EditorWidgets {
    private static final String TAG = "dressroom.advanced.widgets.menu";
    private final DressroomModule module;
    private WeakReference<Object> owner = new WeakReference<>(null);
    private WeakReference<Object> ui = new WeakReference<>(null);
    private WeakReference<Context> host = new WeakReference<>(null);
    private Bundle draft, clock;
    private String scene;
    private boolean dirty;
    private final WidgetDepthPolicies depthPolicies = new WidgetDepthPolicies();
    private final java.util.Map<String, Bundle> layoutDrafts = new java.util.HashMap<>();
    private final java.util.Set<String> dirtyLayouts = new java.util.HashSet<>();
    private final ThreadLocal<Boolean> nativePreview = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Boolean> largePicker = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Boolean> nativeLifecycle = ThreadLocal.withInitial(() -> false);
    private final java.util.WeakHashMap<Object, Boolean> previewManagers = new java.util.WeakHashMap<>();
    private final WidgetItemGestures itemGestures = new WidgetItemGestures();
    private final java.util.WeakHashMap<Object, Boolean> previewCandidates = new java.util.WeakHashMap<>();
    private float groupPinchSpan;
    private boolean groupPinching;
    private Bundle dragStart;
    private android.view.MotionEvent dragDown;
    private float dragX, dragY, dragModelX, dragModelY;
    private boolean dragging;
    private boolean dragWasDirty;
    private long lastDrag;
    private WidgetEditorBinding legacyBinding;
    private boolean legacyScaleReady, legacyGridReady, legacyLayoutReady;
    private OneUi85WidgetLayout legacyLayout;
    private final WidgetRotationGuide rotationGuide;
    private Boolean nativeRotationCapability;

    boolean customGridAvailable() {
        return !legacy() || legacyGridReady;
    }

    private static Object optionalDeviceRole(Object device, String role)
            throws ReflectiveOperationException {
        try {
            return Reflect.call(device, role);
        } catch (NoSuchMethodException unavailable) {
            return Boolean.FALSE;
        }
    }

    boolean nativeRotationAvailable() {
        if (nativeRotationCapability != null) return nativeRotationCapability;
        Context context = host.get();
        return context != null
                && context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    private Method pendingBinding;
    private final WidgetScalePreview scalePreview = new WidgetScalePreview();
    private final RootLayoutWatch previewLayoutWatch = new RootLayoutWatch();

    EditorWidgets(DressroomModule module) {
        this.module = module;
        rotationGuide = new WidgetRotationGuide(() -> !nativeRotationAvailable());
    }

    boolean active() { return owner.get() != null; }

    boolean legacy() {
        return legacyBinding != null;
    }

    boolean stackAvailable() {
        if (!legacy()) return true;
        try {
            NowBarStackBinding.of(
                    Class.forName(
                            "com.samsung.android.uniform.widget.clock.AbsClockView",
                            false,
                            grid().getClass().getClassLoader()));
            return true;
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    boolean depthAvailable() {
        if (!legacy()) return true;
        try {
            return module.depthAvailable(
                    Class.forName(
                            "com.samsung.android.uniform.widget.clock.AbsClockView",
                            false,
                            grid().getClass().getClassLoader()));
        } catch (ReflectiveOperationException | RuntimeException unavailable) {
            return false;
        }
    }

    boolean depthImageAvailable() {
        return module.depthImageAvailable();
    }

    void clearDepth(int screen) {
        java.util.function.Consumer<Bundle> clear = value -> {
            DepthSafety.clearMask(value);
            if (value.getInt("depth") == 1) value.putInt("depth", 2);
        };
        EditorDepthSources.forScreen(layoutDrafts.values(), screen, clear);
        if (draft != null && ScreenIdentity.value(draft) == screen) clear.accept(draft);
        depthPolicies.clearDepth(screen);
        if (draft != null && ScreenIdentity.value(draft) == screen) {
            clock = null;
            applyPolicy();
            applyEffects();
        }
    }

    String rowTag(EditorBinding.Row row) {
        return module.editorBinding().rowTag(row);
    }

    void reset() {
        clearManagerPreviews();
        rotationGuide.clear();
        nativeRotationCapability = null;
        cancelScalePreview();
        previewLayoutWatch.clear();
        owner.clear();
        ui.clear();
        host.clear();
        draft = clock = null;
        scene = null;
        dirty = false;
        layoutDrafts.clear();
        dirtyLayouts.clear();
        depthPolicies.clear();
        itemGestures.clear();
        groupPinchSpan = 0;
        groupPinching = false;
        dragStart = null;
        dragging = false;
        legacyScaleReady = false;
        legacyGridReady = false;
        if (dragDown != null) {
            dragDown.recycle();
            dragDown = null;
        }
        DepthEffects.setEditorWidgetPolicy(null, null);
    }

    Bundle settings(Context context) {
        Context application = context.getApplicationContext();
        host = new WeakReference<>(application == null ? context : application);
        String current = module.scene();
        int orientation = module.layoutOrientation(context);
        int screen = ScreenIdentity.current();
        if (draft == null
                || !current.equals(scene)
                || draft.getInt(LayoutOrientation.FIELD) != orientation
                || ScreenIdentity.value(draft) != screen) {
            clearManagerPreviews();
            if (!current.equals(scene)) depthPolicies.clear();
            String key = current + ":" + orientation + ":" + screen;
            Bundle previous = layoutDrafts.get(key);
            draft =
                    previous == null
                            ? WidgetPreferences.read(context, current, orientation)
                            : new Bundle(previous);
            scene = current;
            if (previous == null && module.depthProfileInvalid(draft)) {
                DepthSafety.clearMask(draft);
                if (draft.getInt("depth") == 1) draft.putInt("depth", 2);
            }
            dirty = dirtyLayouts.contains(key);
            clock = null;
            if (!depthPolicies.contains(screen)) adoptDepthPolicy(draft);
            overlayDepthPolicy(draft);
            applyPolicy();
        }
        return draft;
    }

    void layoutChanging() {
        clearManagerPreviews();
        cancelScalePreview();
        if (draft == null) return;
        String key = scene + ":" + ScreenIdentity.draft(draft);
        Bundle frozen = DepthEffects.freezeMask(draft);
        frozen.putString("scene", scene);
        layoutDrafts.put(key, frozen);
        if (dirty) dirtyLayouts.add(key);
    }

    void clockProfile(Context context, Bundle profile) {
        settings(context);
        clock = profile == null ? null : new Bundle(profile);
        if (clock != null
                && clock.getByteArray("depthMask") != null
                && draft.getInt("depth") == 1) {
            draft.putByteArray("depthMask", clock.getByteArray("depthMask").clone());
            DepthMetadata.copy(clock, draft);
        }
        applyPolicy();
        applyEffects();
    }

    private void applyPolicy() {
        if (draft == null) return;
        int mode = draft.getInt("depth");
        Boolean policy = mode == 0 ? null : Boolean.valueOf(mode == 1);
        if (!WidgetPreferences.covers(draft)) policy = Boolean.FALSE;
        DepthEffects.setEditorWidgetPolicy(policy, WidgetPreferences.mask(draft, clock));
    }

    private void adoptDepthPolicy(Bundle value) {
        depthPolicies.adopt(value);
    }

    private void overlayDepthPolicy(Bundle value) {
        depthPolicies.overlay(value);
    }

    Bundle freeze() {
        cancelScalePreview();
        if (draft == null) return null;
        DepthEffects.requireSaveReady(draft);
        try {
            if (dirty && owner.get() != null) {
                if ((Boolean) Reflect.call(data(), "getWidgetAlignChanging"))
                    throw new IllegalStateException(
                            "Wait for widget rotation to finish before Apply.");
                if (legacy()) legacyBinding.finalizeList(owner.get());
                else Reflect.call(owner.get(), "c");
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not finalize Samsung widget positions", e);
        }
        Bundle b = DepthEffects.freezeMask(WidgetPreferences.clean(draft));
        DepthEffects.requireSaveReady(b);
        b.putString("scene", module.scene());
        layoutDrafts.put(scene + ":" + ScreenIdentity.draft(b), b);
        java.util.ArrayList<Bundle> layouts = new java.util.ArrayList<>();
        for (var entry : layoutDrafts.entrySet()) {
            if (module.scene().equals(entry.getValue().getString("scene", ""))) {
                Bundle layout = new Bundle(entry.getValue());
                overlayDepthPolicy(layout);
                layouts.add(layout);
            }
        }
        Bundle batch = new Bundle();
        batch.putParcelableArrayList("layoutProfiles", layouts);
        return batch;
    }

    Context context() {
        return host.get();
    }

    EditorExtras extras() {
        return module.editorExtras;
    }

    void committed() {
        clearManagerPreviews(true);
        dirty = false;
        dirtyLayouts.clear();
    }

    boolean vertical() throws ReflectiveOperationException {
        return (Boolean) Reflect.call(Reflect.call(data(), "getWidgetAlignType"), "isVertical");
    }

    void rotate() throws Exception {
        if (!draft.getBoolean("layout"))
            throw new IllegalStateException("Enable Custom grid to unlock rotation on this phone.");
        Object target =
                WidgetEngine.enumValue(
                        Reflect.call(data(), "getWidgetAlignType").getClass(),
                        vertical() ? "Default" : "Vertical");
        Object result =
                legacy()
                        ? legacyLayout.rotate.invoke(legacyLayout.rotation(owner.get()), target)
                        : Reflect.call(Reflect.field(owner.get(), "y"), "a", target);
        if (!Boolean.TRUE.equals(result))
            throw new IllegalStateException("Wait for the current layout change to finish.");
    }

    void orient(Bundle b, boolean vertical) throws ReflectiveOperationException {
        int[] dimensions =
                WidgetLayout.dimensions(
                        b.getInt("columns"), b.getInt("rows"), b.getBoolean("vertical"), vertical);
        int columns = dimensions[0], rows = dimensions[1];
        Object value = Reflect.call(grid(), "getModuleInfo");
        if (value instanceof List<?> items)
            for (Object item : items) {
                columns = Math.max(columns, ((Number) Reflect.call(item, "getWidth")).intValue());
                rows = Math.max(rows, ((Number) Reflect.call(item, "getHeight")).intValue());
            }
        if (vertical) rows = Math.max(rows, columns + 1);
        else columns = Math.max(columns, rows);
        b.putBoolean("vertical", vertical);
        b.putInt("columns", columns);
        b.putInt("rows", rows);
    }

    private Object grid() throws ReflectiveOperationException {
        Object item = owner.get();
        if (item == null) throw new IllegalStateException("Widget preview is not ready");
        if (legacy()) return legacyBinding.grid(item);
        Object wrapper = Reflect.call(Reflect.field(item, "a"), "get");
        return Reflect.call(wrapper, "d");
    }

    private Object data() throws ReflectiveOperationException {
        if (legacy()) return legacyBinding.data(owner.get());
        return Reflect.call(Reflect.field(Reflect.field(owner.get(), "l"), "a"), "getData");
    }

    void update(Bundle next, boolean structural) throws Exception {
        cancelScalePreview();
        Context c = host.get();
        if (c == null || owner.get() == null)
            throw new IllegalStateException("Widget preview is not ready");
        if (!next.containsKey(LayoutOrientation.FIELD) && draft != null)
            LayoutOrientation.copy(draft, next);
        if (!next.containsKey(ScreenIdentity.FIELD) && draft != null)
            ScreenIdentity.copy(draft, next);
        if (ScreenIdentity.value(next) != ScreenIdentity.current())
            throw new IllegalStateException("Reopen widget controls after switching screens.");
        if (next.getInt(LayoutOrientation.FIELD) != module.layoutOrientation(c))
            throw new IllegalStateException("Layout changed. Open Advanced again.");
        next = WidgetPreferences.clean(next);
        if (structural) {
            if (!customGridAvailable())
                throw new IllegalStateException("Custom grid integration is not ready");
            if (!next.getBoolean("layout") && vertical())
                throw new IllegalArgumentException(
                        "Rotate to Horizontal before restoring the stock grid");
            if (next.getBoolean("layout")
                    && !WidgetLayout.matchesOrientation(
                            next.getInt("columns"), next.getInt("rows"), vertical()))
                throw new IllegalArgumentException("Use Rotate to change the arrangement");
            if (!module.widgetEngine.fits(grid(), next))
                throw new IllegalArgumentException(
                        "The widgets do not fit. Increase the grid first");
        }
        if (legacy()) {
            if (!legacyScaleReady) throw new IllegalStateException("Widget sizing is not ready.");
            if (WidgetPreferences.covers(next)
                    && next.getInt("depth") == 1
                    && WidgetPreferences.mask(next, clock) == null)
                throw new IllegalStateException(
                        "Detect a wallpaper subject in Clock → Advanced first.");
            Bundle previous = draft;
            boolean previousDirty = dirty;
            draft = next;
            dirty = true;
            adoptDepthPolicy(next);
            try {
                if (structural) {
                    restoreModel(data());
                    Reflect.call(data(), "setEditableAlign", next.getBoolean("layout"));
                    module.widgetEngine.refreshChecked(grid());
                    module.widgetEngine.reflow(grid());
                    legacyBinding.finalizeList(owner.get());
                } else if (previous == null
                        || previous.getFloat("scale", 1) != next.getFloat("scale", 1))
                    module.widgetEngine.refreshChecked(grid());
                recalculate();
                applyPolicy();
                applyEffects();
                if (structural
                        || previous == null
                        || previous.getInt("depth") != draft.getInt("depth")
                        || previous.getInt("depthLayouts") != draft.getInt("depthLayouts"))
                    module.widgetDepthChanged();
            } catch (Exception error) {
                draft = previous;
                dirty = previousDirty;
                adoptDepthPolicy(previous);
                applyPolicy();
                applyEffects();
                try {
                    module.widgetEngine.refreshChecked(grid());
                    recalculate();
                } catch (Exception rollback) {
                    error.addSuppressed(rollback);
                }
                throw error;
            }
            return;
        }
        if (structural && !next.getBoolean("layout") && vertical())
            throw new IllegalArgumentException(
                    "Rotate back to Horizontal before restoring the stock phone grid.");
        if (structural
                && next.getBoolean("layout")
                && !WidgetLayout.matchesOrientation(
                        next.getInt("columns"), next.getInt("rows"), vertical()))
            throw new IllegalArgumentException(
                    vertical()
                            ? "A vertical grid needs more rows than columns. Use Rotate to change"
                                    + " arrangement."
                            : "A horizontal grid needs at least as many columns as rows. Use Rotate"
                                    + " to change arrangement.");
        if (structural && !module.widgetEngine.fits(grid(), next))
            throw new IllegalArgumentException(
                    "The current widgets do not fit this grid. Remove a widget or increase"
                            + " rows/columns.");
        if (WidgetPreferences.covers(next) && next.getInt("depth") == 1) {
            byte[] mask = WidgetPreferences.mask(next, clock);
            if (mask == null)
                throw new IllegalStateException(
                        "Detect a wallpaper subject in Clock → Advanced → Depth effect first.");
            next.putByteArray("depthMask", mask.clone());
        }
        Bundle previous = draft;
        boolean wasDirty = dirty;
        draft = next;
        dirty = true;
        adoptDepthPolicy(next);
        try {
            Object activeGrid = grid();
            syncManagerPreview(Reflect.call(activeGrid, "getWidgetSizeManager"));
            if (structural) {
                Object model = data();
                restoreModel(model);
                Reflect.call(model, "setEditableAlign", draft.getBoolean("layout"));
                module.widgetEngine.refreshChecked(grid());
                module.widgetEngine.reflow(grid());
                Reflect.call(
                        Reflect.field(owner.get(), "p"),
                        "a"); // Refresh native widget list after reflow.
            } else if (previous == null
                    || previous.getFloat("scale", 1) != draft.getFloat("scale", 1)) {
                module.widgetEngine.refreshChecked(grid());
            }
            recalculate();
            applyPolicy();
            applyEffects();
            if (structural
                    || previous == null
                    || previous.getInt("depth") != draft.getInt("depth")
                    || previous.getInt("depthLayouts") != draft.getInt("depthLayouts"))
                module.widgetDepthChanged();
        } catch (Exception e) {
            draft = previous;
            dirty = wasDirty;
            adoptDepthPolicy(previous);
            try { syncManagerPreview(Reflect.call(grid(), "getWidgetSizeManager")); restoreModel(data()); }
            catch (Exception rollback) { e.addSuppressed(rollback); }
            applyPolicy();
            try {
                module.widgetEngine.refresh(grid());
                recalculate();
                applyEffects();
            } catch (Exception rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        }
    }

    void previewScale(float zoom) throws ReflectiveOperationException {
        Object item = owner.get();
        if (item == null || draft == null) return;
        if ((legacy() ? Reflect.field(item, "N") : OneUi9WidgetEditor.root(item)) instanceof View view)
            scalePreview.show(
                    view, draft.getFloat("scale", 1), WidgetLayout.finite(zoom, 1, .5f, 2));
    }

    void cancelScalePreview() {
        scalePreview.clear();
    }

    void resetAdjustments() throws Exception {
        Bundle b = WidgetPreferences.defaults();
        if (legacy()) {
            update(b, legacyGridReady);
            return;
        }
        b.putBoolean("vertical", false);
        if (!module.widgetEngine.fits(grid(), b))
            throw new IllegalStateException(
                    "The selected widgets need more than the stock 4 × 1 grid.");
        update(b, true);
    }

    private void recalculate() throws ReflectiveOperationException {
        if (legacy()) {
            if (owner.get() != null) legacyBinding.recalculate(owner.get());
            return;
        }
        Object item = owner.get();
        if (item != null) Reflect.call(Reflect.field(item, "z"), "a");
    }

    void applyEffects() {
        Object item = owner.get();
        if (item == null || draft == null) return;
        try {
            if (legacy()) {
                if (Reflect.field(item, "N") instanceof View root)
                    rotationGuide.update(root.getRootView());
                if (depthAvailable() && Reflect.field(item, "N") instanceof View root) {
                    DepthEffects.applyEditorWidgets(
                            root,
                            clock != null && clock.getBoolean("depthEnabled"),
                            clock == null ? null : clock.getByteArray("depthMask"));
                    DepthEffects.applyWidgetRoot(
                            root,
                            WidgetPreferences.depth(draft, clock),
                            WidgetPreferences.mask(draft, clock));
                }
                return;
            }
            Object view = OneUi9WidgetEditor.root(item);
            if (view instanceof View root)
                DepthEffects.applyEditorWidgets(
                        root,
                        clock != null && clock.getBoolean("depthEnabled"),
                        clock == null ? null : clock.getByteArray("depthMask"));
            Object inner = OneUi9WidgetEditor.inner(item);
            if (inner instanceof View root) WidgetDepthBlur.blur(root, draft.getBoolean("blurOff"));
        } catch (Exception e) {
            module.report("Widget editor effects unavailable", e);
        }
    }

    private boolean isMain(Object item) {
        if (legacy()) {
            try {
                return legacyBinding.main(item);
            } catch (ReflectiveOperationException error) {
                return false;
            }
        }
        try {
            return (Boolean)
                    Reflect.call(Reflect.call(Reflect.field(item, "c"), "b"), "isLockScreen");
        } catch (Exception e) {
            return false;
        }
    }





    private void queueClockDepthRefresh() {
        Object current = owner.get();
        if (current == null) return;
        try {
            Object root = (legacy() ? Reflect.field(current, "N") : OneUi9WidgetEditor.root(current));
            long generation = module.generation();
            if (root instanceof View view)
                view.postOnAnimation(
                        () -> {
                            if (generation == module.generation() && owner.get() == current)
                                module.widgetDepthChanged();
                        });
        } catch (ReflectiveOperationException error) {
            module.report("Widget rotation depth refresh unavailable", error);
        }
    }





    private void placeLegacy(Object model, Object assist, Object screen)
            throws ReflectiveOperationException {
        Rect clockRect = (Rect) Reflect.call(assist, "getClockPositionRect");
        float width = ((Number) Reflect.call(model, "getWidth")).floatValue();
        float height = ((Number) Reflect.call(model, "getHeight")).floatValue();
        if (draft.getBoolean("layout")) {
            Object size = Reflect.call(model, "getWidgetSizeData");
            width = ((Number) Reflect.call(size, "getContainerWidth")).floatValue();
            height = ((Number) Reflect.call(size, "getContainerHeight")).floatValue();
            Reflect.call(model, "setWidth", Math.round(width));
            Reflect.call(model, "setHeight", Math.round(height));
        }
        float sw = ((Number) Reflect.call(screen, "getWidth")).floatValue();
        float sh = ((Number) Reflect.call(screen, "getHeight")).floatValue();
        if (draft.getInt("anchor") == 0) {
            int x = ((Number) Reflect.call(model, "getX")).intValue();
            int y = ((Number) Reflect.call(model, "getY")).intValue();
            x = Math.max(0, Math.min(Math.max(0, Math.round(sw - width)), x));
            y = Math.max(0, Math.min(Math.max(0, Math.round(sh - height)), y));
            Reflect.call(model, "setX", x);
            Reflect.call(model, "setY", y);
            Reflect.call(
                    model,
                    "setWidgetPositionRect",
                    new Rect(x, y, x + Math.round(width), y + Math.round(height)));
            return;
        }
        Context context = host.get();
        if (context == null) return;
        float density =
                context.getResources().getDisplayMetrics().density
                        * sw
                        / context.getResources().getDisplayMetrics().widthPixels;
        float[] point =
                WidgetLayout.place(
                        draft.getInt("anchor"),
                        clockRect.left,
                        clockRect.top,
                        clockRect.right,
                        clockRect.bottom,
                        width,
                        height,
                        sw,
                        sh,
                        draft.getFloat("gap", 8) * density,
                        draft.getFloat("freeX", .5f),
                        draft.getFloat("freeY", .4f),
                        draft.getFloat("dockOffset") * density);
        int x = Math.round(point[0]), y = Math.round(point[1]);
        Reflect.call(model, "setX", x);
        Reflect.call(model, "setY", y);
        Reflect.call(
                model,
                "setWidgetPositionRect",
                new Rect(x, y, x + Math.round(width), y + Math.round(height)));
    }

    private void restoreModel(Object model) throws ReflectiveOperationException {
        if (draft == null) return;
        boolean vertical =
                (Boolean) Reflect.call(Reflect.call(model, "getWidgetAlignType"), "isVertical");
        int[] dimensions =
                WidgetLayout.dimensions(
                        draft.getInt("columns"),
                        draft.getInt("rows"),
                        draft.getBoolean("vertical"),
                        vertical);
        draft.putBoolean("vertical", vertical);
        draft.putInt("columns", dimensions[0]);
        draft.putInt("rows", dimensions[1]);
        if (!draft.getBoolean("layout")) return;
        Reflect.call(model, "setEditableAlign", true);
        Object grid = Reflect.call(model, "getWidgetGridData");
        Reflect.call(grid, "setCol", draft.getInt("columns"));
        Reflect.call(grid, "setRow", draft.getInt("rows"));
    }







    private void moveGroup(float rawX, float rawY) throws Exception {
        float dx, dy;
        if (legacy()) {
            View root = (View) Reflect.field(owner.get(), "N");
            android.graphics.Matrix global = new android.graphics.Matrix(),
                    inverse = new android.graphics.Matrix();
            root.transformMatrixToGlobal(global);
            if (!global.invert(inverse)) return;
            float[] delta = {rawX, rawY};
            inverse.mapVectors(delta);
            dx = delta[0];
            dy = delta[1];
        } else {
            float previewScale =
                    ((Number) Reflect.field(Reflect.field(owner.get(), "g"), "r")).floatValue();
            if (!Float.isFinite(previewScale) || previewScale <= 0) return;
            dx = rawX / previewScale;
            dy = rawY / previewScale;
        }
        Bundle next = new Bundle(dragStart);
        int anchor = next.getInt("anchor");
        if (anchor >= 1 && anchor <= 4) {
            float density = context().getResources().getDisplayMetrics().density;
            next.putFloat(
                    "dockOffset", next.getFloat("dockOffset") + (anchor <= 2 ? dx : dy) / density);
        } else {
            Object screen =
                    Reflect.call(
                            Reflect.field(Reflect.field(owner.get(), legacy() ? "E" : "C"), "a"),
                            "getData");
            float sw = ((Number) Reflect.call(screen, "getWidth")).floatValue(),
                    sh = ((Number) Reflect.call(screen, "getHeight")).floatValue();
            float width = ((Number) Reflect.call(data(), "getWidth")).floatValue(),
                    height = ((Number) Reflect.call(data(), "getHeight")).floatValue();
            next.putInt("anchor", 5);
            next.putFloat("freeX", (dragModelX + dx) / Math.max(1, sw - width));
            next.putFloat("freeY", (dragModelY + dy) / Math.max(1, sh - height));
        }
        update(next, false);
        if (pendingBinding != null) {
            View root = (View) (legacy() ? Reflect.field(owner.get(), "N") : OneUi9WidgetEditor.root(owner.get()));
            Object binding = pendingBinding.invoke(null, root);
            if (binding != null) Reflect.call(binding, "executePendingBindings");
        }
    }



    void addMenu(Object toolbar, ClassLoader loader) throws Exception {
        if (legacy()) {
            Context context = (Context) Reflect.field(toolbar, "a");
            legacyBinding.addMenu(toolbar, () -> open(context), TAG);
            return;
        }
        Object models = Reflect.field(toolbar, "g");
        if (models == null || Array.getLength(models) == 0) return;
        Context c = (Context) Reflect.field(toolbar, "a");
        Class<?> model = models.getClass().getComponentType();
        if (model == null) model = Class.forName(OneUi9WidgetEditor.MENU_ITEM, false, loader);
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < Array.getLength(models); i++) {
            Object value = Array.get(models, i);
            if (TAG.equals(Reflect.field(value, "e"))) return;
            list.add(value);
        }
        int icon =
                c.getResources()
                        .getIdentifier("preview_menu_icon_effect", "drawable", c.getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_menu_manage;
        Object advanced =
                model.getDeclaredConstructor(int.class, int.class, Runnable.class, String.class)
                        .newInstance(-1, icon, (Runnable) () -> open(c), TAG);
        Reflect.set(advanced, "f", "Advanced");
        list.add(advanced);
        Object result = Array.newInstance(model, list.size());
        for (int i = 0; i < list.size(); i++) Array.set(result, i, list.get(i));
        Reflect.set(toolbar, "g", result);
    }

    void open(Context context) {
        try {
            recoverPreview(context);
            if (owner.get() == null || ui.get() == null)
                throw new IllegalStateException(
                        "Open the lockscreen editor with widgets available first.");
            new WidgetPanel(context, owner.get().getClass().getClassLoader(), this).show(ui.get());
        } catch (Exception e) {
            module.report("Widget Advanced panel unavailable", e);
            android.widget.Toast.makeText(context, e.getMessage(), android.widget.Toast.LENGTH_LONG)
                    .show();
        }
    }

    void nativeCreate(Object item) throws ReflectiveOperationException {
        previewCandidates.put(item, Boolean.TRUE);
        if (!isMain(item)) return;
        if(owner.get()!=item)clearManagerPreviews();
        owner = new WeakReference<>(item);
        Context c = (Context) Reflect.field(item, "b");
        host = new WeakReference<>(c);
        ui = new WeakReference<>(Reflect.field(item, "i"));
        settings(c);
        restoreModel(OneUi9WidgetEditor.data(item));
        Object activeGrid=grid();
        if(activeGrid!=null)syncManagerPreview(Reflect.call(activeGrid,"getWidgetSizeManager"));
    }
    void nativeCreated(Object item, View root) {
        previewCandidates.put(item, Boolean.TRUE);
        if (item != owner.get()) try { nativeCreate(item); } catch (Exception error) { module.report("Widget owner rebind", error); }
        if (item != owner.get()) return;
        applyEffects();
        previewLayoutWatch.bind(root, (v,l,t,r,b,ol,ot,or,ob) -> { if(item == owner.get()) applyEffects(); });
    }
    void nativeRestore(Object store, Object model, Object profile) throws ReflectiveOperationException {
        if (model != null && (Boolean) Reflect.call(profile, "isLockScreen")) {
            settings((Context) Reflect.field(store, "a")); restoreModel(model);
        }
    }
    void nativeGridMetrics(Object grid, Object align) throws ReflectiveOperationException {
        if(draft == null || !draft.getBoolean("layout")) return;
        int[] d = WidgetLayout.dimensions(draft.getInt("columns"),draft.getInt("rows"),draft.getBoolean("vertical"),(Boolean)Reflect.call(align,"isVertical"));
        Reflect.call(grid,"setCol",d[0]); Reflect.call(grid,"setRow",d[1]);
    }
    void nativeCellMetrics(Object owner, Object grid, Object size, Object profile) throws ReflectiveOperationException {
        if(draft == null || (!draft.getBoolean("layout") && draft.getFloat("scale",1)==1) || !(Boolean)Reflect.call(profile,"isLockScreen")) return;
        Object manager=Reflect.call(Reflect.field(owner,"c"),"getValue");
        syncManagerPreview(manager);
        Reflect.call(manager,"init",Reflect.call(manager,"getWidgetDisplayType"));
        if(draft.getBoolean("layout")) {
            boolean vertical=(Boolean)Reflect.call(Reflect.call(data(),"getWidgetAlignType"),"isVertical");
            int[] dimensions=WidgetLayout.dimensions(draft.getInt("columns"),draft.getInt("rows"),draft.getBoolean("vertical"),vertical);
            // Samsung's size manager may still carry 4x1. The same draft determines both
            // the visible editor boundary and the plugin's native grid-cell capacity.
            WidgetBoundaryMetrics.apply(size,grid,manager,dimensions[0],dimensions[1]);
        } else WidgetEditorBinding.metrics(size,grid,manager);
    }
    void nativePlacement(Object model,Object assist,Object profile,Object screen) throws ReflectiveOperationException {
        if(draft != null && (Boolean)Reflect.call(profile,"isLockScreen") && (draft.getBoolean("layout") || draft.getInt("anchor")!=0)) placeLegacy(model,assist,screen);
    }
    boolean nativeEditable(Object model,boolean original) throws ReflectiveOperationException {
        return original || (owner.get()!=null && draft!=null && draft.getBoolean("layout") && model==data());
    }
    boolean nativeFeedback() { return draft == null || draft.getInt("anchor")==0; }

    private Bundle rotationPrevious;
    private boolean rotationWasDirty;
    boolean nativeRotateBefore(Object rotation,Object align)throws ReflectiveOperationException {
        rotationPrevious=null;
        if(owner.get()==null||draft==null||!draft.getBoolean("layout")||rotation!=Reflect.field(owner.get(),"y"))return true;
        if((Boolean)Reflect.call(data(),"getWidgetAlignChanging"))return true;
        boolean target=(Boolean)Reflect.call(align,"isVertical");if(target==vertical())return true;
        Bundle next=new Bundle(draft);orient(next,target);
        if(!WidgetLayout.fits(next.getInt("columns"),next.getInt("rows"),new int[0][])||!module.widgetEngine.fits(grid(),next))return false;
        rotationPrevious=draft;rotationWasDirty=dirty;draft=WidgetPreferences.clean(next);dirty=true;syncManagerPreview(Reflect.call(grid(),"getWidgetSizeManager"));return true;
    }
    boolean nativeRotateAfter(boolean result) {
        if(rotationPrevious!=null){if(!result){draft=rotationPrevious;dirty=rotationWasDirty;}else queueClockDepthRefresh();rotationPrevious=null;}
        try { syncManagerPreview(Reflect.call(grid(),"getWidgetSizeManager")); } catch(Exception failure){module.report("Widget rotated preview",failure);}
        return result;
    }
    Object nativeTrimmed(Object policy)throws ReflectiveOperationException {
        if(owner.get()!=null&&draft!=null&&draft.getBoolean("layout")&&policy==Reflect.field(owner.get(),"D"))return Reflect.call(data(),"getWidgetAlignType");
        return null;
    }
    private boolean replayingTouch;
    int nativeTouch(android.app.Activity activity,android.view.MotionEvent event)throws Exception {
        if(replayingTouch || itemGestures.replaying()) return -1;
        if(owner.get()==null || draft==null) recoverPreview(activity);
        if(owner.get()==null||draft==null)return -1;
        View root=OneUi9WidgetEditor.root(owner.get());if(root==null||!root.isShown())return -1;
        int itemResult=itemGestures.touch(activity,event,this);
        if(itemResult>=0)return itemResult;
        if(itemResult==-2)return -1;
        int action=event.getActionMasked();
        if(action==android.view.MotionEvent.ACTION_DOWN){
            Rect bounds=new Rect();if(!ScreenBounds.visible(root,bounds)||!bounds.contains(Math.round(event.getRawX()),Math.round(event.getRawY())))return -1;
            if(dragDown!=null)dragDown.recycle();dragDown=android.view.MotionEvent.obtain(event);
            dragStart=new Bundle(settings(root.getContext()));dragging=false;dragWasDirty=dirty;groupPinching=false;groupPinchSpan=0;
            dragX=event.getRawX();dragY=event.getRawY();dragModelX=((Number)Reflect.call(data(),"getX")).floatValue();dragModelY=((Number)Reflect.call(data(),"getY")).floatValue();return 1;
        }
        if(dragStart==null)return -1;
        if(action==android.view.MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount()==2){
            groupPinchSpan=(float)Math.hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1));
            groupPinching=true;dragging=true;return 1;
        }
        if(groupPinching){
            if(action==android.view.MotionEvent.ACTION_MOVE && event.getPointerCount()>=2 && groupPinchSpan>1){
                float span=(float)Math.hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1));
                Bundle next=new Bundle(dragStart);
                next.putFloat("scale",WidgetLayout.finite(dragStart.getFloat("scale",1)*span/groupPinchSpan,1,.5f,2));
                update(next,false);
            }
            if(action==android.view.MotionEvent.ACTION_CANCEL){update(new Bundle(dragStart),false);dirty=dragWasDirty;}
            if(action==android.view.MotionEvent.ACTION_UP || action==android.view.MotionEvent.ACTION_CANCEL){
                dragStart=null;dragging=false;groupPinching=false;groupPinchSpan=0;
                if(dragDown!=null){dragDown.recycle();dragDown=null;}
            }
            return 1;
        }
        if(action==android.view.MotionEvent.ACTION_MOVE&&!dragging&&Math.hypot(event.getRawX()-dragX,event.getRawY()-dragY)>android.view.ViewConfiguration.get(root.getContext()).getScaledTouchSlop()){dragging=true;lastDrag=0;}
        if(!dragging){
            if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL){
                dragStart=null;
                try{if(action==android.view.MotionEvent.ACTION_UP&&dragDown!=null){replayingTouch=true;activity.dispatchTouchEvent(dragDown);return activity.dispatchTouchEvent(event)?1:0;}}
                finally{replayingTouch=false;if(dragDown!=null){dragDown.recycle();dragDown=null;}}
            }return 1;
        }
        try{
            if(action==android.view.MotionEvent.ACTION_MOVE||action==android.view.MotionEvent.ACTION_UP){if(action==android.view.MotionEvent.ACTION_UP||event.getEventTime()-lastDrag>=16){lastDrag=event.getEventTime();moveGroup(event.getRawX()-dragX,event.getRawY()-dragY);}}
            else if(action==android.view.MotionEvent.ACTION_CANCEL){update(new Bundle(dragStart),false);dirty=dragWasDirty;}
        }finally{if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL){dragStart=null;dragging=false;if(dragDown!=null){dragDown.recycle();dragDown=null;}}}
        return 1;
    }

    Object gestureGrid() throws ReflectiveOperationException { return grid(); }
    boolean gestureBoundary(float x,float y) throws ReflectiveOperationException {
        View root=owner.get()==null?null:OneUi9WidgetEditor.root(owner.get());
        if(root==null)return false;
        Rect r=new Rect();if(!ScreenBounds.visible(root,r)||!r.contains(Math.round(x),Math.round(y)))return false;
        float margin=12*root.getResources().getDisplayMetrics().density;
        return x-r.left<margin || r.right-x<margin || y-r.top<margin || r.bottom-y<margin;
    }
    void gestureItemChanged(Object grid) throws ReflectiveOperationException {
        dirty=true;
        // Samsung's editor serializes holder StateFlow through getModuleInfo -> WidgetData.
        Reflect.call(owner.get(),"c");
        module.widgetEngine.refreshChecked(grid);
        recalculate();applyEffects();
    }
    private static android.app.Activity activityOf(Context context) {
        for(int i=0;i<16 && context!=null;i++){
            if(context instanceof android.app.Activity a)return a;
            if(!(context instanceof android.content.ContextWrapper wrapper))break;
            Context next=wrapper.getBaseContext();if(next==context)break;context=next;
        }
        return null;
    }
    private void recoverPreview(Context context) throws ReflectiveOperationException {
        android.app.Activity activity=activityOf(context);
        Object current=owner.get();
        if(current!=null){
            View view=OneUi9WidgetEditor.root(current);
            if(isMain(current) && view!=null && view.isAttachedToWindow() && activity!=null && activityOf((Context)Reflect.field(current,"b"))==activity) return;
        }
        if(activity==null)return;
        for(Object item:new java.util.ArrayList<>(previewCandidates.keySet())){
            if(item==null || !isMain(item))continue;
            if(activityOf((Context)Reflect.field(item,"b"))!=activity)continue;
            View view=OneUi9WidgetEditor.root(item);
            if(view==null || !view.isAttachedToWindow())continue;
            nativeCreate(item);return;
        }
    }

    private void syncManagerPreview(Object manager) throws ReflectiveOperationException {
        if(manager==null || draft==null)return;
        boolean first=!previewManagers.containsKey(manager);
        previewManagers.put(manager,Boolean.TRUE);
        module.widgetEngine.nativePreview(manager,draft);
        Class<?> target=Class.forName(NativeWidgets.class.getName(),true,manager.getClass().getClassLoader());
        if(target!=NativeWidgets.class){
            target.getMethod("preview",Object.class,Bundle.class).invoke(null,manager,new Bundle(draft));
            if(first)android.util.Log.i(DressroomModule.TAG,"Widget preview synchronized with plugin helper classloader");
        }
    }
    private void clearManagerPreviews() { clearManagerPreviews(false); }
    private void clearManagerPreviews(boolean committed) {
        for(Object manager:new java.util.ArrayList<>(previewManagers.keySet())){
            module.widgetEngine.nativePreviewEnd(manager,committed);
            try{
                Class<?> target=Class.forName(NativeWidgets.class.getName(),false,manager.getClass().getClassLoader());
                if(target!=NativeWidgets.class)target.getMethod("previewEnd",Object.class,boolean.class).invoke(null,manager,committed);
            }catch(Exception failure){module.report("Widget preview scope cleanup",failure);}
        }
        previewManagers.clear();
    }
}
