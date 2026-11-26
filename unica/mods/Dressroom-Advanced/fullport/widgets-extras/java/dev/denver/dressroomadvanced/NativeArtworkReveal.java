// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.widget.ImageView;

import org.json.*;

import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;

final class NativeArtworkReveal {
    private final WeakReference<View> mask, light;
    private final WeakReference<ImageView> incoming, previous;
    private WeakReference<Animator> animator = new WeakReference<>(null);
    private Bitmap old;
    private RuntimeShader shader;
    private RenderNode node;
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF crop = new RectF(), localBounds = new RectF();
    private float progress = 1,
            random = .19f,
            zoomX = 1,
            zoomY = 1,
            oldZoomX = 1,
            oldZoomY = 1,
            maskAlpha = 1;
    private int revision, drawnRevision = -1, effectWidth, effectHeight;
    private float effectProgress = Float.NaN, effectRandom = Float.NaN;
    private boolean unavailable;

    NativeArtworkReveal(View mask, ImageView incoming, ImageView previous, View light) {
        this.mask = new WeakReference<>(mask);
        this.incoming = new WeakReference<>(incoming);
        this.previous = new WeakReference<>(previous);
        this.light = new WeakReference<>(light);
    }

    static RuntimeShader load(Context context) throws Exception {
        int id =
                context.getResources()
                        .getIdentifier(
                                "media_album_masking", "raw", "com.samsung.android.app.aodservice");
        if (id == 0) throw new IllegalStateException("Native artwork reveal asset is unavailable");
        JSONObject layer;
        try (var input = context.getResources().openRawResource(id)) {
            layer =
                    new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                            .getJSONArray("shaders")
                            .getJSONObject(0);
        }
        RuntimeShader shader = new RuntimeShader(layer.getString("agslShaderCode"));
        JSONArray uniforms = layer.optJSONArray("uniforms");
        if (uniforms != null)
            for (int i = 0; i < uniforms.length(); i++) {
                JSONObject uniform = uniforms.getJSONObject(i);
                Object value = uniform.opt("value");
                if (value instanceof Number number)
                    shader.setFloatUniform(uniform.getString("name"), number.floatValue());
                else if (value instanceof JSONArray array) {
                    float[] values = new float[array.length()];
                    for (int n = 0; n < values.length; n++) values[n] = (float) array.getDouble(n);
                    shader.setFloatUniform(uniform.getString("name"), values);
                }
            }
        return shader;
    }

    void begin(Bitmap before, Bitmap after) {
        if (before == null) {
            ImageView view = previous.get();
            if (view != null && view.getDrawable() instanceof BitmapDrawable bd)
                before = bd.getBitmap();
        }
        old = valid(before) ? before : null;
        progress = 0;
        revision++;
        View view = mask.get();
        if (shader == null && !unavailable && view != null)
            try {
                shader = load(view.getContext());
                node = new RenderNode("Dressroom native song reveal");
            } catch (Exception | LinkageError error) {
                unavailable = true;
                android.util.Log.w(
                        DressroomModule.TAG,
                        "Native artwork shader unavailable; retaining timed blend",
                        error);
            }
    }

    void progress(float value) {
        progress = clamp(value);
        if (progress >= 1) old = null;
        revision++;
    }

    void random(float value) {
        random = value;
        revision++;
    }

    void animation(Animator value) {
        animator = new WeakReference<>(value);
        if (!value.isStarted()) {
            old = null;
            progress = 1;
            return;
        }
        WeakReference<NativeArtworkReveal> reference = new WeakReference<>(this);
        value.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator ended) {
                        ended.removeListener(this);
                        NativeArtworkReveal state = reference.get();
                        if (state != null && state.animator.get() == ended) {
                            state.old = null;
                            state.progress = 1;
                            state.animator.clear();
                            state.revision++;
                        }
                    }
                });
    }

    boolean update() {
        ImageView image = incoming.get(), prior = previous.get();
        View masking = mask.get();
        float x = image == null ? 1 : image.getScaleX(), y = image == null ? 1 : image.getScaleY();
        float ox = prior == null ? 1 : prior.getScaleX(),
                oy = prior == null ? 1 : prior.getScaleY();
        float alpha = masking == null ? 1 : MediaBlurHost.requestedAlpha(masking);
        boolean changed =
                drawnRevision != revision
                        || zoomX != x
                        || zoomY != y
                        || oldZoomX != ox
                        || oldZoomY != oy
                        || maskAlpha != alpha;
        zoomX = x;
        zoomY = y;
        oldZoomX = ox;
        oldZoomY = oy;
        maskAlpha = alpha;
        drawnRevision = revision;
        return changed || running();
    }

    boolean running() {
        Animator animation = animator.get();
        if (animation != null && animation.isStarted()) return true;
        View view = light.get();
        try {
            return view != null
                    && Boolean.TRUE.equals(Reflect.field(Reflect.field(view, "r"), "f"));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            DressroomModule.note("Could not read artwork animation state", ignored);
            return false;
        }
    }

    void draw(Canvas canvas, Bitmap current, RectF bounds, Paint paint) {
        boolean reveal = valid(old) && valid(current) && current != old && progress < 1;
        if (reveal) bitmap(canvas, old, bounds, oldZoomX, oldZoomY, paint, crop);
        if (reveal && shader != null && canvas.isHardwareAccelerated()) {
            int width = Math.max(1, (int) Math.ceil(bounds.width())),
                    height = Math.max(1, (int) Math.ceil(bounds.height()));
            node.setPosition(0, 0, width, height);
            if (effectWidth != width
                    || effectHeight != height
                    || effectProgress != progress
                    || effectRandom != random) {
                shader.setFloatUniform("iResolution", width, height);
                shader.setFloatUniform("uAnimationProgress", progress);
                shader.setFloatUniform("uRandomOffset", random);
                node.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "composable"));
                effectWidth = width;
                effectHeight = height;
                effectProgress = progress;
                effectRandom = random;
            }
            Canvas record = node.beginRecording();
            localBounds.set(0, 0, width, height);
            bitmap(record, current, localBounds, zoomX, zoomY, paint, crop);
            node.endRecording();
            node.setAlpha(clamp(maskAlpha));
            int save = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / width, bounds.height() / height);
            canvas.drawRenderNode(node);
            canvas.restoreToCount(save);
        } else {
            int alpha = paint.getAlpha();
            if (reveal) paint.setAlpha(Math.round(alpha * progress));
            try {
                bitmap(canvas, current, bounds, zoomX, zoomY, paint, crop);
            } finally {
                paint.setAlpha(alpha);
            }
        }
        drawLighting(canvas, bounds);
    }

    private void drawLighting(Canvas canvas, RectF bounds) {
        View view = light.get();
        if (view == null
                || !canvas.isHardwareAccelerated()
                || view.getWidth() <= 0
                || view.getHeight() <= 0) return;
        try {
            float alpha =
                    ((Number) Reflect.field(view, "s")).floatValue()
                            * ((Number) Reflect.field(view, "t")).floatValue();
            if (view.getParent() instanceof View parent)
                alpha *= MediaBlurHost.requestedAlpha(parent);
            if (alpha <= .001f) return;
            RuntimeShader nativeShader = (RuntimeShader) Reflect.call(view, "getRuntimeShader");
            nativeShader.setFloatUniform("iResolution", view.getWidth(), view.getHeight());
            nativeShader.setFloatUniform(
                    "iTime", ((Number) Reflect.call(view, "getITime")).floatValue());
            lightPaint.setShader(nativeShader);
            lightPaint.setAlpha(Math.round(clamp(alpha) * 255));
            int save = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(bounds.width() / view.getWidth(), bounds.height() / view.getHeight());
            canvas.drawRect(0, 0, view.getWidth(), view.getHeight(), lightPaint);
            canvas.restoreToCount(save);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            DressroomModule.note("Artwork reveal light paint", ignored);
        }
    }

    void discard() {
        if (node != null) node.discardDisplayList();
    }

    static void bitmap(
            Canvas canvas,
            Bitmap image,
            RectF bounds,
            float zoomX,
            float zoomY,
            Paint paint,
            RectF crop) {
        if (!valid(image)) return;
        float scale =
                Math.max(bounds.width() / image.getWidth(), bounds.height() / image.getHeight());
        float width = image.getWidth() * scale * zoomX, height = image.getHeight() * scale * zoomY;
        crop.set(
                bounds.centerX() - width / 2,
                bounds.centerY() - height / 2,
                bounds.centerX() + width / 2,
                bounds.centerY() + height / 2);
        canvas.drawBitmap(image, null, crop, paint);
    }

    private static boolean valid(Bitmap bitmap) {
        return bitmap != null && !bitmap.isRecycled();
    }

    private static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }
}
