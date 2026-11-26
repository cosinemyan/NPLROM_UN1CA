package io.mesalabs.rezoss.ai;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Supplies inference to Samsung's classifier and MT5 seams, retaining its parser. */
public final class SamsungRecallInference {
    private static final String ENGINE="com.samsung.android.aioskernelservice";
    private SamsungRecallInference(){}
    public static String classify(String input){
        RecallSession.clear();
        try {
            Context context=(Context)Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if(context==null||Settings.Global.getInt(context.getContentResolver(),"rezoss_aios_v73_backend",0)!=1||
                Settings.Global.getInt(context.getContentResolver(),"rezoss_now_nudge_context_actions",0)!=1)return null;
            if(input==null||input.isEmpty()||input.length()>12000||Looper.myLooper()==Looper.getMainLooper())return "Unsafe";
            String output=request(context,input);
            RecallPrediction prediction=RecallPrediction.parse(input,output);
            if(prediction==null){Log.i("RezossRecallInference","classification invalid_output outputPresent="+(output!=null));return "Unsafe";}
            RecallSession.put(input,prediction,SystemClock.elapsedRealtime()+2000);
            Log.i("RezossRecallInference","classification="+prediction.action);
            return prediction.action;
        }catch(Exception|LinkageError failure){Log.i("RezossRecallInference","classification unavailable");return "Unsafe";}
    }
    public static String parameters(String prompt){return RecallSession.consume(prompt,SystemClock.elapsedRealtime());}
    private static String request(Context context,String input)throws Exception {
        PackageManager pm=context.getPackageManager();
        // Both APKs are signed with the ROM platform key during packaging.
        if(pm.checkSignatures(context.getPackageName(),ENGINE)!=PackageManager.SIGNATURE_MATCH)return null;
        long deadline=SystemClock.elapsedRealtime()+8000;
        CountDownLatch done=new CountDownLatch(1);
        AtomicReference<String> value=new AtomicReference<>();
        AtomicReference<Messenger> remote=new AtomicReference<>();
        AtomicBoolean finished=new AtomicBoolean();
        HandlerThread thread=new HandlerThread("SamsungRecallIPC");thread.start();
        Messenger callback=new Messenger(new Handler(thread.getLooper()){
            @Override public void handleMessage(Message message){
                synchronized(remote){
                    if(finished.get()||SystemClock.elapsedRealtime()>=deadline)return;
                    if(message.what==1&&"generated".equals(message.getData().getString("status")))value.set(message.getData().getString("content"));
                    finished.set(true);done.countDown();
                }
            }
        });
        ServiceConnection connection=new ServiceConnection(){
            public void onServiceConnected(ComponentName name,IBinder binder){
                synchronized(remote){
                    if(finished.get())return;
                    Messenger server=new Messenger(binder);remote.set(server);
                    Message message=Message.obtain(null,1);Bundle data=new Bundle();
                    data.putString("task","recall");data.putString("input",input);data.putLong("deadline",deadline-200);
                    message.setData(data);message.replyTo=callback;
                    try{server.send(message);}catch(RemoteException failure){done.countDown();}
                }
            }
            public void onServiceDisconnected(ComponentName name){done.countDown();}
            public void onNullBinding(ComponentName name){done.countDown();}
            public void onBindingDied(ComponentName name){done.countDown();}
        };
        boolean bound=false;long identity=Binder.clearCallingIdentity();
        try{
            bound=context.bindService(new Intent().setClassName(ENGINE,"io.mesalabs.rezoss.nudge.LocalInferenceService"),connection,Context.BIND_AUTO_CREATE);
            if(!bound)return null;
            if(!done.await(Math.max(1,deadline-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS))return null;
            return value.get();
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();return null;}
        finally{
            synchronized(remote){
                boolean completed=finished.getAndSet(true);
                if(!completed&&remote.get()!=null){Message cancel=Message.obtain(null,2);cancel.replyTo=callback;try{remote.get().send(cancel);}catch(RemoteException ignored){}}
            }
            if(bound)try{context.unbindService(connection);}catch(IllegalArgumentException ignored){}
            Binder.restoreCallingIdentity(identity);thread.quitSafely();
        }
    }
}
