// Native adaptation of BlueOutside / Dressroom Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class BlurEffects {
    private static final ThreadLocal<Integer> radiusForDraw = new ThreadLocal<>();
    private static final Map<View, Bundle> previews =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<View> forced = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ClassLoader> installed =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ClassLoader> glyphSyncInstalled =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean radiusHooked;

    private BlurEffects() {}
    private static final ThreadLocal<java.util.ArrayDeque<Integer>> draws = ThreadLocal.withInitial(java.util.ArrayDeque::new);
    static void nativeBegin(Object strategy) {
        Integer previous = radiusForDraw.get(); draws.get().push(previous == null ? -1 : previous);
        try { View clock=clockFromOwner(strategy,"b"); Bundle profile=read(clock);
            if(profile != null) radiusForDraw.set(clamp(profile.getInt("blurRadius",200),1,300));
        } catch(Exception ignored) {}
    }
    static void nativeEnd() {
        java.util.ArrayDeque<Integer> stack=draws.get(); Integer previous=stack.isEmpty()?-1:stack.pop();
        if(previous<0) radiusForDraw.remove(); else radiusForDraw.set(previous);
    }
    static int nativeRadius(int original) { Integer value=radiusForDraw.get(); return value==null?original:value; }
    static boolean force(View clock) { Bundle saved=read(clock);return saved!=null&&saved.getBoolean("forceBlur"); }
    static boolean stable(Object strategy) {
        try {View clock=clockFromOwner(strategy,"b");return clock!=null&&!DepthEffects.mediaActive(clock)&&!DepthEffects.wallpaperHidden(clock)&&(DepthEffects.hasSubject(clock)||force(clock));}
        catch(Exception ignored){return false;}
    }
    static Object tone(Object manager) {
        try { View clock=clockFromOwner(manager,"a");
            return clock!=null&&force(clock)&&!DepthEffects.mediaActive(clock)&&"Custom".equals(String.valueOf(ClockGlassBinding.of(clock).mode.get(clock)))?WallpaperBlurTone.read(manager.getClass(),clock):null;
        }catch(Exception ignored){return null;}
    }
    static float nativeVisibility(Object manager,float original) {
        try {
            View clock=clockFromOwner(manager,"a");Bundle saved=read(clock);if(saved==null)return original;
            boolean custom="Custom".equals(String.valueOf(ClockGlassBinding.of(clock).mode.get(clock)));
            double brightness=Double.NaN;
            if(custom&&saved.getBoolean("forceBlur"))brightness=((Number)Reflect.field(Reflect.call(manager,"h"),"a")).doubleValue();
            return BlurOpacity.resolve(saved.getFloat("blurVisibility",-1),saved.getBoolean("forceBlur"),custom,((Number)ClockGlassBinding.of(clock).opacity.get(clock)).intValue(),brightness,original);
        }catch(Exception ignored){return original;}
    }
    static boolean defer(Object manager,int color,int retry,boolean force) {
        try {
            View clock=clockFromOwner(manager,"a");Object strategy=Reflect.field(manager,"p");
            if(clock==null||!clock.isShown()||!clock.isAttachedToWindow()||ClockBlurSync.rebuilding(clock)||strategy==null)return false;
            Object items=Reflect.field(strategy,"d");
            Class<?> glyph=Class.forName("com.samsung.android.uniform.widget.NonePaddingTextView",false,manager.getClass().getClassLoader());
            if(!(items instanceof java.util.List<?> views)||(!ClockBlurSync.pending(clock)&&!GlyphBlurReadiness.needsDraw(views,glyph)))return false;
            java.lang.ref.WeakReference<Object> reference=new java.lang.ref.WeakReference<>(manager);
            return ClockBlurSync.defer(clock,target->{Object current=reference.get();if(current==null||!target.isShown())return;try {if(ClockGlassBinding.of(target).manager.get(target)==current)Reflect.call(current,"c",color,retry,force);}catch(Exception failure){DressroomModule.note("Blur glyph resume",failure);}});
        }catch(Exception ignored){return false;}
    }

    static void preview(View clock, boolean enable, int radius, float visibility) {
        Bundle value = new Bundle();
        value.putBoolean("forceBlur", enable);
        value.putInt("blurRadius", radius);
        value.putFloat("blurVisibility", visibility);
        previews.put(clock, value);
        if (!"com.samsung.android.app.dressroom".equals(clock.getContext().getOpPackageName()))
            apply(clock);
    }

    static void clearPreview(View clock) {
        previews.remove(clock);
    }

    static void resetPreviews() {
        previews.clear();
        forced.clear();
    }

    static boolean discardOutline(View clock) throws ReflectiveOperationException {
        return discardStrategy(Reflect.field(ClockGlassBinding.of(clock).manager.get(clock), "p"));
    }

    static boolean discardStrategy(Object strategy) throws ReflectiveOperationException {
        if (strategy == null) return false;
        Reflect.call(strategy, "c");
        return true;
    }

    static void apply(View clock) {
        Bundle settings = read(clock);
        boolean enable = settings != null && settings.getBoolean("forceBlur");
        if (!enable) {
            if (forced.remove(clock))
                clock.post(
                        () -> {
                            try {
                                Reflect.call(clock, "setBlurEffectEnabled", false);
                            } catch (Exception e) {
                                Log.w(DressroomModule.TAG, "Could not clear forced blur", e);
                            }
                        });
            return;
        }
        forced.add(clock);
        clock.post(
                () -> {
                    try {
                        if (Boolean.TRUE.equals(ClockGlassBinding.of(clock).dozing.get(clock))) return;
                        Reflect.call(clock, "setBlurEffectEnabled", true);
                        float scale = ((Number) Reflect.call(clock, "getScale")).floatValue();
                        Reflect.call(
                                clock, "addBlur", Float.isFinite(scale) && scale > 0f ? scale : 1f);
                        Reflect.call(clock, "setClockTextBlur");
                    } catch (Exception e) {
                        Log.w(DressroomModule.TAG, "Forced blur unavailable for clock", e);
                    }
                });
    }

    private static View clockFromOwner(Object owner, String field) throws Exception {
        Object holder = Reflect.field(owner, field);
        Object clock = Reflect.field(holder, "o");
        return clock instanceof View ? (View) clock : null;
    }

    private static Bundle read(View clock) {
        if (clock == null) return null;
        Bundle preview = previews.get(clock);
        if (preview != null) return preview;
        try {
            Bundle query = new Bundle();
            query.putInt("type", ((Number) Reflect.call(clock, "getClockType")).intValue());
            query.putInt("font", ((Number) ClockBinding.of(clock).fontId(clock)).intValue());
            return SettingsBridge.read(clock.getContext(), query);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
