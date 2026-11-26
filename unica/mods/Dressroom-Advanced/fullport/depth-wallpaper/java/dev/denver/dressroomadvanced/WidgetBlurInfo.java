// Native port of Dressroom-Advanced by @BlueOutside. See upstream LICENSE.
package dev.denver.dressroomadvanced;

import android.graphics.Path;
import android.graphics.RectF;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

final class WidgetBlurInfo {
    private final Constructor<?> constructor;
    private final List<Field> fields = new ArrayList<>();
    private final Field clip;

    WidgetBlurInfo(Class<?> type) throws ReflectiveOperationException {
        constructor =
                type.getConstructor(
                        int.class,
                        android.graphics.Bitmap.class,
                        int.class,
                        int.class,
                        float.class,
                        float.class,
                        float.class,
                        float.class,
                        int.class);
        for (Field field : type.getDeclaredFields())
            if (!Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                fields.add(field);
            }
        clip = type.getDeclaredField("mClipPath");
        clip.setAccessible(true);
    }

    Path path(Object info) throws ReflectiveOperationException {
        return (Path) clip.get(info);
    }

    Path visible(Object info, float width, float height, Path subject)
            throws ReflectiveOperationException {
        Path original = path(info);
        Path result = original == null ? new Path() : new Path(original);
        if (original == null) {
            float tl = corner(info, "mCornerRadiusTL"), tr = corner(info, "mCornerRadiusTR");
            float br = corner(info, "mCornerRadiusBR"), bl = corner(info, "mCornerRadiusBL");
            result.addRoundRect(
                    new RectF(0, 0, width, height),
                    new float[] {tl, tl, tr, tr, br, br, bl, bl},
                    Path.Direction.CW);
        }
        return subject == null ? result : DepthGeometry.subtract(result, subject);
    }

    private float corner(Object info, String name) throws ReflectiveOperationException {
        for (Field field : fields) if (field.getName().equals(name)) return field.getFloat(info);
        throw new NoSuchFieldException(name);
    }

    Object clipped(Object original, Path path) throws ReflectiveOperationException {
        Object copy = constructor.newInstance(0, null, 0, 0, 0f, 0f, 0f, 0f, 0);
        for (Field field : fields) field.set(copy, field.get(original));
        clip.set(copy, new Path(path));
        return copy;
    }

    Object submission(Object original, Path visible) throws ReflectiveOperationException {
        return visible.isEmpty() ? null : clipped(original, visible);
    }
}
