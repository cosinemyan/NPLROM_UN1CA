package dev.denver.dressroomadvanced;

import android.net.Uri;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class WallpaperSourceBinding {
    record Source(Object data, String key, boolean image) {}

    private final Field[] path;
    final Method changed;
    final Class<?> dataType;

    WallpaperSourceBinding(ClassLoader loader, boolean legacy) throws ReflectiveOperationException {
        dataType =
                Class.forName(
                        "com.samsung.android.app.dressroom.data.profile.wallpaper.EditorData",
                        false,
                        loader);
        String[] types =
                legacy
                        ? new String[] {"P3.f", "X2.B", "X2.W"}
                        : new String[] {"p8.a", "o6.t", "o6.e0"};
        String[] names =
                legacy ? new String[] {"n", "v", "w", "Y"} : new String[] {"j", "p", "t", "T"};
        Class<?> activityType =
                Class.forName(
                        "com.samsung.android.app.dressroom.presentation.preview.activity.EditBaseActivity",
                        false,
                        loader);
        path = resolvePath(loader, activityType, dataType, types, names);
        changed =
                NativeContract.findMethod(
                        path[2].getType(), legacy ? "b3" : "c1", void.class, dataType);
        NativeContract.method(dataType, "getOriginalUri", Uri.class);
        NativeContract.method(dataType, "getUri", Uri.class);
        NativeContract.method(dataType, "getStorageFilePath", String.class);
        NativeContract.method(dataType, "getWallpaperSubType", int.class);
        NativeContract.method(dataType, "getWallpaperParentSubType", int.class);
    }

    private static Field[] resolvePath(
            ClassLoader loader,
            Class<?> activityType,
            Class<?> dataType,
            String[] types,
            String[] names)
            throws ReflectiveOperationException {
        try {
            Field[] direct = new Field[4];
            Class<?> owner = activityType;
            for (int i = 0; i < direct.length; i++) {
                Class<?> next = i == 3 ? dataType : Class.forName(types[i], false, loader);
                direct[i] = NativeContract.findField(owner, names[i], next);
                owner = direct[i].getType();
            }
            return direct;
        } catch (ReflectiveOperationException fallback) {
            for (Field f0 : activityType.getDeclaredFields()) {
                if (f0.getType().getName().startsWith("android.")) continue;
                for (Field f1 : f0.getType().getDeclaredFields()) {
                    if (f1.getType().getName().startsWith("android.")) continue;
                    for (Field f2 : f1.getType().getDeclaredFields()) {
                        if (!NativeContract.hasField(f2.getType(), dataType)
                                || !NativeContract.hasMethod(f2.getType(), void.class, dataType))
                            continue;
                        Field f3 = NativeContract.findField(f2.getType(), names[3], dataType);
                        f0.setAccessible(true);
                        f1.setAccessible(true);
                        f2.setAccessible(true);
                        return new Field[] {f0, f1, f2, f3};
                    }
                }
            }
            throw fallback;
        }
    }

    Source read(Object activity) throws ReflectiveOperationException {
        Object data = activity;
        for (Field field : path) {
            if (data == null) return null;
            data = field.get(data);
        }
        if (data == null) return null;
        int type = ((Number) Reflect.call(data, "getWallpaperSubType")).intValue();
        int parent = ((Number) Reflect.call(data, "getWallpaperParentSubType")).intValue();
        Object uri = Reflect.call(data, "getOriginalUri");
        if (uri == null) uri = Reflect.call(data, "getUri");
        if (uri == null) uri = Reflect.call(data, "getStorageFilePath");
        String key = uri == null ? "" : uri.toString();
        if (DepthSourcePolicy.image(type, parent) && key.isEmpty()) return null;
        return new Source(data, key, DepthSourcePolicy.image(type, parent) && !key.isEmpty());
    }
}
