package dev.denver.dressroomadvanced;

import android.app.Dialog;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class NativeClockPanel {
    private static final String MARKER = "dressroom.advanced.native.v2";

    interface Edit {
        void apply(Object draft) throws Exception;
    }

    interface SliderEdit {
        void apply(float value) throws Exception;
    }

    interface ToggleEdit {
        void apply(boolean value) throws Exception;
    }

    interface ColorEdit {
        void apply(Object draft, int color) throws Exception;
    }

    private final LinearLayout root;
    private final Context context;
    private final ClassLoader loader;
    private final Object controller, repo;
    private final EditorBinding binding;
    private final DressroomModule module;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Edit> pending = new LinkedHashMap<>();
    private final Runnable applyPending = this::flush;
    private View stock;
    private ScrollView scroll;
    private LinearLayout content;
    private LinearLayout currentGroup;
    private LinearLayout advanced;
    private PanelTabs pages;
    private TextView status;
    private Object tabs, renderer;
    private Object initialSnapshot;
    private int expectedType, expectedFont, expectedOrientation, expectedScreen;
    private boolean ready;
    private int movedX, movedY;
    private boolean customShadow;
    private boolean initialShadowEnabled, initialManual;
    private Boolean shadowPreference;
    private boolean hideDate;
    private boolean initialHideDate;
    private Boolean weatherPreference;
    private boolean initialWeather;
    private boolean forceBlur;
    private boolean initialForceBlur;
    private int blurRadius = 200;
    private int initialBlurRadius = 200;
    private float blurVisibility = -1f;
    private float initialBlurVisibility = -1f;
    private boolean depthEnabled, initialDepthEnabled;
    private boolean depthOwnsManual, initialDepthOwnsManual;
    private byte[] depthMask, initialDepthMask;
    private android.os.Bundle depthSource = new android.os.Bundle();
    private CompoundButton dateOrderControl;
    private int[] manualProgress;
    private android.os.Bundle fontValues = new android.os.Bundle();
    private android.os.Bundle initialFontValues = new android.os.Bundle();
    private VariableFonts.Axes fontAxes;
    private String fontFile = "", fontFileName = "";
    private String initialFontFile = "", initialFontFileName = "";
    private boolean nativeFontStretchable;
    private boolean followDisplayFont, initialFollowDisplayFont;
    private String expectedScene = "";
    private String expectedFontPath = "";
    private Object weatherJob;
    private int editGeneration;
    private Runnable finishEdit;
    private AdvancedPanelDismiss outsideDismiss;

    private void watchOutside() throws ReflectiveOperationException {
        if (outsideDismiss==null) {
            Object owner=binding.activity(controller);
            if (!(owner instanceof android.app.Activity activity)) return;
            outsideDismiss=new AdvancedPanelDismiss(activity,root,null,
                    () -> advanced!=null && advanced.isShown(), () -> {
                        flush();
                        Reflect.call(Reflect.field(activity,"j"),"f",false);
                    });
        }
        outsideDismiss.activate();
    }

    void releaseOutside() { unwatchOutside(); }
    private void unwatchOutside() {
        if(outsideDismiss!=null) outsideDismiss.deactivate();
    }

    static void attach(LinearLayout root, Object controller, Object repo, DressroomModule module)
            throws ReflectiveOperationException {
        if (root.findViewWithTag(MARKER) != null) return;
        new NativeClockPanel(root, controller, repo, module).install();
    }

    private NativeClockPanel(
            LinearLayout root, Object controller, Object repo, DressroomModule module) {
        this.root = root;
        this.context = root.getContext();
        this.controller = controller;
        this.binding = EditorBinding.of(controller);
        this.repo = repo;
        this.module = module;
        this.loader = controller.getClass().getClassLoader();
    }

    private void install() throws ReflectiveOperationException {
        module.registerPanel(this);
        int id =
                context.getResources()
                        .getIdentifier("tab_layout", "id", "com.samsung.android.app.dressroom");
        tabs = root.findViewById(id);
        if (tabs == null || root.getChildCount() < 2)
            throw new IllegalStateException("Native tabs missing");
        if (((Number) Reflect.call(tabs, "getTabCount")).intValue() != 2)
            throw new IllegalStateException("Unexpected native tab count");
        stock = root.getChildAt(1);
        try {
            renderer = binding.renderer(controller);
        } catch (ReflectiveOperationException ignored) {
            renderer = null;
        }
        scroll = new EditorScrollView(context);
        scroll.setTag(MARKER);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVisibility(View.GONE);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(16));
        scroll.addView(content);
        advanced = new LinearLayout(context);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        View pageStrip = nativeView(EditorBinding.Row.TABS);
        advanced.addView(pageStrip);
        TextView credit=new TextView(context);
        NativeCredits.bind(credit);
        credit.setTextSize(12);credit.setPadding(dp(16),dp(4),dp(16),dp(4));
        advanced.addView(credit);
        pages =
                new PanelTabs(
                        pageStrip,
                        loader,
                        new String[] {"Layout", "Effects", "Depth", "Date"},
                        0,
                        page -> scroll.post(() -> scroll.scrollTo(0, 0)));
        scroll.setVisibility(View.VISIBLE);
        advanced.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(advanced, new LinearLayout.LayoutParams(-1, panelHeight()));
        NativeTabs nativeTabs = NativeTabs.of(tabs);
        Class<?> listenerClass = nativeTabs.listener;
        Object listener =
                proxy(
                        listenerClass,
                        (method, args) -> {
                            if (method.getName().equals("onTabSelected")
                                    || method.getName().equals("onTabReselected")) {
                                int position = nativeTabs.position(args[0]);
                                boolean advanced = position == 2;
                                stock.setVisibility(advanced ? View.GONE : View.VISIBLE);
                                this.advanced.setVisibility(advanced ? View.VISIBLE : View.GONE);
                                if (advanced) refresh();
                                else { flush(); unwatchOutside(); }
                                root.requestLayout();
                            }
                            return null;
                        });
        nativeTabs.listen(tabs, listener);
        nativeTabs.add(tabs, "Advanced", false);
        scroll.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {}

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        handler.removeCallbacks(applyPending);
                        releaseOutside();
                        flush();
                    }
                });
    }

    private int panelHeight() {
        int available = context.getResources().getDisplayMetrics().heightPixels;
        return Math.min(dp(310), Math.max(dp(190), Math.round(available * .32f)));
    }

    private void refresh() {
        long outsideSession=module.generation();
        root.post(() -> {
            if (outsideSession != module.generation()) return;
            try { watchOutside(); }
            catch (Exception failure) { module.report("Advanced outside events unavailable",failure); }
        });
        ready = false;
        content.removeAllViews();
        status = null;
        pages.clear();
        currentGroup = content;
        try {
            status = label("", 12, false);
            status.setMinHeight(0);
            status.setMinimumHeight(0);
            status.setVisibility(View.GONE);
            Object data = data();
            currentRenderer();
            module.prepareRenderer(renderer);
            if (renderer instanceof View) DateEffects.attach(module, (View) renderer);
            movedX = movedY = 0;
            int newType = number(data, "getClockType").intValue();
            Object font = Reflect.call(data, "getClockFontData");
            int newFont = number(font, "getClockFontType").intValue();
            String newFontPath = "";
            try {
                Object path = Reflect.call(font, "getClockFontPath");
                if (path instanceof String) newFontPath = (String) path;
            } catch (Exception ignored) {
            }
            boolean newSelection =
                    initialSnapshot == null
                            || expectedType != newType
                            || expectedFont != newFont
                            || !expectedFontPath.equals(newFontPath)
                            || expectedScreen != ScreenIdentity.current()
                            || expectedOrientation != module.layoutOrientation(context)
                            || !expectedScene.equals(module.scene());
            expectedOrientation = module.layoutOrientation(context);
            expectedScreen = ScreenIdentity.current();
            expectedType = newType;
            expectedFont = newFont;
            expectedFontPath = newFontPath;
            expectedScene = module.scene();
            if (newSelection) {
                NativeEdits.cancel(weatherJob);
                weatherJob = null;
                editGeneration++;
                shadowPreference = null;
                weatherPreference = null;
                android.os.Bundle saved =
                        module.editorSettings(context, newType, newFont, newFontPath);
                depthSource =
                        saved == null ? new android.os.Bundle() : new android.os.Bundle(saved);
                nativeFontStretchable =
                        saved != null && saved.containsKey("fontNativeStretch")
                                ? saved.getBoolean("fontNativeStretch")
                                : bool(font, "isStretchableFont");
                if (saved != null) {
                    forceBlur = saved.getBoolean("forceBlur") && bool(data, "getBlurEnabled");
                    if (forceBlur) module.forceBlurTypes.add(newType);
                    else module.forceBlurTypes.remove(newType);
                    customShadow = saved.getBoolean("enabled");
                    shadowPreference = customShadow;
                    initialShadowEnabled = customShadow;
                    initialManual = saved.getBoolean("manual");
                    manualProgress = saved.getIntArray("manualProgress");
                    hideDate = saved.getBoolean("hideDate");
                    initialHideDate = hideDate;
                    initialForceBlur = forceBlur;
                    blurRadius = saved.getInt("blurRadius", 200);
                    initialBlurRadius = blurRadius;
                    blurVisibility = saved.getFloat("blurVisibility", -1f);
                    initialBlurVisibility = blurVisibility;
                    depthEnabled = initialDepthEnabled = saved.getBoolean("depthEnabled");
                    depthOwnsManual = initialDepthOwnsManual = saved.getBoolean("depthOwnsManual");
                    depthMask = initialDepthMask = saved.getByteArray("depthMask");
                    font = Reflect.call(data, "getClockFontData");
                    if (FontAxisSettings.nativeStretch(saved)
                            && saved.getBoolean("manual")
                            && bool(data, "getStretchable")
                            && hasInterface("FontExtension")) {
                        Object stretch = Reflect.call(font, "getClockStretchData");
                        @SuppressWarnings("unchecked")
                        List<Float> heights = (List<Float>) Reflect.call(stretch, "getFontHeight");
                        if (heights != null && !heights.isEmpty()) {
                            module.setManualRenderer(renderer, true);
                            module.stretchLocks.put(stretch, heights);
                        }
                    }
                } else {
                    initialShadowEnabled = false;
                    initialManual = false;
                    hideDate = false;
                    initialHideDate = false;
                    forceBlur = initialForceBlur = false;
                    blurRadius = initialBlurRadius = 200;
                    blurVisibility = initialBlurVisibility = -1f;
                    depthEnabled = initialDepthEnabled = false;
                    depthOwnsManual = initialDepthOwnsManual = false;
                    depthMask = initialDepthMask = null;
                    manualProgress = null;
                    module.forceBlurTypes.remove(newType);
                }
                if (renderer instanceof View && hasInterface("ClockBlurExtension"))
                    BlurEffects.preview(
                            (View) renderer,
                            forceBlur || bool(data, "getBlurEnabled"),
                            blurRadius,
                            blurVisibility);
                followDisplayFont = initialFollowDisplayFont = saved != null && saved.getBoolean("followDisplayFont");
                fontFile = saved == null ? "" : saved.getString("fontFile", "");
                fontFileName = saved == null ? "" : saved.getString("fontFileName", "");
                initialFontFile = fontFile;
                initialFontFileName = fontFileName;
                fontAxes =
                        followDisplayFont ? VariableFonts.displayAxes(context) : fontFile.isEmpty()
                                ? VariableFonts.axes(context, newFontPath, newFont)
                                : VariableFonts.importedAxes(context, fontFile);
                fontValues = followDisplayFont ? VariableFonts.displayValues(context, saved) : FontAxisSettings.values(saved);
                if (fontAxes != null) {
                    Object nativeWeight = Reflect.call(font, "getFontWeight");
                    for (FontAxes.Axis axis : fontAxes.values) {
                        float value =
                                fontValues.containsKey(axis.tag())
                                        ? fontValues.getFloat(axis.tag())
                                        : axis.tag().equals("wght")
                                                        && nativeWeight instanceof Number n
                                                        && n.floatValue() > 0
                                                        && fontFile.isEmpty() && !followDisplayFont
                                                ? n.floatValue()
                                                : axis.defaultValue();
                        fontValues.putFloat(axis.tag(), axis.clamp(value));
                    }
                }
                initialFontValues = new android.os.Bundle(fontValues);
                initialSnapshot = copy(data);
                initialWeather =
                        weatherPreference != null
                                ? weatherPreference
                                : bool(data, "getShowWeatherInfo");
            }
            if (weatherJob == null) weatherPreference = null;
            if (followDisplayFont || !fontFile.isEmpty()) suppressImportedStretch(data);
            updateDepthPreview(data);
            Object color = Reflect.call(data, "getClockColorData");
            Object shadow = Reflect.call(color, "getClockShadowStyle");
            section("Size & position");
            boolean editable = bool(data, "isEditableScale");
            float min = number(data, "getMinScale").floatValue();
            float max = number(data, "getMaxScale").floatValue();
            float scale = number(data, "getScale").floatValue();
            if (editable && Float.isFinite(min) && Float.isFinite(max) && max > min) {
                slider("Clock size", min, max, scale, true, v -> edit("scale", d -> resize(d, v)));
            }
            if (bool(data, "isEditablePosition")) {
                int width = context.getResources().getDisplayMetrics().widthPixels;
                int height = context.getResources().getDisplayMetrics().heightPixels;
                slider(
                        "Move left / right",
                        -width / 2f,
                        width / 2f,
                        0,
                        false,
                        v -> edit("x", d -> move(d, Math.round(v), movedY)));
                slider(
                        "Move up / down",
                        -height / 2f,
                        height / 2f,
                        0,
                        false,
                        v -> edit("y", d -> move(d, movedX, Math.round(v))));
            }
            section("Appearance");
            toggle(
                    "Adaptive color",
                    bool(color, "isAdaptiveColor"),
                    true,
                    v ->
                            edit(
                                    "adaptive",
                                    d ->
                                            Reflect.call(
                                                    Reflect.call(d, "getClockColorData"),
                                                    "setAdaptiveColor",
                                                    v)));
            colorRow(
                    "Clock color",
                    number(color, "getPaletteCustomColor").intValue(),
                    (d, v) -> {
                        Object c = Reflect.call(d, "getClockColorData");
                        Reflect.call(c, "setAdaptiveColor", false);
                        Reflect.call(c, "setPaletteType", 1);
                        Reflect.call(c, "setPaletteCustomColor", v);
                    });
            customShadow =
                    shadowPreference != null
                            ? shadowPreference
                            : number(shadow, "getShadowRadius").floatValue() > 0
                                    && (number(shadow, "getShadowColor").intValue() >>> 24) != 0;
            if (newSelection && shadowPreference == null) initialShadowEnabled = customShadow;
            toggle(
                    "Custom shadow",
                    customShadow,
                    true,
                    enabled -> {
                        customShadow = enabled;
                        shadowPreference = enabled;
                        edit(
                                "shadowEnabled",
                                d -> {
                                    shadow(d, "setShadowRadius", enabled ? 12f : 0f);
                                    if (enabled) shadow(d, "setShadowColor", 0x99000000);
                                });
                    });
            slider(
                    "Shadow softness",
                    0,
                    64,
                    number(shadow, "getShadowRadius").floatValue(),
                    false,
                    v -> edit("shadowRadius", d -> shadow(d, "setShadowRadius", v)));
            slider(
                    "Shadow horizontal offset",
                    -64,
                    64,
                    number(shadow, "getShadowDx").floatValue(),
                    false,
                    v -> edit("shadowX", d -> shadow(d, "setShadowDx", v)));
            slider(
                    "Shadow vertical offset",
                    -64,
                    64,
                    number(shadow, "getShadowDy").floatValue(),
                    false,
                    v -> edit("shadowY", d -> shadow(d, "setShadowDy", v)));
            colorRow(
                    "Shadow color",
                    number(shadow, "getShadowColor").intValue(),
                    (d, v) -> shadow(d, "setShadowColor", v));
            if (!binding.legacy) buildBlur(data);
            section("Depth effect");
            if (!module.depthAvailable(renderer)) {
                label(module.depthUnavailableReason(renderer), 12, false);
            } else if (!module.depthHardwareAvailable()) {
                label(
                        "Depth effect requires Samsung Object Capture, which is not supported"
                                + " on this device model.",
                        12,
                        false);
            } else {
                boolean imageAvailable = module.depthImageAvailable();
                toggle(
                        "Clock behind subject",
                        depthEnabled && imageAvailable,
                        imageAvailable,
                        enabled -> {
                            flush();
                            if (enabled) holdStretchForDepth();
                            depthEnabled = enabled;
                            if (!enabled) releaseDepthStretch();
                            updateDepthPreview(data());
                            stage(data());
                            refresh();
                        });
                TextView detectSubject = action("Detect wallpaper subject again", "");
                detectSubject.setEnabled(imageAvailable);
                if (!imageAvailable)
                    label(
                            "Depth requires a still-image wallpaper. Gyro, video and live"
                                    + " wallpapers are not supported.",
                            12,
                            false);
                detectSubject.setOnClickListener(
                        v -> {
                            if (!(renderer instanceof View)) return;
                            try {
                                flush();
                                if (!depthEnabled) {
                                    holdStretchForDepth();
                                    depthEnabled = true;
                                    stage(data());
                                }
                                module.detectDepth();
                                setStatus("Detecting subject…");
                            } catch (Exception error) {
                                error(error);
                            }
                        });
            }
            section("Typography");
            toggle("Use Display font", followDisplayFont, true,
                    enabled -> { followDisplayFont = enabled; changeFontFile("", ""); });
            label("Uses the font selected in Settings > Display > Font, including UN1CA fonts.", 12, false);
            if (bool(font, "getFontChangeable")) {
                TextView chooseFont =
                        action(
                                "Choose font file",
                                fontFile.isEmpty() ? "TTF, OTF or TTC" : fontFileName);
                chooseFont.setOnClickListener(view -> pickFont());
                if (!fontFile.isEmpty()) {
                    TextView restore = action("Use Samsung font", "");
                    restore.setOnClickListener(
                            view -> {
                                try {
                                    changeFontFile("", "");
                                } catch (Exception error) {
                                    error(error);
                                }
                            });
                }
            }
            Object range = Reflect.call(Reflect.call(font, "getFontItem"), "getWeightVariation");
            Object weight = Reflect.call(font, "getFontWeight");
            boolean builtInWeight =
                    bool(font, "getSupportFontVariation")
                            && range != null
                            && weight instanceof Number;
            if (builtInWeight && fontAxes == null && fontFile.isEmpty() && !followDisplayFont) {
                slider(
                        "Weight",
                        number(range, "getMinValue").floatValue(),
                        number(range, "getMaxValue").floatValue(),
                        ((Number) weight).floatValue(),
                        false,
                        v ->
                                edit(
                                        "weight",
                                        d ->
                                                Reflect.call(
                                                        Reflect.call(d, "getClockFontData"),
                                                        "setFontWeight",
                                                        Math.round(v))));
            }
            if (fontAxes != null) {
                for (FontAxes.Axis axis : fontAxes.values) {
                    // Advanced exposes hidden axes too, including fonts' parametric controls.
                    if (axis.max() <= axis.min()) continue;
                    float current =
                            axis.clamp(fontValues.getFloat(axis.tag(), axis.defaultValue()));
                    if (axis.tag().equals("ital") && axis.min() == 0 && axis.max() == 1)
                        toggle(
                                axis.label(),
                                current >= .5f,
                                true,
                                enabled -> previewFontAxis(axis, enabled ? 1 : 0));
                    else
                        slider(
                                axis.label(),
                                axis.min(),
                                axis.max(),
                                current,
                                false,
                                1000,
                                value -> previewFontAxis(axis, value));
                }
            }
            if ((!builtInWeight || !fontFile.isEmpty() || followDisplayFont) && fontAxes == null)
                label("This font has no adjustable axes.", 12, false);
            int rounding = number(font, "getRoundedCorners").intValue();
            if (rounding >= 0)
                slider(
                        "Corner rounding",
                        0,
                        100,
                        rounding,
                        false,
                        v ->
                                edit(
                                        "rounding",
                                        d ->
                                                Reflect.call(
                                                        Reflect.call(d, "getClockFontData"),
                                                        "setRoundedCorners",
                                                        Math.round(v))));
            section("Date & weather");
            toggle(
                    "Show date",
                    !hideDate,
                    true,
                    show -> {
                        currentRenderer();
                        NativeEdits.changing(controller, true);
                        hideDate = !show;
                        if (dateOrderControl != null)
                            dateOrderControl.setVisibility(hideDate ? View.GONE : View.VISIBLE);
                        if (renderer instanceof View) {
                            View clock = (View) renderer;
                            DateEffects.preview(clock, module, hideDate);
                            DateEffects.apply(clock);
                            clock.requestLayout();
                            clock.invalidate();
                        }
                        NativeEdits.remeasure(controller, bool(data(), "getShowWeatherInfo"));
                        finishNativeEdit();
                        stage(data());
                        setStatus("");
                    });
            dateOrderControl =
                    toggle(
                            "Date above the time",
                            bool(data, "getShowDateInfoFirst"),
                            true,
                            v -> NativeEdits.toggle(controller, v, false));
            dateOrderControl.setVisibility(hideDate ? View.GONE : View.VISIBLE);
            boolean weatherSupported = false;
            try {
                weatherSupported = binding.supportsWeather(controller, data);
            } catch (ReflectiveOperationException error) {
                module.report("Weather capability unavailable", error);
                label("Weather controls are unavailable for this clock.", 12, false);
            }
            if (weatherSupported) {
                toggle(
                        "Weather information",
                        weatherPreference != null
                                ? weatherPreference
                                : bool(data, "getShowWeatherInfo"),
                        bool(data, "isWeatherLocationExist"),
                        v -> {
                            setWeather(v);
                        });
                if (!bool(data, "isWeatherLocationExist"))
                    label("Add a city in Samsung Weather to show weather information.", 12, false);
            }
            buildStretch(data, font);
            section("Restore");
            TextView reset =
                    action(
                            "Restore this editing session",
                            "Undo changes made since opening Advanced");
            reset.setOnClickListener(
                    v -> {
                        try {
                            pending.clear();
                            handler.removeCallbacks(applyPending);
                            module.stretchLocks.clear();
                            module.setManualRenderer(renderer, false);
                            module.forceBlurTypes.remove(expectedType);
                            Reflect.call(repo, "setData", copy(initialSnapshot), true);
                            customShadow = initialShadowEnabled;
                            shadowPreference = customShadow;
                            hideDate = initialHideDate;
                            weatherPreference = initialWeather;
                            forceBlur = initialForceBlur;
                            blurRadius = initialBlurRadius;
                            blurVisibility = initialBlurVisibility;
                            depthEnabled = initialDepthEnabled;
                            depthOwnsManual = initialDepthOwnsManual;
                            depthMask = initialDepthMask;
                            followDisplayFont = initialFollowDisplayFont;
                            fontFile = initialFontFile;
                            fontFileName = initialFontFileName;
                            updateDepthPreview(data());
                            if (forceBlur) module.forceBlurTypes.add(expectedType);
                            if (renderer instanceof View)
                                DateEffects.preview((View) renderer, module, hideDate);
                            if (initialManual && initialFontFile.isEmpty() && !initialFollowDisplayFont) {
                                Object restored =
                                        Reflect.call(
                                                Reflect.call(initialSnapshot, "getClockFontData"),
                                                "getClockStretchData");
                                @SuppressWarnings("unchecked")
                                List<Float> heights =
                                        (List<Float>) Reflect.call(restored, "getFontHeight");
                                if (heights != null && !heights.isEmpty()) {
                                    module.setManualRenderer(renderer, true);
                                    module.stretchLocks.put(restored, heights);
                                }
                            }
                            fontFile = initialFontFile;
                            fontFileName = initialFontFileName;
                            fontAxes =
                                    followDisplayFont ? VariableFonts.displayAxes(context) : fontFile.isEmpty()
                                            ? VariableFonts.axes(
                                                    context, expectedFontPath, expectedFont)
                                            : VariableFonts.importedAxes(context, fontFile);
                            fontValues = new android.os.Bundle(initialFontValues);
                            applyShadow(initialSnapshot);
                            stage(initialSnapshot);
                            if (renderer instanceof View)
                                VariableFonts.preview((View) renderer, fontPreviewSettings());
                            if (renderer != null && hasInterface("ClockBlurExtension")) {
                                Reflect.call(
                                        renderer,
                                        "setBlurEffectEnabled",
                                        bool(initialSnapshot, "getBlurEnabled"));
                                BlurEffects.preview(
                                        (View) renderer,
                                        forceBlur || bool(initialSnapshot, "getBlurEnabled"),
                                        blurRadius,
                                        blurVisibility);
                            }
                            refresh();
                        } catch (Exception e) {
                            error(e);
                        }
                    });
            ready = true;
        } catch (Exception e) {
            error(e);
        }
    }

    private void buildBlur(Object data) throws Exception {
        boolean extension = hasInterface("ClockBlurExtension");
        toggle(
                "Clock blur",
                forceBlur || bool(data, "getBlurEnabled"),
                extension,
                enabled -> {
                    forceBlur = enabled;
                    if (enabled) module.forceBlurTypes.add(expectedType);
                    else module.forceBlurTypes.remove(expectedType);
                    edit(
                            "blur",
                            d -> {
                                if (enabled)
                                    Reflect.call(
                                            Reflect.call(d, "getClockTypeData"),
                                            "setSupportTextBlur",
                                            true);
                                Reflect.call(d, "setBlurEnabled", enabled);
                            });
                    if (renderer instanceof View)
                        BlurEffects.preview((View) renderer, enabled, blurRadius, blurVisibility);
                });
        if (!extension) {
            label("Blur is unavailable for this clock.", 12, false);
            return;
        }
        slider(
                "Blur strength",
                1,
                300,
                blurRadius,
                false,
                value -> {
                    blurRadius = Math.round(value);
                    edit("blurRadius", d -> {});
                    if (renderer instanceof View)
                        BlurEffects.preview(
                                (View) renderer,
                                forceBlur || bool(data(), "getBlurEnabled"),
                                blurRadius,
                                blurVisibility);
                });
        boolean custom = blurVisibility >= 0f;
        View[] visibility = new View[1];
        toggle(
                "Custom text visibility",
                custom,
                true,
                enabled -> {
                    blurVisibility = enabled ? .5f : -1f;
                    if (visibility[0] != null) visibility[0].setEnabled(enabled);
                    edit("blurVisibility", d -> {});
                    if (renderer instanceof View)
                        BlurEffects.preview(
                                (View) renderer,
                                forceBlur || bool(data(), "getBlurEnabled"),
                                blurRadius,
                                blurVisibility);
                });
        visibility[0] =
                slider(
                        "Text visibility over blur",
                        0,
                        100,
                        custom ? blurVisibility * 100 : 50,
                        false,
                        value -> {
                            blurVisibility = value / 100f;
                            edit("blurVisibility", d -> {});
                            if (renderer instanceof View)
                                BlurEffects.preview(
                                        (View) renderer,
                                        forceBlur || bool(data(), "getBlurEnabled"),
                                        blurRadius,
                                        blurVisibility);
                        });
        visibility[0].setEnabled(custom);
    }

    @SuppressWarnings("unchecked")
    private void buildStretch(Object data, Object font) throws Exception {
        section("Dynamic stretch");
        if (followDisplayFont || !fontFile.isEmpty()) {
            label("Samsung dynamic stretching is available only for Samsung stretchable clock fonts.", 12, false);
            return;
        }
        if (!bool(data, "getStretchable") || renderer == null || !hasInterface("FontExtension")) {
            label("Choose a dynamic clock with a stretchable font to adjust its shape.", 12, false);
            return;
        }
        Object measured = Reflect.call(renderer, "getStretchMeasureData");
        if (measured == null) {
            label("Stretch controls appear after the clock finishes measuring.", 12, false);
            return;
        }
        List<Rect> mins = (List<Rect>) Reflect.call(measured, "getMinRects");
        List<Rect> maxs = (List<Rect>) Reflect.call(measured, "getMaxRects");
        List<Rect> defaults = (List<Rect>) Reflect.call(measured, "getDefaultRects");
        if (mins == null
                || maxs == null
                || defaults == null
                || mins.isEmpty()
                || mins.size() != maxs.size()
                || mins.size() != defaults.size()
                || mins.size() > 12) {
            label("This clock does not expose compatible stretch measurements.", 12, false);
            return;
        }
        Object stretch = Reflect.call(font, "getClockStretchData");
        List<Rect> current = (List<Rect>) Reflect.call(stretch, "getFontHeightRects");
        int[] progress = new int[mins.size()];
        for (int i = 0; i < progress.length; i++) {
            int h =
                    current != null && current.size() == mins.size()
                            ? current.get(i).height()
                            : defaults.get(i).height();
            int span = maxs.get(i).height() - mins.get(i).height();
            progress[i] =
                    span > 0
                            ? Math.max(
                                    0,
                                    Math.min(
                                            100,
                                            Math.round(100f * (h - mins.get(i).height()) / span)))
                            : 0;
        }
        if (manualProgress != null && manualProgress.length == progress.length)
            System.arraycopy(manualProgress, 0, progress, 0, progress.length);
        else {
            List<Float> savedHeights = module.stretchLocks.get(stretch);
            if (savedHeights != null && savedHeights.size() == progress.length) {
                try {
                    Object manager =
                            Reflect.call(
                                    Reflect.field(controller, binding.legacy ? "j" : "g"),
                                    binding.legacy ? "i" : "d");
                    Object calculator = Reflect.call(manager, "getClockPositionCalculator");
                    int padding = number(stretch, "getInnerBottomPadding").intValue();
                    float scale = number(data, "getScale").floatValue();
                    @SuppressWarnings("unchecked")
                    List<Float> low =
                            (List<Float>)
                                    Reflect.call(
                                            calculator,
                                            "calculateStretchHeightList",
                                            mins,
                                            mins,
                                            padding,
                                            scale);
                    @SuppressWarnings("unchecked")
                    List<Float> high =
                            (List<Float>)
                                    Reflect.call(
                                            calculator,
                                            "calculateStretchHeightList",
                                            mins,
                                            maxs,
                                            padding,
                                            scale);
                    if (low != null
                            && high != null
                            && low.size() == progress.length
                            && high.size() == progress.length) {
                        for (int i = 0; i < progress.length; i++) {
                            float span = high.get(i) - low.get(i);
                            if (Math.abs(span) > 0.0001f)
                                progress[i] =
                                        Math.max(
                                                0,
                                                Math.min(
                                                        100,
                                                        Math.round(
                                                                100f
                                                                        * (savedHeights.get(i)
                                                                                - low.get(i))
                                                                        / span)));
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (depthEnabled && module.stretchLocks.get(stretch) == null) holdStretchForDepth();
        boolean locked = module.stretchLocks.get(stretch) != null;
        List<View> controls = new ArrayList<>();
        toggle(
                "Manual stretch",
                locked,
                !depthEnabled,
                enabled -> {
                    if (!enabled) {
                        manualProgress = null;
                        module.stretchLocks.clear();
                        module.setManualRenderer(renderer, false);
                        edit(
                                "stretch",
                                d -> {
                                    Object item =
                                            Reflect.call(
                                                    Reflect.call(d, "getClockFontData"),
                                                    "getClockStretchData");
                                    Reflect.call(item, "setFontHeight", (Object) null);
                                    Reflect.call(item, "setFontHeightRects", (Object) null);
                                });
                    } else applyStretch(mins, maxs, progress);
                    for (View control : controls) control.setEnabled(enabled);
                });
        for (int index = 0; index < progress.length; index++) {
            final int segment = index;
            View control =
                    slider(
                            "Segment " + (index + 1),
                            0,
                            100,
                            progress[index],
                            false,
                            v -> {
                                progress[segment] = Math.round(v);
                                applyStretch(mins, maxs, progress);
                            });
            control.setEnabled(locked);
            controls.add(control);
        }
    }

    @SuppressWarnings("unchecked")
    private void holdStretchForDepth() throws Exception {
        if (followDisplayFont || !fontFile.isEmpty()) return;
        Object draft = data();
        if (!bool(draft, "getStretchable") || renderer == null || !hasInterface("FontExtension"))
            return;
        Object font = Reflect.call(draft, "getClockFontData");
        Object stretch = Reflect.call(font, "getClockStretchData");
        if (module.stretchLocks.get(stretch) != null) {
            module.setManualRenderer(renderer, true);
            return;
        }
        List<Float> heights = (List<Float>) Reflect.call(stretch, "getFontHeight");
        if (heights != null
                && !heights.isEmpty()
                && heights.stream().allMatch(h -> h != null && Float.isFinite(h) && h > 0)) {
            depthOwnsManual = true;
            module.setManualRenderer(renderer, true);
            module.stretchLocks.put(stretch, new ArrayList<>(heights));
            stage(draft);
            return;
        }
        Object measured = Reflect.call(renderer, "getStretchMeasureData");
        if (measured == null)
            throw new IllegalStateException(
                    "Wait for the clock to finish measuring before enabling depth.");
        List<Rect> mins = (List<Rect>) Reflect.call(measured, "getMinRects");
        List<Rect> maxs = (List<Rect>) Reflect.call(measured, "getMaxRects");
        List<Rect> current = (List<Rect>) Reflect.call(stretch, "getFontHeightRects");
        List<Rect> defaults = (List<Rect>) Reflect.call(measured, "getDefaultRects");
        if (mins == null || maxs == null || mins.isEmpty() || mins.size() != maxs.size())
            throw new IllegalStateException("Clock stretch measurements are not ready.");
        int[] progress = new int[mins.size()];
        for (int i = 0; i < progress.length; i++) {
            int low = mins.get(i).height(), high = maxs.get(i).height();
            int height =
                    current != null && current.size() == progress.length
                            ? current.get(i).height()
                            : defaults != null && defaults.size() == progress.length
                                    ? defaults.get(i).height()
                                    : low;
            progress[i] =
                    high > low
                            ? Math.max(
                                    0,
                                    Math.min(100, Math.round(100f * (height - low) / (high - low))))
                            : 0;
        }
        List<Rect> targets = current != null && current.size() == mins.size() ? current : defaults;
        if (targets == null || targets.size() != mins.size())
            throw new IllegalStateException("No current stretch measurements available.");
        Object calculator = binding.calculator(controller);
        heights =
                (List<Float>)
                        Reflect.call(
                                calculator,
                                "calculateStretchHeightList",
                                mins,
                                targets,
                                number(stretch, "getInnerBottomPadding").intValue(),
                                number(draft, "getScale").floatValue());
        if (heights == null
                || heights.isEmpty()
                || heights.stream().anyMatch(h -> h == null || !Float.isFinite(h) || h <= 0))
            throw new IllegalStateException("Clock did not expose valid manual heights.");
        depthOwnsManual = true;
        manualProgress = progress;
        module.setManualRenderer(renderer, true);
        Reflect.call(stretch, "setFontHeight", new ArrayList<>(heights));
        Reflect.call(stretch, "setFontHeightRects", targets);
        module.stretchLocks.put(stretch, heights);
        stage(draft);
    }

    private void releaseDepthStretch() {
        if (!depthOwnsManual) return;
        depthOwnsManual = false;
        manualProgress = null;
        module.setManualRenderer(renderer, false);
        try {
            Object current = data();
            Object stretch =
                    Reflect.call(Reflect.call(current, "getClockFontData"), "getClockStretchData");
            module.stretchLocks.remove(stretch);
            Reflect.call(stretch, "setFontHeight", (Object) null);
            Reflect.call(stretch, "setFontHeightRects", (Object) null);
            stage(current);
        } catch (Exception e) {
            error(e);
        }
        edit(
                "stretch",
                draft -> {
                    Object stretch =
                            Reflect.call(
                                    Reflect.call(draft, "getClockFontData"), "getClockStretchData");
                    module.stretchLocks.remove(stretch);
                    Reflect.call(stretch, "setFontHeight", (Object) null);
                    Reflect.call(stretch, "setFontHeightRects", (Object) null);
                });
        flush();
    }

    @SuppressWarnings("unchecked")
    private void applyStretch(List<Rect> mins, List<Rect> maxs, int[] progress) {
        manualProgress = progress.clone();
        List<Rect> targets = new ArrayList<>();
        for (int i = 0; i < mins.size(); i++) {
            Rect r = new Rect(mins.get(i));
            r.bottom =
                    r.top
                            + Math.round(
                                    r.height()
                                            + (maxs.get(i).height() - r.height())
                                                    * progress[i]
                                                    / 100f);
            targets.add(r);
        }
        edit(
                "stretch",
                draft -> {
                    Object item =
                            Reflect.call(
                                    Reflect.call(draft, "getClockFontData"), "getClockStretchData");
                    Object manager =
                            Reflect.call(
                                    Reflect.field(controller, binding.legacy ? "j" : "g"),
                                    binding.legacy ? "i" : "d");
                    Object calculator = Reflect.call(manager, "getClockPositionCalculator");
                    if (calculator == null)
                        throw new IllegalStateException(
                                "No stretch calculator is available for this clock.");
                    List<Float> heights =
                            (List<Float>)
                                    Reflect.call(
                                            calculator,
                                            "calculateStretchHeightList",
                                            mins,
                                            targets,
                                            number(item, "getInnerBottomPadding").intValue(),
                                            number(draft, "getScale").floatValue());
                    if (heights == null
                            || heights.isEmpty()
                            || heights.stream()
                                    .anyMatch(h -> h == null || !Float.isFinite(h) || h <= 0)) {
                        throw new IllegalArgumentException(
                                "This clock rejected that stretch amount.");
                    }
                    module.setManualRenderer(renderer, true);
                    module.stretchLocks.remove(item);
                    Reflect.call(item, "setFontHeight", new ArrayList<>(heights));
                    Reflect.call(item, "setFontHeightRects", targets);
                    Reflect.call(draft, "setSupportStretch", true);
                    module.stretchLocks.put(item, heights);
                });
    }

    private boolean hasInterface(String name) {
        if (renderer == null) return false;
        try {
            return Class.forName(
                            "com.samsung.android.clockpack.plugins.clock.extension." + name,
                            false,
                            loader)
                    .isInstance(renderer);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void currentRenderer() throws Exception {
        Object current = binding.renderer(controller);
        if (current != renderer && renderer instanceof View) {
            View retired = (View) renderer;
            EditorClockRefresh.cancel(retired);
            BlurEffects.clearPreview(retired);
            DepthEffects.apply(retired, false, null);
            ShadowEffects.apply(retired, false, 0, 0, 0, 0);
        }
        renderer = current;
    }

    private void setWeather(boolean value) throws Exception {
        currentRenderer();
        NativeEdits.cancel(weatherJob);
        weatherPreference = value;
        final int generation = editGeneration;
        final long session = module.generation();
        Object job = NativeEdits.toggle(controller, value, true);
        weatherJob = job;
        stage(data());
        if (job == null) {
            weatherPreference = null;
            if (renderer instanceof View) refreshClockSurface((View) renderer);
            return;
        }
        Class<?> function = Class.forName("kotlin.jvm.functions.Function1", false, loader);
        Object completion =
                proxy(
                        function,
                        (method, args) -> {
                            if (method.getName().equals("invoke"))
                                handler.post(
                                        () -> {
                                            if (weatherJob != job
                                                    || generation != editGeneration
                                                    || session != module.generation()) return;
                                            weatherJob = null;
                                            weatherPreference = null;
                                            try {
                                                currentRenderer();
                                                stage(data());
                                                if (renderer instanceof View)
                                                    refreshClockSurface((View) renderer);
                                            } catch (Exception error) {
                                                error(error);
                                            }
                                        });
                            return null;
                        });
        Reflect.call(job, "invokeOnCompletion", completion);
    }

    private void finishNativeEdit() {
        if (finishEdit != null) handler.removeCallbacks(finishEdit);
        final int generation = editGeneration;
        final long session = module.generation();
        finishEdit =
                () -> {
                    if (generation != editGeneration || session != module.generation()) return;
                    try {
                        if (weatherJob == null) NativeEdits.changing(controller, false);
                        currentRenderer();
                        if (renderer instanceof View) refreshClockSurface((View) renderer);
                    } catch (Exception error) {
                        error(error);
                    }
                };
        handler.postDelayed(finishEdit, 320);
    }

    boolean prepareSave() {
        flush();
        try {
            Object current = data();
            if (number(current, "getClockType").intValue() == expectedType
                    && number(Reflect.call(current, "getClockFontData"), "getClockFontType")
                                    .intValue()
                            == expectedFont
                    && expectedScreen == ScreenIdentity.current()
                    && expectedOrientation == module.layoutOrientation(context)
                    && expectedScene.equals(module.scene())) {
                stage(current);
                return true;
            }
        } catch (Exception error) {
            error(error);
        }
        return false;
    }

    private void edit(String key, Edit edit) {
        if (!ready) return;
        pending.put(key, edit);
        if (!handler.hasCallbacks(applyPending)) handler.postDelayed(applyPending, 32);
    }

    private void flush() {
        handler.removeCallbacks(applyPending);
        if (pending.isEmpty()) return;
        Map<String, Edit> edits = new LinkedHashMap<>(pending);
        pending.clear();
        try {
            currentRenderer();
            Object source = data();
            if (number(source, "getClockType").intValue() != expectedType
                    || number(Reflect.call(source, "getClockFontData"), "getClockFontType")
                                    .intValue()
                            != expectedFont
                    || expectedScreen != ScreenIdentity.current()
                            || expectedOrientation != module.layoutOrientation(context)
                    || !expectedScene.equals(module.scene())) {
                throw new IllegalStateException("Clock changed. Select Advanced again.");
            }
            Object draft = copy(source);
            NativeEdits.changing(controller, true);
            for (Edit edit : edits.values()) edit.apply(draft);
            stage(draft);
            Reflect.call(repo, "setData", draft, true);
            stage(draft);
            updateDepthPreview(draft);
            finishNativeEdit();
            if ((edits.containsKey("weather") || edits.containsKey("dateVisibility"))
                    && renderer instanceof View) {
                View clock = (View) renderer;
                clock.postDelayed(
                        () -> {
                            if (clock.isAttachedToWindow()) DateEffects.apply(clock);
                        },
                        80);
            }
            if (customShadow || edits.keySet().stream().anyMatch(k -> k.startsWith("shadow")))
                applyShadow(draft);
            if (renderer != null
                    && hasInterface("ClockBlurExtension")
                    && (module.forceBlurTypes.contains(expectedType)
                            || edits.containsKey("blur"))) {
                Reflect.call(renderer, "setBlurEffectEnabled", bool(draft, "getBlurEnabled"));
            }
            if (renderer instanceof View
                    && hasInterface("ClockBlurExtension")
                    && (edits.containsKey("blur")
                            || edits.containsKey("blurRadius")
                            || edits.containsKey("blurVisibility")))
                BlurEffects.preview(
                        (View) renderer,
                        forceBlur || bool(draft, "getBlurEnabled"),
                        blurRadius,
                        blurVisibility);
            if (renderer instanceof View) {
                View clock = (View) renderer;
                clock.requestLayout();
                clock.invalidate();
            }
            setStatus("");
        } catch (Exception e) {
            try {
                if (weatherJob == null) NativeEdits.changing(controller, false);
            } catch (Exception ignored) {
            }
            error(e);
        }
    }

    private Object data() throws Exception {
        return Reflect.call(repo, "getData");
    }

    private void refreshClockSurface(View clock) {
        long session = module.generation();
        int generation = editGeneration;
        clock.invalidate();
        EditorClockRefresh.request(
                clock,
                () -> {
                    try {
                        if (session != module.generation()
                                || generation != editGeneration
                                || binding.renderer(controller) != clock) return;
                        applyShadow(data());
                    } catch (Exception e) {
                        error(e);
                    }
                });
    }

    private void updateDepthPreview(Object draft) throws Exception {
        if (!module.depthAvailable(renderer)) return;
        currentRenderer();
        if (!(renderer instanceof View)) return;
        View clock = (View) renderer;
        DepthEffects.setEditorState(clock, depthEnabled, depthMask);
        DepthEffects.applyEditor(clock, depthEnabled, depthMask);
        DepthEffects.applyEditorDigits(clock, depthEnabled, depthMask);
        DepthEffects.applyWidgets(clock, depthEnabled, depthMask, true);
        if (!depthEnabled || depthMask == null) return;
        float scale = binding.previewScale(controller);
        Rect fullRect = (Rect) Reflect.call(draft, "getClockPositionRect");
        if (Float.isFinite(scale) && scale > 0)
            DepthEffects.editorTransform(clock, scale, fullRect);
    }

    private void stage(Object draft) throws Exception {
        if (followDisplayFont || !fontFile.isEmpty()) suppressImportedStretch(draft);
        Object value =
                Reflect.call(Reflect.call(draft, "getClockColorData"), "getClockShadowStyle");
        android.os.Bundle settings = new android.os.Bundle();
        DepthMetadata.copy(depthSource, settings);
        settings.putInt("type", expectedType);
        settings.putInt("font", expectedFont);
        try {
            Object fontData = Reflect.call(draft, "getClockFontData");
            Object fontPath = Reflect.call(fontData, "getClockFontPath");
            if (fontPath instanceof String) settings.putString("fontPath", (String) fontPath);
        } catch (Exception ignored) {
        }
        settings.putBoolean("followDisplayFont", followDisplayFont);
        if (followDisplayFont) settings.putString("fontSourceIdentity", DisplayFontResolver.currentDisplay(context).identity);
        settings.putString("fontFile", fontFile);
        settings.putString("fontFileName", fontFileName);
        settings.putBoolean("fontNativeStretch", nativeFontStretchable);
        if (fontAxes != null || !fontFile.isEmpty() || followDisplayFont)
            settings.putBundle("fontAxes", FontAxisSettings.clean(fontValues));
        settings.putBoolean("enabled", customShadow);
        Object stretch =
                Reflect.call(Reflect.call(draft, "getClockFontData"), "getClockStretchData");
        List<Float> lockedHeights = module.stretchLocks.get(stretch);
        settings.putBoolean("manual", lockedHeights != null);
        if (lockedHeights != null) {
            float[] heights = new float[lockedHeights.size()];
            for (int i = 0; i < heights.length; i++) heights[i] = lockedHeights.get(i);
            settings.putFloatArray("manualHeights", heights);
            if (manualProgress != null) settings.putIntArray("manualProgress", manualProgress);
        }
        settings.putBoolean("hideDate", hideDate);
        settings.putBoolean(
                "weatherEnabled",
                weatherJob != null && weatherPreference != null
                        ? weatherPreference
                        : bool(draft, "getShowWeatherInfo"));
        settings.putBoolean("forceBlur", forceBlur);
        settings.putInt("blurRadius", blurRadius);
        settings.putFloat("blurVisibility", blurVisibility);
        settings.putBoolean("depthEnabled", depthEnabled);
        settings.putBoolean("depthOwnsManual", depthOwnsManual);
        if (depthMask != null) settings.putByteArray("depthMask", depthMask);
        settings.putFloat("radius", number(value, "getShadowRadius").floatValue());
        settings.putFloat("dx", number(value, "getShadowDx").floatValue());
        settings.putFloat("dy", number(value, "getShadowDy").floatValue());
        settings.putInt("color", number(value, "getShadowColor").intValue());
        module.stageSettings(context, settings);
        if (renderer instanceof View clock) VariableFonts.configure(module, clock, settings);
    }

    void depthStatus(String message) {
        if (status != null && ready) setStatus(message);
    }

    void clearDepth(String message) {
        if (ready && expectedScreen != ScreenIdentity.current()) return;
        releaseDepthStretch();
        depthEnabled = false;
        depthMask = null;
        initialDepthMask = null;
        DepthSafety.clearMask(depthSource);
        if (!ready) return;
        try {
            stage(data());
            updateDepthPreview(data());
            refresh();
            depthStatus(message);
        } catch (Exception error) {
            error(error);
        }
    }

    void adoptDepth(android.os.Bundle settings, String message) throws Exception {
        if (!ready
                || expectedScreen != ScreenIdentity.current()
                || ScreenIdentity.value(settings) != expectedScreen
                || settings.getInt("type", -1) != expectedType
                || settings.getInt("font", -1) != expectedFont
                || !expectedScene.equals(module.scene())) return;
        depthMask = settings.getByteArray("depthMask");
        depthSource = new android.os.Bundle(settings);
        depthEnabled = settings.getBoolean("depthEnabled");
        if (initialDepthEnabled) initialDepthMask = depthMask;
        updateDepthPreview(data());
        refresh();
        depthStatus(message);
    }

    private void move(Object draft, int x, int y) throws Exception {
        binding.position(controller, draft, false, 0, x - movedX, y - movedY);
        movedX = x;
        movedY = y;
    }

    private void resize(Object draft, float scale) throws Exception {
        binding.position(controller, draft, true, scale, 0, 0);
    }

    private void applyShadow(Object draft) throws Exception {
        currentRenderer();
        if (renderer == null) return;
        Object value =
                Reflect.call(Reflect.call(draft, "getClockColorData"), "getClockShadowStyle");
        Class<?> style =
                Class.forName(
                        "com.samsung.android.clockpack.plugins.clock.ShadowStyle",
                        true,
                        renderer.getClass().getClassLoader());
        Object nativeStyle = style.getConstructor().newInstance();
        float scale = number(draft, "getScale").floatValue();
        if (!Float.isFinite(scale) || scale <= 0) scale = 1f;
        style.getField("shadowRadius")
                .setFloat(nativeStyle, number(value, "getShadowRadius").floatValue() / scale);
        style.getField("shadowDx")
                .setFloat(nativeStyle, number(value, "getShadowDx").floatValue() / scale);
        style.getField("shadowDy")
                .setFloat(nativeStyle, number(value, "getShadowDy").floatValue() / scale);
        style.getField("shadowColor")
                .setInt(nativeStyle, number(value, "getShadowColor").intValue());
        Reflect.call(renderer, "setShadowStyle", nativeStyle);
        ClockBinding.of(renderer).refreshAppearance(renderer);
        if (renderer instanceof View && android.os.Build.VERSION.SDK_INT >= 31) {
            View view = (View) renderer;
            ShadowEffects.apply(
                    view,
                    customShadow,
                    number(value, "getShadowRadius").floatValue() / scale,
                    number(value, "getShadowDx").floatValue() / scale,
                    number(value, "getShadowDy").floatValue() / scale,
                    number(value, "getShadowColor").intValue());
        }
        if (renderer instanceof View) ((View) renderer).invalidate();
    }

    private Object copy(Object data) throws Exception {
        Class<?> helper =
                Class.forName(
                        "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockDataKt",
                        false,
                        loader);
        Object copy = helper.getDeclaredMethod("deepCopy", data.getClass()).invoke(null, data);
        Object colors = Reflect.call(copy, "getClockColorData");
        Object shadow = Reflect.call(colors, "getClockShadowStyle");
        Object isolated =
                shadow.getClass()
                        .getConstructor(float.class, float.class, float.class, int.class)
                        .newInstance(
                                number(shadow, "getShadowRadius").floatValue(),
                                        number(shadow, "getShadowDx").floatValue(),
                                number(shadow, "getShadowDy").floatValue(),
                                        number(shadow, "getShadowColor").intValue());
        Reflect.call(colors, "setClockShadowStyle", isolated);
        return copy;
    }

    private void shadow(Object draft, String method, Object value) throws Exception {
        Object c = Reflect.call(draft, "getClockColorData");
        Object s = Reflect.call(c, "getClockShadowStyle");
        Object fresh =
                s.getClass()
                        .getConstructor(float.class, float.class, float.class, int.class)
                        .newInstance(
                                number(s, "getShadowRadius").floatValue(),
                                        number(s, "getShadowDx").floatValue(),
                                number(s, "getShadowDy").floatValue(),
                                        number(s, "getShadowColor").intValue());
        Reflect.call(fresh, method, value);
        Reflect.call(c, "setClockShadowStyle", fresh);
    }

    private void section(String name) {
        if (name.equals("Restore")) {
            currentGroup = pages.group(content, 0);
            return;
        }
        int page =
                switch (name) {
                    case "Appearance" -> 1;
                    case "Depth effect" -> 2;
                    case "Date & weather" -> 3;
                    default -> 0;
                };
        boolean separated = pages.hasGroup(page);
        currentGroup = pages.group(content, page);
        int divider = resource("clock_panel_item_divider", "drawable");
        if (separated && divider != 0) {
            View line = new View(context);
            android.graphics.drawable.Drawable drawable = context.getDrawable(divider);
            line.setBackground(drawable);
            currentGroup.addView(
                    line, PanelRows.divider(context, Math.max(1, drawable.getIntrinsicHeight())));
        }
        TextView header = (TextView) nativeView(EditorBinding.Row.LABEL);
        header.setText(name);
        PanelRows.heading(header);
        addNativeRow(header);
    }

    private TextView label(String text, int size, boolean bold) {
        TextView view = (TextView) nativeView(EditorBinding.Row.LABEL);
        view.setText(text);
        PanelRows.text(view, size <= 13);
        addNativeRow(view);
        return view;
    }

    private CompoundButton toggle(String name, boolean value, boolean enabled, ToggleEdit edit)
            throws Exception {
        CompoundButton control = (CompoundButton) nativeView(EditorBinding.Row.TOGGLE);
        control.setText(name);
        control.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        control.setChecked(value);
        control.setEnabled(enabled);
        control.setOnCheckedChangeListener(
                (button, selected) -> {
                    if (!ready) return;
                    try {
                        edit.apply(selected);
                        flush();
                    } catch (Exception e) {
                        error(e);
                    }
                });
        addNativeRow(control);
        return control;
    }

    private void pickFont() {
        flush();
        long session = module.generation();
        int generation = editGeneration;
        java.lang.ref.WeakReference<NativeClockPanel> reference =
                new java.lang.ref.WeakReference<>(this);
        try {
            FontPicker.open(
                    context,
                    result -> {
                        NativeClockPanel panel = reference.get();
                        if (panel == null
                                || !panel.root.isAttachedToWindow()
                                || session != panel.module.generation()
                                || generation != panel.editGeneration) return;
                        try {
                            String error = result.getString("error", "");
                            if (!error.isEmpty()) {
                                panel.setStatus(error);
                                return;
                            }
                            String file = result.getString("fontFile", "");
                            if (!file.isEmpty())
                                panel.changeFontFile(
                                        file, result.getString("fontFileName", "Custom font"));
                        } catch (Exception error) {
                            panel.error(error);
                        }
                    });
        } catch (RuntimeException error) {
            error(error);
        }
    }

    private void changeFontFile(String id, String name) throws Exception {
        currentRenderer();
        if (!id.isEmpty()) followDisplayFont = false;
        fontFile = id;
        fontFileName = name;
        fontValues = new android.os.Bundle();
        fontAxes =
                followDisplayFont ? VariableFonts.displayAxes(context) : id.isEmpty()
                        ? VariableFonts.axes(context, expectedFontPath, expectedFont)
                        : VariableFonts.importedAxes(context, id);
        if (fontAxes != null)
            for (FontAxes.Axis axis : fontAxes.values)
                fontValues.putFloat(axis.tag(), axis.defaultValue());
        Object draft = data();
        if (id.isEmpty() && !followDisplayFont)
            Reflect.call(
                    Reflect.call(draft, "getClockFontData"),
                    "setStretchableFont",
                    nativeFontStretchable);
        else suppressImportedStretch(draft);
        stage(draft);
        edit(
                "fontFile",
                value -> {
                    if (followDisplayFont || !fontFile.isEmpty()) suppressImportedStretch(value);
                    else
                        Reflect.call(
                                Reflect.call(value, "getClockFontData"),
                                "setStretchableFont",
                                nativeFontStretchable);
                });
        flush();
        if (renderer instanceof View clock) {
            Object nativeFont = ClockBinding.of(clock).font(clock);
            if (nativeFont != null) ClockBinding.of(clock).render(clock, nativeFont);
            FontBinding.of(clock).refresh(clock);
            refreshClockSurface(clock);
            clock.requestLayout();
            clock.invalidate();
        }
        refresh();
    }

    private void suppressImportedStretch(Object draft) throws Exception {
        Object font = Reflect.call(draft, "getClockFontData");
        Reflect.call(font, "setStretchableFont", false);
        Object stretch = Reflect.call(font, "getClockStretchData");
        module.stretchLocks.remove(stretch);
        Reflect.call(stretch, "setFontHeight", (Object) null);
        Reflect.call(stretch, "setFontHeightRects", (Object) null);
        manualProgress = null;
        depthOwnsManual = false;
        if (renderer != null) module.setManualRenderer(renderer, false);
    }

    private static String axisValue(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value).replaceAll("\\.?0+$", "");
    }

    private android.os.Bundle fontPreviewSettings() {
        android.os.Bundle profile = new android.os.Bundle();
        profile.putBoolean("followDisplayFont", followDisplayFont);
        if (followDisplayFont) profile.putString("fontSourceIdentity", DisplayFontResolver.currentDisplay(context).identity);
        profile.putString("fontFile", fontFile);
        profile.putBundle("fontAxes", FontAxisSettings.clean(fontValues));
        return profile;
    }

    private void previewFontAxis(FontAxes.Axis axis, float value) throws Exception {
        fontValues.putFloat(axis.tag(), axis.clamp(value));
        if (axis.tag().equals("wght") && fontFile.isEmpty() && !followDisplayFont)
            edit(
                    "weight",
                    draft ->
                            Reflect.call(
                                    Reflect.call(draft, "getClockFontData"),
                                    "setFontWeight",
                                    Math.round(value)));
        stage(data());
        if (renderer instanceof View clock) VariableFonts.preview(clock, fontPreviewSettings());
    }

    private View slider(
            String name, float min, float max, float current, boolean percent, SliderEdit edit)
            throws Exception {
        return slider(name, min, max, current, percent, 0, edit);
    }

    private View slider(
            String name,
            float min,
            float max,
            float current,
            boolean percent,
            int precision,
            SliderEdit edit)
            throws Exception {
        LinearLayout parent = currentGroup;
        currentGroup = new LinearLayout(context);
        currentGroup.setOrientation(LinearLayout.VERTICAL);
        parent.addView(currentGroup, new LinearLayout.LayoutParams(-1, -2));
        TextView caption =
                label(
                        name
                                + "   "
                                + (precision > 0 ? axisValue(current) : format(current, percent)),
                        15,
                        false);
        View slider = nativeView(EditorBinding.Row.SLIDER);
        int steps =
                precision > 0
                        ? precision
                        : Math.max(
                                1, Math.min(1000, Math.round((max - min) * (percent ? 100 : 1))));
        Reflect.call(slider, "setMax", steps);
        int initial = max > min ? Math.round((current - min) * steps / (max - min)) : 0;
        Reflect.call(slider, "setProgress", Math.max(0, Math.min(steps, initial)));
        slider.setContentDescription(name);
        Class<?> listener =
                Class.forName(
                        "androidx.appcompat.widget.SeslSeekBar$OnSeekBarChangeListener",
                        false,
                        loader);
        Object callback =
                proxy(
                        listener,
                        (method, args) -> {
                            if (method.getName().equals("onStartTrackingTouch"))
                                NativeEdits.changing(controller, true);
                            else if (method.getName().equals("onProgressChanged")
                                    && Boolean.TRUE.equals(args[2])) {
                                float value =
                                        min + ((Number) args[1]).floatValue() * (max - min) / steps;
                                caption.setText(
                                        name
                                                + "   "
                                                + (precision > 0
                                                        ? axisValue(value)
                                                        : format(value, percent)));
                                edit.apply(value);
                            } else if (method.getName().equals("onStopTrackingTouch")) {
                                flush();
                                finishNativeEdit();
                            }
                            return null;
                        });
        Reflect.call(slider, "setOnSeekBarChangeListener", callback);
        currentGroup.addView(slider);
        PanelRows.slider(currentGroup, caption, slider);
        currentGroup = parent;
        return slider;
    }

    private int resource(String name, String kind) {
        return context.getResources()
                .getIdentifier(name, kind, "com.samsung.android.app.dressroom");
    }

    private View nativeView(EditorBinding.Row row) {
        String tag = binding.rowTag(row);
        try (XmlResourceParser xml =
                context.getResources().getLayout(resource("clock_panel_layout", "layout"))) {
            while (xml.getEventType() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (xml.getEventType() == org.xmlpull.v1.XmlPullParser.START_TAG
                        && tag.equals(
                                xml.getAttributeValue(
                                        "http://schemas.android.com/apk/res/android", "tag"))) {
                    android.util.AttributeSet attributes = android.util.Xml.asAttributeSet(xml);
                    View view =
                            LayoutInflater.from(context)
                                    .createView(context, xml.getName(), null, attributes);
                    view.setLayoutParams(content.generateLayoutParams(attributes));
                    view.setId(View.NO_ID);
                    view.setTag(null);
                    return view;
                }
                xml.next();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot inflate Dressroom row " + tag, e);
        }
        throw new IllegalStateException("Dressroom row missing: " + tag);
    }

    private void addNativeRow(View view) {
        addRowTo(currentGroup == null ? content : currentGroup, view);
    }

    private void addRowTo(LinearLayout parent, View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(view.getLayoutParams());
        int inset =
                context.getResources()
                        .getDimensionPixelSize(
                                resource(
                                        "clock_panel_horizontal_clock_list_horizontal_padding_start",
                                        "dimen"));
        params.setMarginStart(inset);
        params.setMarginEnd(inset);
        parent.addView(view, params);
    }

    private TextView action(String title, String summary) {
        TextView row = label(title, 15, false);
        PanelRows.action(row, title, summary);
        LinearLayout.LayoutParams layout = (LinearLayout.LayoutParams) row.getLayoutParams();
        layout.topMargin = dp(4);
        layout.bottomMargin = dp(8);
        row.setLayoutParams(layout);
        return row;
    }

    private void colorRow(String name, int initial, ColorEdit edit) {
        TextView row = action(name, hex(initial));
        int[] lastColor = {initial};
        row.setOnClickListener(
                v -> {
                    try {
                        flush();
                        final int pickerScreen = ScreenIdentity.current();
                        final long pickerSession = module.generation();
                        Object snapshot = copy(data());
                        boolean[] accepted = {false};
                        Class<?> listener =
                                Class.forName(
                                        "androidx.picker3.app.SeslColorPickerDialog$OnColorSetListener",
                                        false,
                                        loader);
                        Object callback =
                                proxy(
                                        listener,
                                        (method, args) -> {
                                            if (method.getName().equals("onColorSet")) {
                                                if (pickerScreen != ScreenIdentity.current()
                                                        || pickerSession != module.generation()) return null;
                                                accepted[0] = true;
                                                int chosen = ((Number) args[0]).intValue();
                                                lastColor[0] = chosen;
                                                edit("color-" + name, d -> edit.apply(d, chosen));
                                                flush();
                                                PanelRows.actionText(row, name, hex(chosen));
                                            }
                                            return null;
                                        });
                        Object pickerDialog =
                                Class.forName(
                                                "androidx.picker3.app.SeslColorPickerDialog",
                                                true,
                                                loader)
                                        .getConstructor(Context.class, listener, int.class)
                                        .newInstance(context, callback, lastColor[0]);
                        Reflect.call(pickerDialog, "setTransparencyControlEnabled", true);
                        Object picker = Reflect.call(pickerDialog, "getColorPicker");
                        Class<?> changed =
                                Class.forName(
                                        "androidx.picker3.widget.SeslColorPicker$OnColorChangedListener",
                                        false,
                                        loader);
                        Reflect.call(
                                picker,
                                "setOnColorChangedListener",
                                proxy(
                                        changed,
                                        (method, args) -> {
                                            if (method.getName().equals("onColorChanged")) {
                                                if (pickerScreen != ScreenIdentity.current()
                                                        || pickerSession != module.generation()) return null;
                                                int chosen = ((Number) args[0]).intValue();
                                                edit("color-" + name, d -> edit.apply(d, chosen));
                                            }
                                            return null;
                                        }));
                        Dialog dialog = (Dialog) pickerDialog;
                        dialog.setOnDismissListener(
                                d -> {
                                    if (pickerScreen != ScreenIdentity.current()
                                            || pickerSession != module.generation()) return;
                                    if (!accepted[0]) {
                                        pending.clear();
                                        handler.removeCallbacks(applyPending);
                                        try {
                                            Reflect.call(repo, "setData", snapshot, true);
                                        } catch (Exception e) {
                                            error(e);
                                        }
                                    }
                                });
                        dialog.show();
                        AdvancedPanelDismiss.protectCurrent(dialog);
                    } catch (Exception e) {
                        error(e);
                    }
                });
    }

    interface Invocation {
        Object invoke(Method method, Object[] args) throws Exception;
    }

    static Object proxy(Class<?> type, Invocation target) {
        return Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (self, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        if (method.getName().equals("hashCode"))
                            return System.identityHashCode(self);
                        if (method.getName().equals("equals")) return self == args[0];
                        return "Dressroom Advanced " + type.getSimpleName();
                    }
                    try {
                        return target.invoke(method, args);
                    } catch (Exception e) {
                        android.util.Log.e("DressroomAdvanced", "Native callback failed", e);
                        return null;
                    }
                });
    }

    private void setStatus(String message) {
        if (status == null) return;
        status.setText(message);
        status.setVisibility(message == null || message.isBlank() ? View.GONE : View.VISIBLE);
    }

    private void error(Exception e) {
        module.report("Native Advanced control failed", e);
        if (status == null) {
            status = new TextView(context);
            content.addView(status, new LinearLayout.LayoutParams(-1, -2));
        }
        if (status != null)
            setStatus(
                    "Could not update: "
                            + (e.getMessage() == null ? "select Advanced again" : e.getMessage()));
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private int themeColor(int attr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        return value.resourceId != 0 ? context.getColor(value.resourceId) : value.data;
    }

    private static Number number(Object object, String getter) throws Exception {
        return (Number) Reflect.call(object, getter);
    }

    private static boolean bool(Object object, String getter) throws Exception {
        return Boolean.TRUE.equals(Reflect.call(object, getter));
    }

    private static String format(float value, boolean percent) {
        return percent ? Math.round(value * 100) + "%" : String.format(Locale.ROOT, "%.0f", value);
    }

    private static String hex(int color) {
        return String.format(Locale.ROOT, "#%08X", color);
    }
}
