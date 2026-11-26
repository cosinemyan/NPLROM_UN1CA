// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class MediaClockContrast {
    private static final Set<View> clocks = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<TextView, Saved> colors = new WeakHashMap<>();
    private static final Map<ViewGroup, Snapshot> samples = new WeakHashMap<>();
    private static final Map<ImageView, Icon> icons = new WeakHashMap<>();
    private static final Map<ViewGroup, Set<View>> suspended = new WeakHashMap<>();
    private static final Set<Class<?>> customClasses =
            Collections.newSetFromMap(new WeakHashMap<>());

    private record Snapshot(WeakReference<Bitmap> source, int generation, ArtworkSamples pixels) {}

    private static final class Work {
        final RectF cover = new RectF();
        final Rect bounds = new Rect();
        final int[] origin = new int[2];
        final Set<TextView> visible = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final Set<ImageView> weather = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        final ArrayList<View> clocks = new ArrayList<>();
        final ArrayList<TextView> texts = new ArrayList<>();
        final ArrayList<ImageView> images = new ArrayList<>();
        boolean busy;

        void clear() {
            visible.clear();
            weather.clear();
            clocks.clear();
            texts.clear();
            images.clear();
            busy = false;
        }
    }

    private static final ThreadLocal<Work> workspace = ThreadLocal.withInitial(Work::new);
    private static boolean installed = true, writing;
    private static final ThreadLocal<TextView> drawing = new ThreadLocal<>();

    private static final class Saved {
        final WeakReference<ViewGroup> root;
        ColorStateList nativeColors;
        int color;
        int target;
        boolean closing;
        ValueAnimator animator;
        boolean initialized;
        final boolean custom;
        Bitmap bitmap;
        int generation;
        final RectF cover = new RectF();
        final Rect bounds = new Rect();

        Saved(ViewGroup root, TextView view) {
            this.root = new WeakReference<>(root);
            nativeColors = view.getTextColors();
            color = target = nativeColors.getDefaultColor();
            custom = customClasses.stream().anyMatch(type -> type.isInstance(view));
        }
    }

    private static final class Icon {
        final WeakReference<ViewGroup> root;
        ColorFilter nativeFilter;
        ColorFilter filter;
        int color, target;
        boolean closing;
        ValueAnimator animator;

        Icon(ViewGroup root, ImageView view) {
            this.root = new WeakReference<>(root);
            nativeFilter = view.getColorFilter();
        }
    }

    static void track(View clock) {
        clocks.add(clock);
    }

    static boolean overridesDraw(TextView view) {
        Saved value = colors.get(view);
        return value != null && value.initialized;
    }

    static void depth(ViewGroup root, boolean active) {
        if (!active) {
            Set<View> previous = suspended.remove(root);
            if (previous != null)
                for (View clock : new ArrayList<>(previous))
                    if (clock != null) MediaDepthTransition.restore(clock);
            return;
        }
        Set<View> held =
                suspended.computeIfAbsent(
                        root, ignored -> Collections.newSetFromMap(new WeakHashMap<>()));
        for (View clock : new ArrayList<>(clocks))
            if (clock != null && clock.getWindowToken() == root.getWindowToken()) {
                if (held.add(clock)) MediaDepthTransition.update(clock, true);
            }
    }



    static void clear(ViewGroup root) {
        samples.remove(root);
        if (colors.isEmpty() && icons.isEmpty()) return;
        for (TextView view : new ArrayList<>(colors.keySet())) {
            Saved saved = colors.get(view);
            if (saved != null && (saved.root.get() == root || saved.root.get() == null)) {
                if (!saved.closing) {
                    if (saved.custom) saved.nativeColors = view.getTextColors();
                    fade(view, saved, saved.nativeColors.getDefaultColor(), true);
                }
            }
        }
        for (ImageView view : new ArrayList<>(icons.keySet())) {
            Icon saved = icons.get(view);
            if (saved != null && (saved.root.get() == root || saved.root.get() == null)) {
                Integer nativeTint = nativeTint(saved.nativeFilter);
                if (nativeTint != null) {
                    if (!saved.closing) fadeIcon(view, saved, nativeTint, true);
                } else {
                    if (saved.animator != null) saved.animator.cancel();
                    icons.remove(view);
                    writeIcon(view, saved.nativeFilter);
                }
            }
        }
    }

    static void apply(ViewGroup root, Bitmap image, RectF destination) {
        if (!installed || image == null || image.isRecycled() || destination.isEmpty()) return;
        Work work = workspace.get();
        if (work.busy) work = new Work();
        work.busy = true;
        try {
            apply(root, image, destination, work);
        } finally {
            work.clear();
        }
    }

    private static void apply(ViewGroup root, Bitmap image, RectF destination, Work work) {
        int generation = image.getGenerationId();
        Snapshot snapshot = samples.get(root);
        if (snapshot == null
                || snapshot.source.get() != image
                || snapshot.generation != generation) {
            Bitmap readable =
                    image.getConfig() == Bitmap.Config.HARDWARE
                            ? image.copy(Bitmap.Config.ARGB_8888, false)
                            : image;
            if (readable == null) return;
            Bitmap pixels = null;
            ArtworkSamples cached;
            try {
                pixels =
                        Bitmap.createScaledBitmap(
                                readable,
                                Math.min(96, image.getWidth()),
                                Math.min(96, image.getHeight()),
                                true);
                if (pixels == null) return;
                cached = ArtworkSamples.read(pixels);
            } finally {
                if (pixels != null && pixels != image && pixels != readable) pixels.recycle();
                if (readable != image) readable.recycle();
            }
            snapshot = new Snapshot(new WeakReference<>(image), generation, cached);
            samples.put(root, snapshot);
        }
        float scale =
                Math.max(
                        destination.width() / image.getWidth(),
                        destination.height() / image.getHeight());
        RectF cover = work.cover;
        cover.set(
                destination.centerX() - image.getWidth() * scale / 2,
                destination.centerY() - image.getHeight() * scale / 2,
                destination.centerX() + image.getWidth() * scale / 2,
                destination.centerY() + image.getHeight() * scale / 2);
        root.getLocationOnScreen(work.origin);
        cover.offset(work.origin[0], work.origin[1]);
        Set<TextView> visible = work.visible;
        Set<ImageView> weather = work.weather;
        for (View clock : clocks) work.clocks.add(clock);
        for (View clock : work.clocks) {
            if (clock == null
                    || !clock.isShown()
                    || clock.getWindowToken() != root.getWindowToken()) continue;
            if (clock.getDisplay() == null
                    || (!ScreenIdentity.supported() && clock.getDisplay().getDisplayId() != 0)
                    || root.getDisplay() == null
                    || clock.getDisplay().getDisplayId() != root.getDisplay().getDisplayId())
                continue;
            try {
                String category = String.valueOf(Reflect.call(clock, "getCategory"));
                if (!category.equals("LOCK_SCREEN") && !category.equals("AOD")) continue;
            } catch (Exception ignored) {
                continue;
            }
            collect(clock, visible, weather);
        }
        for (TextView view : visible) {
            Rect bounds = work.bounds;
            if (!view.getGlobalVisibleRect(bounds) || bounds.isEmpty()) continue;
            Saved saved = colors.get(view);
            if (saved == null) {
                saved = new Saved(root, view);
                colors.put(view, saved);
            }
            if (saved.initialized
                    && !saved.closing
                    && saved.bitmap == image
                    && saved.generation == generation
                    && saved.cover.equals(cover)
                    && saved.bounds.equals(bounds)) continue;
            double brightness = snapshot.pixels.sample(cover, bounds);
            boolean dark = saved.initialized ? (saved.target & 0xffffff) == 0 : brightness > .179;
            if (brightness > .22) dark = true;
            else if (brightness < .14) dark = false;
            saved.initialized = true;
            saved.bitmap = image;
            saved.generation = generation;
            saved.cover.set(cover);
            saved.bounds.set(bounds);
            fade(view, saved, dark ? 0xff000000 : 0xffffffff, false);
        }
        for (TextView view : colors.keySet()) work.texts.add(view);
        for (TextView view : work.texts) {
            Saved saved = colors.get(view);
            if (saved != null && saved.root.get() == root && !visible.contains(view)) {
                if (saved.animator != null) {
                    ValueAnimator animator = saved.animator;
                    saved.animator = null;
                    animator.cancel();
                }
                colors.remove(view);
                if (!saved.custom) write(view, saved.nativeColors);
                else view.invalidate();
            }
        }
        for (ImageView view : weather) {
            Rect bounds = work.bounds;
            if (!view.getGlobalVisibleRect(bounds) || bounds.isEmpty()) continue;
            Icon saved = icons.get(view);
            if (saved == null) {
                saved = new Icon(root, view);
                icons.put(view, saved);
            }
            double brightness = snapshot.pixels.sample(cover, bounds);
            boolean dark = saved.filter == null ? brightness > .179 : saved.target == 0xff000000;
            if (brightness > .22) dark = true;
            else if (brightness < .14) dark = false;
            int color = dark ? 0xff000000 : 0xffffffff;
            if (saved.filter == null || color != saved.target || saved.closing)
                fadeIcon(view, saved, color, false);
            if (view.getColorFilter() != saved.filter) writeIcon(view, saved.filter);
        }
        for (ImageView view : icons.keySet()) work.images.add(view);
        for (ImageView view : work.images) {
            Icon saved = icons.get(view);
            if (saved != null && saved.root.get() == root && !weather.contains(view)) {
                if (saved.animator != null) {
                    ValueAnimator animator = saved.animator;
                    saved.animator = null;
                    animator.cancel();
                }
                icons.remove(view);
                writeIcon(view, saved.nativeFilter);
            }
        }
    }

    private static void fade(TextView view, Saved saved, int target, boolean closing) {
        if (saved.target == target
                && saved.closing == closing
                && (saved.animator != null || saved.color == target)) return;
        if (saved.animator != null) {
            ValueAnimator previous = saved.animator;
            saved.animator = null;
            previous.cancel();
        }
        saved.target = target;
        saved.closing = closing;
        ValueAnimator animator = ValueAnimator.ofArgb(saved.color, target);
        saved.animator = animator;
        animator.setDuration(220);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        animator.addUpdateListener(
                value -> {
                    saved.color = (Integer) value.getAnimatedValue();
                    if (saved.custom) view.invalidate();
                    else write(view, ColorStateList.valueOf(saved.color));
                });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (saved.animator != animation) return;
                        saved.animator = null;
                        if (saved.closing && colors.get(view) == saved) {
                            colors.remove(view);
                            if (!saved.custom) write(view, saved.nativeColors);
                            else view.invalidate();
                        }
                    }
                });
        animator.start();
    }

    private static void fadeIcon(ImageView view, Icon saved, int target, boolean closing) {
        if (saved.animator != null) {
            ValueAnimator previous = saved.animator;
            saved.animator = null;
            previous.cancel();
        }
        if (saved.filter == null) {
            Integer nativeTint = nativeTint(saved.nativeFilter);
            saved.color = nativeTint == null ? target : nativeTint;
        }
        saved.target = target;
        saved.closing = closing;
        ValueAnimator animator = ValueAnimator.ofArgb(saved.color, target);
        saved.animator = animator;
        animator.setDuration(220);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        animator.addUpdateListener(
                value -> {
                    saved.color = (Integer) value.getAnimatedValue();
                    saved.filter = new PorterDuffColorFilter(saved.color, PorterDuff.Mode.SRC_IN);
                    writeIcon(view, saved.filter);
                });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (saved.animator != animation) return;
                        saved.animator = null;
                        if (saved.closing && icons.get(view) == saved) {
                            icons.remove(view);
                            writeIcon(view, saved.nativeFilter);
                        }
                    }
                });
        animator.start();
    }

    private static void collect(View view, Set<TextView> found, Set<ImageView> weather) {
        if (!view.isShown()) return;
        if (view instanceof TextView text && text.getText().length() > 0) found.add(text);
        if (view instanceof ImageView image
                && image.getDrawable() != null
                && image.getId() != View.NO_ID)
            try {
                if (image.getResources().getResourceEntryName(image.getId()).contains("weather"))
                    weather.add(image);
            } catch (android.content.res.Resources.NotFoundException ignored) {
            }
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++)
                collect(group.getChildAt(i), found, weather);
    }

    private static void write(TextView view, ColorStateList value) {
        boolean old = writing;
        writing = true;
        try {
            if (view.getTextColors() != value) view.setTextColor(value);
        } finally {
            writing = old;
        }
    }

    private static void writeIcon(ImageView view, ColorFilter value) {
        boolean old = writing;
        writing = true;
        try {
            view.setColorFilter(value);
        } finally {
            writing = old;
        }
    }

    private static Integer nativeTint(ColorFilter filter) {
        if (filter == null) return null;
        try {
            return ((Number) Reflect.call(filter, "getColor")).intValue();
        } catch (Exception ignored) {
            return null;
        }
    }
}
