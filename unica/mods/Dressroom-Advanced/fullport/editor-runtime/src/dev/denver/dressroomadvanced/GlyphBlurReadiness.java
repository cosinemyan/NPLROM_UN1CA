package dev.denver.dressroomadvanced;

import android.view.View;

import java.util.List;

final class GlyphBlurReadiness {
    static boolean stale(View view, Class<?> glyphType) throws ReflectiveOperationException {
        return glyphType.isInstance(view)
                && Boolean.TRUE.equals(Reflect.field(view, "S"))
                && (view.isLayoutRequested()
                        || view.isDirty()
                        || !Boolean.TRUE.equals(Reflect.field(view, "R")));
    }

    static boolean needsDraw(List<?> views, Class<?> glyphType)
            throws ReflectiveOperationException {
        for (Object item : views)
            if (item instanceof View view && view.isShown() && stale(view, glyphType)) return true;
        return false;
    }
}
