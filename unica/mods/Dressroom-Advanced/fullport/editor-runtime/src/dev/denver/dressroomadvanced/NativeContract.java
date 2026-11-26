package dev.denver.dressroomadvanced;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

final class NativeContract {
    private static final Map<ClassLoader, DexIndex> dexClassCache =
            Collections.synchronizedMap(new WeakHashMap<>());

    static final class AmbiguousClassException extends ClassNotFoundException {
        AmbiguousClassException(String message) {
            super(message);
        }
    }

    private record DexIndex(long stamp, List<String> names) {}

    private NativeContract() {}

    static Field field(Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != type || Modifier.isStatic(field.getModifiers()))
            throw new NoSuchFieldException(
                    owner.getName() + "." + name + ": expected " + type.getName());
        field.setAccessible(true);
        return field;
    }

    static Field assignableField(Class<?> owner, String name, Class<?> type)
            throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        if (!type.isAssignableFrom(field.getType()) || Modifier.isStatic(field.getModifiers()))
            throw new NoSuchFieldException(
                    owner.getName() + "." + name + ": expected " + type.getName());
        field.setAccessible(true);
        return field;
    }

    static Method method(Class<?> owner, String name, Class<?> result, Class<?>... parameters)
            throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameters);
        if (method.getReturnType() != result
                || method.isBridge()
                || Modifier.isStatic(method.getModifiers()))
            throw new NoSuchMethodException(
                    owner.getName() + "." + name + ": expected instance " + result.getName());
        method.setAccessible(true);
        return method;
    }

    static Field findField(Class<?> owner, String preferredName, Class<?> type)
            throws NoSuchFieldException {
        if (preferredName != null) {
            try {
                return assignableField(owner, preferredName, type);
            } catch (NoSuchFieldException ignored) {
            }
        }
        Field found = null;
        for (Field f : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType())) {
                if (found != null)
                    throw new NoSuchFieldException("Ambiguous fields: " + found + " and " + f);
                found = f;
            }
        }
        if (found != null) {
            found.setAccessible(true);
            return found;
        }
        throw new NoSuchFieldException(
                owner.getName() + " has no field matching " + type.getName());
    }

    static Method findMethod(
            Class<?> owner, String preferredName, Class<?> result, Class<?>... parameters)
            throws NoSuchMethodException {
        return selectMethod(owner, preferredName, result, false, false, parameters);
    }

    static Method findStaticMethod(
            Class<?> owner, String preferredName, Class<?> result, Class<?>... parameters)
            throws NoSuchMethodException {
        return selectMethod(owner, preferredName, result, true, false, parameters);
    }

    static Method findMethodPrefix(
            Class<?> owner, String preferredName, Class<?> result, Class<?>... prefixParameters)
            throws NoSuchMethodException {
        return selectMethod(owner, preferredName, result, false, true, prefixParameters);
    }

    private static Method selectMethod(
            Class<?> owner,
            String preferredName,
            Class<?> result,
            boolean isStatic,
            boolean prefix,
            Class<?>[] parameters)
            throws NoSuchMethodException {
        // Known mappings are deliberate tie-breakers, but even a named wildcard must be unique.
        for (int pass = preferredName == null ? 1 : 0; pass < 2; pass++) {
            Method found = null;
            for (Method method : owner.getDeclaredMethods()) {
                if(method.getName().startsWith("nativeAdvancedOriginal"))continue;
                if (Modifier.isStatic(method.getModifiers()) != isStatic
                        || method.isBridge()
                        || (pass == 0 && !method.getName().equals(preferredName))
                        || (result != null && !result.isAssignableFrom(method.getReturnType()))
                        || !(prefix
                                ? matchPrefix(method.getParameterTypes(), parameters)
                                : matchParameters(method.getParameterTypes(), parameters)))
                    continue;
                if (found != null)
                    throw new NoSuchMethodException(
                            "Ambiguous methods: " + found + " and " + method);
                found = method;
            }
            if (found != null) {
                found.setAccessible(true);
                return found;
            }
        }
        throw new NoSuchMethodException(
                owner.getName()
                        + " has no matching "
                        + (isStatic ? "static " : "instance ")
                        + "method for "
                        + preferredName);
    }

    static boolean hasField(Class<?> owner, Class<?> type) {
        try {
            for (Field f : owner.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType()))
                    return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean hasMethod(Class<?> owner, Class<?> result, Class<?>... parameters) {
        try {
            for (Method m : owner.getDeclaredMethods()) {
                if(m.getName().startsWith("nativeAdvancedOriginal"))continue;
                if (!Modifier.isStatic(m.getModifiers())
                        && !m.isBridge()
                        && (result == null || result.isAssignableFrom(m.getReturnType()))
                        && matchParameters(m.getParameterTypes(), parameters)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean hasStaticMethod(Class<?> owner, Class<?> result, Class<?>... parameters) {
        try {
            for (Method m : owner.getDeclaredMethods()) {
                if(m.getName().startsWith("nativeAdvancedOriginal"))continue;
                if (Modifier.isStatic(m.getModifiers())
                        && !m.isBridge()
                        && (result == null || result.isAssignableFrom(m.getReturnType()))
                        && matchParameters(m.getParameterTypes(), parameters)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean hasMethodPrefix(Class<?> owner, Class<?> result, Class<?>... prefixParameters) {
        try {
            for (Method m : owner.getDeclaredMethods()) {
                if(m.getName().startsWith("nativeAdvancedOriginal"))continue;
                if (!Modifier.isStatic(m.getModifiers())
                        && !m.isBridge()
                        && (result == null || result.isAssignableFrom(m.getReturnType()))
                        && matchPrefix(m.getParameterTypes(), prefixParameters)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean hasConstructor(Class<?> owner, Class<?>... parameters) {
        try {
            for (Constructor<?> c : owner.getDeclaredConstructors()) {
                if (matchParameters(c.getParameterTypes(), parameters)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static Class<?> findClassInPackage(
            ClassLoader loader, String prefix, Predicate<Class<?>> signature)
            throws ClassNotFoundException {
        Class<?> found = null;
        for (String name : dexClasses(loader)) {
            if (!name.startsWith(prefix)) continue;
            Class<?> candidate;
            try {
                candidate = Class.forName(name, false, loader);
                if (!signature.test(candidate)) continue;
            } catch (LinkageError | RuntimeException | ClassNotFoundException ignored) {
                continue;
            }
            if (found != null && found != candidate)
                throw new AmbiguousClassException("Ambiguous contract in " + prefix);
            found = candidate;
        }
        if (found == null) throw new ClassNotFoundException("No matching contract in " + prefix);
        return found;
    }

    static Class<?> findClass(
            ClassLoader loader, Predicate<Class<?>> signature, String... candidates)
            throws ClassNotFoundException {
        Class<?> known = knownClass(loader, signature, candidates);
        if (known != null) return known;
        if (signature != null) {
            Class<?> found = null;
            for (String name : dexClasses(loader)) {
                Class<?> cls;
                try {
                    cls = Class.forName(name, false, loader);
                    if (!signature.test(cls)) continue;
                } catch (LinkageError | RuntimeException | ClassNotFoundException ignored) {
                    continue;
                }
                if (found != null && found != cls)
                    throw new AmbiguousClassException(
                            "Ambiguous classes: " + found.getName() + " and " + cls.getName());
                found = cls;
            }
            if (found != null) return found;
        }
        throw new ClassNotFoundException(
                "No class matched signature for candidates: " + String.join(", ", candidates));
    }

    static Class<?> knownClass(
            ClassLoader loader, Predicate<Class<?>> signature, String... candidates) {
        for (String name : candidates) {
            if (name == null || name.isEmpty()) continue;
            try {
                Class<?> cls = Class.forName(name, false, loader);
                if (signature == null || signature.test(cls)) return cls;
            } catch (ClassNotFoundException | RuntimeException | LinkageError ignored) {
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private static List<String> dexClasses(ClassLoader loader) {
        try {
            Object pathList = Reflect.field(loader, "pathList");
            Object elements = Reflect.field(pathList, "dexElements");
            int length = java.lang.reflect.Array.getLength(elements);
            long stamp = System.identityHashCode(elements);
            for (int i = 0; i < length; i++) {
                Object element = java.lang.reflect.Array.get(elements, i);
                stamp = stamp * 31 + System.identityHashCode(Reflect.field(element, "dexFile"));
            }
            DexIndex cached = dexClassCache.get(loader);
            if (cached != null && cached.stamp == stamp) return cached.names;
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            for (int i = 0; i < length; i++) {
                Object element = java.lang.reflect.Array.get(elements, i);
                Object dexFile = Reflect.field(element, "dexFile");
                if (dexFile instanceof dalvik.system.DexFile dex) {
                    Enumeration<String> entries = dex.entries();
                    while (entries.hasMoreElements()) {
                        String entry = entries.nextElement();
                        if (isCandidateClassName(entry)) names.add(entry);
                    }
                }
            }
            List<String> result = List.copyOf(names);
            // Empty/failed scans must not poison discovery before a plugin finishes loading.
            if (!result.isEmpty()) dexClassCache.put(loader, new DexIndex(stamp, result));
            return result;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    private static boolean isCandidateClassName(String name) {
        if (name.startsWith("aod.") || name.startsWith("com.samsung.android.lockscreen.editor."))
            return true;
        int dot = name.indexOf('.');
        return dot > 0 && dot <= 3 && name.lastIndexOf('.') == dot;
    }

    private static boolean matchParameters(Class<?>[] actual, Class<?>[] expected) {
        if (actual.length != expected.length) return false;
        for (int i = 0; i < actual.length; i++) {
            if (expected[i] != null && expected[i] != actual[i]) return false;
        }
        return true;
    }

    private static boolean matchPrefix(Class<?>[] actual, Class<?>[] prefix) {
        if (actual.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (prefix[i] != null && prefix[i] != actual[i]) return false;
        }
        return true;
    }
}
