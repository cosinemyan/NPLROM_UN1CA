package dev.denver.dressroomadvanced;

import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;



import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class NativeClockRuntime {
    private static final NativeClockRuntime INSTANCE = new NativeClockRuntime(DressroomModule.get());
    private final DressroomModule module;
    private boolean unlockObserved;
    private final List<WeakReference<View>> clocks = new ArrayList<>();
    private final Set<View> effects = Collections.newSetFromMap(new WeakHashMap<>());
    private final java.util.Map<View, Bundle> profiles =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final ThreadLocal<Boolean> manual = ThreadLocal.withInitial(() -> false);
    private final Set<View> restoringFont = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<View> mediaReturnStarted = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<View> mediaRestorePending = Collections.newSetFromMap(new WeakHashMap<>());
    private final java.util.Map<View, Integer> settingsRetries = new WeakHashMap<>();
    private final java.util.Map<View, Integer> fontRetries = new WeakHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final java.util.concurrent.atomic.AtomicBoolean observing =
            new java.util.concurrent.atomic.AtomicBoolean();

    NativeClockRuntime(DressroomModule module) {
        this.module = module;
        ScreenIdentity.observe(() -> main.post(() -> {
            MediaDepthTransition.screenChanged();
            MediaFullscreenExtras.screenChanged();
            for (View clock : new java.util.ArrayList<>(mediaRestorePending)) finishMediaReturn(clock);
            mediaReturnStarted.clear();
            RuntimeClockData.invalidate();
            module.widgetEngine.invalidate();
            clocks.removeIf(ref -> ref.get() == null);
            for (WeakReference<View> ref : clocks) {
                View clock = ref.get();
                if (clock != null && clock.isAttachedToWindow()) {
                    apply(clock);
                    applyBlur(clock);
                    DateEffects.apply(clock);
                }
            }
        }));
    }


    public static Bundle profile(View clock) {
        Bundle editor=DressroomModule.get().fontSettings(clock);
        if(editor!=null) return editor;
        try { Object font=ClockBinding.of(clock).font(clock); return font==null?null:INSTANCE.read(clock,font); }
        catch(Exception ignored) { return null; }
    }
    public static void attached(View clock) {
        if (DepthEffects.isEditorClock(clock)) { DressroomModule.get().nativeRendererBound(clock); return; }
        try { INSTANCE.track(clock); DateEffects.attach(INSTANCE.module,clock); INSTANCE.apply(clock); INSTANCE.applyBlur(clock); }
        catch(Exception failure) { DressroomModule.note("Native clock attachment",failure); }
    }
    public static void changed(View clock) {
        if(DepthEffects.isEditorClock(clock)) { DressroomModule.get().nativeRendererBound(clock); return; }
        INSTANCE.apply(clock); INSTANCE.applyBlur(clock); DateEffects.nativeChanged(clock);
    }
    private static final ClassValue<java.lang.reflect.Method> stretchPredicates=new ClassValue<>() {
        @Override protected java.lang.reflect.Method computeValue(Class<?> font) {
            try {return Class.forName("aod.rl2",false,font.getClassLoader()).getDeclaredMethod("w",int.class);}
            catch(ReflectiveOperationException failure){throw new IllegalStateException("Samsung stretch predicate changed",failure);}
        }
    };
    static boolean nativeStretchFont(Object font,int id) {
        try{return Boolean.TRUE.equals(stretchPredicates.get(font.getClass()).invoke(null,id));}catch(Exception ignored){return false;}
    }
    /** Samsung capability, rather than a nonempty/stale saved height list, owns eligibility. */
    static boolean nativeStretchFont(Object font) {
        if (font == null) return false;
        try { return nativeStretchFont(font, ((Number) Reflect.call(font, "getFontType")).intValue()); }
        catch (Exception | LinkageError unavailable) { return false; }
    }

    private static List<Float> savedHeights(Bundle settings, Object font) {
        if (!nativeStretchFont(font)) return null;
        try {
            int id = ((Number) Reflect.call(font, "getFontType")).intValue();
            if (settings == null) return null;
            Object path = Reflect.call(font, "getFontPath");
            String selectedPath = path instanceof String ? (String) path : "";
            if (!StretchRestorePolicy.eligible(true, id, settings.getInt("font", -1),
                    selectedPath, settings.getString("fontPath", ""),
                    !FontAxisSettings.nativeStretch(settings))) return null;
            return RuntimeClockData.manualHeights(settings);
        } catch (Exception | LinkageError unavailable) { return null; }
    }

    public static Object lockFont(android.content.Context context,Object original) {
        if(context==null||original==null)return original;
        try {
            ClassLoader loader=original.getClass().getClassLoader();
            Object settings=Class.forName("com.samsung.android.app.aodservice.common.provider.settingdata.LockClockSettingData",false,loader).getConstructor(android.content.Context.class).newInstance(context);
            int id=((Number)Reflect.call(Reflect.field(settings,"q"),"c")).intValue();
            if(id<0)return original;
            String path=(String)Reflect.call(Reflect.field(settings,"r"),"c");if(path==null)path="";
            Integer weight=(Integer)Reflect.call(Reflect.field(settings,"s"),"c");
            Integer width=(Integer)Reflect.call(Reflect.field(settings,"t"),"c");
            java.util.List<?> heights=null;
            int stretch=((Number)Reflect.call(Reflect.field(settings,"v"),"c")).intValue();
            if(stretch!=0&&nativeStretchFont(original,id)) {
                String stored=(String)Reflect.call(Reflect.field(settings,"u"),"c");
                if(stored!=null) heights=(java.util.List<?>)Class.forName("aod.qn1",false,loader).getDeclaredMethod("Y",String.class).invoke(null,stored);
            }
            return original.getClass().getConstructor(int.class,String.class,Integer.class,Integer.class,java.util.List.class).newInstance(id,path,weight,width,heights);
        }catch(Exception ignored){return original;}
    }
    public static Object font(View clock,Object requested) {
        try {
            if("AOD".equals(String.valueOf(Reflect.call(clock,"getCategory")))) requested=lockFont(clock.getContext(),requested);
            Bundle settings=DepthEffects.isEditorClock(clock)?DressroomModule.get().fontSettings(clock):INSTANCE.read(clock,requested);
            if(settings==null) INSTANCE.profiles.remove(clock); else INSTANCE.profiles.put(clock,settings);
            VariableFonts.configure(INSTANCE.module,clock,settings); VariableFonts.forget(clock);
            return SavedStretch.font(requested,savedHeights(settings,requested));
        } catch(Exception failure) { return requested; }
    }
    public static List<Float> normalized(View clock,Object requested,List<Float> normalized) {
        try {
            if(DepthEffects.isEditorClock(clock)&&DressroomModule.get().nativeManual(clock)) {
                if(nativeStretchFont(requested) && FontAxisSettings.nativeStretch(DressroomModule.get().fontSettings(clock))) return (List<Float>)Reflect.call(requested,"getFontHeights");
            }
            List<Float> saved=savedHeights(INSTANCE.read(clock,requested),requested);return saved==null?normalized:saved;
        }
        catch(Exception ignored) { return normalized; }
    }
    public static int stretchable(View clock,List<?> values) {
        Bundle saved=profile(clock);
        try { if(savedHeights(saved,ClockBinding.of(clock).font(clock))==null) return -1; }
        catch(ReflectiveOperationException | RuntimeException unavailable) { return -1; }
        if(values==null || values.isEmpty()) return 0;
        for(Object value:values) if(!(value instanceof Number n)||!Float.isFinite(n.floatValue())||n.floatValue()<=0) return 0;
        return 1;
    }
    public static android.graphics.Typeface fixed(View clock,android.graphics.Typeface original,android.widget.TextView[] views) { return VariableFonts.nativeFixed(clock,original,views); }
    public static void fixedDone(android.widget.TextView[] views,android.graphics.Typeface typeface) { VariableFonts.nativeApplied(views,typeface); }
    public static boolean variable(View clock,android.widget.TextView[] views) { return VariableFonts.nativeVariable(clock,views); }
    public static Object render(View clock,Object requested) { return VariableFonts.nativeRender(clock,requested); }
    public static android.graphics.Typeface typeface(View clock,android.graphics.Typeface original) { return VariableFonts.nativeTypeface(clock,original); }
    public static void date(View clock) { DateEffects.nativeChanged(clock); }
    public static void effect(View view,android.graphics.RenderEffect original) { view.setRenderEffect(ShadowEffects.nativeEffect(view,original)); }
    public static void drawn(View view) { ClockBlurSync.drawn(view); }
    public static void mediaBefore(View clock,boolean expanded) {
        try {
            boolean was=ClockTransitionBinding.of(clock).mediaExpanded.getBoolean(clock);
            if(was&&!expanded) { INSTANCE.mediaRestorePending.add(clock); AodStretchExtras.mediaReturn(clock,true); BlurEffects.discardOutline(clock); }
            if(expanded) { ClockBlurSync.cancel(clock); INSTANCE.finishMediaReturn(clock); }
        }catch(Exception ignored){}
    }
    public static void mediaAfter(View clock,boolean expanded) {
        MediaDepthTransition.update(clock,expanded);
        if(!expanded&&INSTANCE.mediaRestorePending.contains(clock)) clock.postOnAnimation(()->INSTANCE.restoreAfterMedia(clock));
    }
    public static Object transitionBegin(View clock) { return AodStretchExtras.begin(clock); }
    public static void transitionEnd(View clock,Object token) { AodStretchExtras.end(clock,token); }
    public static Boolean sdkStretch(android.content.Context context,Object font,Boolean original) {
        try {int id=((Number)Reflect.call(font,"getFontType")).intValue();java.util.List<?> heights=(java.util.List<?>)Reflect.call(font,"getFontHeights");return nativeStretchFont(font,id)&&heights!=null&&!heights.isEmpty();}catch(Exception ignored){return original;}
    }
    public static boolean blurForced(View clock) {return BlurEffects.force(clock);}
    public static boolean blurStable(Object strategy) {return BlurEffects.stable(strategy);}
    public static Object blurTone(Object manager) {return BlurEffects.tone(manager);}
    public static boolean blurDefer(Object manager,int color,int retry,boolean force) {return BlurEffects.defer(manager,color,retry,force);}
    public static void blurBegin(Object strategy) { BlurEffects.nativeBegin(strategy); }
    public static void blurEnd() { BlurEffects.nativeEnd(); }
    public static int radius(int nativeRadius) { return BlurEffects.nativeRadius(nativeRadius); }
    public static float visibility(Object manager,float nativeVisibility) { return BlurEffects.nativeVisibility(manager,nativeVisibility); }

    private void applyBlur(View view) {
        if (!ClockBinding.of(view).legacy) BlurEffects.apply(view);
    }

    private Bundle read(View view, Object font) {
        try {
            String category = String.valueOf(Reflect.call(view, "getCategory"));
            if (!category.equals("LOCK_SCREEN") && !category.equals("AOD")) return null;
            if (!ScreenIdentity.supported() && view.getDisplay() != null && view.getDisplay().getDisplayId() != 0) return null;
            if (SettingsBridge.wallpaperId(view.getContext()) < 0)
                throw new IllegalStateException("Wallpaper identity is not ready");
            Bundle query = new Bundle();
            query.putInt("type", ((Number) Reflect.call(view, "getClockType")).intValue());
            query.putInt("font", ((Number) Reflect.call(font, "getFontType")).intValue());
            try {
                Object path = Reflect.call(font, "getFontPath");
                if (path instanceof String && !((String) path).isEmpty())
                    query.putString("fontPath", (String) path);
            } catch (Exception ignored) {
            }
            return SettingsBridge.read(view.getContext(), query);
        } catch (Exception e) {
            throw new IllegalStateException("Runtime settings temporarily unavailable", e);
        }
    }

    private Bundle read(View view, int font) {
        try {
            String category = String.valueOf(Reflect.call(view, "getCategory"));
            if (!category.equals("LOCK_SCREEN") && !category.equals("AOD")) return null;
            if (!ScreenIdentity.supported() && view.getDisplay() != null && view.getDisplay().getDisplayId() != 0) return null;
            if (SettingsBridge.wallpaperId(view.getContext()) < 0)
                throw new IllegalStateException("Wallpaper identity is not ready");
            Bundle query = new Bundle();
            query.putInt("type", ((Number) Reflect.call(view, "getClockType")).intValue());
            query.putInt("font", font);
            return SettingsBridge.read(view.getContext(), query);
        } catch (Exception e) {
            throw new IllegalStateException("Runtime settings temporarily unavailable", e);
        }
    }

    private void restoreAfterMedia(View clock) {
        try {
            if (!mediaRestorePending.contains(clock)) return;
            if (!clock.isAttachedToWindow()) return;
            Object controller = ClockTransitionBinding.of(clock).controller.get(clock);
            if (Boolean.TRUE.equals(Reflect.field(controller, "h"))) {
                finishMediaReturn(clock);
                return;
            }
            boolean dozing = Boolean.TRUE.equals(Reflect.field(controller, "d"));
            if (dozing && !RuntimeExtras.enabled(clock.getContext(), "keepStretch")) return;
            Bundle profile = profiles.get(clock);
            if (profile == null
                    || !FontAxisSettings.nativeStretch(profile)
                    || (!profile.getBoolean("manual") && !profile.getBoolean("depthEnabled"))) {
                finishMediaReturn(clock);
                return;
            }
            Object original = ClockBinding.of(clock).font(clock);
            List<Float> heights = savedHeights(profile, original);
            if (original == null || heights == null) {
                finishMediaReturn(clock);
                return;
            }
            Object restored = SavedStretch.font(original, heights);
            if (restored != original) Reflect.call(clock, "setFontType", restored);
            if (!dozing) {
                android.animation.Animator animation =
                        (android.animation.Animator) Reflect.field(controller, "i");
                if (animation != null && animation.isStarted()) mediaReturnStarted.add(clock);
                if (mediaReturnStarted.add(clock)) {
                    ClockTransitionBinding.of(clock).transition.invoke(clock, 0f, false);
                    animation = (android.animation.Animator) Reflect.field(controller, "i");
                }
                WeakReference<View> reference = new WeakReference<>(clock);
                if (MediaReturnCompletion.await(
                        clock,
                        animation,
                        () -> {
                            View view = reference.get();
                            if (view != null) view.postOnAnimation(() -> restoreAfterMedia(view));
                        })) return;
            }
            ClockBinding.of(clock).render(clock, restored);
            Reflect.call(clock, "refresh");
            clock.requestLayout();
            finishMediaReturn(clock);
        } catch (Exception error) {
            finishMediaReturn(clock);
            module.report("Manual media-exit restore unavailable", error);
        }
    }

    private void finishMediaReturn(View clock) {
        mediaRestorePending.remove(clock);
        mediaReturnStarted.remove(clock);
        MediaReturnCompletion.cancel(clock);
        AodStretchExtras.mediaReturn(clock, false);
    }

    private void apply(View view) {
        boolean providerReady = false;
        try {
            Object clockFont = ClockBinding.of(view).font(view);
            Bundle settings =
                    clockFont != null
                            ? read(view, clockFont)
                            : read(view, ClockBinding.of(view).fontId(view));
            providerReady = true;
            VariableFonts.configure(module, view, settings);
            if (settings != null) settingsRetries.remove(view);
            if (settings == null && ClockBinding.of(view).fontId(view) == 0) {
                Bundle previous = profiles.get(view);
                if (previous != null
                        && previous.getInt("wallpaperId", -1)
                                == SettingsBridge.wallpaperId(view.getContext())
                        && previous.getInt(LayoutOrientation.FIELD)
                                == LayoutOrientation.current(view.getContext()))
                    settings = previous;
            }
            if (settings == null) {
                profiles.remove(view);
                if (effects.remove(view)) ShadowEffects.apply(view, false, 0, 0, 0, 0);
                DepthEffects.apply(view, false, null);
                DepthEffects.applyDigits(view, false, null);
                int attempt = settingsRetries.getOrDefault(view, 0);
                if (attempt < 4 && view.isAttachedToWindow()) {
                    settingsRetries.put(view, attempt + 1);
                    WeakReference<View> reference = new WeakReference<>(view);
                    main.postDelayed(
                            () -> {
                                View clock = reference.get();
                                if (clock != null && clock.isAttachedToWindow()) apply(clock);
                            },
                            250L << attempt);
                }
                return;
            }
            profiles.put(view, settings);
            if (FontAxisSettings.nativeStretch(settings)
                    && (settings.getBoolean("manual") || settings.getBoolean("depthEnabled"))
                    && settings.getFloatArray("manualHeights") != null
                    && restoringFont.add(view)) {
                try {
                    Object current = ClockBinding.of(view).font(view);
                    if (current != null) {
                        java.util.List<Float> heights =
                                savedHeights(settings, current);
                        Object restored = SavedStretch.font(current, heights);
                        if (restored != current) Reflect.call(view, "setFontType", restored);
                        fontRetries.remove(view);
                    } else if (fontRetries.getOrDefault(view, 0) < 4 && view.isAttachedToWindow()) {
                        int attempt = fontRetries.getOrDefault(view, 0);
                        fontRetries.put(view, attempt + 1);
                        WeakReference<View> reference = new WeakReference<>(view);
                        main.postDelayed(
                                () -> {
                                    View clock = reference.get();
                                    if (clock != null && clock.isAttachedToWindow()) apply(clock);
                                },
                                250L << attempt);
                    }
                } catch (Exception error) {
                    module.report("Saved runtime stretch restore unavailable", error);
                } finally {
                    restoringFont.remove(view);
                }
            }
            float scale = ((Number) Reflect.call(view, "getScale")).floatValue();
            if (!Float.isFinite(scale) || scale <= 0) scale = 1f;
            ShadowEffects.apply(
                    view,
                    settings.getBoolean("enabled"),
                    settings.getFloat("radius") / scale,
                    settings.getFloat("dx") / scale,
                    settings.getFloat("dy") / scale,
                    settings.getInt("color"));
            if (DepthBinding.provider(view)) {
                DepthEffects.apply(
                        view,
                        settings.getBoolean("depthEnabled"),
                        settings.getByteArray("depthMask"));
                DepthEffects.applyDigits(
                        view,
                        settings.getBoolean("depthEnabled"),
                        settings.getByteArray("depthMask"));
            }
            effects.add(view);
            ClockBinding.of(view).refreshAppearance(view);
            if (VariableFonts.needsRefresh(view, settings)) {
                Object font = ClockBinding.of(view).font(view);
                if (font != null) {
                    ClockBinding.of(view).render(view, font);
                    view.requestLayout();
                }
            }
            view.invalidate();
        } catch (Exception e) {
            module.report("Runtime settings apply deferred", e);
            int attempt = settingsRetries.getOrDefault(view, 0);
            if (!providerReady && attempt < 4 && view.isAttachedToWindow()) {
                settingsRetries.put(view, attempt + 1);
                WeakReference<View> reference = new WeakReference<>(view);
                main.postDelayed(
                        () -> {
                            View clock = reference.get();
                            if (clock != null && clock.isAttachedToWindow()) apply(clock);
                        },
                        250L << attempt);
            }
        }
    }

    private void track(View view) {
        if(!unlockObserved) {
            android.content.Context app=view.getContext().getApplicationContext();if(app==null)app=view.getContext();
            app.registerReceiver(new android.content.BroadcastReceiver(){
                @Override public void onReceive(android.content.Context context,android.content.Intent intent) {
                    if(!android.content.Intent.ACTION_USER_UNLOCKED.equals(intent.getAction()))return;
                    main.post(()->{
                        RuntimeClockData.invalidate();NativeWidgets.profileChanged();RuntimeExtras.userUnlocked();
                        clocks.removeIf(reference->reference.get()==null);
                        for(WeakReference<View> reference:clocks){View clock=reference.get();if(clock!=null&&clock.isAttachedToWindow()){apply(clock);applyBlur(clock);DateEffects.apply(clock);}}
                    });
                }
            },new android.content.IntentFilter(android.content.Intent.ACTION_USER_UNLOCKED),android.content.Context.RECEIVER_NOT_EXPORTED);
            unlockObserved=true;
        }

        if (!ClockBinding.of(view).legacy) MediaClockContrast.track(view);
        clocks.removeIf(reference -> reference.get() == null);
        for (WeakReference<View> ref : clocks) if (ref.get() == view) return;
        clocks.add(new WeakReference<>(view));
        view.addOnLayoutChangeListener(
                (clock, l, t, r, b, oldL, oldT, oldR, oldB) -> {
                    Bundle profile = profiles.get(clock);
                    if (profile == null
                            || profile.getInt(LayoutOrientation.FIELD)
                                    != LayoutOrientation.current(clock.getContext())
                            || ScreenIdentity.value(profile) != ScreenIdentity.current()) {
                        apply(clock);
                        applyBlur(clock);
                        DateEffects.apply(clock);
                        profile = profiles.get(clock);
                    }
                    if (profile != null && DepthBinding.provider(clock)) {
                        boolean depthOn = profile.getBoolean("depthEnabled");
                        byte[] mask = profile.getByteArray("depthMask");
                        DepthEffects.apply(clock, depthOn, mask);
                        DepthEffects.applyDigits(clock, depthOn, mask);
                    }
                });
        if (!observing.compareAndSet(false, true)) return;
        view.getContext()
                .getContentResolver()
                .registerContentObserver(
                        SettingsProvider.URI,
                        false,
                        new ContentObserver(new Handler(Looper.getMainLooper())) {
                            @Override
                            public void onChange(boolean selfChange) {
                                RuntimeClockData.invalidate();
                                clocks.removeIf(ref -> ref.get() == null);
                                for (WeakReference<View> ref : clocks) {
                                    View clock = ref.get();
                                    if (clock != null && clock.isAttachedToWindow()) {
                                        apply(clock);
                                        applyBlur(clock);
                                        DateEffects.apply(clock);
                                    }
                                }
                            }
                        });
    }
}
