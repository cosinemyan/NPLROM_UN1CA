package dev.denver.dressroomadvanced;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

final class Reflect {
    private Reflect() {}

    private static final class Members {
        final ConcurrentHashMap<String, Field> fields = new ConcurrentHashMap<>();
        final ConcurrentHashMap<String, Method> noArguments = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Signature, Method> methods = new ConcurrentHashMap<>();
    }

    private record Signature(String name, List<Class<?>> arguments) {}

    private static final ClassValue<Members> members =
            new ClassValue<>() {
                @Override
                protected Members computeValue(Class<?> type) {
                    return new Members();
                }
            };
    private static final Object[] NO_ARGUMENTS = new Object[0];

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Members cache = members.get(type);
        Field cached = cache.fields.get(name);
        if (cached != null) return cached;
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                cache.fields.putIfAbsent(name, field);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        findField(target.getClass(), name).set(target, value);
    }

    static Object field(Object target, String name) throws ReflectiveOperationException {
        return findField(target.getClass(), name).get(target);
    }

    static Object call(Object target, String name) throws ReflectiveOperationException {
        Members cache = members.get(target.getClass());
        Method match = cache.noArguments.get(name);
        if (match == null) {
            match = findMethod(target.getClass(), name, NO_ARGUMENTS);
            match.setAccessible(true);
            cache.noArguments.putIfAbsent(name, match);
        }
        return invoke(target, name, match, NO_ARGUMENTS);
    }

    static Object call(Object target, String name, Object... args)
            throws ReflectiveOperationException {
        if (args.length == 0) return call(target, name);
        List<Class<?>> types = new ArrayList<>(args.length);
        for (Object arg : args) types.add(arg == null ? void.class : arg.getClass());
        Signature key = new Signature(name, List.copyOf(types));
        Members cache = members.get(target.getClass());
        Method match = cache.methods.get(key);
        if (match == null) {
            match = findMethod(target.getClass(), name, args);
            match.setAccessible(true);
            cache.methods.putIfAbsent(key, match);
        }
        return invoke(target, name, match, args);
    }

    private static Object invoke(Object target, String name, Method method, Object[] args)
            throws ReflectiveOperationException {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw new ReflectiveOperationException("Target failed: " + name, e.getCause());
        }
    }

    private static Method findMethod(Class<?> type, String name, Object[] args)
            throws NoSuchMethodException {
        Method match = null;
        for (Class<?> c = type; c != null && match == null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name)
                        || m.getParameterCount() != args.length
                        || m.isBridge()) continue;
                boolean fits = true;
                Class<?>[] types = m.getParameterTypes();
                for (int i = 0; i < args.length; i++) {
                    if (args[i] == null
                            ? types[i].isPrimitive()
                            : !box(types[i]).isInstance(args[i])) {
                        fits = false;
                        break;
                    }
                }
                if (fits) {
                    if (match != null) throw new NoSuchMethodException("Ambiguous method: " + name);
                    match = m;
                }
            }
        }
        if (match == null) throw new NoSuchMethodException(type.getName() + "." + name);
        return match;
    }

    private static Class<?> box(Class<?> c) {
        if (c == boolean.class) return Boolean.class;
        if (c == int.class) return Integer.class;
        if (c == float.class) return Float.class;
        if (c == long.class) return Long.class;
        if (c == double.class) return Double.class;
        if (c == byte.class) return Byte.class;
        if (c == short.class) return Short.class;
        if (c == char.class) return Character.class;
        return c;
    }
}
