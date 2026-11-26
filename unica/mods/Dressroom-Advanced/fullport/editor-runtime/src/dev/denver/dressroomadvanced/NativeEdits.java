package dev.denver.dressroomadvanced;

import java.lang.reflect.Method;

final class NativeEdits {
    private NativeEdits() {}

    private static Object model(Object controller) throws Exception {
        return EditorBinding.of(controller).model(controller);
    }

    static Object toggle(Object controller, boolean value, boolean weather) throws Exception {
        Object model = model(controller);
        if (EditorBinding.of(controller).legacy) {
            if (weather) {
                Reflect.call(Reflect.field(model, "c"), "a", value);
                return null;
            }
            return launch(controller, model, "Pa.L$b", value, false);
        }
        return launch(controller, model, "vo.g", value, weather);
    }

    private static Object launch(
            Object controller, Object model, String owner, boolean value, boolean weather)
            throws Exception {
        ClassLoader loader = controller.getClass().getClassLoader();
        Class<?> continuation = Class.forName("kotlin.coroutines.Continuation", false, loader);
        boolean modern = !EditorBinding.of(controller).legacy;
        Class<?> transactionType = NativeContract.findClass(loader,
                cls -> modern
                        ? NativeContract.hasConstructor(cls, model.getClass(), boolean.class, continuation, int.class)
                        : NativeContract.hasConstructor(cls, model.getClass(), boolean.class, continuation), owner);
        java.lang.reflect.Constructor<?> constructor = modern
                ? transactionType.getDeclaredConstructor(model.getClass(), boolean.class, continuation, int.class)
                : transactionType.getDeclaredConstructor(model.getClass(), boolean.class, continuation);
        constructor.setAccessible(true);
        Object transaction =
                modern
                        ? constructor.newInstance(model, value, null, weather ? 0 : 1)
                        : constructor.newInstance(model, value, null);
        Class<?> scope = Class.forName("kotlinx.coroutines.CoroutineScope", false, loader);
        Class<?> context = Class.forName("kotlin.coroutines.CoroutineContext", false, loader);
        Class<?> start = Class.forName("kotlinx.coroutines.CoroutineStart", false, loader);
        Class<?> function = Class.forName("kotlin.jvm.functions.Function2", false, loader);
        Method launch =
                Class.forName("kotlinx.coroutines.BuildersKt", false, loader)
                        .getMethod(
                                "launch$default",
                                scope,
                                context,
                                start,
                                function,
                                int.class,
                                Object.class);
        return launch.invoke(null, Reflect.field(model, "a"), null, null, transaction, 3, null);
    }

    static void changing(Object controller, boolean value) throws Exception {
        Object model = model(controller);
        if (EditorBinding.of(controller).legacy) {
            Reflect.call(Reflect.field(model, "k"), "b", value, null, null, null, null);
            return;
        }
        Method change = model.getClass().getDeclaredMethod("c", model.getClass(), boolean.class);
        change.setAccessible(true);
        change.invoke(null, model, value);
    }

    static void remeasure(Object controller, boolean weather) throws Exception {
        Object operation = Reflect.field(model(controller), "c");
        if (EditorBinding.of(controller).legacy) Reflect.call(operation, "a", weather);
        else Reflect.call(operation, "a", weather, true);
    }

    static void cancel(Object job) {
        if (job == null) return;
        try {
            Reflect.call(job, "cancel", (Object) null);
        } catch (Exception ignored) {
        }
    }
}
