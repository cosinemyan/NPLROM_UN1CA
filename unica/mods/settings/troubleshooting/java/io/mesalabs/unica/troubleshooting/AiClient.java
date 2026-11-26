/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.troubleshooting;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import java.io.IOException;
import java.util.concurrent.*;
/** Uses the signed ROM inference service; never reads API keys or falls back to another provider. */
final class AiClient implements AutoCloseable {
    private final Context context;private final Handler main=new Handler(Looper.getMainLooper());
    private Messenger remote;private boolean bound,closed;private volatile CompletableFuture<Bundle> pending;private volatile int expected;
    private final CompletableFuture<Void> connected=new CompletableFuture<>();
    private final Messenger callback=new Messenger(new Handler(Looper.getMainLooper()){
        @Override public void handleMessage(Message m){if(pending!=null&&(m.what==expected||m.getData().containsKey("status")))pending.complete(m.getData());}
    });
    private final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName n,IBinder b){remote=new Messenger(b);connected.complete(null);}
        public void onServiceDisconnected(ComponentName n){fail("AI service disconnected");}
        public void onBindingDied(ComponentName n){fail("AI service binding died");}
        public void onNullBinding(ComponentName n){fail("AI backend unavailable");}
    };
    AiClient(Context context){this.context=context;main.post(()->{
        if(closed)return;
        try{
            String pkg="com.samsung.android.aioskernelservice";PackageManager pm=context.getPackageManager();
            if(pm.checkSignatures("android",pkg)!=PackageManager.SIGNATURE_MATCH)throw new SecurityException("AI service signature mismatch");
            bound=context.bindService(new Intent().setComponent(new ComponentName(pkg,"io.mesalabs.rezoss.nudge.LocalInferenceService")),connection,Context.BIND_AUTO_CREATE);
            if(!bound)fail("AI diagnostic service is not installed");
        }catch(Exception e){fail(e.getMessage());}
    });}
    private void fail(String text){IOException e=new IOException(text);connected.completeExceptionally(e);if(pending!=null)pending.completeExceptionally(e);}
    synchronized Bundle request(int what,Bundle input)throws Exception {
        connected.get(10,TimeUnit.SECONDS);if(closed)throw new IOException("Analysis cancelled");
        CompletableFuture<Bundle> reply=new CompletableFuture<>();pending=reply;expected=what;
        Message m=Message.obtain(null,what);m.replyTo=callback;m.setData(input);remote.send(m);
        try{Bundle result=reply.get(50,TimeUnit.SECONDS);String status=result.getString("status");if(status!=null&&!status.equals("generated"))throw new IOException(status);return result;}
        finally{pending=null;}
    }
    Bundle config()throws Exception{
        Bundle config=request(3,new Bundle());
        if(!config.getString("destination","").matches("[0-9a-f]{64}"))throw new IOException("AI diagnostic protocol unavailable. Install the updated ROM backend.");
        return config;
    }
    String analyze(String log,Bundle destination)throws Exception{
        Bundle b=new Bundle();b.putString("task","diagnostic");b.putString("input",log);b.putString("destination",destination.getString("destination"));
        b.putBoolean("online_consent",destination.getBoolean("online"));b.putLong("deadline",SystemClock.elapsedRealtime()+45000);
        Bundle result=request(1,b);String content=result.getString("content","");if(content.trim().isEmpty())throw new IOException("No analysis returned");return content;
    }
    String chat(String input,Bundle destination)throws Exception{
        Bundle b=new Bundle();b.putString("task","diagnostic_chat");b.putString("input",input);b.putString("destination",destination.getString("destination"));
        b.putBoolean("online_consent",destination.getBoolean("online"));b.putLong("deadline",SystemClock.elapsedRealtime()+45000);
        Bundle result=request(1,b);String content=result.getString("content","");if(content.trim().isEmpty())throw new IOException("No answer returned");return content;
    }
    @Override public void close(){closed=true;main.post(()->{
        if(remote!=null)try{Message m=Message.obtain(null,2);m.replyTo=callback;remote.send(m);}catch(Exception ignored){}
        fail("Analysis cancelled");if(bound){context.unbindService(connection);bound=false;}
    });}
}
