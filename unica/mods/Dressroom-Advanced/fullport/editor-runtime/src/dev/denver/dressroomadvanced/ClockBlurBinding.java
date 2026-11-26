package dev.denver.dressroomadvanced;

import android.graphics.Path;
import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class ClockBlurBinding {
    final Method render, clip, clear, host, origin;
    private final Field owner, clock;

    static ClockBlurBinding resolve(Class<?> base) throws ReflectiveOperationException {
        ClockBinding binding = ClockBinding.of(base);
        if (binding.clockStyle) throw new NoSuchMethodException("Clock Style blur is not mapped");
        ClassLoader loader = base.getClassLoader();
        if (!binding.legacy) return OneUi9AodBlur.resolve(loader);
        return OneUi85AodBlur.resolve(loader);
    }

    ClockBlurBinding(
            ClassLoader loader,
            String strategyName,
            String optionsName,
            String helperName,
            String clipName,
            String clockField)
            throws ReflectiveOperationException {
        this(
                Class.forName(strategyName, false, loader),
                Class.forName(optionsName, false, loader),
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.WeatherInfo", false, loader),
                Class.forName(helperName, false, loader),
                clipName,
                clockField);
    }

    ClockBlurBinding(
            Class<?> strategy,
            Class<?> options,
            Class<?> weather,
            Class<?> helper,
            String clipName,
            String clockField)
            throws ReflectiveOperationException {
        render =
                NativeContract.findMethod(
                        strategy, "b", null, android.widget.ImageView.class, weather, options);
        clear = NativeContract.findMethod(strategy, "c", void.class);
        host = NativeContract.findMethod(strategy, "d", ViewGroup.class);
        origin = NativeContract.findMethod(strategy, "e", PointF.class, ViewGroup.class);
        clip =
                NativeContract.findStaticMethod(
                        helper,
                        clipName,
                        void.class,
                        Class.forName("android.view.SemBlurInfo$Builder"),
                        Path.class);
        owner = strategy.getDeclaredField("b");
        owner.setAccessible(true);
        clock = owner.getType().getDeclaredField(clockField);
        clock.setAccessible(true);
        if (clock.getType() != Object.class && !View.class.isAssignableFrom(clock.getType()))
            throw new NoSuchFieldException("Native blur owner is not a clock view");
    }

    View clock(Object strategy) throws IllegalAccessException {
        Object value = clock.get(owner.get(strategy));
        return value instanceof View view ? view : null;
    }
}
