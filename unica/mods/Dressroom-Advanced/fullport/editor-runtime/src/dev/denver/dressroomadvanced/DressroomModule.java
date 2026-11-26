// Native adaptation of BlueOutside / Dressroom Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;



import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class DressroomModule {
    public static final String TAG = "DressroomAdvanced";
    private static final DressroomModule INSTANCE = new DressroomModule();
    public static DressroomModule get() { return INSTANCE; }
    private DressroomModule() {
        editorWidgets = new EditorWidgets(this);
        depthAuto = new DepthAuto(this);
        NativeWidgets.bind(this);
    }
    void log(int level,String tag,String message) { android.util.Log.println(level,tag,message); }
    void log(int level,String tag,String message,Throwable failure) { android.util.Log.println(level,tag,message+": "+failure); }
    boolean nativeAdvancedShown(android.app.Activity activity) {
        return AdvancedPanelDismiss.shownFor(activity);
    }
    boolean nativeOutsideTouch(Object source,android.view.MotionEvent event) {
        return AdvancedPanelDismiss.touch(source,event);
    }
    void nativeSession(android.app.Activity activity) {
        if (editorActivity.get()==activity) return;
        beginEditor(); editorActivity=new java.lang.ref.WeakReference<>(activity);
        editorContext=activity;
    }
    void nativeClose(android.app.Activity activity) {
        if(editorActivity.get()==activity) beginEditor();
    }
    void nativeOpen(Object owner) throws ReflectiveOperationException {
        editorBinding=EditorBinding.of(owner);
        activeClockController=new java.lang.ref.WeakReference<>(owner);
        Object act=editorBinding.activity(owner);
        if(act instanceof android.app.Activity activity) {editorActivity=new java.lang.ref.WeakReference<>(activity);editorContext=activity;}
        if(editorContext==null) return;
        editorOrientation=editorBinding.orientation(owner); editorScreen=ScreenIdentity.current();
        Object focus=editorBinding.focusedScene(owner);scene=String.valueOf(focus);
        wallpaperSourceBinding=new WallpaperSourceBinding(owner.getClass().getClassLoader(),false);
        attach(owner); applyEditorDepth((View)editorBinding.renderer(owner));
    }
    void nativeWallpaperSourceChanged() {
        if (editorActivity.get() != null) queueWallpaperSourceCheck();
    }
    void nativeConfigurationBefore(android.app.Activity activity, android.content.res.Configuration next) {
        if (editorActivity.get() != activity || next == null) return;
        if (next.orientation != editorOrientation || editorScreen != ScreenIdentity.current()) {
            try { checkpointLayout(); }
            catch (Exception failure) { report("Editor layout checkpoint before rotation unavailable", failure); }
        }
    }
    void nativeConfigurationAfter(android.app.Activity activity) {
        if (editorActivity.get() != activity) return;
        Object owner = activeClockController.get();
        if (owner != null) try {
            Object renderer = editorBinding.renderer(owner);
            if (renderer instanceof View clock) clock.postOnAnimation(() -> {
                if (editorActivity.get() == activity) applyEditorDepth(clock);
            });
        } catch (Exception failure) { report("Rotated editor refresh unavailable", failure); }
    }
    boolean nativeBeforeSave(Object mediator) {
        try {
            if(editorContext==null) return true;
            Object activity=Reflect.field(mediator,"b");
            if(activity!=editorActivity.get()) return true;
            if(nativeSaveActive) return false;
            prepareSave(); nativeSaveActive=true; depthSaveHolds++;
            return true;
        }catch(Exception error){savePreparationFailed(error);return false;}
    }
    void nativeSaveCompleted(Object mediator) {
        try {if(Reflect.field(mediator,"b")==editorActivity.get()) commitSavedSettings();}
        catch(Exception error){report("Native Apply completion failed",error);}
    }
    void nativeSaveReturned(Object mediator) {
        try {if(nativeSaveActive && ((android.widget.Button)Reflect.field(mediator,"e")).isEnabled()) releaseSaveHold();}
        catch(Exception ignored){}
    }
    void nativeRendererBound(View clock) { prepareRenderer(clock); applyEditorDepth(clock); }
    boolean nativeManual(View clock) { return manualRenderer.get()==clock || Boolean.TRUE.equals(savedManualRenderers.get(clock)); }
    java.util.List<Float> nativeLocked(Object data) { return stretchLocks.get(data); }
    void nativeCopied(Object before,Object after) { java.util.List<Float> heights=stretchLocks.get(before);if(heights!=null) stretchLocks.put(after,heights); }
    void nativeSelectionChanged() {stretchLocks.clear();manualRenderer.clear();savedManualRenderers.clear();}

    final IdentityLocks stretchLocks = new IdentityLocks();
    final java.util.Set<Integer> forceBlurTypes =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private EditorBinding editorBinding;
    private final Set<ClassLoader> installing = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<ClassLoader> installed = Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<ClassLoader> rendererInstalling =
            Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<ClassLoader> rendererHooks = Collections.newSetFromMap(new WeakHashMap<>());
    private final java.util.Map<Object, Boolean> savedManualRenderers =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final java.util.Map<View, android.os.Bundle> editorRendererProfiles =
            Collections.synchronizedMap(new WeakHashMap<>());
    private java.lang.ref.WeakReference<Object> manualRenderer =
            new java.lang.ref.WeakReference<>(null);
    private final ThreadLocal<Boolean> manualFontCall = ThreadLocal.withInitial(() -> false);
    private android.content.Context editorContext;
    private android.os.Bundle draftSettings;
    private int editorOrientation, editorScreen;
    private boolean foldEditorObserved;
    private boolean layoutCheckpointed;
    private final java.util.Map<String, android.os.Bundle> layoutClocks =
            new java.util.HashMap<>();
    private final java.util.Map<String, android.os.Bundle> editorDrafts = new java.util.HashMap<>();
    private final Set<String> restoredSelections = new java.util.HashSet<>();
    private final SaveSnapshot<android.os.Bundle> saving = new SaveSnapshot<>();
    private final SaveSnapshot<android.os.Bundle> savingWidgets = new SaveSnapshot<>();
    private final SaveSnapshot<android.os.Bundle> savingExtras = new SaveSnapshot<>();

    final EditorExtras editorExtras = new EditorExtras();
    final WidgetEngine widgetEngine = new WidgetEngine(this);
    EditorWidgets editorWidgets;
    private java.lang.ref.WeakReference<NativeClockPanel> panel =
            new java.lang.ref.WeakReference<>(null);
    private String scene = "", processName = "";
    private boolean platformReported;
    private long sessionGeneration;
    private final java.util.Map<View, android.os.Bundle> previewDepthProfiles =
            Collections.synchronizedMap(new WeakHashMap<>());
    
    private DepthAuto depthAuto;
    private boolean legacyDepthReady;
    private String legacyDepthFailure = "Editor depth hooks have not initialized";
    private WallpaperSourceBinding wallpaperSourceBinding;
    private final EditorDepthSources depthSources = new EditorDepthSources();
    private java.lang.ref.WeakReference<Object> editorActivity =
            new java.lang.ref.WeakReference<>(null);
    private boolean sourceSyncing;
    private int depthSaveHolds;
    private boolean nativeSaveActive;
    private final android.os.Handler sourceMain =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.concurrent.atomic.AtomicBoolean sourceCheckQueued =
            new java.util.concurrent.atomic.AtomicBoolean();

    private Boolean objectCaptureSupported;

    boolean depthHardwareAvailable() {
        if (objectCaptureSupported != null) return objectCaptureSupported;
        android.content.Context context = editorContext;
        if (context == null && editorActivity.get() instanceof android.content.Context act)
            context = act;
        if (context != null) {
            Boolean supported = DepthBinding.isObjectCaptureSupported(context);
            if (supported != null) {
                objectCaptureSupported = supported;
                return supported;
            }
        }
        return true;
    }

    boolean depthImageAvailable() {
        syncWallpaperSource();
        return source().known && source().image;
    }

    boolean depthProfilesReset() {
        return source().reset;
    }

    boolean depthProfileInvalid(android.os.Bundle profile) {
        return DepthSourcePolicy.invalidProfile(
                source().known,
                source().image,
                source().reset,
                source().imageKey(),
                profile == null ? "" : profile.getString("depthImageKey", ""));
    }

    private EditorDepthSources.State source() {
        return depthSources.screen(editorScreen);
    }

    private void queueWallpaperSourceCheck() {
        if (sourceCheckQueued.compareAndSet(false, true))
            sourceMain.post(
                    () -> {
                        sourceCheckQueued.set(false);
                        syncWallpaperSource();
                    });
    }

    private void syncWallpaperSource() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            queueWallpaperSourceCheck();
            return;
        }
        if (sourceSyncing || wallpaperSourceBinding == null || editorActivity.get() == null) return;
        if (editorScreen != ScreenIdentity.current()) return;
        EditorDepthSources.State state = source();
        sourceSyncing = true;
        try {
            WallpaperSourceBinding.Source current =
                    wallpaperSourceBinding.read(editorActivity.get());
            if (current == null) return;
            boolean changed = state.source.observe(current.key());
            state.known = true;
            state.image = current.image();
            if (depthSaveHolds == 0 && (changed || !state.image)) {
                if (changed || !state.reset)
                    invalidateDepth(
                            changed
                                    ? "Wallpaper image changed. Enable depth for the new image."
                                    : "Depth requires a still-image wallpaper.");
            }
        } catch (Exception error) {
            // A rebuilding editor can temporarily have no readable source. Suspend drawing,
            // but only a confirmed replacement/type change may reset the user's depth choice.
            state.known = state.image = false;
            DepthEffects.pendingSource(true);
            report("Wallpaper source check failed", error);
        } finally {
            sourceSyncing = false;
        }
    }

    void invalidateDepth(String reason) {
        source().invalidate();
        try {
            Object owner = activeClockController.get();
            Object renderer = owner == null ? null : editorBinding.renderer(owner);
            android.os.Bundle applied =
                    renderer instanceof View view ? editorRendererProfiles.get(view) : null;
            if ((draftSettings != null && draftSettings.getBoolean("depthOwnsManual"))
                    || (applied != null && applied.getBoolean("depthOwnsManual"))) {
                Object stretch =
                        Reflect.call(
                                Reflect.call(editorBinding.data(owner), "getClockFontData"),
                                "getClockStretchData");
                stretchLocks.remove(stretch);
                setManualRenderer(renderer, false);
                Reflect.call(stretch, "setFontHeight", (Object) null);
                Reflect.call(stretch, "setFontHeightRects", (Object) null);
            }
        } catch (Exception error) {
            report("Depth-owned stretch cleanup unavailable", error);
        }
        if (depthAuto != null) depthAuto.reset();
        DepthEffects.clearEditorSubject();
        EditorDepthSources.forScreen(editorDrafts.values(), editorScreen, DepthSafety::disable);
        EditorDepthSources.forScreen(layoutClocks.values(), editorScreen, DepthSafety::disable);
        DepthSafety.disable(draftSettings);
        previewDepthProfiles.values().removeIf(value -> ScreenIdentity.value(value) == editorScreen);
        editorRendererProfiles.values().removeIf(value -> ScreenIdentity.value(value) == editorScreen);
        if (editorWidgets != null) editorWidgets.clearDepth(editorScreen);
        NativeClockPanel current = panel.get();
        if (current != null) current.clearDepth(reason);
        depthStatus(reason);
    }

    void depthDetectionFailed(String message) {
        // A failed scan is not a wallpaper replacement. Keep user choices, suspend the matte.
        DepthEffects.clearEditorSubject();
        DepthEffects.pendingSource(true);
        DepthEffects.detectionFailure(message);
        EditorDepthSources.forScreen(editorDrafts.values(), editorScreen, DepthSafety::clearMask);
        EditorDepthSources.forScreen(layoutClocks.values(), editorScreen, DepthSafety::clearMask);
        DepthSafety.clearMask(draftSettings);
        depthStatus(message);
    }

    boolean depthAvailable(Object clock) {
        return clock != null
                && DepthBinding.provider(clock)
                && (editorBinding == null || !editorBinding.legacy || legacyDepthReady);
    }

    String depthUnavailableReason(Object clock) {
        String issue = DepthBinding.providerIssue(clock);
        if (issue != null) return "Clock provider binding failed: " + issue;
        if (editorBinding != null && editorBinding.legacy && !legacyDepthReady)
            return "Depth editor binding failed: " + legacyDepthFailure;
        return "Depth is not available for this clock";
    }

    private java.lang.ref.WeakReference<Object> activeClockController =
            new java.lang.ref.WeakReference<>(null);

    void stageSettings(android.content.Context context, android.os.Bundle settings) {
        int orientation = layoutOrientation(context);
        EditorDepthSources.State state = source();
        long revision = state.revision;
        syncWallpaperSource();
        if (revision != state.revision || (state.known && !state.image))
            DepthSafety.disable(settings);
        if (state.known) settings.putString("depthImageKey", state.imageKey());
        DepthEffects.sourceMetadata(settings);
        android.content.Context application = context.getApplicationContext();
        if (application != null
                && application.getPackageName().equals("com.samsung.android.app.dressroom"))
            editorContext = application;
        else if (editorContext == null) editorContext = context;
        draftSettings = new android.os.Bundle(settings);
        layoutCheckpointed = false;
        draftSettings.putInt(LayoutOrientation.FIELD, orientation);
        draftSettings.putInt(ScreenIdentity.FIELD, editorScreen);
        if (draftSettings.getInt("wallpaperId", -1) < 0)
            draftSettings.putInt("wallpaperId", SettingsBridge.wallpaperId(context));
        draftSettings.putString("scene", scene);
        editorDrafts.put(
                selectionKey(
                        settings.getInt("type"),
                        settings.getInt("font"),
                        settings.getString("fontPath", "")),
                new android.os.Bundle(draftSettings));
        if (depthAuto != null) depthAuto.settings(draftSettings, widgetDepthOn(context));
    }

    private boolean widgetDepthOn(android.content.Context context) {
        if (editorWidgets == null) return false;
        android.os.Bundle settings = editorWidgets.settings(context);
        return settings.getInt("depth") == 1 && WidgetPreferences.covers(settings);
    }

    void detectDepth() {
        if (depthAuto != null) depthAuto.retry();
    }

    void widgetDepthChanged() {
        Object owner = activeClockController.get();
        if (editorBinding == null || owner == null) return;
        try {
            Object renderer = editorBinding.renderer(owner);
            if (!(renderer instanceof View clock)) return;
            applyEditorDepth(clock);
            DepthEffects.refreshEditorClock(clock);
            long generation = sessionGeneration;
            // Grid reflow can rebuild Samsung's clock/blur owners after the widget callback.
            // Rebind the selected clock, without changing the clock or widget draft settings.
            clock.postOnAnimation(
                    () -> {
                        if (generation != sessionGeneration || activeClockController.get() != owner)
                            return;
                        try {
                            Object current = editorBinding.renderer(owner);
                            if (current instanceof View view && view.isAttachedToWindow())
                                applyEditorDepth(view);
                        } catch (ReflectiveOperationException | RuntimeException error) {
                            report("Clock depth refresh after widget layout unavailable", error);
                        }
                    });
        } catch (ReflectiveOperationException | RuntimeException error) {
            report("Widget depth refresh unavailable", error);
        }
    }

    void depthStatus(String message) {
        NativeClockPanel current = panel.get();
        if (current != null) current.depthStatus(message);
    }

    boolean depthDetected(View clock, byte[] mask, String message) {
        if (clock == null || !clock.isAttachedToWindow()) return false;
        try {
            int type = editorType(clock), font = editorFont(clock, type);
            android.os.Bundle settings = editorSettings(clock.getContext(), type, font);
            if (settings == null) settings = new android.os.Bundle();
            settings.putInt("type", type);
            settings.putInt("font", font);
            settings.putByteArray("depthMask", mask);
            View wallpaper = DepthEffects.wallpaper(clock);
            stageSettings(
                    wallpaper == null ? clock.getContext() : wallpaper.getContext(), settings);
            applyEditorDepth(clock);
            NativeClockPanel current = panel.get();
            if (current != null) current.adoptDepth(settings, message);
            depthStatus(message);
            return true;
        } catch (Exception error) {
            report("Detected depth could not update the editor", error);
            depthStatus("Could not apply subject: " + error.getMessage());
            return false;
        }
    }

    String scene() {
        return scene;
    }

    EditorBinding editorBinding() {
        return editorBinding;
    }

    long generation() {
        return sessionGeneration;
    }

    void registerPanel(NativeClockPanel current) {
        panel = new java.lang.ref.WeakReference<>(current);
    }

    int layoutOrientation(android.content.Context context) {
        int orientation = editorOrientation == 0 ? LayoutOrientation.current(context) : editorOrientation;
        if (editorScreen != ScreenIdentity.current()) switchLayout(orientation);
        return orientation;
    }

    private String selectionKey(int type, int font) {
        return selectionKey(type, font, editorFontPath(type, font));
    }

    private String selectionKey(int type, int font, String fontPath) {
        String key = scene + ":" + editorOrientation + ":" + editorScreen + ":" + type + ":" + font;
        return fontPath == null || fontPath.isEmpty()
                ? key
                : key + ":" + Integer.toHexString(fontPath.hashCode());
    }

    android.os.Bundle editorSettings(android.content.Context context, int type, int font) {
        return editorSettings(context, type, font, editorFontPath(type, font));
    }

    android.os.Bundle editorSettings(
            android.content.Context context, int type, int font, String fontPath) {
        android.os.Bundle staged = editorDrafts.get(selectionKey(type, font, fontPath));
        if (staged != null) return new android.os.Bundle(staged);
        android.os.Bundle query = new android.os.Bundle();
        query.putInt("type", type);
        query.putInt("font", font);
        query.putString("scene", scene);
        query.putInt(LayoutOrientation.FIELD, layoutOrientation(context));
        query.putInt(ScreenIdentity.FIELD, editorScreen);
        if (fontPath != null && !fontPath.isEmpty()) query.putString("fontPath", fontPath);
        android.os.Bundle saved =
                SettingsBridge.read(editorContext == null ? context : editorContext, query);
        // Reading an old profile must not reset the currently active wallpaper or other clocks.
        if (saved != null && depthProfileInvalid(saved)) DepthSafety.disable(saved);
        return saved;
    }



    private void beginEditor() {
        AdvancedPanelDismiss.clear();
        sourceSyncing = false;
        depthSaveHolds = 0;
        nativeSaveActive = false;
        depthSources.clear();
        editorActivity.clear();
        if (depthAuto != null) depthAuto.reset();
        sessionGeneration++;
        draftSettings = null;
        editorContext = null;
        scene = "";
        editorOrientation = editorScreen = 0;
        layoutCheckpointed = false;
        layoutClocks.clear();
        editorDrafts.clear();
        restoredSelections.clear();
        previewDepthProfiles.clear();
        saving.cancel();
        NativeClockPanel previousPanel=panel.get();
        if(previousPanel!=null) previousPanel.releaseOutside();
        panel.clear();
        manualRenderer.clear();
        savedManualRenderers.clear();
        stretchLocks.clear();
        editorRendererProfiles.clear();
        savingWidgets.cancel();
        savingExtras.cancel();
        editorExtras.reset();
        activeClockController.clear();
        if (editorWidgets != null) editorWidgets.reset();
        EditorClockRefresh.cancelAll();
        DateEffects.resetPreviews();
        BlurEffects.resetPreviews();
        DepthEffects.resetEditor();
    }



    private static android.os.Bundle deepFreeze(android.os.Bundle value) { return value == null ? null : value.deepCopy(); }

    private void prepareSave() throws Exception {
        syncSaveLayout();
        NativeClockPanel current = panel.get();
        boolean captured = current != null && current.prepareSave();
        saving.begin(
                freezeClockLayouts(
                        captured
                                ? DepthEffects.freezeMask(draftSettings)
                                : clockSnapshotWithoutPanel()));
        savingWidgets.begin(editorWidgets == null ? null : deepFreeze(editorWidgets.freeze()));
        savingExtras.begin(deepFreeze(editorExtras.freeze()));
    }



    private void appendApply(java.util.ArrayList<android.os.Bundle> operations,String scope,android.os.Bundle snapshot) {
        if(snapshot==null)return;
        java.util.ArrayList<android.os.Bundle> layouts=snapshot.getParcelableArrayList("layoutProfiles",android.os.Bundle.class);
        if(layouts==null)layouts=snapshot.getParcelableArrayList("screenProfiles",android.os.Bundle.class);
        if(layouts!=null){for(android.os.Bundle layout:layouts)appendApply(operations,scope,layout);return;}
        android.os.Bundle saved=snapshot.deepCopy();
        if("extras".equals(scope)) saved=ExtrasPreferences.clean(saved);
        else {
            if("widgets".equals(scope))saved=WidgetPreferences.clean(saved);
            LayoutOrientation.stamp(editorContext,saved);
            saved=SettingsBridge.committedIdentity(saved,screen->SettingsBridge.wallpaperId(editorContext,screen));
            saved=DepthStorage.pack(editorContext,saved);
        }
        android.os.Bundle operation=new android.os.Bundle();operation.putString("scope",scope);operation.putBundle("profile",saved);operations.add(operation);
    }
    private void commitSavedSettings() {
        if(!nativeSaveActive)return;
        nativeSaveActive=false;
        try {
            android.os.Bundle clock=saving.complete(),widgets=savingWidgets.complete(),extras=savingExtras.complete();
            if(editorContext==null)return;
            if(clock!=null)stampSavedSource(clock);
            if(widgets!=null)stampSavedSource(widgets);
            java.util.ArrayList<android.os.Bundle> operations=new java.util.ArrayList<>();
            appendApply(operations,"clock",clock);appendApply(operations,"widgets",widgets);appendApply(operations,"extras",extras);
            if(operations.isEmpty())return;
            android.os.Bundle request=new android.os.Bundle();request.putParcelableArrayList("operations",operations);
            android.os.Bundle result=editorContext.getContentResolver().call(SettingsProvider.URI,"commitBatch",null,request);
            if(result==null||!result.getBoolean("saved"))throw new IllegalStateException("Advanced settings were not saved");
            if(widgets!=null&&editorWidgets!=null)editorWidgets.committed();
            if(extras!=null)editorExtras.committed(extras);
            log(Log.INFO,TAG,"Advanced clock, widget and Extras profiles committed atomically after native Apply");
        }catch(Exception failure){savePreparationFailed(failure);}
        finally {depthSaveHolds=Math.max(0,depthSaveHolds-1);}
    }

    private void savePreparationFailed(Exception error) {
        report("Apply postponed; editor settings were kept", error);
        android.content.Context context = editorContext;
        if (context == null && editorWidgets != null) context = editorWidgets.context();
        if (context != null)
            android.widget.Toast.makeText(
                            context,
                            error.getMessage() == null
                                    ? "Could not prepare settings. Try Apply again."
                                    : error.getMessage(),
                            android.widget.Toast.LENGTH_LONG)
                    .show();
    }

    private void releaseSaveHold() {
        nativeSaveActive = false;
        depthSaveHolds = Math.max(0, depthSaveHolds - 1);
        saving.cancel();
        savingWidgets.cancel();
        savingExtras.cancel();
    }



    private android.os.Bundle clockSnapshotWithoutPanel() {
        Object controller = activeClockController.get();
        if (controller == null) return null;
        try {
            Object data = editorBinding.data(controller);
            int type = ((Number) Reflect.call(data, "getClockType")).intValue();
            int font =
                    ((Number)
                                    Reflect.call(
                                            Reflect.call(data, "getClockFontData"),
                                            "getClockFontType"))
                            .intValue();
            android.os.Bundle snapshot = draftSettings;
            if (snapshot == null
                    || type != snapshot.getInt("type", -1)
                    || font != snapshot.getInt("font", -1))
                snapshot = readSettings(data, editorContext);
            if (snapshot == null
                    || !SettingsScope.sceneMatches(scene, snapshot.getString("scene", "")))
                return null;
            return DepthEffects.freezeMask(snapshot);
        } catch (Exception e) {
            report("Clock profile could not follow native Apply", e);
            return null;
        }
    }

    private android.os.Bundle freezeClockLayouts(android.os.Bundle current) {
        DepthEffects.requireSaveReady(current);
        if (current != null) layoutClocks.put(ScreenIdentity.draft(current), current);
        java.util.ArrayList<android.os.Bundle> layouts = new java.util.ArrayList<>();
        for (android.os.Bundle value : layoutClocks.values()) {
            if (SettingsScope.sceneMatches(scene, value.getString("scene", "")))
                layouts.add(value.deepCopy());
        }
        if (layouts.isEmpty()) return null;
        android.os.Bundle batch = new android.os.Bundle();
        batch.putParcelableArrayList("layoutProfiles", layouts);
        return batch;
    }

    private void checkpointLayout() {
        if (draftSettings != null) {
            android.os.Bundle frozen = DepthEffects.freezeMask(draftSettings);
            layoutClocks.put(ScreenIdentity.draft(frozen), frozen);
            editorDrafts.put(
                    selectionKey(
                            frozen.getInt("type"),
                            frozen.getInt("font"),
                            frozen.getString("fontPath", "")),
                    frozen);
        }
        if (editorWidgets != null) editorWidgets.layoutChanging();
        layoutCheckpointed = true;
    }

    private void syncSaveLayout() {
        try {
            syncWallpaperSource();
            Object owner = activeClockController.get();
            if (owner == null) return;
            switchLayout(editorBinding.orientation(owner));
            if (editorContext != null && editorWidgets != null)
                editorWidgets.settings(editorContext);
        } catch (Exception error) {
            report("Native Apply layout refresh unavailable", error);
        }
    }

    private void stampSavedSource(android.os.Bundle snapshot) {
        syncWallpaperSource();
        depthSources.stamp(snapshot);
    }

    private void switchLayout(int nextOrientation) {
        int nextScreen = ScreenIdentity.current();
        if (nextOrientation == 0
                || (nextOrientation == editorOrientation && nextScreen == editorScreen)) return;
        if (!layoutCheckpointed) checkpointLayout();
        layoutCheckpointed = false;
        editorOrientation = nextOrientation;
        editorScreen = nextScreen;
        sessionGeneration++;
        draftSettings = null;
        manualRenderer.clear();
        stretchLocks.clear();
        savedManualRenderers.clear();
        editorRendererProfiles.clear();
        previewDepthProfiles.clear();
        DateEffects.resetPreviews();
        BlurEffects.resetPreviews();
        DepthEffects.resetEditor();
        if (depthAuto != null) depthAuto.reset();
    }



    void restoreManualData(Object data, android.content.Context context) throws Exception {
        restoreManualData(data, readSettings(data, context));
    }

    private android.os.Bundle readSettings(Object data, android.content.Context context)
            throws Exception {
        Object fontData = Reflect.call(data, "getClockFontData");
        return editorSettings(
                context,
                ((Number) Reflect.call(data, "getClockType")).intValue(),
                ((Number) Reflect.call(fontData, "getClockFontType")).intValue());
    }

    private void restoreManualData(Object data, android.os.Bundle saved) throws Exception {
        if (saved == null
                || !FontAxisSettings.nativeStretch(saved)
                || (!saved.getBoolean("manual") && !saved.getBoolean("depthEnabled"))) return;
        if (!Boolean.TRUE.equals(Reflect.call(data, "getStretchable"))) return;
        Object fontData = Reflect.call(data, "getClockFontData");
        Object stretch = Reflect.call(fontData, "getClockStretchData");
        float[] values = saved.getFloatArray("manualHeights");
        List<Float> heights = new java.util.ArrayList<>();
        if (values != null && values.length > 0) for (float value : values) heights.add(value);
        else {
            @SuppressWarnings("unchecked")
            List<Float> current = (List<Float>) Reflect.call(stretch, "getFontHeight");
            if (current != null) heights.addAll(current);
        }
        if (heights.isEmpty()) return;
        stretchLocks.put(stretch, heights);
        Reflect.call(stretch, "setFontHeight", heights);
    }

    private void restoreShadowData(Object data, android.os.Bundle saved) throws Exception {
        if (saved == null) return;
        Object colors = Reflect.call(data, "getClockColorData");
        Object old = Reflect.call(colors, "getClockShadowStyle");
        Object shadow =
                old.getClass()
                        .getConstructor(float.class, float.class, float.class, int.class)
                        .newInstance(
                                saved.getFloat("radius"),
                                saved.getFloat("dx"),
                                saved.getFloat("dy"),
                                saved.getInt("color"));
        Reflect.call(colors, "setClockShadowStyle", shadow);
    }

    void prepareRenderer(Object renderer) {
        if (!(renderer instanceof View clock)) return;
        rendererHooks.add(renderer.getClass().getClassLoader());
        VariableFonts.configure(this,clock,fontSettings(clock));
        DateEffects.attach(this,clock);
    }

    private Object editorModel(View clock) throws Exception {
        Object owner = activeClockController.get();
        if (owner != null && editorBinding.renderer(owner) == clock) {
            return editorBinding.data(owner);
        }
        return null;
    }

    private int editorType(View clock) throws Exception {
        Object model = editorModel(clock);
        return ((Number) Reflect.call(model == null ? clock : model, "getClockType")).intValue();
    }

    private int editorFont(View clock, int type) throws Exception {
        Object model = editorModel(clock);
        if (model != null)
            return ((Number)
                            Reflect.call(
                                    Reflect.call(model, "getClockFontData"), "getClockFontType"))
                    .intValue();
        return ClockBinding.of(clock).fontId(clock);
    }

    private String editorFontPath(int type, int font) {
        Object owner = activeClockController.get();
        try {
            if (owner != null && editorBinding != null) {
                Object data = editorBinding.data(owner);
                if (((Number) Reflect.call(data, "getClockType")).intValue() == type) {
                    Object fontData = Reflect.call(data, "getClockFontData");
                    if (((Number) Reflect.call(fontData, "getClockFontType")).intValue() == font) {
                        Object path = Reflect.call(fontData, "getClockFontPath");
                        if (path instanceof String) return (String) path;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        android.os.Bundle draft = draftSettings;
        if (draft != null && draft.getInt("type", -1) == type && draft.getInt("font", -1) == font)
            return draft.getString("fontPath", "");
        return "";
    }

    android.os.Bundle fontSettings(View clock) {
        android.os.Bundle draft = draftSettings;
        if (draft != null) {
            try {
                int type = editorType(clock);
                int font = editorFont(clock, type);
                if (draft.getInt("type", -1) == type && draft.getInt("font", -1) == font) {
                    String path = draft.getString("fontPath", "");
                    Object current = ClockBinding.of(clock).font(clock);
                    Object currentPath =
                            current == null ? null : Reflect.call(current, "getFontPath");
                    if (path.isEmpty()
                            || !(currentPath instanceof String)
                            || path.equals(currentPath)) return draft;
                }
            } catch (Exception ignored) {
            }
        }
        android.os.Bundle saved = editorRendererProfiles.get(clock);
        if (saved == null) return null;
        try {
            String path = saved.getString("fontPath", "");
            Object current = ClockBinding.of(clock).font(clock);
            Object currentPath = current == null ? null : Reflect.call(current, "getFontPath");
            if (!path.isEmpty() && currentPath instanceof String && !path.equals(currentPath))
                return null;
        } catch (Exception ignored) {
        }
        return saved;
    }

    private void applyEditorDepth(View clock) {
        if (editorBinding == null || !depthAvailable(clock) || !DepthEffects.isEditorClock(clock))
            return;
        try {
            syncWallpaperSource();
            Object controller = activeClockController.get();
            if (controller != null) {
                Object selected = editorBinding.renderer(controller);
                if (selected != null && selected != clock) return;
                switchLayout(editorBinding.orientation(controller));
            }
            int type = editorType(clock);
            int font = editorFont(clock, type);
            android.os.Bundle settings = draftSettings;
            if (settings == null
                    || settings.getInt("type", -1) != type
                    || settings.getInt("font", -1) != font) {
                android.os.Bundle query = new android.os.Bundle();
                query.putInt("type", type);
                query.putInt("font", font);
                settings = editorSettings(clock.getContext(), type, font);
            }
            if (settings == null) settings = previewDepthProfiles.get(clock);
            android.os.Bundle previous = editorRendererProfiles.get(clock);
            if (settings == null) editorRendererProfiles.remove(clock);
            else editorRendererProfiles.put(clock, settings);
            VariableFonts.configure(this, clock, settings);
            Object selectedModel = editorModel(clock);
            boolean stretchable =
                    selectedModel != null
                            && Boolean.TRUE.equals(Reflect.call(selectedModel, "getStretchable"));
            if (settings != null
                    && FontAxisSettings.nativeStretch(settings)
                    && stretchable
                    && (settings.getBoolean("manual") || settings.getBoolean("depthEnabled"))) {
                setManualRenderer(clock, true);
                Object owner = activeClockController.get();
                if (owner != null) {
                    Object model = editorBinding.data(owner);
                    if (((Number) Reflect.call(model, "getClockType")).intValue() == type)
                        restoreManualData(model, settings);
                }
                if (previous == null
                        || !java.util.Arrays.equals(
                                previous.getFloatArray("manualHeights"),
                                settings.getFloatArray("manualHeights"))) {
                    Object fontData = ClockBinding.of(clock).font(clock);
                    if (fontData != null) {
                        ClockBinding.of(clock).render(clock, fontData);
                        clock.requestLayout();
                        clock.invalidate();
                    }
                }
            } else if (manualRenderer.get() == clock) setManualRenderer(clock, false);
            if (editorWidgets != null) editorWidgets.clockProfile(clock.getContext(), settings);
            boolean widgetDepth = widgetDepthOn(clock.getContext());
            android.os.Bundle sourceProfile = settings;
            if (widgetDepth && sourceProfile == null) {
                sourceProfile = new android.os.Bundle();
                sourceProfile.putInt("type", type);
                sourceProfile.putInt("font", font);
            }
            if (widgetDepth
                    && sourceProfile != null
                    && sourceProfile.getByteArray(DepthMetadata.SOURCE) == null) {
                android.os.Bundle widgets = editorWidgets.settings(clock.getContext());
                DepthMetadata.copy(widgets, sourceProfile);
                if (sourceProfile.getByteArray("depthMask") == null)
                    sourceProfile.putByteArray("depthMask", widgets.getByteArray("depthMask"));
            }
            if (depthAuto == null) depthAuto = new DepthAuto(this);
            depthAuto.bind(clock, sourceProfile, widgetDepth);
            DepthEffects.setEditorState(
                    clock,
                    settings != null && settings.getBoolean("depthEnabled"),
                    settings == null ? null : settings.getByteArray("depthMask"));
            DepthEffects.applyEditor(
                    clock,
                    settings != null && settings.getBoolean("depthEnabled"),
                    settings == null ? null : settings.getByteArray("depthMask"));
            DepthEffects.applyEditorDigits(
                    clock,
                    settings != null && settings.getBoolean("depthEnabled"),
                    settings == null ? null : settings.getByteArray("depthMask"));
            DepthEffects.applyWidgets(
                    clock,
                    settings != null && settings.getBoolean("depthEnabled"),
                    settings == null ? null : settings.getByteArray("depthMask"),
                    true);
        } catch (Exception e) {
            report("Editor depth unavailable", e);
        }
    }

    void setManualRenderer(Object renderer, boolean enabled) {
        if (enabled
                && (renderer == null
                        || !rendererHooks.contains(renderer.getClass().getClassLoader())))
            throw new IllegalStateException(
                    "Independent stretching is unavailable for this renderer");
        manualRenderer = new java.lang.ref.WeakReference<>(enabled ? renderer : null);
        if (renderer != null) savedManualRenderers.put(renderer, enabled);
    }



    @SuppressWarnings("unchecked")




    private void attach(Object controller) throws ReflectiveOperationException {
        Object value = editorBinding.root(controller);
        if (!(value instanceof LinearLayout))
            throw new IllegalStateException("Unexpected clock root");
        LinearLayout root = (LinearLayout) value;
        Object repo = editorBinding.repo(controller);
        Object model = Reflect.call(repo, "getData");
        if (model == null
                || !model.getClass()
                        .getName()
                        .equals(
                                "com.samsung.android.lockscreen.editor.item.domain.entity.item.clock.ClockData")) {
            throw new IllegalStateException("Unexpected clock model");
        }
        NativeClockPanel.attach(root, controller, repo, this);
    }

    private static final java.util.Set<String> noted =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void note(String key, Throwable error) {
        if (noted.size() > 256 || !noted.add(key)) return;
        android.util.Log.w(TAG, key, error);
    }

    void report(String message, Throwable error) {
        android.util.Log.e(TAG,message,error);
    }


}
