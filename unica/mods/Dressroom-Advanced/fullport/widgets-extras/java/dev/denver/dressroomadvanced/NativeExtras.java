// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import java.util.Map;
import java.util.WeakHashMap;

public final class NativeExtras {
    private static final Map<ClassLoader,NowBarStackLayers> layers=new WeakHashMap<>();
    private static final Map<ClassLoader,NowBarStackRepair> repairs=new WeakHashMap<>();
    private NativeExtras(){}
    public static Object songBegin(Object coordinator){return MediaFullscreenExtras.nativeSongBegin(coordinator);}
    public static void songEnd(Object previous){MediaFullscreenExtras.nativeSongEnd(previous);}
    public static void songAnimator(android.animation.Animator animation){MediaFullscreenExtras.nativeSongAnimator(animation);}
    public static int pillBounds(View root,int x,int y){return MediaInteraction.bounds(root,x,y);}
    public static int stackDispatch(ViewGroup root,MotionEvent event){return MediaInteraction.stack(root,event);}
    public static void nowBarHost(Object host,Context context){NowBarExtras.nativeHost(host,context);}
    public static boolean keepCharging(Object owner){return NowBarExtras.nativeRemove(owner);}
    public static void chargingFinishBegin(){NowBarExtras.nativeFinish(true);}
    public static void chargingFinishEnd(){NowBarExtras.nativeFinish(false);}
    public static void chargingAttached(View view){NowBarExtras.nativeAttached(view);}
    public static void chargingResources(View view){NowBarExtras.nativeResources(view);}
    public static void artworkRoot(ViewGroup root){MediaFullscreenExtras.nativeRoot(root);}
    public static void artworkHolder(Object holder){MediaFullscreenExtras.nativeHolder(holder);}
    public static void songProgress(View view,float value){MediaFullscreenExtras.nativeSongProgress(view,value);}
    public static void songRandom(View view,float value){MediaFullscreenExtras.nativeSongRandom(view,value);}
    public static void cardReveal(Object card,boolean expanded){MediaFullscreenExtras.nativeCardReveal(card,expanded);}
    public static void artworkExpanded(View big,View overlay){MediaFullscreenExtras.nativeExpanded(big,overlay);}
    public static void artworkCollapsed(Object card,View overlay,View big,View small){MediaFullscreenExtras.nativeCollapsed(card,overlay,big,small);}
    public static void artworkCollapseEnd(View small){MediaFullscreenExtras.nativeCollapseEnd(small);}
    public static void artworkExpansion(Object transaction){MediaFullscreenExtras.nativeExpansion(transaction);}
    public static void artworkExpandEnd(View big){MediaFullscreenExtras.nativeExpandEnd(big);}
    public static float alpha(View view,float original){return MediaBlurHost.nativeAlpha(view,original);}
    private static NowBarStackLayers stack(ViewGroup root)throws ReflectiveOperationException{
        ClassLoader loader=root.getClass().getClassLoader();NowBarStackLayers value=layers.get(loader);
        if(value==null){NowBarStackBinding binding=NowBarStackBinding.of(Class.forName("com.samsung.android.uniform.widget.clock.AbsClockView",false,loader));value=new NowBarStackLayers(DressroomModule.get(),binding);layers.put(loader,value);repairs.put(loader,new NowBarStackRepair(binding));}return value;
    }
    public static float stackScale(ViewGroup root,int rank,float original){try{return stack(root).nativeDensity(root,rank,original,true);}catch(Exception e){return original;}}
    public static float stackOffset(ViewGroup root,int rank,float original){try{return stack(root).nativeDensity(root,rank,original,false);}catch(Exception e){return original;}}
    public static void stackBeforeRefresh(ViewGroup root,java.util.concurrent.CopyOnWriteArrayList<?> incoming){try{stack(root).nativeBeforeRefresh(root);repairs.get(root.getClass().getClassLoader()).beforeRefresh(root,incoming);}catch(Exception e){DressroomModule.note("Stack refresh",e);}}
    public static void stackAfterRefresh(ViewGroup root){try{stack(root).nativeAfterRefresh(root);repairs.get(root.getClass().getClassLoader()).updatePivotsSafely(DressroomModule.get(),root);}catch(Exception e){DressroomModule.note("Stack refresh",e);}}
    public static void stackLayout(ViewGroup root){try{stack(root).nativeLayout(root);repairs.get(root.getClass().getClassLoader()).updatePivotsSafely(DressroomModule.get(),root);}catch(Exception e){DressroomModule.note("Stack layout",e);}}
    public static void stackState(ViewGroup root){try{stack(root).nativeState(root);}catch(Exception e){DressroomModule.note("Stack state",e);}}
    public static void stackDetached(ViewGroup root){try{stack(root).nativeDetach(root);}catch(Exception e){DressroomModule.note("Stack release",e);}}
    public static void stackConfiguration(ViewGroup root){try{stack(root).nativeConfiguration(root);}catch(Exception e){DressroomModule.note("Stack configuration",e);}}
    public static void stackTouchBefore(ViewGroup root,MotionEvent event){try{stack(root).nativeTouchBefore(root,event);}catch(Exception e){DressroomModule.note("Stack touch",e);}}
    public static void stackTouchAfter(ViewGroup root){try{stack(root).nativeTouchAfter(root);}catch(Exception e){DressroomModule.note("Stack touch",e);}}
    public static int stackDepth(ViewGroup root,View card,int original){try{return stack(root).nativeDepth(root,card,original);}catch(Exception e){return original;}}
    public static boolean stackDrawSkip(ViewGroup root,View child){try{return stack(root).nativeDrawSkip(root,child);}catch(Exception ignored){return false;}}
    public static void setAlpha(View view,float alpha){view.setAlpha(MediaBlurHost.nativeAlpha(view,alpha));}
    public static boolean stackWave(View view){return !(view.getParent()!=null&&view.getParent().getClass().getName().contains("NowBarStackLayers$Tail"));}
}
