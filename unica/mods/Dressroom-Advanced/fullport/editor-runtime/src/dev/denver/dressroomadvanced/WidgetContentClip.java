package dev.denver.dressroomadvanced;

import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.util.Map;
import java.util.WeakHashMap;

final class WidgetContentClip extends ViewOutlineProvider {
    private static final Map<View, WidgetContentClip> clips = new WeakHashMap<>();
    private ViewOutlineProvider original;
    private boolean originalClip;

    private WidgetContentClip(View view) {
        capture(view);
    }

    static boolean supported() {
        return android.os.Build.VERSION.SDK_INT >= 33;
    }

    private void capture(View view) {
        original = view.getOutlineProvider();
        originalClip = view.getClipToOutline();
    }

    static boolean apply(View view, boolean enabled) {
        enabled =
                ((enabled && !DepthEffects.wallpaperHidden(view))
                                || WidgetMediaOcclusion.active(view))
                        && supported();
        WidgetContentClip clip = clips.get(view);
        if (!enabled) {
            clips.remove(view);
            if (clip != null && view.getOutlineProvider() == clip) {
                view.setOutlineProvider(clip.original);
                view.setClipToOutline(clip.originalClip);
                view.invalidateOutline();
                view.invalidate();
            }
            return clip != null;
        }
        if (clip == null) {
            clip = new WidgetContentClip(view);
            clips.put(view, clip);
        } else if (view.getOutlineProvider() != clip) clip.capture(view);
        if (view.getOutlineProvider() != clip) {
            view.setOutlineProvider(clip);
            view.setClipToOutline(true);
            refresh(view);
            return true;
        }
        if (!view.getClipToOutline()) {
            view.setClipToOutline(true);
            refresh(view);
            return true;
        }
        return false;
    }

    static void refresh(View view) {
        view.invalidateOutline();
        view.invalidate();
    }

    @Override
    public void getOutline(View view, Outline outline) {
        Path subject = DepthEffects.localSubjectPath(view);
        Path cover = WidgetMediaOcclusion.localPath(view);
        if (subject == null && cover == null) {
            if (original != null) original.getOutline(view, outline);
            else outline.setEmpty();
            return;
        }
        Path visible = new Path();
        Rect nativeRect = new Rect();
        Outline nativeOutline = new Outline();
        if (originalClip && original != null) original.getOutline(view, nativeOutline);
        if (originalClip && nativeOutline.getRect(nativeRect)) {
            float radius = Math.max(0, nativeOutline.getRadius());
            visible.addRoundRect(new RectF(nativeRect), radius, radius, Path.Direction.CW);
        } else visible.addRect(0, 0, view.getWidth(), view.getHeight(), Path.Direction.CW);
        if (subject != null) visible = DepthGeometry.subtract(visible, subject);
        if (cover != null) visible = DepthGeometry.subtract(visible, cover);
        DepthGeometry.outline(outline, visible);
    }
}
