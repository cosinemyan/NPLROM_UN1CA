/* Native callsites for Dressroom Advanced by BlueOutside. See LICENSE.upstream. */
package dev.denver.dressroomadvanced;
import android.app.Activity;
import android.view.View;
import java.util.List;
public final class NativeEditor {
    private NativeEditor() {}
    public static void touch(Activity activity,android.view.MotionEvent event) { DressroomModule.get().nativeOutsideTouch(activity,event); }
    public static boolean floatingTouch(View root,android.view.MotionEvent event) { return DressroomModule.get().nativeOutsideTouch(root,event); }
    public static void session(Activity activity) { DressroomModule.get().nativeSession(activity); }
    public static void close(Activity activity) { DressroomModule.get().nativeClose(activity); }
    public static void opened(boolean opened,Object controller) {
        if (!opened) return;
        try { DressroomModule.get().nativeOpen(controller); }
        catch(Exception failure) { DressroomModule.note("Advanced clock panel",failure); }
    }
    public static void bound(Object helper) {
        try { Object clock=Reflect.field(helper,"a"); if(clock instanceof View view) DressroomModule.get().nativeRendererBound(view); }
        catch(Exception failure) { DressroomModule.note("Clock binding",failure); }
    }
    public static void sourceChanged() { DressroomModule.get().nativeWallpaperSourceChanged(); }
    public static void configurationBefore(Activity activity, android.content.res.Configuration next) {
        DressroomModule.get().nativeConfigurationBefore(activity, next);
    }
    public static void configurationAfter(Activity activity) {
        DressroomModule.get().nativeConfigurationAfter(activity);
    }
    public static boolean beforeSave(Object mediator) { return DressroomModule.get().nativeBeforeSave(mediator); }
    public static void completed(Object mediator) { DressroomModule.get().nativeSaveCompleted(mediator); }
    public static void returned(Object mediator) { DressroomModule.get().nativeSaveReturned(mediator); }
    public static int blurModel(Object model,boolean support) {
        try {int type=((Number)Reflect.call(model,"getClockType")).intValue();if(!DressroomModule.get().forceBlurTypes.contains(type))return -1;return support||Boolean.TRUE.equals(Reflect.call(model,"getBlurEnabled"))?1:0;}catch(Exception ignored){return -1;}
    }
    public static boolean blurHelper(Object clock,boolean enable,boolean text) {
        if(!(clock instanceof View view))return false;
        try {if(!DressroomModule.get().forceBlurTypes.contains(((Number)Reflect.call(clock,"getClockType")).intValue()))return false;
            if(text)Reflect.call(clock,"setClockTextBlur");else Reflect.call(clock,"setBlurEffectEnabled",enable);return true;
        }catch(Exception ignored){return false;}
    }
    public static List<Float> heights(Object stretch,List<Float> original) {
        List<Float> locked=DressroomModule.get().nativeLocked(stretch); return locked==null?original:locked;
    }
    public static boolean locked(Object stretch) { return DressroomModule.get().nativeLocked(stretch)!=null; }
    public static void copied(Object source,Object copy) { DressroomModule.get().nativeCopied(source,copy); }
    public static void selection(int before,int after) { if(before!=after) DressroomModule.get().nativeSelectionChanged(); }
}
