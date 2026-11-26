package io.mesalabs.rezoss.callscreen;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.ContactsContract;
import android.util.Log;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Phone 18.1.00.75 adapter. No global caller state, call-log lookup or name logging. */
public final class CallerIdentity {
    private static final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"caller-lookup-timeout");t.setDaemon(true);return t;});
    private static Object field(Object o,String name)throws Exception{
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);
    }
    public static List<String> decorate(List<String> messages,Object session){
        try{
            if(session==null||!session.getClass().getName().equals("zd.x"))return messages;
            Context context=(Context)field(session,"a");
            Object listener=field(session,"j");
            // These fields are pinned to the exact APK; no heuristic reflection.
            if(listener==null||!listener.getClass().getName().equals("bo.h"))return messages;
            Object call=field(listener,"q");
            if(call==null)return messages;
            Class<?> model=Class.forName("l9.n",false,session.getClass().getClassLoader());
            String number=(String)model.getMethod("b").invoke(call);
            if(number==null||number.isEmpty()||number.length()>128)return messages;
            CancellationSignal cancellation=new CancellationSignal();
            ScheduledFuture<?> timeout=timer.schedule(cancellation::cancel,800,TimeUnit.MILLISECONDS);
            String name="";
            try(Cursor cursor=context.getContentResolver().query(
                    Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,Uri.encode(number)),
                    new String[]{ContactsContract.PhoneLookup._ID,ContactsContract.PhoneLookup.DISPLAY_NAME},
                    null,null,null,cancellation)){
                if(cursor==null)return messages; // Lookup failure is not proof of an unsaved number.
                if(cursor.moveToFirst()){
                    String value=cursor.getString(1);
                    // Preserve the exact saved name; unsupported names use Samsung's original path.
                    if(value==null||value.trim().isEmpty()||value.length()>64||value.indexOf('"')>=0||value.indexOf('\\')>=0||value.contains("<|"))return messages;
                    for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))return messages;
                    name=value;
                }
            }finally{timeout.cancel(false);}
            JSONObject identity=new JSONObject().put("saved",!name.isEmpty()).put("name",name);
            ArrayList<String> result=new ArrayList<>(messages.size()+1);
            result.add("REZOSS_CALLER_CONTEXT_V1:"+identity.toString());result.addAll(messages);
            Log.i("RezossCallerIdentity","contactLookup="+(name.isEmpty()?"unsaved":"saved"));
            return result;
        }catch(Exception e){
            Log.w("RezossCallerIdentity","contact_lookup_unavailable "+e.getClass().getSimpleName());
            return messages;
        }
    }
    private CallerIdentity(){}
}
