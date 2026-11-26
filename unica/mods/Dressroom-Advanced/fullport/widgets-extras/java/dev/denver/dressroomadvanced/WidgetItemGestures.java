// Native adaptation of BlueOutside / Dressroom-Advanced.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.app.Activity;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import java.util.List;

/** Edits Samsung GridItem StateFlow, which getModuleInfo serializes on normal Apply. */
final class WidgetItemGestures {
    private Object holder,original,grid;
    private View root;
    private MotionEvent down;
    private float startX,startY,span;
    private int[] initial;
    private boolean changed,pinching,vertical;
    private boolean replaying,passThrough;
    private int columns,rows,supportedForms;
    void clear(){holder=original=grid=null;root=null;initial=null;span=0;changed=pinching=passThrough=false;if(down!=null){down.recycle();down=null;}}
    boolean replaying(){return replaying;}
    static Object value(Object holder)throws ReflectiveOperationException{return Reflect.call(Reflect.call(holder,"getItemFlow"),"getValue");}
    private static int number(Object item,String getter)throws ReflectiveOperationException{return ((Number)Reflect.call(item,getter)).intValue();}
    static List<?> holders(Object grid)throws ReflectiveOperationException{
        Object id=Reflect.call(Reflect.field(grid,"containerManager"),"getContainerId");
        if(id==null)return java.util.Collections.emptyList();
        Object container=Reflect.call(Reflect.call(grid,"getGridManager"),"b",id);
        return container==null?java.util.Collections.emptyList():(List<?>)Reflect.call(container,"f");
    }
    private int[] cells(Object item)throws ReflectiveOperationException{
        return new int[]{number(item,vertical?"getSecondaryX":"getX"),number(item,vertical?"getSecondaryY":"getY"),number(item,"getGridWidth"),number(item,"getGridHeight")};
    }
    private static float span(MotionEvent e){return (float)Math.hypot(e.getX(0)-e.getX(1),e.getY(0)-e.getY(1));}
    private static float[] local(View view,float x,float y){
        Matrix global=new Matrix(),inverse=new Matrix();view.transformMatrixToGlobal(global);
        int[] origin=new int[2];view.getRootView().getLocationOnScreen(origin);
        float[] result={x-origin[0],y-origin[1]};if(!global.invert(inverse))return null;inverse.mapPoints(result);return result;
    }
    private void update(int[] next)throws ReflectiveOperationException{
        for(Object other:holders(grid))if(other!=holder&&WidgetCellBounds.overlaps(next,cells(value(other))))return;
        Object current=value(holder);int[] now=cells(current);if(java.util.Arrays.equals(now,next))return;
        if((next[2]!=now[2]||next[3]!=now[3])&&!WidgetFormModel.supported(supportedForms,next[2],next[3]))return;
        Object updated=WidgetFormModel.copy(current,next,vertical);
        Reflect.call(holder,"updateGridItem",updated);changed=true;
    }
    int touch(Activity activity,MotionEvent event,EditorWidgets editor)throws Exception{
        if(replaying)return -1;
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            clear();if(editor.gestureBoundary(event.getRawX(),event.getRawY()))return -1;
            boolean custom=editor.settings(activity).getBoolean("layout");
            Object candidateGrid=editor.gestureGrid();
            for(Object candidate:holders(candidateGrid)){
                View view=(View)Reflect.call(candidate,"getRoot");Rect bounds=new Rect();
                if(!view.isShown()||!ScreenBounds.visible(view,bounds)||!bounds.contains(Math.round(event.getRawX()),Math.round(event.getRawY())))continue;
                if(!custom){passThrough=true;return -2;}
                holder=candidate;original=value(candidate);grid=candidateGrid;root=view;supportedForms=WidgetFormControls.mask(candidate);
                Object sizes=Reflect.call(grid,"getWidgetSizeManager");
                columns=number(sizes,"getColumn");rows=number(sizes,"getRow");
                vertical=(Boolean)Reflect.call(Reflect.field(sizes,"widgetAlignType"),"isVertical");
                initial=cells(original);down=MotionEvent.obtain(event);startX=event.getRawX();startY=event.getRawY();return 1;
            }
            return -1;
        }
        if(passThrough){if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)passThrough=false;return -2;}
        if(holder==null)return -1;
        if(action==MotionEvent.ACTION_POINTER_DOWN&&event.getPointerCount()==2){
            span=span(event);initial=cells(value(holder));pinching=true;return 1;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if(pinching&&event.getPointerCount()>=2&&span>1){
                float factor=span(event)/span;
                int w=Math.round(initial[2]*factor),h=Math.round(initial[3]*factor);
                int[] form=WidgetFormModel.nearest(supportedForms,w,h,columns,rows);
                if(form!=null)update(WidgetCellBounds.fit(initial[0],initial[1],form[0],form[1],columns,rows));
            }else if(!pinching&&Math.hypot(event.getRawX()-startX,event.getRawY()-startY)>ViewConfiguration.get(activity).getScaledTouchSlop()){
                float[] p=local(root,event.getRawX(),event.getRawY()),start=local(root,startX,startY);
                if(p!=null&&start!=null&&root.getWidth()>0&&root.getHeight()>0){
                    float cellWidth=(float)root.getWidth()/Math.max(1,initial[2]),cellHeight=(float)root.getHeight()/Math.max(1,initial[3]);
                    int x=initial[0]+Math.round((p[0]-start[0])/cellWidth),y=initial[1]+Math.round((p[1]-start[1])/cellHeight);
                    update(WidgetCellBounds.fit(x,y,initial[2],initial[3],columns,rows));
                }
            }
            return 1;
        }
        if(action==MotionEvent.ACTION_CANCEL){Reflect.call(holder,"updateGridItem",original);clear();return 1;}
        if(action==MotionEvent.ACTION_UP){
            try{
                if(changed){editor.gestureItemChanged(grid);return 1;}
                if(!pinching&&down!=null){replaying=true;activity.dispatchTouchEvent(down);return activity.dispatchTouchEvent(event)?1:0;}
                return 1;
            }finally{replaying=false;clear();}
        }
        return 1;
    }
}
