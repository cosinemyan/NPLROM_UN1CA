// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class MediaFullscreenExtras {
    private static final Map<View, Art> art = new WeakHashMap<>();
    private static final Map<View, Art> collapsing = new WeakHashMap<>();
    private static final Map<View, Art> maskOwners = new WeakHashMap<>();
    private static final ThreadLocal<Art> nativeSong = new ThreadLocal<>();
    private static final Map<ViewGroup, WeakReference<Backdrop>> hosts = new WeakHashMap<>();

    private record ClockPath(Path path, float x, float y, float[] shape) {}

    private static final Map<View, ClockPath> clockPaths = new WeakHashMap<>();
    private static final Set<View> blurRebuilt =
            java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ClassLoader> motionInstalled =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final android.os.Handler main =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static DressroomModule module;
    private static boolean rootInstalled;

    static void screenChanged() {
        clockPaths.clear();
        for (WeakReference<Backdrop> reference : new ArrayList<>(hosts.values())) {
            Backdrop backdrop = reference.get();
            if (backdrop == null) continue;
            backdrop.restore();
            backdrop.current = null;
            backdrop.showing = false;
            backdrop.returnBounds.setEmpty();
            backdrop.returnExclusion.setEmpty();
            ViewGroup root = backdrop.root.get();
            if (root != null) {
                MediaClockContrast.depth(root, false);
                WidgetMediaOcclusion.publish(root, null);
                MediaClockContrast.clear(root);
            }
            backdrop.invalidate();
        }
        for (Art state : new ArrayList<>(art.values())) {
            state.reveal.discard();
            state.overlayActive = state.closing = false;
            state.returningCard.clear();
        }
    }

    static void clockPath(View clock, float x, float y, Path path) {
        ClockPath old = clockPaths.get(clock);
        if (path == null) {
            if (clockPaths.remove(clock) == null) return;
        } else {
            float[] shape = path.approximate(.5f);
            if (old != null
                    && old.x == x
                    && old.y == y
                    && java.util.Arrays.equals(old.shape, shape)) return;
            clockPaths.put(clock, new ClockPath(new Path(path), x, y, shape));
        }
        for (WeakReference<Backdrop> ref : new ArrayList<>(hosts.values())) {
            Backdrop backdrop = ref.get();
            ViewGroup root = backdrop == null ? null : backdrop.root.get();
            if (root != null
                    && root.getWindowToken() == clock.getWindowToken()
                    && backdrop.showing) {
                backdrop.clockMaskDirty = true;
                backdrop.invalidate();
            }
        }
    }



    private static Backdrop ensureHost(ViewGroup root) {
        WeakReference<Backdrop> reference = hosts.get(root);
        Backdrop host = reference == null ? null : reference.get();
        if (host != null) return host;
        if (!root.getClass()
                .getName()
                .equals("com.android.systemui.shade.NotificationShadeWindowView")) return null;
        host = new Backdrop(root);
        hosts.put(root, new WeakReference<>(host));
        int position = root.getChildCount();
        for (int i = 0; i < root.getChildCount(); i++) {
            String name = name(root.getChildAt(i));
            if (name.equals("notification_panel") || name.equals("lock_clock_blur")) {
                position = i;
                break;
            }
        }
        root.addView(host, position, new FrameLayout.LayoutParams(-1, -1));
        Backdrop backdrop = host;
        root.getViewTreeObserver()
                .addOnPreDrawListener(
                        () -> {
                            backdrop.update();
                            return true;
                        });
        android.util.Log.i(
                DressroomModule.TAG, "Artwork backdrop attached behind native clock/player");
        return host;
    }

    private static String name(View view) {
        if (view.getId() == View.NO_ID) return "";
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (android.content.res.Resources.NotFoundException e) {
            return "";
        }
    }

    private static final class Art {
        final WeakReference<View> target;
        final WeakReference<ImageView> image;
        final WeakReference<Object> holder;
        final Field bitmapField;
        WeakReference<View> small = new WeakReference<>(null);
        float startScale = 1, collapseScale = 1;
        WeakReference<View> overlayHost = new WeakReference<>(null);
        WeakReference<View> returningCard = new WeakReference<>(null);
        WeakReference<View> normalContent = new WeakReference<>(null);
        float returnHeight;
        final WeakReference<View> controls;
        final WeakReference<View> controlHost;
        boolean overlayActive, closing, thumbnailMuted;
        final NativeArtworkReveal reveal;
        Drawable displayedDrawable;
        Bitmap displayedBitmap;

        Art(View view, ImageView image, Object holder) throws ReflectiveOperationException {
            target = new WeakReference<>(view);
            this.image = new WeakReference<>(image);
            this.holder = new WeakReference<>(holder);
            bitmapField = holder.getClass().getDeclaredField("G0");
            bitmapField.setAccessible(true);

            controls = new WeakReference<>((View) Reflect.field(holder, "t0"));
            controlHost = new WeakReference<>((View) Reflect.field(holder, "o0"));
            View mask = optionalView(holder, "Q"), lighting = optionalView(holder, "S");
            View prior = optionalView(holder, "P");
            reveal =
                    new NativeArtworkReveal(
                            mask, image, prior instanceof ImageView old ? old : null, lighting);
            if (mask != null) maskOwners.put(mask, this);
        }

        private static View optionalView(Object holder, String field) {
            try {
                return (View) Reflect.field(holder, field);
            } catch (Exception ignored) {
                DressroomModule.note("Fullscreen optional holder field", ignored);
                return null;
            }
        }

        Bitmap bitmap() {
            ImageView view = image.get();
            Drawable drawable = view == null ? null : view.getDrawable();
            if (drawable == null) {
                displayedDrawable = null;
                displayedBitmap = null;
                return null;
            }
            if (drawable == displayedDrawable
                    && displayedBitmap != null
                    && !displayedBitmap.isRecycled()) return displayedBitmap;
            Bitmap source = drawable instanceof BitmapDrawable bd ? bd.getBitmap() : null;
            try {
                Object object = holder.get();
                Bitmap original = object == null ? null : (Bitmap) bitmapField.get(object);
                if (original != null && !original.isRecycled()) source = original;
            } catch (IllegalAccessException ignored) {
                DressroomModule.note("Fullscreen bitmap field", ignored);
            }
            displayedDrawable = drawable;
            displayedBitmap = source;
            return source != null && !source.isRecycled() ? source : null;
        }

        View moving() {
            View view = small.get();
            return view == null ? target.get() : view;
        }

        float progress() {
            View view = moving();
            if (view == null) return 0;
            float scale = view.getScaleX();
            if (small.get() != null)
                return clamp(collapseScale > 1.001f ? (scale - 1) / (collapseScale - 1) : 0);
            if (scale < .99f) startScale = Math.min(startScale, scale);
            return startScale < .99f ? clamp((scale - startScale) / (1 - startScale)) : 1;
        }
    }

    private static float clamp(float v) {
        return Float.isFinite(v) ? Math.max(0, Math.min(1, v)) : 0;
    }

    private static final class EmptyBackground extends Drawable {
        final Drawable original;

        EmptyBackground(Drawable original) {
            this.original = original;
        }

        public void draw(Canvas canvas) {}

        public void setAlpha(int alpha) {
            original.setAlpha(alpha);
        }

        public int getAlpha() {
            return original.getAlpha();
        }

        public void setColorFilter(ColorFilter filter) {
            original.setColorFilter(filter);
        }

        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    private static final class Saved {
        final View view;
        final float alpha;
        final int imageAlpha;
        final Drawable foreground;
        final int foregroundAlpha;
        EmptyBackground empty;
        boolean hidePixels, hideView, clearBackground, muteBlurHost;

        Saved(View v) {
            view = v;
            alpha = v.getAlpha();
            imageAlpha = v instanceof ImageView image ? image.getImageAlpha() : 255;
            foreground = v.getForeground();
            foregroundAlpha = foreground == null ? 255 : foreground.getAlpha();
        }

        void apply() {
            if (muteBlurHost) MediaBlurHost.hold(view);
            if (hidePixels && view instanceof ImageView image && image.getImageAlpha() != 0)
                image.setImageAlpha(0);
            if (hideView) MediaBlurHost.hold(view);
            if (clearBackground
                    && view.getBackground() != null
                    && !(view.getBackground() instanceof EmptyBackground)) {
                empty = new EmptyBackground(view.getBackground());
                view.setBackground(empty);
            }
            if (hidePixels && foreground != null && foreground.getAlpha() != 0)
                foreground.setAlpha(0);
        }

        void restore() {
            if (muteBlurHost) MediaBlurHost.release(view);
            if (hidePixels && view instanceof ImageView image) image.setImageAlpha(imageAlpha);
            if (hideView) MediaBlurHost.release(view);
            if (empty != null && view.getBackground() == empty) view.setBackground(empty.original);
            if (hidePixels && foreground != null) foreground.setAlpha(foregroundAlpha);
        }
    }

    private static final class Backdrop extends View {
        final WeakReference<ViewGroup> root;
        final Map<View, Saved> hidden = new WeakHashMap<>();
        final Map<View, Boolean> shortcuts = new WeakHashMap<>(), revealHeld = new WeakHashMap<>();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final RectF destination = new RectF(), cover = new RectF(), next = new RectF();
        final RectF controlRect = new RectF();
        final Path nativeCover = new Path(), nativeLocal = new Path();
        final android.graphics.Outline nativeOutline = new android.graphics.Outline();
        final android.graphics.Rect nativeRect = new android.graphics.Rect();
        boolean regularPublished;
        final Path coverShape = new Path(), maskScratch = new Path();
        final Matrix maskParent = new Matrix(), maskLocal = new Matrix(), maskRoot = new Matrix();
        final Map<View, Boolean> maskVisibility = new WeakHashMap<>();
        int maskSources = -1;
        final View returnLayer;
        final Path outline = new Path();
        final RectF returnBounds = new RectF(),
                returnExclusion = new RectF(),
                previousExclusion = new RectF();
        final RenderNode blurredArt = new RenderNode("Dressroom media controls artwork");
        final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint blurOpacity = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path clockMask = new Path();
        boolean clockMaskDirty = true;
        float controlAlpha;
        boolean compact, blurBandReady;
        float controlsTop = -1, fadeTop, fadeBottom;
        float bandTopNorm = 0, bandSolidNorm = 1;
        final RectF blurBounds = new RectF();
        float blurCorner = -1, expandedRadius = -1;
        boolean blurDirty = true;
        final Matrix global = new Matrix(),
                rootGlobal = new Matrix(),
                inverse = new Matrix(),
                local = new Matrix();
        Art current;
        Bitmap bitmap;
        int bitmapGeneration;
        View boundPage;
        boolean showing;

        Backdrop(ViewGroup root) {
            super(root.getContext());
            this.root = new WeakReference<>(root);
            returnLayer =
                    new View(root.getContext()) {
                        @Override
                        protected void onDraw(Canvas canvas) {
                            if (!showing || current == null || current.small.get() == null) return;
                            if (returnBounds.isEmpty()) return;
                            int save = canvas.save();
                            canvas.clipRect(returnBounds);
                            if (!returnExclusion.isEmpty()) canvas.clipOutRect(returnExclusion);
                            Backdrop.this.onDraw(canvas);
                            canvas.restoreToCount(save);
                        }
                    };
            returnLayer.setClickable(false);
            returnLayer.setFocusable(false);
            returnLayer.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            returnLayer.setVisibility(INVISIBLE);
            root.getOverlay().add(returnLayer);
            float radius = 24 * getResources().getDisplayMetrics().density;
            blurredArt.setRenderEffect(
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
            fadePaint.setBlendMode(BlendMode.DST_IN);
            setVisibility(INVISIBLE);
            setClickable(false);
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void update() {
            ViewGroup parent = root.get();
            if (parent == null) return;
            Art best = null;
            float area = 0;
            if (RuntimeExtras.enabled(getContext(), "fullscreenArt"))
                for (Art candidate : new ArrayList<>(art.values())) {
                    if (candidate.target.get() == null) continue;
                    View moving = candidate.moving();
                    View overlay = candidate.overlayHost.get();
                    boolean inOverlay =
                            candidate.overlayActive
                                    && overlay != null
                                    && overlay.getWindowToken() == parent.getWindowToken();
                    if (moving == null
                            || (!inOverlay
                                    && (!moving.isShown()
                                            || moving.getWindowToken() == null
                                            || moving.getWindowToken() != parent.getWindowToken()))
                            || candidate.bitmap() == null) continue;
                    float size =
                            moving.getWidth()
                                    * moving.getHeight()
                                    * moving.getScaleX()
                                    * moving.getScaleY();
                    if (size > area) {
                        best = candidate;
                        area = size;
                    }
                }
            if (best == null) {
                if (showing) {
                    restore();
                    MediaClockContrast.clear(parent);
                    MediaClockContrast.depth(parent, false);
                    WidgetMediaOcclusion.publish(parent, null);
                    showing = false;
                    bitmap = null;
                    current = null;
                    for (View clock : new ArrayList<>(clockPaths.keySet()))
                        if (clock != null && clock.isAttachedToWindow())
                            try {
                                Reflect.call(clock, "setClockTextBlur");
                                if (blurRebuilt.add(clock))
                                    android.util.Log.i(
                                            DressroomModule.TAG,
                                            "Clock blur rebuilt after media return");
                            } catch (Exception ignored) {
                                DressroomModule.note("Fullscreen clock blur rebuild", ignored);
                            }
                    clockMask.reset();
                    clockMaskDirty = true;
                    maskVisibility.clear();
                    maskSources = -1;
                    blurredArt.discardDisplayList();
                    setVisibility(INVISIBLE);
                    returnLayer.setVisibility(INVISIBLE);
                }
                updateRegularWidgets(parent);
                return;
            }
            regularPublished = false;
            View target = best.target.get();
            View page = target;
            for (View v = target; v != null; v = v.getParent() instanceof View p ? p : null) {
                if (name(v).equals("facewidget_music_page_container")) {
                    page = v;
                    break;
                }
            }
            if (best != current || page != boundPage) {
                if (best != current) blurBandReady = false;
                restore();
                current = best;
                boundPage = page;
                collect(parent);
                collect(target);
                android.util.Log.i(
                        DressroomModule.TAG,
                        "Artwork layer active: bitmap="
                                + best.bitmap().getWidth()
                                + "x"
                                + best.bitmap().getHeight()
                                + " target="
                                + target.getWidth()
                                + "x"
                                + target.getHeight()
                                + " replacements="
                                + hidden.size());
            }
            for (Saved saved : hidden.values()) saved.apply();
            View nativeThumbnail = best.small.get();
            if (nativeThumbnail != null) {
                MediaBlurHost.hold(nativeThumbnail);
                best.thumbnailMuted = true;
            }

            Bitmap image = best.bitmap();
            View moving = best.moving();
            if (moving == null) return;
            global.reset();
            rootGlobal.reset();
            View overlay = best.overlayHost.get();
            if (best.overlayActive && overlay != null) {
                global.set(moving.getMatrix());
                global.postTranslate(moving.getLeft(), moving.getTop());
                overlay.transformMatrixToGlobal(rootGlobal);
                local.setConcat(rootGlobal, global);
                global.set(local);
            } else moving.transformMatrixToGlobal(global);
            rootGlobal.reset();
            parent.transformMatrixToGlobal(rootGlobal);
            if (!maskRoot.equals(rootGlobal)) {
                maskRoot.set(rootGlobal);
                clockMaskDirty = true;
            }
            if (maskSources != clockPaths.size()) {
                maskSources = clockPaths.size();
                maskVisibility.clear();
                clockMaskDirty = true;
            }
            for (View clock : clockPaths.keySet()) {
                boolean visible =
                        clock.isShown() && clock.getWindowToken() == parent.getWindowToken();
                Boolean old = maskVisibility.put(clock, visible);
                if (old == null || old != visible) clockMaskDirty = true;
            }
            if (!rootGlobal.invert(inverse)) return;
            local.setConcat(inverse, global);
            next.set(0, 0, moving.getWidth(), moving.getHeight());
            local.mapRect(next);
            float p = best.progress();
            if (best.closing || best.small.get() != null) {
                float reveal = compactReveal(p);
                for (View shortcut : shortcuts.keySet()) {
                    MediaBlurHost.fade(shortcut, reveal);
                    revealHeld.put(shortcut, true);
                }
                View normal = best.normalContent.get();
                if (normal != null) {
                    MediaBlurHost.fade(normal, reveal);
                    revealHeld.put(normal, true);
                }
            } else releaseReveal();
            next.set(
                    next.left * (1 - p),
                    next.top * (1 - p),
                    next.right + (parent.getWidth() - next.right) * p,
                    next.bottom + (parent.getHeight() - next.bottom) * p);
            // Once the clock starts returning, clip its moving glyphs against the wallpaper.
            // Waiting for the artwork layer to disappear leaves the whole return in front.
            MediaClockContrast.depth(parent, !best.closing && best.small.get() == null);
            try {
                if (best.small.get() != null) MediaClockContrast.clear(parent);
                else MediaClockContrast.apply(parent, image, next);
            } catch (RuntimeException error) {
                MediaClockContrast.clear(parent);
            }
            int generation = image.getGenerationId();
            boolean geometryChanged = !showing || !destination.equals(next);
            boolean changed = geometryChanged || bitmap != image || bitmapGeneration != generation;
            boolean coverChanged = changed;
            if (best.reveal.update()) {
                changed = true;
                coverChanged = true;
            }
            if (best.reveal.running()) postInvalidateOnAnimation();
            View controls = best.controls.get();
            View controlHost = best.controlHost.get();
            float controlsOpacity =
                    controls == null || !controls.isShown()
                            ? 0
                            : controls.getAlpha()
                                    * (controlHost == null ? 1 : controlHost.getAlpha());
            controlsOpacity = clamp(controlsOpacity) * clamp(p);
            if (controlAlpha != controlsOpacity) {
                controlAlpha = controlsOpacity;
                changed = true;
            }
            if (controls != null && controls.isShown()) {
                global.reset();
                controls.transformMatrixToGlobal(global);
                local.setConcat(inverse, global);
                controlRect.set(0, 0, controls.getWidth(), controls.getHeight());
                local.mapRect(controlRect);
                if (controlsTop != controlRect.top) {
                    controlsTop = controlRect.top;
                    changed = true;
                }
                if (!best.closing
                        && best.small.get() == null
                        && controls.getWidth() > 0
                        && controls.getHeight() > 0) {
                    float density = getResources().getDisplayMetrics().density,
                            height = Math.max(1, next.height());
                    float top = Math.max(next.top, controlsTop - 64 * density),
                            bottom = Math.min(next.bottom, controlsTop + 48 * density);
                    if (bottom > top) {
                        bandTopNorm = clamp((top - next.top) / height);
                        bandSolidNorm = clamp((bottom - next.top) / height);
                        blurBandReady = true;
                    }
                }
            }
            compact = best.small.get() != null;
            bitmap = image;
            bitmapGeneration = generation;
            destination.set(next);
            showing = true;
            if (geometryChanged) {
                coverShape.reset();
                float corner = Math.min(destination.width(), destination.height()) * .5f * (1 - p);
                coverShape.addRoundRect(destination, corner, corner, Path.Direction.CW);
                WidgetMediaOcclusion.publish(parent, coverShape);
            }
            View card = best.returningCard.get();
            if (best.small.get() != null && card != null) {
                global.reset();
                card.transformMatrixToGlobal(global);
                local.setConcat(inverse, global);
                next.set(0, 0, card.getWidth(), card.getHeight());
                local.mapRect(next);
                if (best.returnHeight > 0)
                    next.top = Math.max(next.top, next.bottom - best.returnHeight);
                if (!returnBounds.equals(next)) {
                    returnBounds.set(next);
                    changed = true;
                }
                previousExclusion.set(returnExclusion);
                returnExclusion.setEmpty();
                if (visibleControls(controlHost) && visibleControls(controls)) {
                    global.reset();
                    controlHost.transformMatrixToGlobal(global);
                    local.setConcat(inverse, global);
                    returnExclusion.set(0, 0, controlHost.getWidth(), controlHost.getHeight());
                    local.mapRect(returnExclusion);
                }
                if (!previousExclusion.equals(returnExclusion)) changed = true;
                if (returnLayer.getWidth() != parent.getWidth()
                        || returnLayer.getHeight() != parent.getHeight())
                    returnLayer.layout(0, 0, parent.getWidth(), parent.getHeight());
                if (returnLayer.getVisibility() != VISIBLE) returnLayer.setVisibility(VISIBLE);
            } else detachReturnLayer();
            if (getVisibility() != VISIBLE) setVisibility(VISIBLE);
            float alpha = 1;
            for (View v = page;
                    v != null && v != parent;
                    v = v.getParent() instanceof View pv ? pv : null) {
                Saved state = hidden.get(v);
                alpha *= state != null && state.hideView ? state.alpha : v.getAlpha();
            }
            if (getAlpha() != alpha) setAlpha(alpha);
            if (coverChanged) blurDirty = true;
            if (changed) {
                invalidate();
                if (returnLayer.getVisibility() == VISIBLE) returnLayer.invalidate();
            }
        }

        void collect(View view) {
            String n = name(view);
            if (n.equals("left_shortcut_area") || n.equals("right_shortcut_area"))
                shortcuts.put(view, true);
            boolean pixels =
                    n.equals("facewidget_album_art_background")
                            || n.equals("now_bar_media_prev_album_art")
                            || n.equals("now_bar_media_expand_album_art_aod_mask");
            boolean whole =
                    n.equals("now_bar_media_album_art_circle_reveal")
                            || n.equals("now_bar_media_album_art_circle_ripple_parent");
            boolean liveBlur = n.equals("lock_clock_blur");
            boolean bg =
                    n.equals("keyguard_status_view")
                            || n.equals("expand_content_rootview")
                            || n.equals("nowbar_expandview_background")
                            || n.equals("nowbar_cardview_background")
                            || n.equals("nowbar_cardview_bgview_for_media_setting");
            if ((pixels || whole || bg || liveBlur) && !hidden.containsKey(view)) {
                Saved saved = new Saved(view);
                saved.hidePixels = pixels;
                saved.hideView = whole;
                saved.clearBackground = bg;
                saved.muteBlurHost = liveBlur;
                hidden.put(view, saved);
            }
            if (view instanceof ViewGroup group)
                for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i));
        }

        private void detachReturnLayer() {
            returnLayer.setVisibility(INVISIBLE);
            returnBounds.setEmpty();
            returnExclusion.setEmpty();
        }

        void beginClosing(Art state) {
            if (!showing || current != state) return;
            state.closing = true;
            for (View shortcut : shortcuts.keySet()) {
                MediaBlurHost.fade(shortcut, 0);
                revealHeld.put(shortcut, true);
            }
            View normal = state.normalContent.get();
            if (normal != null) {
                MediaBlurHost.fade(normal, 0);
                revealHeld.put(normal, true);
            }
        }

        private void releaseReveal() {
            for (View view : revealHeld.keySet()) MediaBlurHost.release(view);
            revealHeld.clear();
        }

        void restore() {
            releaseReveal();
            shortcuts.clear();
            detachReturnLayer();
            View small = current == null ? null : current.small.get();
            if (small != null && current.thumbnailMuted) {
                MediaBlurHost.release(small);
                current.thumbnailMuted = false;
            }
            if (current != null) current.reveal.discard();
            for (Saved saved : hidden.values()) saved.restore();
            hidden.clear();
            boundPage = null;
        }

        private void drawArtwork(Canvas canvas) {
            Bitmap image = bitmap;
            if (!showing || image == null || image.isRecycled() || destination.isEmpty()) return;
            float scale =
                    Math.max(
                            destination.width() / image.getWidth(),
                            destination.height() / image.getHeight());
            cover.set(
                    destination.centerX() - image.getWidth() * scale / 2,
                    destination.centerY() - image.getHeight() * scale / 2,
                    destination.centerX() + image.getWidth() * scale / 2,
                    destination.centerY() + image.getHeight() * scale / 2);
            float radius = coverCorner();
            outline.reset();
            outline.addRoundRect(destination, radius, radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(outline);
            drawPixels(canvas);
            canvas.restoreToCount(save);
        }

        private void drawPixels(Canvas canvas) {
            if (current != null) current.reveal.draw(canvas, bitmap, destination, paint);
        }

        private void updateRegularWidgets(ViewGroup parent) {
            if (RuntimeExtras.enabled(getContext(), "fullscreenArt")) return;
            Art source = null;
            for (Art state : art.values()) {
                View view = state.moving(), overlay = state.overlayHost.get();
                boolean owns =
                        state.overlayActive
                                && overlay != null
                                && overlay.getWindowToken() == parent.getWindowToken();
                if (state.closing
                        && view != null
                        && (owns
                                || view.isShown()
                                        && view.getWindowToken() == parent.getWindowToken())) {
                    source = state;
                    break;
                }
            }
            if (source == null) {
                if (regularPublished) {
                    WidgetMediaOcclusion.publish(parent, null);
                    regularPublished = false;
                }
                return;
            }
            View view = source.moving();
            global.reset();
            rootGlobal.reset();
            View overlay = source.overlayHost.get();
            if (source.overlayActive && overlay != null) {
                global.set(view.getMatrix());
                global.postTranslate(view.getLeft(), view.getTop());
                overlay.transformMatrixToGlobal(rootGlobal);
                local.setConcat(rootGlobal, global);
                global.set(local);
            } else view.transformMatrixToGlobal(global);
            rootGlobal.reset();
            parent.transformMatrixToGlobal(rootGlobal);
            if (rootGlobal.invert(inverse)) {
                local.setConcat(inverse, global);
                nativeLocal.reset();
                nativeOutline.setEmpty();
                if (view.getOutlineProvider() != null)
                    view.getOutlineProvider().getOutline(view, nativeOutline);
                if (view.getClipToOutline() && nativeOutline.getRect(nativeRect))
                    nativeLocal.addRoundRect(
                            new RectF(nativeRect),
                            nativeOutline.getRadius(),
                            nativeOutline.getRadius(),
                            Path.Direction.CW);
                else
                    nativeLocal.addRect(0, 0, view.getWidth(), view.getHeight(), Path.Direction.CW);
                nativeLocal.transform(local, nativeCover);
                WidgetMediaOcclusion.publish(parent, nativeCover);
                regularPublished = true;
            }
        }

        float coverCorner() {
            float shrunk =
                    Math.min(destination.width(), destination.height())
                            * .5f
                            * (1 - current.progress());
            if (expandedRadius < 0) expandedRadius = nativeExpandedRadius();
            return Math.max(expandedRadius, shrunk);
        }

        private float nativeExpandedRadius() {
            View view = current == null ? null : current.target.get();
            android.content.res.Resources resources =
                    view == null ? getResources() : view.getResources();
            for (String owner :
                    new String[] {
                        "com.samsung.android.app.aodservice", getContext().getPackageName()
                    }) {
                int id =
                        resources.getIdentifier(
                                "now_bar_media_card_expand_bg_radius", "dimen", owner);
                if (id != 0)
                    try {
                        return resources.getDimension(id);
                    } catch (android.content.res.Resources.NotFoundException ignored) {
                    }
            }
            return 24 * getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawArtwork(canvas);
            Bitmap image = bitmap;
            if (!showing || image == null || image.isRecycled() || destination.isEmpty()) return;
            if (canvas.isHardwareAccelerated()) {
                float height = destination.height();
                float start = destination.top + bandTopNorm * height;
                float solid = destination.top + bandSolidNorm * height;
                float end = destination.bottom;
                float bandAlpha = compact ? clamp(current.progress()) : controlAlpha;
                boolean band = blurBandReady && end > start && solid > start && bandAlpha > 0;
                if (band || clockMaskDirty || !clockMask.isEmpty()) {
                    float corner = coverCorner();
                    if (blurDirty
                            || blurredArt.getWidth() != getWidth()
                            || blurredArt.getHeight() != getHeight()
                            || !blurBounds.equals(destination)
                            || blurCorner != corner) {
                        blurredArt.setPosition(0, 0, getWidth(), getHeight());
                        Canvas record = blurredArt.beginRecording();
                        int clipped = record.save();
                        record.clipPath(outline);
                        drawPixels(record);
                        record.restoreToCount(clipped);
                        blurredArt.endRecording();
                        blurBounds.set(destination);
                        blurCorner = corner;
                        blurDirty = false;
                    }
                    if (clockMaskDirty) {
                        clockMask.reset();
                        ViewGroup parent = root.get();
                        maskParent.reset();
                        boolean mapped = false;
                        if (parent != null) {
                            parent.transformMatrixToGlobal(maskParent);
                            mapped = maskParent.invert(maskLocal);
                        }
                        if (parent != null)
                            for (Map.Entry<View, ClockPath> entry : clockPaths.entrySet()) {
                                View clock = entry.getKey();
                                if (!clock.isShown()
                                        || clock.getWindowToken() != parent.getWindowToken())
                                    continue;
                                ClockPath shape = entry.getValue();
                                maskScratch.set(shape.path);
                                maskScratch.offset(shape.x, shape.y);
                                if (mapped) {
                                    maskScratch.transform(maskLocal);
                                    clockMask.addPath(maskScratch);
                                }
                            }
                        clockMaskDirty = false;
                    }
                    if (!clockMask.isEmpty()) {
                        int clip = canvas.save();
                        canvas.clipPath(clockMask);
                        canvas.drawRenderNode(blurredArt);
                        canvas.restoreToCount(clip);
                    }
                    if (!band) return;
                    if (fadeTop != start || fadeBottom != solid) {
                        fadeTop = start;
                        fadeBottom = solid;
                        fadePaint.setShader(
                                new LinearGradient(
                                        0,
                                        start,
                                        0,
                                        solid,
                                        0x00ffffff,
                                        0xffffffff,
                                        Shader.TileMode.CLAMP));
                    }
                    blurOpacity.setAlpha(Math.round(bandAlpha * 255));
                    int coverClip = canvas.save();
                    canvas.clipPath(outline);
                    int layer = canvas.saveLayer(0, start, getWidth(), end, blurOpacity);
                    canvas.drawRenderNode(blurredArt);
                    canvas.drawRect(0, start, getWidth(), end, fadePaint);
                    canvas.restoreToCount(layer);
                    canvas.restoreToCount(coverClip);
                }
            }
        }
    }



    private static void invalidateSong(Art state) {
        for (WeakReference<Backdrop> reference : hosts.values()) {
            Backdrop host = reference.get();
            if (host != null && host.current == state && host.showing) {
                host.blurDirty = true;
                host.invalidate();
            }
        }
    }



    private static void bind(View view) {
        if (view.getRootView() instanceof ViewGroup root) main.post(() -> ensureHost(root));
        for (View v = view; v != null; v = v.getParent() instanceof View p ? p : null)
            if (v.getClass()
                    .getName()
                    .equals("com.samsung.android.app.facewidget.pages.music.FaceWidgetMusicPage")) {
                ClassLoader loader = v.getClass().getClassLoader();
                
                break;
            }
    }



    static float compactReveal(float coverProgress) {
        return clamp((.2f - coverProgress) / .2f);
    }

    static boolean visibleControls(View view) {
        if (view == null || !view.isShown()) return false;
        float alpha = 1;
        for (View current = view;
                current != null;
                current = current.getParent() instanceof View parent ? parent : null) {
            alpha *= current.getAlpha();
            if (alpha <= .01f) return false;
        }
        return true;
    }

    private static View find(View root, String name) {
        if (root == null) return null;
        if (name(root).equals(name)) return root;
        if (root instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), name);
                if (found != null) return found;
            }
        return null;
    }

    static Object nativeSongBegin(Object coordinator) {
        Art previous=nativeSong.get();try{Art current=maskOwners.get(Reflect.field(coordinator,"g"));if(current!=null)nativeSong.set(current);}catch(Exception ignored){}return previous;
    }
    static void nativeSongEnd(Object previous){if(previous==null)nativeSong.remove();else nativeSong.set((Art)previous);}
    static void nativeSongAnimator(android.animation.Animator animation){Art state=nativeSong.get();if(state!=null){state.reveal.animation(animation);invalidateSong(state);}}
    static void nativeRoot(ViewGroup root) {module=DressroomModule.get();ensureHost(root);}
    static void nativeHolder(Object holder) {
        module=DressroomModule.get();
        try{
            View view=(View)Reflect.field(holder,"U"); ImageView image=(ImageView)Reflect.field(holder,"O");
            if(view==null)return;art.put(view,new Art(view,image,holder));MediaInteraction.bind(holder,view);bind(view);
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){bind(v);}public void onViewDetachedFromWindow(View v){}});
        }catch(Exception error){module.report("Artwork native holder",error);}
    }
    static void nativeSongProgress(View mask,float value){
        Art s=maskOwners.get(mask);if(s==null)return;
        if(value==0&&RuntimeExtras.enabled(mask.getContext(),"fullscreenArt"))s.reveal.begin(s.displayedBitmap,s.bitmap());
        s.reveal.progress(value);invalidateSong(s);
    }
    static void nativeSongRandom(View mask,float value){Art s=maskOwners.get(mask);if(s!=null)s.reveal.random(value);}
    static void nativeCardReveal(Object card,boolean expanded){
        try{View page=(View)Reflect.field(card,"I1");Art s=art.get(find(page,"facewidget_album_art_vi_container_parent"));
            if(s==null)return;s.closing=!expanded;
            if(s.closing){s.normalContent=new WeakReference<>((View)Reflect.call(card,"getNormalContentRootView"));for(var ref:hosts.values()){Backdrop h=ref.get();if(h!=null)h.beginClosing(s);}}
        }catch(Exception error){DressroomModule.note("Media reveal",error);}
    }
    static void nativeExpanded(View big,View overlay){Art s=art.get(big);if(s!=null){s.startScale=big.getScaleX();s.overlayHost=new WeakReference<>(overlay);s.overlayActive=true;}}
    static void nativeCollapsed(Object card,View overlay,View big,View small){
        Art s=art.get(big);if(s==null)return;s.closing=true;s.small=new WeakReference<>(small);s.collapseScale=small.getScaleX();s.overlayHost=new WeakReference<>(overlay);s.overlayActive=true;s.returningCard=new WeakReference<>((View)card);
        try{s.normalContent=new WeakReference<>((View)Reflect.call(card,"getNormalContentRootView"));s.returnHeight=((Number)Reflect.call(card,"getNormalCardHeight")).floatValue();}catch(Exception e){s.returnHeight=small.getHeight()*1.8f;}
        collapsing.put(small,s);if(RuntimeExtras.enabled(big.getContext(),"fullscreenArt")){MediaBlurHost.hold(small);s.thumbnailMuted=true;}
    }
    static void nativeCollapseEnd(View small){Art s=collapsing.remove(small);if(s==null)return;if(s.thumbnailMuted){MediaBlurHost.release(small);s.thumbnailMuted=false;}s.small.clear();s.closing=false;s.startScale=1;s.overlayActive=false;s.overlayHost.clear();s.returningCard.clear();s.normalContent.clear();}
    static void nativeExpandEnd(View big){Art s=art.get(big);if(s!=null){s.closing=false;s.overlayActive=false;s.overlayHost.clear();s.startScale=1;}}

    static void nativeExpansion(Object transaction){
        try{if(((Number)Reflect.field(transaction,"o")).intValue()!=5)return;
            View page=(View)Reflect.field(transaction,"q"),overlay=(View)Reflect.field(transaction,"r");
            View target=find(page,"facewidget_album_art_vi_container_parent");if(target!=null&&RuntimeExtras.enabled(page.getContext(),"fullscreenArt"))nativeExpanded(target,overlay);
        }catch(Exception error){DressroomModule.note("Artwork expansion",error);}
    }
}
