package io.mesalabs.rezoss.ai;

import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.WeakHashMap;

/** Metadata only: SystemUI remains owner of the notification and RemoteInput send action. */
public final class ShadeReplyEditor {
    private static final String COMMAND="rezoss.shade.invalidate";
    private static final WeakHashMap<EditText,Session> sessions=new WeakHashMap<>();
    private static final class Session {
        final String key,pkg,fp,token=UUID.randomUUID().toString();
        boolean blocked;
        Session(StatusBarNotification sbn,String fingerprint,boolean draft){key=sbn.getKey();pkg=sbn.getPackageName();fp=fingerprint;blocked=draft;}
        boolean matches(StatusBarNotification sbn,String fingerprint){return key.equals(sbn.getKey())&&pkg.equals(sbn.getPackageName())&&fp.equals(fingerprint);}
    }
    private ShadeReplyEditor() {}
    private static Object field(Object object,String name)throws ReflectiveOperationException {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass()){
            try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException next){}
        }
        throw new NoSuchFieldException(name);
    }
    private static StatusBarNotification notification(Object view)throws ReflectiveOperationException {
        Object adapter=field(view,"mRemoteInputEntryAdapter");
        return (StatusBarNotification)field(field(adapter,"entry"),"mSbn");
    }
    private static EditText editor(Object view)throws ReflectiveOperationException{return (EditText)field(view,"mEditText");}
    private static void invalidate(EditText editor,Session session,boolean typed){
        if(session==null)return;
        Bundle data=new Bundle();data.putString(ShadeReplyContract.TOKEN,session.token);data.putBoolean(ShadeReplyContract.BLOCKED,typed);
        InputMethodManager imm=editor.getContext().getSystemService(InputMethodManager.class);
        if(imm!=null)imm.sendAppPrivateCommand(editor,COMMAND,data);
    }
    private static void restart(EditText editor){
        editor.post(()->{if(editor.isFocused()&&editor.isAttachedToWindow()){
            InputMethodManager imm=editor.getContext().getSystemService(InputMethodManager.class);
            if(imm!=null)imm.restartInput(editor);
        }});
    }
    public static void decorate(EditText editor,EditorInfo info,Object view){
        if(info==null)return;
        // Clear our own extras on every connection; never alter ordinary editor options.
        if(info.extras==null)info.extras=new Bundle();
        for(String key:new String[]{ShadeReplyContract.KEY,ShadeReplyContract.PACKAGE,ShadeReplyContract.FINGERPRINT,ShadeReplyContract.TOKEN,ShadeReplyContract.BLOCKED})info.extras.remove(key);
        try{
            if(!"com.android.systemui".equals(editor.getContext().getPackageName())||!editor.isFocused()||!editor.isAttachedToWindow()
                    ||android.provider.Settings.Global.getInt(editor.getContext().getContentResolver(),"rezoss_now_nudge_native",0)!=1){
                invalidate(editor,sessions.remove(editor),false);return;
            }
            StatusBarNotification sbn=notification(view);String fp=ShadeReplyIdentity.notification(sbn.getNotification());
            if(!sbn.getUser().equals(android.os.Process.myUserHandle())||!ShadeReplyContract.valid(sbn.getKey(),sbn.getPackageName(),fp))return;
            Session current=sessions.get(editor);
            if(current==null||!current.matches(sbn,fp)){
                invalidate(editor,current,false);
                current=new Session(sbn,fp,editor.length()!=0);sessions.put(editor,current);
            }
            current.blocked|=editor.length()!=0;
            info.extras.putString(ShadeReplyContract.KEY,current.key);info.extras.putString(ShadeReplyContract.PACKAGE,current.pkg);
            info.extras.putString(ShadeReplyContract.FINGERPRINT,current.fp);info.extras.putString(ShadeReplyContract.TOKEN,current.token);
            info.extras.putBoolean(ShadeReplyContract.BLOCKED,current.blocked);
        }catch(Exception failure){invalidate(editor,sessions.remove(editor),false);}
    }
    public static void focus(EditText editor,boolean focused){
        if(!focused)invalidate(editor,sessions.remove(editor),false);
    }
    public static void changed(Object view){
        try{
            EditText editor=editor(view);Session session=sessions.get(editor);
            if(session!=null&&editor.isFocused()&&editor.length()!=0&&!session.blocked){
                session.blocked=true;invalidate(editor,session,true);restart(editor);
            }
        }catch(Exception ignored){}
    }
    public static void updated(Object view){
        try{
            EditText editor=editor(view);Session session=sessions.get(editor);if(session==null)return;
            StatusBarNotification sbn=notification(view);String fp=ShadeReplyIdentity.notification(sbn.getNotification());
            if(!session.matches(sbn,fp)){invalidate(editor,sessions.remove(editor),false);restart(editor);}
        }catch(Exception ignored){detached(view);}
    }
    public static void detached(Object view){
        try{EditText editor=editor(view);invalidate(editor,sessions.remove(editor),false);}catch(Exception ignored){}
    }
}
