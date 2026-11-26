// Native port of Dressroom-Advanced by @BlueOutside. See upstream LICENSE.
package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;



import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class DepthEffects {
    interface Result {
        void done(byte[] mask, String message);

        default void retry(String message) {
            done(null, message);
        }
    }

    private static final Map<View, Entry> entries =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, android.view.ViewTreeObserver.OnGlobalLayoutListener>
            editorRoots = Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> drawingClock = ThreadLocal.withInitial(() -> false);
    private static byte[] editorPng;
    private static boolean editorEnabled;
    private static boolean editorPreviewClip;
    private static float editorScale = 1f, editorOriginX, editorOriginY;
    private static final Paint erase = new Paint(Paint.FILTER_BITMAP_FLAG);
    private static byte[] cachedPng;
    private static Bitmap cachedMask;
    private static Path cachedSubject;
    private static LiveSubject activeSubject;

    private record LocalPath(Path screen, Matrix matrix, Path path) {}

    private static final Map<View, LocalPath> localPaths = new WeakHashMap<>();
    private static final java.util.Set<View> blurRefreshPending =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final java.util.Set<View> mediaClocks =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static java.lang.ref.WeakReference<View> editorWallpaper =
            new java.lang.ref.WeakReference<>(null);
    private static volatile long editorGeneration;
    private static volatile long scanRequest;
    private static java.util.function.Consumer<View> wallpaperListener;
    private static boolean sourcePending;
    private static String detectionFailure;
    private static final android.os.Handler scanMain =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static final java.util.concurrent.ExecutorService scanner =
            java.util.concurrent.Executors.newSingleThreadExecutor(
                    task -> {
                        Thread thread = new Thread(task, "Dressroom-depth-scan");
                        thread.setDaemon(true);
                        return thread;
                    });

    static {
        erase.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    private DepthEffects() {}

    /** Called directly by patched Samsung draw methods and our XML container subclasses. */
    static int beforeDraw(View view, Canvas canvas) {
        Entry entry = entries.get(view);
        if (entry == null) entry = inheritedSubject(view);
        if (entry == null || entry.mask == null || !entry.enabled || entry.nodeClip
                || (entry.editor && sourcePending) || drawingClock.get()
                || subjectSuppressed(view, entry)) return -1;
        int save = canvas.saveLayer(null, null);
        drawingClock.set(true);
        return save;
    }

    static void afterDraw(View view, Canvas canvas, int saved) {
        if (saved < 0) return;
        try {
            Entry entry = entries.get(view);
            if (entry == null) entry = inheritedSubject(view);
            if (entry != null) drawMask(canvas, view, entry);
        } finally {
            drawingClock.set(false);
            canvas.restoreToCount(saved);
        }
    }

    private static void drawMask(Canvas canvas, View view, Entry entry) {
        Matrix source = subjectTransform(view, entry, 0, 0);
        if (source == null) return;
        Matrix global = new Matrix(), inverse = new Matrix();
        view.transformMatrixToGlobal(global);
        if (!global.invert(inverse)) return;
        Matrix local = new Matrix();
        local.setConcat(inverse, source);
        canvas.drawBitmap(entry.mask, local, erase);
    }

    static void apply(View view, boolean enabled, byte[] png) {
        if (!enabled || png == null || png.length == 0) {
            ClockContentClip.watch(view, false);
            DepthViewMotion.watch(view, false);
            if (entries.remove(view) != null) {
                view.invalidate();
                refreshNativeBlur(view);
            }
            return;
        }
        Entry entry = entries.get(view);
        LiveSubject binding =
                activeSubject != null && Arrays.equals(activeSubject.png, png)
                        ? activeSubject
                        : null;
        boolean changed = entry == null || entry.live != binding || !Arrays.equals(entry.png, png);
        if (changed) {
            Bitmap mask;
            if (Arrays.equals(cachedPng, png)) mask = cachedMask;
            else {
                Bitmap decoded = BitmapFactory.decodeByteArray(png, 0, png.length);
                mask = decoded;
                cachedPng = png;
                cachedMask = mask;
                cachedSubject = null;
            }
            if (mask == null) {
                ClockContentClip.watch(view, false);
                DepthViewMotion.watch(view, false);
                if (entries.remove(view) != null) {
                    view.invalidate();
                    refreshNativeBlur(view);
                }
                return;
            }
            entry = new Entry(png, mask);
            entries.put(view, entry);
        }
        String packageName = view.getContext().getPackageName();
        if (packageName.equals("com.android.systemui")
                || packageName.equals("com.samsung.android.app.aodservice")
                || packageName.equals("com.samsung.android.app.clockpack")) {
            int screen = ScreenIdentity.current();
            if (changed || !entry.runtimeMotion || entry.screen != screen) {
                entry.runtimeMotion = true;
                entry.screen = screen;
                entry.wallpaperId = SettingsBridge.wallpaperId(view.getContext(), screen);
            }
            WallpaperMotion.start(view, entry.wallpaperId);
        }
        entry.enabled = true;
        entry.wholeDraw = isClockView(view);
        entry.nodeClip = false;
        ClockContentClip.watch(view, false);
        if (entry.runtimeMotion && entry.wholeDraw) DepthViewMotion.watch(view, true);
        if (changed) {
            view.invalidate();
            refreshNativeBlur(view);
        }
    }

    static void refreshEditorClock(View view) {
        if (!isEditorClock(view)) return;
        view.invalidate();
        // Native blur lives outside the clock's normal draw pass. Its matte can be
        // unchanged while Samsung has replaced the underlying blur owner.
        refreshNativeBlur(view);
    }

    private static void refreshNativeBlur(View view) {
        if (!isClockView(view) || !blurRefreshPending.add(view)) return;
        java.lang.ref.WeakReference<View> reference = new java.lang.ref.WeakReference<>(view);
        view.post(
                () -> {
                    View current = reference.get();
                    if (current == null) return;
                    blurRefreshPending.remove(current);
                    if (!current.isAttachedToWindow()) return;
                    try {
                        Reflect.call(current, "setClockTextBlur");
                    } catch (Exception error) {
                        android.util.Log.w(
                                "Dressroom-Advanced", "Depth blur rebuild unavailable", error);
                    }
                });
    }

    static void viewMotionChanged(View view) {
        if (!hasVisibleSubject(view)) return;
        invalidateTree(view);
        if (isClockView(view)) refreshNativeBlur(view);
        else WidgetDepthBlur.mediaChanged(view);
    }

    static Path nativeSubjectPath(View clock, float hostX, float hostY) {
        Entry entry = inheritedSubject(clock);
        if (entry == null
                || !entry.enabled
                || (entry.editor && sourcePending)
                || subjectSuppressed(clock, entry)) return null;
        return screenSubjectPath(clock, entry, hostX, hostY);
    }

    private static Entry inheritedSubject(View view) {
        for (View current = view; current != null; ) {
            Entry entry = entries.get(current);
            if (entry != null && entry.enabled) return entry;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return null;
    }

    static boolean hasSubject(View view) {
        return inheritedSubject(view) != null;
    }

    static boolean wallpaperHidden(View view) {
        Entry entry = inheritedSubject(view);
        return entry != null
                && entry.runtimeMotion
                && !entry.editor
                && !WallpaperMotion.visible(entry.screen);
    }

    static boolean hasVisibleSubject(View view) {
        Entry entry = inheritedSubject(view);
        return entry != null && !subjectSuppressed(view, entry);
    }

    private static boolean subjectSuppressed(View view, Entry entry) {
        return mediaSuspended(view)
                || entry.runtimeMotion && !entry.editor && !WallpaperMotion.visible(entry.screen);
    }

    static Object subjectIdentity(View view) {
        Entry e = inheritedSubject(view);
        return e == null || subjectSuppressed(view, e) ? null : e.identity;
    }

    static Path localSubjectPath(View view) {
        Entry entry = inheritedSubject(view);
        if (entry == null || (entry.editor && sourcePending) || subjectSuppressed(view, entry))
            return null;
        Matrix global = new Matrix(), local = new Matrix();
        view.transformMatrixToGlobal(global);
        if (!global.invert(local)) return null;
        Path subject = screenSubjectPath(view, entry, 0, 0);
        if (subject == null) return null;
        LocalPath cached = localPaths.get(view);
        if (cached != null && cached.screen == subject && cached.matrix.equals(local))
            return cached.path;
        Path result = new Path(subject);
        result.transform(local);
        localPaths.put(view, new LocalPath(subject, new Matrix(local), result));
        return result;
    }

    static void media(View clock, boolean active) {
        boolean changed = active ? mediaClocks.add(clock) : mediaClocks.remove(clock);
        if (!changed) return;
        invalidateTree(clock);
        refreshNativeBlur(clock);
    }

    private static boolean mediaSuspended(View view) {
        if (mediaClocks.isEmpty()) return false;
        for (View current = view;
                current != null;
                current = current.getParent() instanceof View parent ? parent : null)
            if (mediaClocks.contains(current)) return true;
        return false;
    }

    static boolean mediaActive(View view) {
        return mediaSuspended(view);
    }

    private static void invalidateTree(View view) {
        localPaths.remove(view);
        view.invalidate();
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) invalidateTree(group.getChildAt(i));
    }

    private static Matrix subjectTransform(View clock, Entry entry, float hostX, float hostY) {
        float screenWidth = clock.getResources().getDisplayMetrics().widthPixels;
        float screenHeight = clock.getResources().getDisplayMetrics().heightPixels;
        try {
            WindowManager wm = clock.getContext().getSystemService(WindowManager.class);
            if (wm != null) {
                Rect bounds = wm.getCurrentWindowMetrics().getBounds();
                screenWidth = bounds.width();
                screenHeight = bounds.height();
            }
        } catch (Throwable ignored) {
        }
        float width = screenWidth * entry.previewScale;
        float height = screenHeight * entry.previewScale;
        RectF source = new RectF(0, 0, entry.mask.getWidth(), entry.mask.getHeight());
        float ratio = width / height;
        if (source.width() / source.height() > ratio) {
            float crop = source.height() * ratio;
            source.inset((source.width() - crop) / 2, 0);
        } else {
            float crop = source.width() / ratio;
            source.inset(0, (source.height() - crop) / 2);
        }
        Matrix transform = new Matrix();
        transform.setRectToRect(
                source,
                new RectF(
                        entry.previewOriginX - hostX,
                        entry.previewOriginY - hostY,
                        entry.previewOriginX - hostX + width,
                        entry.previewOriginY - hostY + height),
                Matrix.ScaleToFit.FILL);
        if (entry.live != null) {
            transform = entry.live.global();
            if (transform == null) return null;
            transform.postTranslate(-hostX, -hostY);
        } else if (entry.runtimeMotion && !entry.editor) {
            // Work in wallpaper screen coordinates before moving into the blur host.
            transform.postTranslate(hostX, hostY);
            WallpaperMotion.transform(transform, entry.wallpaperId, entry.screen, width, height);
            transform.postTranslate(-hostX, -hostY);
        }
        return transform;
    }

    private static Path screenSubjectPath(View clock, Entry entry, float hostX, float hostY) {
        Matrix transform = subjectTransform(clock, entry, hostX, hostY);
        if (transform == null) return null;
        Path cached = entry.paths.get(transform);
        if (cached != null) return cached;
        Path subject = new Path(entry.subject);
        subject.transform(transform);
        if (entry.paths.size() >= 8) entry.paths.clear();
        entry.paths.put(new Matrix(transform), subject);
        return subject;
    }

    static void wallpaperMotionChanged() {
        localPaths.clear();
        java.util.ArrayList<View> roots = new java.util.ArrayList<>();
        synchronized (entries) {
            for (var item : entries.entrySet()) {
                Entry entry = item.getValue();
                if (entry.editor || !entry.runtimeMotion || !entry.enabled) continue;
                entry.paths.clear();
                entry.identity = new Object();
                View view = item.getKey();
                boolean nested = false;
                for (android.view.ViewParent parent = view.getParent();
                        parent instanceof View up;
                        parent = up.getParent()) {
                    Entry ancestor = entries.get(up);
                    if (ancestor != null && ancestor.enabled && ancestor.runtimeMotion) {
                        nested = true;
                        break;
                    }
                }
                if (!nested) roots.add(view);
            }
        }
        for (View root : roots) {
            if (!root.isAttachedToWindow()) continue;
            invalidateTree(root);
            WidgetDepthBlur.mediaChanged(root);
            if (isClockView(root) && !mediaSuspended(root)) refreshNativeBlur(root);
        }
    }

    static void resetEditor() {
        detectionFailure = null;
        EditorWallpaperSource.reset();
        editorGeneration++;
        scanRequest++;
        sourcePending = false;
        editorWallpaper.clear();
        if (activeSubject != null) activeSubject.stop();
        activeSubject = null;
        localPaths.clear();
        synchronized (entries) {
            entries.entrySet()
                    .removeIf(
                            item -> {
                                if (!item.getValue().editor) return false;
                                ClockContentClip.watch(item.getKey(), false);
                                return true;
                            });
        }
        synchronized (editorRoots) {
            for (Map.Entry<View, android.view.ViewTreeObserver.OnGlobalLayoutListener> item :
                    editorRoots.entrySet())
                if (item.getKey().getViewTreeObserver().isAlive())
                    item.getKey()
                            .getViewTreeObserver()
                            .removeOnGlobalLayoutListener(item.getValue());
            editorRoots.clear();
        }
        editorEnabled = false;
        editorPreviewClip = false;
        editorPng = null;
        editorScale = 1;
        editorOriginX = editorOriginY = 0;
    }

    private static boolean isClockView(View view) {
        for (Class<?> type = view.getClass(); type != null; type = type.getSuperclass())
            if ("com.samsung.android.uniform.widget.clock.AbsClockView".equals(type.getName()))
                return true;
        return false;
    }

    static boolean isEditorClock(View view) {
        for (View current = view;
                current != null;
                current = current.getParent() instanceof View parent ? parent : null) {
            if (current != view && isClockView(current)) return false;
            if (current.getId() != View.NO_ID)
                try {
                    if ("clock_preview"
                            .equals(current.getResources().getResourceEntryName(current.getId())))
                        return true;
                } catch (android.content.res.Resources.NotFoundException ignored) {
                }
        }
        return false;
    }

    static void applyEditor(View view, boolean enabled, byte[] png) {
        png = editorMask(png);
        if (enabled && png != null) {
            editorEnabled = true;
            editorPng = png;
        }
        apply(view, enabled, png);
        Entry entry = entries.get(view);
        if (entry != null) {
            entry.editor = true;
            entry.wholeDraw = isClockView(view);
            entry.nodeClip = false;
            ClockContentClip.watch(view, false);
            entry.previewScale = editorScale;
            entry.previewOriginX = editorOriginX;
            entry.previewOriginY = editorOriginY;
            view.invalidate();
        }
        observeEditorRoot(view.getRootView());
    }

    static void setEditorState(View view, boolean enabled, byte[] png) {
        png = editorMask(png);
        editorEnabled = enabled && png != null;
        editorPng = editorEnabled ? png : null;
        View root = view.getRootView();
        View world = root == null ? null : find(root, "world_clock_view");
        editorPreviewClip = world != null && world.isShown();
        if (root != null) {
            View preview = find(root, "clock_preview");
            if (preview != null) {
                applyEditorPreview(preview, editorEnabled, editorPng, editorPreviewClip);
                View time = find(preview, "common_stretch_time_area");
                if (time != null) applyEditor(time, editorEnabled, editorPng);
                applyDigitChildren(preview, editorEnabled, editorPng, true);
            }
            applyEditorClockOwners(root, editorEnabled, editorPng);
        }
    }

    static void applyEditorPreview(View preview, boolean enabled, byte[] png, boolean nodeClip) {
        applyEditor(preview, enabled, png);
        Entry entry = entries.get(preview);
        if (entry == null) return;
        entry.nodeClip = nodeClip && WidgetContentClip.supported();
        ClockContentClip.watch(preview, entry.nodeClip);
    }

    private static void observeEditorRoot(View root) {
        if (root == null || editorRoots.containsKey(root)) return;
        android.view.ViewTreeObserver.OnGlobalLayoutListener listener =
                () -> {
                    if (!editorEnabled || editorPng == null) return;
                    View preview = find(root, "clock_preview");
                    if (preview != null) {
                        applyEditorPreview(preview, true, editorPng, editorPreviewClip);
                        View time = find(preview, "common_stretch_time_area");
                        if (time != null) applyEditor(time, true, editorPng);
                        applyDigitChildren(preview, true, editorPng, true);
                    }
                    applyEditorClockOwners(root, true, editorPng);
                    View widgets = find(root, "widget_item_preview");
                    if (widgets != null) {
                        applyEditorWidgets(widgets, true, editorPng);
                    }
                };
        editorRoots.put(root, listener);
        root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
    }

    private static void applyEditorClockOwners(View root, boolean enabled, byte[] png) {
        if (isClockView(root) && isEditorClock(root)) applyEditor(root, enabled, png);
        if (root instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++)
                applyEditorClockOwners(group.getChildAt(i), enabled, png);
    }

    static void applyWidgets(View clock, boolean enabled, byte[] png, boolean editor) {
        View root = clock.getRootView();
        if (root == null) return;
        View widgets = find(root, editor ? "widget_item_preview" : "grid_layout");
        if (widgets == null || widgets == clock) return;
        if (editor) applyEditorWidgets(widgets, enabled, png);
        else applyWidgetRoot(widgets, enabled, png);
    }

    private static Boolean editorWidgetDepth;
    private static byte[] editorWidgetMask;

    static void setEditorWidgetPolicy(Boolean enabled, byte[] mask) {
        editorWidgetDepth = enabled;
        editorWidgetMask = mask;
    }

    static void applyEditorWidgets(View widgets, boolean inherited, byte[] mask) {
        boolean enabled = editorWidgetDepth == null ? inherited : editorWidgetDepth;
        byte[] png = editorWidgetDepth == null ? mask : editorWidgetMask;
        boolean oldEnabled = editorEnabled;
        byte[] oldPng = editorPng;
        try {
            applyEditor(widgets, enabled, png);
        } finally {
            editorEnabled = oldEnabled;
            editorPng = oldPng;
        }
    }

    static void applyWidgetRoot(View widgets, boolean enabled, byte[] png) {
        if (widgets.getContext().getPackageName().equals("com.samsung.android.app.dressroom"))
            png = editorMask(png);
        apply(widgets, enabled, png);
        Entry entry = entries.get(widgets);
        if (ScreenIdentity.supported()) {
            boolean active = entry != null && entry.enabled;
            if (entry != null) entry.nodeClip = active && WidgetContentClip.supported();
            WidgetDepthBlur.clipWidgets(widgets, active);
            WidgetDepthBlur.watch(widgets, active || WidgetMediaOcclusion.active(widgets));
        } else {
            if (entry != null) entry.nodeClip = false;
            WidgetContentClip.apply(widgets, false);
            WidgetDepthBlur.watch(widgets, false);
        }
        DepthViewMotion.watch(widgets, entry != null && enabled);
    }

    static void applyDigits(View clock, boolean enabled, byte[] png) {
        applyDigitChildren(clock, enabled, png, false);
    }

    static void applyEditorDigits(View clock, boolean enabled, byte[] png) {
        applyDigitChildren(clock, enabled, png, true);
        long generation = editorGeneration;
        clock.post(
                () -> {
                    if (generation == editorGeneration && clock.isAttachedToWindow())
                        applyDigitChildren(clock, enabled, png, true);
                });
    }

    private static void applyDigitChildren(View root, boolean enabled, byte[] png, boolean editor) {
        if (root == null) return;
        if (root.getId() != View.NO_ID)
            try {
                String name = root.getResources().getResourceEntryName(root.getId());
                if (name.startsWith("common_stretch_hour")
                        || name.startsWith("common_stretch_minute")
                        || name.equals("common_stretch_colon")
                        || name.startsWith("common_hour")
                        || name.startsWith("common_minute")
                        || name.equals("common_colon")
                        || name.equals("common_time_area")
                        || name.equals("common_stretch_time_area")) {
                    if (editor) applyEditor(root, enabled, png);
                    else apply(root, enabled, png);
                }
            } catch (android.content.res.Resources.NotFoundException ignored) {
            }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++)
                applyDigitChildren(group.getChildAt(i), enabled, png, editor);
        }
    }

    private static View find(View root, String entry) {
        if (root.getId() != View.NO_ID)
            try {
                if (entry.equals(root.getResources().getResourceEntryName(root.getId())))
                    return root;
            } catch (android.content.res.Resources.NotFoundException ignored) {
            }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), entry);
                if (found != null) return found;
            }
        }
        return null;
    }

    static void editorTransform(View clock, float scale, Rect fullClockRect) {
        Entry entry = entries.get(clock);
        if (entry == null || fullClockRect == null) return;
        long generation = editorGeneration;
        Rect position = new Rect(fullClockRect);
        clock.post(
                () -> {
                    if (generation != editorGeneration || !clock.isAttachedToWindow()) return;
                    int[] location = new int[2];
                    clock.getLocationOnScreen(location);
                    entry.previewScale = scale;
                    entry.previewOriginX = location[0] - position.left * scale;
                    entry.previewOriginY = location[1] - position.top * scale;
                    editorScale = entry.previewScale;
                    editorOriginX = entry.previewOriginX;
                    editorOriginY = entry.previewOriginY;
                    synchronized (entries) {
                        for (Map.Entry<View, Entry> item : entries.entrySet()) {
                            Entry layer = item.getValue();
                            if (!layer.editor) continue;
                            layer.previewScale = editorScale;
                            layer.previewOriginX = editorOriginX;
                            layer.previewOriginY = editorOriginY;
                            item.getKey().invalidate();
                        }
                    }
                    refreshNativeBlur(clock);
                });
    }

    /** Direct callback at CropImageView.onDraw before Samsung renders its image. */
    static void editorWallpaperDraw(View view) {
        if (view.isShown() && view.getWidth() > 0 && view.getHeight() > 0)
            editorWallpaper = new java.lang.ref.WeakReference<>(view);
        if (wallpaperListener != null && view.isShown()) wallpaperListener.accept(view);
        if (activeSubject != null && activeSubject.image.get() == view) updateLiveMask();
    }

    /** Direct callback after WeatherSurfaceView's surface lifecycle. */
    static void weatherSource(View surface) {
        View crop = findCropView(surface.getRootView(), false);
        if (crop == null) return;
        EditorWallpaperSource.bind(crop, surface);
        if (wallpaperListener != null) wallpaperListener.accept(crop);
        updateLiveMask();
    }

    private static void updateLiveMask() {
        if (activeSubject == null || !activeSubject.changed()) return;
        synchronized (entries) {
            for (Map.Entry<View, Entry> item : entries.entrySet())
                if (item.getValue().live == activeSubject) {
                    item.getValue().identity = new Object();
                    View layer = item.getKey();
                    layer.invalidate();
                    if (item.getValue().nodeClip) WidgetContentClip.refresh(layer);
                    if (isClockView(layer)) refreshNativeBlur(layer);
                }
        }
    }

    static void listenWallpaper(java.util.function.Consumer<View> listener) {
        wallpaperListener = listener;
    }

    static void cancelScan() {
        detectionFailure = null;
        scanRequest++;
    }

    static void clearEditorSubject() {
        cancelScan();
        sourcePending = false;
        if (activeSubject != null) activeSubject.stop();
        activeSubject = null;
        editorEnabled = false;
        editorPng = null;
        editorWidgetDepth = false;
        editorWidgetMask = null;
        for (View view : new java.util.ArrayList<>(entries.keySet())) {
            Entry entry = entries.get(view);
            if (entry != null && entry.editor) {
                applyWidgetRoot(view, false, null);
                view.invalidate();
            }
        }
        localPaths.clear();
    }

    static void detectionFailure(String message) {
        detectionFailure = message;
    }

    static void pendingSource(boolean pending) {
        if (!pending) detectionFailure = null;
        if (sourcePending == pending) return;
        sourcePending = pending;
        synchronized (entries) {
            for (Map.Entry<View, Entry> item : entries.entrySet())
                if (item.getValue().editor) {
                    item.getValue().identity = new Object();
                    View view = item.getKey();
                    view.invalidate();
                    if (item.getValue().nodeClip) WidgetContentClip.refresh(view);
                    refreshNativeBlur(view);
                }
        }
    }

    static View wallpaper(View clock) {
        View image = findCropView(clock.getRootView());
        return image == null ? editorWallpaper.get() : image;
    }

    static boolean hasLiveSource(View image) {
        return activeSubject != null
                && activeSubject.image.get() == image
                && activeSubject.global() != null;
    }

    static byte[] liveMask(View image) {
        return hasLiveSource(image) ? activeSubject.png : null;
    }

    static byte[] editorMask(byte[] png) {
        return activeSubject != null && activeSubject.matches(png) && activeSubject.global() != null
                ? activeSubject.png
                : null;
    }

    static void sourceMetadata(Bundle settings) {
        if (activeSubject != null && activeSubject.matches(settings.getByteArray("depthMask"))) {
            settings.putInt("depthSourceVersion", 2);
            settings.putString(DepthMetadata.FINGERPRINT, activeSubject.fingerprint);
            settings.putByteArray(DepthMetadata.SOURCE, activeSubject.png);
        }
    }

    static void restoreSource(View image, Bundle saved, String fingerprint) throws Exception {
        byte[] png = saved.getByteArray(DepthMetadata.SOURCE);
        if (png == null
                || saved.getInt("depthSourceVersion") != 2
                || !fingerprint.equals(saved.getString(DepthMetadata.FINGERPRINT))) return;
        LiveSubject subject =
                LiveSubject.bind(image, png, fingerprint, saved.getByteArray("depthMask"));
        if (activeSubject != null) activeSubject.stop();
        activeSubject = subject;
        subject.watch(DepthEffects::updateLiveMask);
    }

    static void scan(android.content.Context editorContext, View clock, Result callback) {
        scan(editorContext, clock, callback, () -> {});
    }

    static void scan(
            android.content.Context editorContext, View clock, Result callback, Runnable finished) {
        scan(editorContext, clock, null, callback, finished);
    }

    static void scan(
            android.content.Context editorContext,
            View clock,
            View preferred,
            Result callback,
            Runnable finished) {
        final Bitmap wallpaper;
        final LiveSubject capture;
        final long generation = editorGeneration;
        final long request = ++scanRequest;
        detectionFailure = null;
        try {
            View image = preferred;
            if (image == null) {
                image = findCropView(clock.getRootView());
                if (image == null) image = editorWallpaper.get();
            }
            if (!EditorWallpaperSource.available(image))
                throw new IllegalStateException(
                        "Wallpaper preview is not ready. Reopen the editor and try again.");
            LiveSubject.Capture snapshot = LiveSubject.capture(image);
            wallpaper = snapshot.bitmap();
            capture = snapshot.subject();
            if (wallpaper == null)
                throw new IllegalStateException("The current wallpaper cannot be scanned.");
        } catch (Exception e) {
            try {
                callback.retry("Depth detection: " + e.getMessage());
            } finally {
                finished.run();
            }
            return;
        }
        scanner.execute(
                () -> {
                    Object detector = null;
                    Bitmap prepared = null;
                    try {
                        if (request != scanRequest || generation != editorGeneration) return;
                        ClassLoader loader = editorContext.getClassLoader();
                        detector = initializeDetector(editorContext, loader);
                        prepared = MaskAlpha.detect(detector, wallpaper, "full image");
                        if (prepared == null && request == scanRequest) {
                            try {
                                Thread.sleep(60);
                            } catch (InterruptedException ignored) {
                            }
                            prepared = MaskAlpha.detect(detector, wallpaper, "full image retry");
                        }
                        final Object activeDetector = detector;
                        prepared =
                                MaskAlpha.refine(
                                        prepared,
                                        () -> detailMask(activeDetector, capture, wallpaper));
                        if (prepared == null)
                            throw new IllegalStateException("No subject found in this wallpaper");
                        final Bitmap readyMask = prepared;
                        byte[] mask = MaskAlpha.encode(readyMask);
                        Path outline = contour(readyMask);
                        if (!scanMain.post(
                                () -> {
                                    if (generation != editorGeneration || request != scanRequest) {
                                        readyMask.recycle();
                                        return;
                                    }
                                    if (capture.global() == null) {
                                        readyMask.recycle();
                                        callback.retry(
                                                "Wallpaper preview changed during detection");
                                        return;
                                    }
                                    if (activeSubject != null) activeSubject.stop();
                                    cachedPng = mask;
                                    cachedMask = readyMask;
                                    cachedSubject = outline;
                                    capture.png = mask;
                                    activeSubject = capture;
                                    capture.watch(DepthEffects::updateLiveMask);
                                    callback.done(
                                            mask,
                                            "Subject detected · follows" + " wallpaper movement");
                                }))
                            throw new IllegalStateException("Editor result handler stopped");
                        prepared = null;
                    } catch (Exception | LinkageError e) {
                        android.util.Log.w("Dressroom-Advanced", "Subject detection failed", e);
                        String message = e.getMessage();
                        if ((message == null || message.isBlank()) && e.getCause() != null)
                            message = e.getCause().getMessage();
                        if (message != null && message.length() > 300)
                            message = message.substring(0, 300) + "...";
                        final String visibleMessage = "Depth detection: " + message;
                        scanMain.post(
                                () -> {
                                    if (generation == editorGeneration && request == scanRequest)
                                        callback.done(null, visibleMessage);
                                });
                    } finally {
                        if (detector != null)
                            try {
                                Reflect.call(detector, "release");
                            } catch (Exception | LinkageError ignored) {
                            }
                        if (prepared != null && !prepared.isRecycled()) prepared.recycle();
                        wallpaper.recycle();
                        scanMain.post(finished);
                    }
                });
    }

    private static Bitmap detailMask(Object detector, LiveSubject capture, Bitmap wallpaper)
            throws Exception {
        LiveSubject.Detail detail = capture.detail(wallpaper);
        try {
            Bitmap focused = MaskAlpha.detect(detector, detail.bitmap(), "visible detail");
            if (focused == null) {
                try {
                    Thread.sleep(60);
                } catch (InterruptedException ignored) {
                }
                focused = MaskAlpha.detect(detector, detail.bitmap(), "visible detail retry");
            }
            if (focused == null) return null;
            try {
                Bitmap mapped =
                        Bitmap.createBitmap(
                                wallpaper.getWidth(),
                                wallpaper.getHeight(),
                                Bitmap.Config.ARGB_8888);
                try {
                    new Canvas(mapped)
                            .drawBitmap(
                                    focused,
                                    detail.toSource(),
                                    new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
                    return mapped;
                } catch (RuntimeException | Error failure) {
                    mapped.recycle();
                    throw failure;
                }
            } finally {
                focused.recycle();
            }
        } finally {
            detail.bitmap().recycle();
        }
    }

    private static Object initializeDetector(
            android.content.Context editorContext, ClassLoader loader) throws Exception {
        return ObjectCaptureBackend.open(editorContext, loader);
    }

    private static View findCropView(View root) {
        return findCropView(root, true);
    }

    private static View findCropView(View root, boolean visible) {
        View largest =
                root.getClass()
                                        .getName()
                                        .equals(
                                                "com.samsung.android.app.dressroom.presentation.preview.widget.CropImageView")
                                && (!visible || EditorWallpaperSource.available(root))
                        ? root
                        : null;
        if (root instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findCropView(group.getChildAt(i), visible);
                if (found != null
                        && (largest == null
                                || (long) found.getWidth() * found.getHeight()
                                        > (long) largest.getWidth() * largest.getHeight()))
                    largest = found;
            }
        return largest;
    }

    static void requireSaveReady(Bundle profile) {
        if (profile == null
                || (!profile.getBoolean("depthEnabled") && profile.getInt("depth") != 1)) return;
        byte[] mask = profile.getByteArray("depthMask");
        boolean missing =
                (mask == null || mask.length == 0)
                        && profile.getString("depthMaskRef", "").isEmpty();
        boolean stale =
                activeSubject != null
                        && activeSubject.matches(mask)
                        && activeSubject.global() == null;
        if (sourcePending || missing || stale)
            throw new IllegalStateException(
                    detectionFailure != null
                            ? detectionFailure
                            : "Depth is not ready. Wait for subject detection, then tap Apply"
                                  + " again.");
    }

    static android.os.Bundle freezeMask(android.os.Bundle draft) {
        if (draft == null) return null;
        android.os.Bundle copy = new android.os.Bundle(draft);
        boolean stale =
                sourcePending
                        || (activeSubject != null
                                && activeSubject.matches(copy.getByteArray("depthMask"))
                                && activeSubject.global() == null);
        if (stale) {
            for (String field : DepthMetadata.BLOBS) {
                copy.remove(field);
                copy.remove(field + "Ref");
            }
            copy.remove(DepthMetadata.FINGERPRINT);
            copy.remove("depthSourceVersion");
            View image = editorWallpaper.get();
            if (image != null)
                android.widget.Toast.makeText(
                                image.getContext(),
                                "Subject detection is not ready yet. Apply again after it"
                                        + " finishes.",
                                android.widget.Toast.LENGTH_LONG)
                        .show();
            return copy;
        }
        byte[] png = copy.getByteArray("depthMask");
        if (activeSubject != null && activeSubject.matches(png)) {
            sourceMetadata(copy);
            png = activeSubject.png;
            Bitmap bitmap =
                    Arrays.equals(cachedPng, png)
                            ? cachedMask
                            : BitmapFactory.decodeByteArray(png, 0, png.length);
            try {
                copy.putByteArray("depthMask", activeSubject.bake(bitmap));
            } catch (Exception e) {
                throw new IllegalStateException("Could not save wallpaper depth crop", e);
            } finally {
                if (bitmap != cachedMask && bitmap != null) bitmap.recycle();
            }
        }
        return copy;
    }

    private static byte[] encodeMask(int width, int height, Bitmap subject, Rect bounds) {
        if (width <= 0 || height <= 0 || subject.isRecycled())
            throw new IllegalArgumentException("Subject image is not ready");
        int outWidth = width;
        int outHeight = height;
        Bitmap output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
        try {
            Paint alpha = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            alpha.setColorFilter(
                    new android.graphics.BlendModeColorFilter(
                            android.graphics.Color.WHITE, android.graphics.BlendMode.SRC_IN));
            RectF destination = new RectF(bounds);
            destination.left *= ((float) outWidth / width);
            destination.right *= ((float) outWidth / width);
            destination.top *= ((float) outHeight / height);
            destination.bottom *= ((float) outHeight / height);
            new Canvas(output).drawBitmap(subject, null, destination, alpha);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            output.compress(Bitmap.CompressFormat.PNG, 100, bytes);
            return bytes.toByteArray();
        } finally {
            output.recycle();
        }
    }

    private static Path contour(Bitmap mask) {
        try {
            return DepthGeometry.simplify(MaskContour.extract(mask, 127.5f), .2f);
        } catch (IllegalStateException error) {
            android.util.Log.w("Dressroom-Advanced", "Contour fallback", error);
            Region region = new Region();
            int[] row = new int[mask.getWidth()];
            for (int y = 0; y < mask.getHeight(); y++) {
                mask.getPixels(row, 0, row.length, 0, y, row.length, 1);
                int start = -1;
                for (int x = 0; x <= row.length; x++) {
                    boolean inside = x < row.length && (row[x] >>> 24) >= 128;
                    if (inside && start < 0) start = x;
                    if (!inside && start >= 0) {
                        region.op(start, y, x, y + 1, Region.Op.UNION);
                        start = -1;
                    }
                }
            }
            return DepthGeometry.simplify(region.getBoundaryPath(), .2f);
        }
    }

    private static final class Entry {
        final byte[] png;
        final Bitmap mask;
        final Path subject;
        final LiveSubject live;
        Object identity = new Object();
        final Map<Matrix, Path> paths = new java.util.LinkedHashMap<>();
        boolean enabled;
        boolean editor;
        boolean wholeDraw;
        boolean nodeClip;
        boolean runtimeMotion;
        int wallpaperId = -1, screen;
        float previewScale = 1f, previewOriginX, previewOriginY;

        Entry(byte[] png, Bitmap mask) {
            this.png = png.clone();
            this.mask = mask;
            live =
                    activeSubject != null && Arrays.equals(activeSubject.png, png)
                            ? activeSubject
                            : null;
            if (mask == cachedMask && cachedSubject != null) {
                subject = cachedSubject;
                return;
            }
            subject = contour(mask);
            if (mask == cachedMask) cachedSubject = subject;
        }
    }
}
