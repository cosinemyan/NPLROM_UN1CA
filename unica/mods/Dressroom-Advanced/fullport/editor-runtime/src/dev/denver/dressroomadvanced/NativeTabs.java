package dev.denver.dressroomadvanced;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class NativeTabs {
    final Class<?> listener;
    private final Method create, text, add, listen, style, positionMethod;
    private final Field positionField;
    private static final ClassValue<NativeTabs> bindings =
            new ClassValue<>() {
                @Override
                protected NativeTabs computeValue(Class<?> type) {
                    try {
                        return new NativeTabs(type);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Unsupported native tabs", error);
                    }
                }
            };

    static NativeTabs of(Object strip) {
        return bindings.get(strip.getClass());
    }

    static NativeTabs forType(Class<?> strip) {
        return bindings.get(strip);
    }

    private NativeTabs(Class<?> strip) throws ReflectiveOperationException {
        ClassLoader loader = strip.getClassLoader();
        boolean legacy;
        try {
            strip.getDeclaredMethod("g0");
            legacy = true;
        } catch (NoSuchMethodException ignored) {
            legacy = false;
        }
        if (legacy) {
            Class<?> tab =
                    Class.forName("com.google.android.material.tabs.TabLayout$g", false, loader);
            listener = Class.forName("com.google.android.material.tabs.TabLayout$d", false, loader);
            create = NativeContract.method(strip, "g0", tab);
            text = NativeContract.method(tab, "C", tab, CharSequence.class);
            add = NativeContract.method(strip, "D", void.class, tab, boolean.class);
            listen = NativeContract.method(strip, "A", void.class, listener);
            style = NativeContract.method(strip, "r0", void.class);
            positionMethod = NativeContract.method(tab, "o", int.class);
            positionField = null;
        } else {
            create = strip.getDeclaredMethod("p");
            create.setAccessible(true);
            Class<?> tab = create.getReturnType();
            listener = Class.forName("com.google.android.material.tabs.c", false, loader);
            text = NativeContract.method(tab, "b", void.class, CharSequence.class);
            add = NativeContract.method(strip, "f", void.class, tab, boolean.class);
            listen = NativeContract.method(strip, "d", void.class, listener);
            style = NativeContract.method(strip, "t", void.class);
            positionField = NativeContract.field(tab, "d", int.class);
            positionMethod = null;
        }
    }

    void subTabs(Object strip) throws ReflectiveOperationException {
        style.invoke(strip);
    }

    void listen(Object strip, Object callback) throws ReflectiveOperationException {
        listen.invoke(strip, callback);
    }

    void add(Object strip, String name, boolean selected) throws ReflectiveOperationException {
        Object tab = create.invoke(strip);
        text.invoke(tab, name);
        add.invoke(strip, tab, selected);
    }

    int position(Object tab) throws ReflectiveOperationException {
        return positionField != null
                ? positionField.getInt(tab)
                : ((Number) positionMethod.invoke(tab)).intValue();
    }
}
