// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Direct calls inserted at the exact Samsung method sites by static patches. */
public final class NativeWidgets {
    private static DressroomModule module;
    private static NativeRuntimeWidgets runtime;
    private NativeWidgets() {}
    static void bind(DressroomModule owner) {
        module = owner;
        runtime = new NativeRuntimeWidgets(owner);
    }
    private static DressroomModule owner() {
        if (module == null) DressroomModule.get();
        return module;
    }
    public static void toolbar(Object toolbar) {
        DressroomModule m=owner();
        if(m.editorWidgets!=null)try{m.editorWidgets.addMenu(toolbar,toolbar.getClass().getClassLoader());}
        catch(Exception e){m.report("Widget toolbar",e);}
    }
    public static void create(Object item) {
        DressroomModule m=owner();if(m.editorWidgets!=null)try{m.editorWidgets.nativeCreate(item);}catch(Exception e){m.report("Widget preview",e);}
    }
    public static void created(Object item,View root) {DressroomModule m=owner();if(m.editorWidgets!=null)m.editorWidgets.nativeCreated(item,root);}
    public static void restore(Object store,Object model,Object profile) {
        DressroomModule m=owner();if(m.editorWidgets!=null)try{m.editorWidgets.nativeRestore(store,model,profile);}catch(Exception e){m.report("Widget restore",e);}
    }
    public static void restoreModel(Object store,Object profile,Object model) {restore(store,model,profile);}
    public static void previewEnd(Object manager,boolean committed){owner().widgetEngine.nativePreviewEnd(manager,committed);}
    public static void preview(Object manager,android.os.Bundle draft){owner().widgetEngine.nativePreview(manager,draft);}
    public static int columnOverride(Object manager){return owner().widgetEngine.nativeDimension(manager,-1,true);}
    public static int rowOverride(Object manager){return owner().widgetEngine.nativeDimension(manager,-1,false);}
    public static int biggerOverride(Object manager){return owner().widgetEngine.nativeBigger(manager,false)?1:-1;}
    public static void gridMetrics(Object grid,Object align) {
        DressroomModule m=owner();if(m.editorWidgets!=null)try{m.editorWidgets.nativeGridMetrics(grid,align);}catch(Exception e){m.report("Grid metrics",e);}
    }
    public static void cellMetrics(Object source,Object grid,Object size,Object profile) {
        DressroomModule m=owner();if(m.editorWidgets!=null)try{m.editorWidgets.nativeCellMetrics(source,grid,size,profile);}catch(Exception e){m.report("Widget cells",e);}
    }
    public static void placement(Object model,Object assist,Object profile,Object screen) {
        DressroomModule m=owner();if(m.editorWidgets!=null)try{m.editorWidgets.nativePlacement(model,assist,profile,screen);}catch(Exception e){m.report("Widget placement",e);}
    }
    public static boolean editable(Object model,boolean original) {
        DressroomModule m=owner();if(m.editorWidgets!=null)try{return m.editorWidgets.nativeEditable(model,original);}catch(Exception ignored){}return original;
    }
    public static void gridBound(Object grid,View root) {owner().widgetEngine.nativeGrid(grid,root);}
    public static int column(Object manager,int original){return owner().widgetEngine.nativeDimension(manager,original,true);}
    public static int row(Object manager,int original){return owner().widgetEngine.nativeDimension(manager,original,false);}
    public static boolean bigger(Object manager,boolean original){return owner().widgetEngine.nativeBigger(manager,original);}
    public static void sizeInitialized(Object manager){owner().widgetEngine.nativeInit(manager);}
    public static Object metricScope(Object manager){return owner().widgetEngine.nativeMetricsBegin(manager);}
    public static void metricScopeEnd(Object manager,Object token,boolean success){owner().widgetEngine.nativeMetricsEnd(manager,token,success);}
    public static Object arrangementBegin(Object requested){return owner().widgetEngine.arrangementBegin(requested);}
    public static void arrangementEnd(Object previous){owner().widgetEngine.arrangementEnd(previous);}
    public static boolean layoutRefresh(Object layouts,Object requested){return owner().widgetEngine.nativeRefresh(layouts,requested);}
    public static void factoryCreated(Object factory,Object result){owner().widgetEngine.nativeCreated(factory,result);}
    public static void metricsBegin(){owner().widgetEngine.beginMetrics();}
    public static void metricsEnd(){owner().widgetEngine.endMetrics();}
    public static void controllerChanged(Object controller){owner();runtime.refresh(controller,true);}
    public static void controllerRelease(Object controller){owner();runtime.clear(controller);}
    public static void profileChanged(){owner();runtime.refreshAll(true);}
    public static boolean allowGravity(View root){return !WidgetPlacement.isCustomPlacement(root);}
    public static boolean controllerGravity(Object controller){
        try{return allowGravity((View)Reflect.field(controller,"container"));}catch(Exception ignored){return true;}
    }
    public static void reflow(Object grid){
        try{WidgetEngine e=owner().widgetEngine;Object m=Reflect.call(grid,"getWidgetSizeManager");if(e.main(m)&&e.settings((Context)Reflect.field(m,"context")).getBoolean("layout"))e.reflow(grid);}catch(Exception error){owner().report("Widget reflow",error);}
    }

    public static int rotateBefore(Object rotation,Object align){try{return owner().editorWidgets==null||owner().editorWidgets.nativeRotateBefore(rotation,align)?-1:0;}catch(Exception e){owner().report("Widget rotation",e);return 0;}}
    public static boolean rotateResult(boolean original){return owner().editorWidgets==null?original:owner().editorWidgets.nativeRotateAfter(original);}
    public static Object trimmed(Object policy){try{return owner().editorWidgets==null?null:owner().editorWidgets.nativeTrimmed(policy);}catch(Exception e){return null;}}
    public static int touch(android.app.Activity activity,android.view.MotionEvent event){try{if(owner().nativeAdvancedShown(activity))return -1;return owner().editorWidgets==null?-1:owner().editorWidgets.nativeTouch(activity,event);}catch(Exception e){owner().report("Widget drag",e);return -1;}}

    public static boolean pickerTablet(Object device,Object mode){
        boolean stock=false;
        try{
            stock=(Boolean)Reflect.call(device,"isTablet");
            EditorWidgets editor=owner().editorWidgets;
            if(editor==null||!editor.active()||editor.context()==null)return stock;
            android.os.Bundle draft=editor.settings(editor.context());
            return WidgetFormModel.tallPicker(stock,(Boolean)Reflect.call(mode,"isLockScreen"),draft.getBoolean("layout"),draft.getInt("rows",1));
        }catch(Exception failure){owner().report("Tall widget picker eligibility",failure);return stock;}
    }
}
