package dev.denver.dressroomadvanced;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/** Keeps FrameLayout/DataBinding semantics while exposing an explicit editor draw boundary. */
public final class DepthFrameLayout extends FrameLayout {
    public DepthFrameLayout(Context context) { super(context); }
    public DepthFrameLayout(Context context, AttributeSet attrs) { super(context, attrs); }
    public DepthFrameLayout(Context context, AttributeSet attrs, int style) { super(context, attrs, style); }
    @Override protected void dispatchDraw(Canvas canvas) {
        int saved = NativeDepth.beforeDraw(this, canvas);
        try { super.dispatchDraw(canvas); }
        finally { NativeDepth.afterDraw(this, canvas, saved); }
    }
}
