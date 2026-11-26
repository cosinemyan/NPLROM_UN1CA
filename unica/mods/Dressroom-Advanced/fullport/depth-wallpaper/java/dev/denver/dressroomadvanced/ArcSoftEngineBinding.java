package dev.denver.dressroomadvanced;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

final class ArcSoftEngineBinding {
    private final Method wrapper;
    private final Field handle;
    final AtomicBoolean ready;
    private final ClassValue<Field> engines;

    ArcSoftEngineBinding(Class<?> sdk) throws ReflectiveOperationException {
        ClassLoader loader = sdk.getClassLoader();
        Class<?> wrapperType =
                Class.forName(
                        "com.samsung.android.app.sdk.deepsky.objectcapture.impl.ArcObjectCaptureWrapper",
                        false,
                        loader);
        Class<?> jni =
                Class.forName("com.arcsoft.libobjectcapture.ArcObjectCaptureJNI", false, loader);
        wrapper = NativeContract.findMethod(sdk, null, wrapperType);
        handle = NativeContract.findField(jni, null, long.class);
        Field flag = null;
        for (Field field : sdk.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == AtomicBoolean.class) {
                if (flag != null)
                    throw new NoSuchFieldException("Ambiguous Object Capture state flags");
                flag = field;
            }
        }
        if (flag == null) throw new NoSuchFieldException("Object Capture state flag missing");
        flag.setAccessible(true);
        ready = (AtomicBoolean) flag.get(null);
        engines =
                new ClassValue<>() {
                    @Override
                    protected Field computeValue(Class<?> type) {
                        try {
                            return NativeContract.findField(type, null, jni);
                        } catch (ReflectiveOperationException error) {
                            throw new IllegalStateException(error);
                        }
                    }
                };
    }

    long handle(Object sdk) {
        try {
            Object owner = wrapper.invoke(sdk);
            Object engine = engines.get(owner.getClass()).get(owner);
            return handle.getLong(engine);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not read the ArcSoft engine handle", error);
        }
    }
}
