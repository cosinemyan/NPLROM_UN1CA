// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.graphics.Canvas;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Extra rear layers; the native three-card stack still owns gestures and promotion. */
final class NowBarStackLayers {
    private final DressroomModule module;
    private final NowBarStackBinding binding;
    private final Class<?> stackClass;
    private final Field items, restricted, mode, screenOn;
    private final Method expanded, content;
    private final Field dragging, reordering, dismissing, animationManager, animationsEnabled;
    private final ThreadLocal<Release> release = new ThreadLocal<>();
    private final Constructor<?> stackSize;
    private final WeakHashMap<ViewGroup, WeakReference<Tail>> tails = new WeakHashMap<>();
    private volatile int liveTails;
    private final WeakHashMap<android.content.res.Resources, float[]> offsets = new WeakHashMap<>();

    NowBarStackLayers(DressroomModule module, NowBarStackBinding binding) {
        this.module = module;
        this.binding = binding;
        stackClass = binding.stack;
        items = binding.items;
        restricted = binding.restricted;
        mode = binding.mode;
        screenOn = binding.screenOn;
        expanded = binding.expanded;
        content = binding.content;
        stackSize = binding.stackSize;
        dragging = binding.dragging;
        reordering = binding.reordering;
        dismissing = binding.dismissing;
        animationManager = binding.animationManager;
        animationsEnabled = binding.animationsEnabled;
    }



    private int count(ViewGroup root) {
        try {
            if (restricted.getBoolean(root)) return 3;
            return Math.min(
                    RuntimeExtras.get(root.getContext()).getInt("stackCards", 3),
                    ((List<?>) items.get(root)).size());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 3;
        }
    }

    private float scale(int rank, int count) {
        float position = 2f * rank / (count - 1);
        return position <= 1 ? 1f - .08f * position : .92f - .12f * (position - 1);
    }

    private float offset(ViewGroup root, int rank, int count) {
        float position = 2f * rank / (count - 1);
        android.content.res.Resources resources = root.getResources();
        float[] dimensions = offsets.get(resources);
        if (dimensions == null) {
            int second =
                    resources.getIdentifier(
                            "now_bar_cardview_2nd_card_bottom_offset", "dimen", binding.resources);
            int third =
                    resources.getIdentifier(
                            "now_bar_cardview_3rd_card_bottom_offset", "dimen", binding.resources);
            dimensions =
                    new float[] {resources.getDimension(second), resources.getDimension(third)};
            offsets.put(resources, dimensions);
        }
        float middle = dimensions[0], back = dimensions[1];
        return position <= 1 ? middle * position : middle + (back - middle) * (position - 1);
    }

    private Tail tail(ViewGroup root) {
        WeakReference<Tail> reference = tails.get(root);
        return reference == null ? null : reference.get();
    }

    private void clear(ViewGroup root) {
        Tail tail = tail(root);
        if (tail != null) {
            tail.stop();
            tail.removeAllViews();
            tail.setVisibility(View.INVISIBLE);
        }
    }

    private void remove(ViewGroup root) {
        Tail tail = tail(root);
        tails.remove(root);
        if (tail != null) {
            liveTails--;
            tail.stop();
            if (tail.getParent() instanceof ViewGroup parent) parent.removeView(tail);
        }
    }

    private void rebuild(ViewGroup root) {
        try {
            // Track native stacks even at the default value, so Apply can enable layers
            // immediately through the existing Extras observer.
            if (!tails.containsKey(root)) tails.put(root, new WeakReference<>(null));
            int count = count(root);
            if (count <= 3
                    || !root.isAttachedToWindow()
                    || Boolean.TRUE.equals(expanded.invoke(root))) return;
            clear(root);
            if (!(root.getParent() instanceof FrameLayout parent)) return;
            Tail tail = tail(root);
            if (tail == null || tail.getParent() != parent) {
                remove(root);
                tail = new Tail(root);
                tails.put(root, new WeakReference<>(tail));
                liveTails++;
                parent.addView(
                        tail, 0, new FrameLayout.LayoutParams(root.getWidth(), root.getHeight()));
            }
            List<?> values = (List<?>) items.get(root);
            for (int rank = Math.min(count, values.size() - 1); rank >= 3; rank--) {
                Object value = values.get(rank);
                View card = inflate(root, value);
                if (card == null) continue;
                Reflect.call(card, "setGetStackSize", stackSize.newInstance(root, 0));
                Reflect.call(card, "setViewMode", mode.get(root));
                Reflect.call(card, "setInitialDozing", !screenOn.getBoolean(root));
                binding.bindItem.invoke(card, value, Integer.valueOf(2));
                binding.hideContents.invoke(card, 2, false);
                card.setClickable(false);
                card.setFocusable(false);
                tail.ranks.put(card, new Rank(rank, count));
                card.setAlpha(rank < count ? 1f : 0f);
                tail.addView(card, new FrameLayout.LayoutParams(-1, -1));
                binding.hideContents.invoke(card, 2, false);
            }
            position(root);
            tail.setVisibility(View.VISIBLE);
            sync(root);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            clear(root);
            module.report("Extra Now Bar rear layers unavailable", error);
        }
    }

    private View inflate(ViewGroup root, Object value) throws ReflectiveOperationException {
        int style = ((Number) Reflect.call(value, "getNowBarViewStyle")).intValue();
        String layout =
                switch (style) {
                    case 1 -> "nowbar_media_layout";
                    case 2 -> "nowbar_mode_layout";
                    case 4 -> "nowbar_battery_layout";
                    case 5 -> "nowbar_contact_layout";
                    case 6, 7 -> "nowbar_external_view_layout";
                    case 8 -> "nowbar_brief_view_layout";
                    default -> "nowbar_ongoing_layout";
                };
        Bundle extras = (Bundle) Reflect.call(value, "getExtraData");
        if (extras != null && extras.getBoolean("nowbar_key_app_search_data_card"))
            layout = "nowbar_app_search_card_layout";
        else if (Boolean.TRUE.equals(Reflect.call(value, "isCall"))) layout = "nowbar_call_layout";
        int id = root.getResources().getIdentifier(layout, "layout", binding.resources);
        return id == 0 ? null : LayoutInflater.from(root.getContext()).inflate(id, root, false);
    }

    private void position(ViewGroup root) {
        Tail tail = tail(root);
        if (tail == null) return;
        ViewGroup.LayoutParams params = tail.getLayoutParams();
        if (params.width != root.getWidth() || params.height != root.getHeight()) {
            params.width = root.getWidth();
            params.height = root.getHeight();
            tail.setLayoutParams(params);
        }
        // This sibling is inserted after the parent's measurement pass. A layout alone
        // leaves new native rear cards at 0 x 0 until another unrelated measure occurs.
        if (tail.isLayoutRequested()
                || tail.getMeasuredWidth() != root.getWidth()
                || tail.getMeasuredHeight() != root.getHeight())
            tail.measure(
                    View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.EXACTLY));
        tail.layout(root.getLeft(), root.getTop(), root.getRight(), root.getBottom());
        tail.setTranslationX(root.getTranslationX());
        tail.setTranslationY(root.getTranslationY());
        tail.setTranslationZ(root.getTranslationZ() - 1);
    }

    private void sync(ViewGroup root) {
        Tail tail = tail(root);
        if (tail == null) return;
        try {
            boolean visible =
                    count(root) > 3
                            && root.getVisibility() == View.VISIBLE
                            && !restricted.getBoolean(root)
                            && !Boolean.TRUE.equals(expanded.invoke(root))
                            && !((Enum<?>) mode.get(root)).name().equals("AOD");
            if (visible
                    && tail.getChildCount() == 0
                    && !tail.rebuilding
                    && tail.getVisibility() != View.VISIBLE) {
                tail.rebuilding = true;
                Tail pending = tail;
                root.post(
                        () -> {
                            pending.rebuilding = false;
                            if (root.isAttachedToWindow() && count(root) > 3) rebuild(root);
                        });
            }
            if (!visible) tail.stop();
            tail.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            tail.setAlpha(root.getAlpha());
        } catch (ReflectiveOperationException | RuntimeException error) {
            tail.setVisibility(View.INVISIBLE);
        }
    }

    private boolean busy(ViewGroup root) throws IllegalAccessException {
        return dragging.getBoolean(root)
                || reordering.getBoolean(root)
                || dismissing.getBoolean(root);
    }

    private record Rank(int rank, int count) {}

    private record Release(ViewGroup root, View front, int count, int target, float startY) {}

    private final class Tail extends FrameLayout {
        private final WeakReference<ViewGroup> root;
        final IdentityHashMap<View, Rank> ranks = new IdentityHashMap<>();
        boolean rebuilding;
        Release outgoing;
        ViewTreeObserver observer;
        float progress;
        final ViewTreeObserver.OnPreDrawListener advance = this::advanceFrame;

        private boolean advanceFrame() {
            if (outgoing == null) return true;
            try {
                if (!reordering.getBoolean(outgoing.root) || !outgoing.root.isAttachedToWindow()) {
                    ViewGroup owner = outgoing.root;
                    stop();
                    if (owner.isAttachedToWindow())
                        owner.post(
                                () -> {
                                    try {
                                        Reflect.call(
                                                owner,
                                                "q",
                                                new CopyOnWriteArrayList<>(
                                                        (List<?>) items.get(owner)));
                                    } catch (ReflectiveOperationException
                                            | RuntimeException error) {
                                        module.report(
                                                "Now Bar interrupted handoff refresh unavailable",
                                                error);
                                    }
                                });
                    return true;
                }
                float destination = offset(outgoing.root, outgoing.target, outgoing.count);
                float distance = destination - outgoing.startY;
                progress =
                        Math.abs(distance) < .01f
                                ? 1f
                                : Math.max(
                                        0,
                                        Math.min(
                                                1,
                                                (outgoing.front.getTranslationY() - outgoing.startY)
                                                        / distance));
                for (int i = 0; i < getChildCount(); i++) {
                    View card = getChildAt(i);
                    Rank rank = ranks.get(card);
                    if (rank == null) continue;
                    float startScale = scale(rank.rank, rank.count),
                            endScale = scale(rank.rank - 1, rank.count);
                    float scale = startScale + (endScale - startScale) * progress;
                    card.setScaleX(scale);
                    card.setScaleY(scale);
                    float startY = offset(outgoing.root, rank.rank, rank.count),
                            endY = offset(outgoing.root, rank.rank - 1, rank.count);
                    card.setTranslationY(startY + (endY - startY) * progress);
                    card.setAlpha(rank.rank >= rank.count ? progress : 1f);
                }
                invalidate();
            } catch (ReflectiveOperationException | RuntimeException error) {
                stop();
                module.report("Now Bar rear transition unavailable", error);
            }
            return true;
        }

        void start(Release moving) {
            stop();
            outgoing = moving;
            progress = 0;
            observer = getViewTreeObserver();
            observer.addOnPreDrawListener(advance);
            invalidate();
            moving.root.invalidate();
        }

        void stop() {
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(advance);
            observer = null;
            outgoing = null;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            Release moving = outgoing;
            if (moving != null && moving.front.getParent() == moving.root) {
                // Draw the outgoing native card below every rear layer while its own spring
                // keeps updating it. Reparenting it would break Samsung's child-count logic.
                View card = moving.front;
                int saved = canvas.save();
                canvas.translate(card.getLeft(), card.getTop());
                canvas.concat(card.getMatrix());
                if (moving.target >= moving.count)
                    canvas.saveLayerAlpha(
                            0,
                            0,
                            card.getWidth(),
                            card.getHeight(),
                            Math.round((1 - progress) * 255));
                card.draw(canvas);
                canvas.restoreToCount(saved);
            }
            super.dispatchDraw(canvas);
        }

        @Override
        protected void onDetachedFromWindow() {
            stop();
            super.onDetachedFromWindow();
        }

        Tail(ViewGroup root) {
            super(root.getContext());
            this.root = new WeakReference<>(root);
            setClipChildren(false);
            setClipToPadding(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }

        @Override
        public void removeAllViews() {
            super.removeAllViews();
            ranks.clear();
        }

        @Override
        public boolean dispatchTouchEvent(android.view.MotionEvent event) {
            return false;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            ViewGroup owner = root.get();
            if (owner == null) return;
            for (int i = 0; i < getChildCount(); i++) {
                View card = getChildAt(i);
                Rank rank = ranks.get(card);
                if (rank == null) continue;
                try {
                    View pill = (View) content.invoke(card);
                    // Media nests the normal container inside its background; find the
                    // direct ancestor before deriving its center in card coordinates.
                    while (pill != null
                            && pill.getParent() instanceof View parent
                            && parent != card) pill = parent;
                    if (pill == null || pill.getHeight() <= 0) continue;
                    card.setPivotX(pill.getLeft() + pill.getWidth() * .5f);
                    card.setPivotY(pill.getTop() + pill.getHeight() * .5f);
                    if (outgoing != null) continue;
                    float scale = scale(rank.rank, rank.count);
                    card.setScaleX(scale);
                    card.setScaleY(scale);
                    card.setTranslationY(offset(owner, rank.rank, rank.count));
                } catch (ReflectiveOperationException | RuntimeException error) {
                    module.report("Now Bar rear geometry unavailable", error);
                }
            }
        }
    }

    boolean nativeDrawSkip(ViewGroup root,View child) {Tail tail=liveTails==0?null:tail(root);return tail!=null&&tail.outgoing!=null&&tail.outgoing.front==child;}
    float nativeDensity(ViewGroup root,int rank,float original,boolean scaling) {
        int count=count(root);Release moving=release.get();
        if(count<=3 || rank<0 || (rank>2 && (moving==null||moving.root!=root||moving.target!=rank)))return original;
        return scaling?scale(rank,count):offset(root,rank,count);
    }
    void nativeBeforeRefresh(ViewGroup root) throws ReflectiveOperationException {
        if(!busy(root)) clear(root);
    }
    void nativeAfterRefresh(ViewGroup root) throws ReflectiveOperationException {
        if(!busy(root)) rebuild(root);
    }
    void nativeLayout(ViewGroup root) {
        if(!tails.containsKey(root)){tails.put(root,new WeakReference<>(null));root.post(()->rebuild(root));}
        position(root);
    }
    void nativeState(ViewGroup root) {sync(root);}
    void nativeDetach(ViewGroup root) {remove(root);}
    void nativeConfiguration(ViewGroup root){offsets.remove(root.getResources());position(root);}
    boolean nativeWave(View view){return !(view.getParent() instanceof Tail);}
    int nativeDepth(ViewGroup root,View card,int original){Release m=release.get();return m!=null&&m.root==root&&m.front==card&&card.getTranslationZ()==0?m.target:original;}
    void nativeTouchBefore(ViewGroup root,MotionEvent event)throws ReflectiveOperationException{
        if(event==null||count(root)<=3||Boolean.TRUE.equals(expanded.invoke(root)))return;
        Tail t=tail(root);if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&t!=null)t.stop();
        if(event.getActionMasked()!=MotionEvent.ACTION_UP)return;
        View front=(View)Reflect.call(root,"getTopCard");if(front==null||t==null||t.getChildCount()==0)return;
        int count=count(root),total=((List<?>)items.get(root)).size();
        release.set(new Release(root,front,count,Math.min(count,total-1),front.getTranslationY()));
    }
    void nativeTouchAfter(ViewGroup root)throws ReflectiveOperationException{
        Release m=release.get();release.remove();Tail t=tail(root);if(m!=null&&t!=null&&reordering.getBoolean(root))t.start(m);
    }
}
