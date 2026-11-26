// Native adaptation of BlueOutside / Dressroom-Advanced.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.content.res.XmlResourceParser;
import android.os.Bundle;

/** Reads provider-owned forms and requests matching native RemoteViews layouts. */
final class WidgetProviderForms {
    private record Original(int id,AppWidgetProviderInfo info,Bundle options) {}
    private static final java.util.WeakHashMap<Object,java.util.WeakHashMap<AppWidgetHostView,Original>> previews=new java.util.WeakHashMap<>();
    static void preview(Object manager,AppWidgetHostView host,int columns,int rows){
        preview(manager,host,columns,rows,mask(host));
    }
    static void preview(Object manager,AppWidgetHostView host,int columns,int rows,int supported){
        AppWidgetProviderInfo info=host.getAppWidgetInfo();
        if(info==null||!WidgetFormModel.supported(supported,columns,rows))return;
        previews.computeIfAbsent(manager,k->new java.util.WeakHashMap<>()).computeIfAbsent(host,h->new Original(h.getAppWidgetId(),info.clone(),new Bundle(android.appwidget.AppWidgetManager.getInstance(h.getContext()).getAppWidgetOptions(h.getAppWidgetId()))));
        apply(host,columns,rows,supported);
    }
    static void endPreview(Object manager,boolean committed){
        java.util.WeakHashMap<AppWidgetHostView,Original> originals=previews.remove(manager);
        if(committed||originals==null)return;
        for(var entry:originals.entrySet()){
            AppWidgetHostView host=entry.getKey();Original original=entry.getValue();
            if(host==null)continue;
            try {
            if(host.getAppWidgetId()!=original.id())continue;
            host.setAppWidget(original.id(),original.info().clone());
            Bundle restored=new Bundle(original.options());
            restored.putInt("semWidgetSize",WidgetFormModel.code(original.info().targetCellWidth,original.info().targetCellHeight));
            restored.putInt("semAppWidgetColumnSpan",original.info().targetCellWidth);
            restored.putInt("semAppWidgetRowSpan",original.info().targetCellHeight);
            host.updateAppWidgetOptions(restored);
            } catch(RuntimeException failure) {
                android.util.Log.w(DressroomModule.TAG,"Widget preview rollback unavailable for "+original.id(),failure);
            }
        }
    }

    static int mask(Context context,ComponentName component,int defaultWidth,int defaultHeight){
        if(component==null)return WidgetFormModel.code(defaultWidth,defaultHeight);
        try{
            ActivityInfo receiver=context.getPackageManager().getReceiverInfo(component,PackageManager.GET_META_DATA);
            for(String metadata:new String[]{"samsung.appwidget.monotone.info","com.samsung.android.appwidget.provider","android.appwidget.provider"}){
                try(XmlResourceParser xml=receiver.loadXmlMetaData(context.getPackageManager(),metadata)){
                    if(xml==null)continue;
                    while(xml.next()!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT){
                        if(xml.getEventType()!=org.xmlpull.v1.XmlPullParser.START_TAG)continue;
                        int mask=0;
                        for(int i=0;i<xml.getAttributeCount();i++){
                            String name=xml.getAttributeName(i);
                            if(name.equals("monotoneWidgetSize")||name.equals("widgetSize"))mask|=xml.getAttributeIntValue(i,0);
                        }
                        if((mask&27)!=0)return mask&27;
                        break;
                    }
                }
            }
        }catch(Exception failure){android.util.Log.w(DressroomModule.TAG,"Widget provider forms unavailable: "+component,failure);}
        return WidgetFormModel.code(defaultWidth,defaultHeight);
    }
    static int mask(AppWidgetHostView host){
        AppWidgetProviderInfo info=host.getAppWidgetInfo();
        return info==null?0:mask(host.getContext(),info.provider,info.targetCellWidth,info.targetCellHeight);
    }
    static void refresh(AppWidgetHostView host,int columns,int rows){
        apply(host,columns,rows,mask(host));
    }
    static void apply(AppWidgetHostView host,int columns,int rows,int supported){
        AppWidgetProviderInfo info=host.getAppWidgetInfo();
        if(info==null||!WidgetFormModel.supported(supported,columns,rows))return;
        Bundle options=new Bundle(android.appwidget.AppWidgetManager.getInstance(host.getContext()).getAppWidgetOptions(host.getAppWidgetId()));
        int form=WidgetFormModel.code(columns,rows);
        if(info.targetCellWidth==columns&&info.targetCellHeight==rows&&options.getInt("semWidgetSize")==form&&options.getInt("semAppWidgetColumnSpan")==columns&&options.getInt("semAppWidgetRowSpan")==rows)return;
        // Same provider and appWidgetId: preserve configured location/calendar and app data.
        AppWidgetProviderInfo resized=info.clone();
        resized.targetCellWidth=columns;resized.targetCellHeight=rows;
        host.setAppWidget(host.getAppWidgetId(),resized);
        options.putInt("appWidgetCategory",0x2000);options.putInt("semHostType",2);options.putInt("semWidgetStyle",2);
        options.putInt("semWidgetSize",form);options.putInt("semAppWidgetColumnSpan",columns);options.putInt("semAppWidgetRowSpan",rows);
        host.updateAppWidgetOptions(options);
    }
}
