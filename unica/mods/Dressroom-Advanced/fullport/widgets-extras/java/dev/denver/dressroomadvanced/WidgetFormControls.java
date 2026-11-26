// Native adaptation of BlueOutside / Dressroom-Advanced.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.app.AlertDialog;
import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Explicit independent width/height forms; never stretch an unsupported provider. */
final class WidgetFormControls {
    static void add(Context context,EditorWidgets editor,LinearLayout parent){
        TextView action=new TextView(context);action.setText("Individual widget sizes");
        PanelRows.action(action,"Individual widget sizes","Choose each provider's supported layout");
        action.setOnClickListener(v->show(context,editor));parent.addView(action);
    }
    static int number(Object item,String getter)throws ReflectiveOperationException{return ((Number)Reflect.call(item,getter)).intValue();}
    static int[] cells(Object item,boolean vertical)throws ReflectiveOperationException{return new int[]{number(item,vertical?"getSecondaryX":"getX"),number(item,vertical?"getSecondaryY":"getY"),number(item,"getGridWidth"),number(item,"getGridHeight")};}
    static AppWidgetHostView host(Object holder)throws ReflectiveOperationException{
        for(AppWidgetHostView host:WidgetHostSizing.inView((View)Reflect.call(holder,"getRoot")).values())return host;
        return null;
    }
    static int mask(Object holder)throws ReflectiveOperationException{
        AppWidgetHostView host=host(holder);if(host!=null)return WidgetProviderForms.mask(host);
        Object item=WidgetItemGestures.value(holder);
        return WidgetFormModel.code(number(item,"getGridWidth"),number(item,"getGridHeight"));
    }
    static void show(Context context,EditorWidgets editor){
        try{
            if(!editor.settings(context).getBoolean("layout"))throw new IllegalStateException("Enable Custom grid first.");
            Object grid=editor.gestureGrid();List<?> holders=WidgetItemGestures.holders(grid);
            ArrayList<String> labels=new ArrayList<>();
            for(Object holder:holders){
                AppWidgetHostView host=host(holder);Object item=WidgetItemGestures.value(holder);
                String title=host!=null&&host.getAppWidgetInfo()!=null?host.getAppWidgetInfo().loadLabel(context.getPackageManager()):"Widget";
                labels.add(title+"  "+number(item,"getGridWidth")+" × "+number(item,"getGridHeight"));
            }
            AlertDialog dialog=new AlertDialog.Builder(context).setTitle("Individual widget sizes").setItems(labels.toArray(new String[0]),(d,i)->choose(context,editor,grid,holders.get(i))).setNegativeButton(android.R.string.cancel,null).create();
            dialog.show();AdvancedPanelDismiss.protectCurrent(dialog);
        }catch(Exception failure){android.widget.Toast.makeText(context,failure.getMessage(),android.widget.Toast.LENGTH_LONG).show();}
    }
    private static void choose(Context context,EditorWidgets editor,Object grid,Object holder){
        try{
            int supported=mask(holder);ArrayList<int[]> forms=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
            for(int[] f:WidgetFormModel.FORMS)if((supported&f[2])!=0){forms.add(f);labels.add(f[0]+" × "+f[1]);}
            if(forms.isEmpty())throw new IllegalStateException("This widget does not advertise resizable forms.");
            AlertDialog dialog=new AlertDialog.Builder(context).setTitle("Provider layout").setItems(labels.toArray(new String[0]),(d,i)->{
                try{resize(editor,grid,holder,forms.get(i)[0],forms.get(i)[1]);}
                catch(Exception failure){android.widget.Toast.makeText(context,failure.getMessage(),android.widget.Toast.LENGTH_LONG).show();}
            }).setNegativeButton(android.R.string.cancel,null).create();dialog.show();AdvancedPanelDismiss.protectCurrent(dialog);
        }catch(Exception failure){android.widget.Toast.makeText(context,failure.getMessage(),android.widget.Toast.LENGTH_LONG).show();}
    }
    static void resize(EditorWidgets editor,Object grid,Object holder,int width,int height)throws Exception{
        if(!WidgetFormModel.supported(mask(holder),width,height))throw new IllegalArgumentException("This provider does not supply that layout.");
        Object manager=Reflect.call(grid,"getWidgetSizeManager");boolean vertical=(Boolean)Reflect.call(Reflect.field(manager,"widgetAlignType"),"isVertical");
        int columns=number(manager,"getColumn"),rows=number(manager,"getRow");
        if(width>columns||height>rows)throw new IllegalArgumentException("Increase the grid to fit this widget.");
        Object current=WidgetItemGestures.value(holder);int[] original=cells(current,vertical);
        int[] next=WidgetCellBounds.fit(original[0],original[1],width,height,columns,rows);
        for(Object other:WidgetItemGestures.holders(grid))if(other!=holder&&WidgetCellBounds.overlaps(next,cells(WidgetItemGestures.value(other),vertical)))throw new IllegalArgumentException("Move nearby widgets to make room first.");
        Reflect.call(holder,"updateGridItem",WidgetFormModel.copy(current,next,vertical));
        try{editor.gestureItemChanged(grid);}catch(Exception error){Reflect.call(holder,"updateGridItem",current);throw error;}
    }
}
