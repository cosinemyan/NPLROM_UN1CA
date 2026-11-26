/* Native adaptation of BlueOutside / Dressroom Advanced. See LICENSE.upstream. */
package dev.denver.dressroomadvanced;
import android.graphics.Rect;
import android.view.*;
import java.lang.ref.WeakReference;
import java.util.*;
final class MediaInteraction {
    private static final Map<View,Target> targets=new WeakHashMap<>();
    private static final Set<View> rejected=Collections.newSetFromMap(new WeakHashMap<>());
    private static final class Target {
        final WeakReference<Object> holder;final WeakReference<View> title;boolean outside;float x,y;
        Target(Object holder,View title){this.holder=new WeakReference<>(holder);this.title=new WeakReference<>(title);}
    }
    static void bind(Object holder,View root)throws ReflectiveOperationException {targets.put(root,new Target(holder,(View)Reflect.field(holder,"c0")));}
    static int artwork(View root,MotionEvent event) {
        Target target=targets.get(root);if(target==null||event==null||!RuntimeExtras.enabled(root.getContext(),"fullscreenArt"))return -1;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){View title=target.title.get();Rect bounds=new Rect();target.outside=title==null||!title.getGlobalVisibleRect(bounds)||!bounds.contains((int)event.getRawX(),(int)event.getRawY());target.x=event.getX();target.y=event.getY();}
        if(!target.outside)return -1;
        if(action==MotionEvent.ACTION_UP){target.outside=false;float dx=event.getX()-target.x,dy=event.getY()-target.y;int slop=ViewConfiguration.get(root.getContext()).getScaledTouchSlop();Object holder=target.holder.get();
            if(holder!=null&&dx*dx+dy*dy<=slop*slop)try{if(!Boolean.TRUE.equals(Reflect.field(holder,"T0")))Reflect.call(Reflect.field(holder,"J"),"m");}catch(Exception failure){DressroomModule.note("Artwork outside-close",failure);}
        }else if(action==MotionEvent.ACTION_CANCEL)target.outside=false;
        return 1;
    }
    static int bounds(View root,int x,int y) {
        if(!(root instanceof ViewGroup group)||!root.getClass().getName().equals("com.samsung.android.app.facewidget.utils.NowBarCardStackView"))return -1;
        try{Boolean value=PillTouchBounds.contains(group,x,y);return value==null?-1:value?1:0;}catch(Exception ignored){return -1;}
    }
    static int stack(ViewGroup root,MotionEvent event) {
        if(event==null)return -1;int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){rejected.remove(root);if(bounds(root,(int)event.getRawX(),(int)event.getRawY())==0)rejected.add(root);}
        boolean ignore=rejected.contains(root);if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)rejected.remove(root);
        return ignore?0:-1;
    }
}
