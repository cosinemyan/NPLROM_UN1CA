package io.mesalabs.rezoss.nudge;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import com.google.ai.edge.litertlm.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.concurrent.*;

/** Experimental, serialized LiteRT engine. Model data stays in AIOS private storage. */
final class GemmaRuntime {
    static final String HASH="181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c";
    static final long SIZE=2588147712L;
    private static GemmaRuntime instance;
    static synchronized GemmaRuntime get(Context context){
        if(instance==null)instance=new GemmaRuntime(context.getApplicationContext());
        return instance;
    }
    private final Context context;
    private final File model,marker,cache;
    private final ThreadPoolExecutor nativeWorker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(2));
    private final ScheduledExecutorService idle=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"gemma-idle");t.setDaemon(true);return t;});
    private volatile long lastUse=SystemClock.elapsedRealtime();
    private volatile boolean loaded;
    private long engine; // Native objects are created/deleted only on nativeWorker.
    private LiteRtLmJni jni;
    private GemmaRuntime(Context c){
        context=c;File dir=new File(c.getFilesDir(),"gemma4-e2b");
        model=new File(dir,"gemma-4-E2B-it.litertlm");marker=new File(dir,"verified.json");
        cache=new File(c.getCacheDir(),"gemma4-"+GemmaBuild.BACKEND);
        idle.scheduleAtFixedRate(()->{
            if(loaded&&SystemClock.elapsedRealtime()-lastUse>60000&&nativeWorker.getActiveCount()==0){
                try{nativeWorker.execute(()->{if(SystemClock.elapsedRealtime()-lastUse>60000)unload();});}catch(RejectedExecutionException ignored){}
            }
        },15,15,TimeUnit.SECONDS);
    }
    boolean filesPresent(){
        try{
            if(Files.isSymbolicLink(model.toPath())||Files.isSymbolicLink(marker.toPath())||model.length()!=SIZE||marker.length()>1024)return false;
            JSONObject m=new JSONObject(new String(Files.readAllBytes(marker.toPath()),"UTF-8"));
            return HASH.equals(m.getString("sha256"))&&m.getLong("size")==SIZE&&m.getLong("modified")==model.lastModified();
        }catch(Exception e){return false;}
    }
    String status(){return "Gemma 4 E2B · "+GemmaBuild.BACKEND.toUpperCase()+"\n"+(filesPresent()?(loaded?"Model verified · engine ready":"Model verified · engine not loaded"):"Import the model to enable local inference");}
    private void load()throws Exception{
        if(engine!=0)return;
        if(!filesPresent())throw new IOException("gemma_model_missing");
        if(!cache.isDirectory()&&!cache.mkdirs())throw new IOException("gemma_cache_unavailable");
        jni=LiteRtLmJni.INSTANCE;jni.nativeSetMinLogSeverity(2);
        long started=SystemClock.elapsedRealtime();
        engine=jni.nativeCreateEngine(model.toString(),GemmaBuild.BACKEND,"","",1024,0,cache.toString(),true,Boolean.FALSE,"","","",4,0,0,-1,null,null);
        if(engine==0)throw new IOException("gemma_load_failed");
        loaded=true;Log.i("RezossGemma","engine_ready backend="+GemmaBuild.BACKEND+" loadMs="+(SystemClock.elapsedRealtime()-started));
    }
    private void unload(){if(engine!=0){jni.nativeDeleteEngine(engine);engine=0;loaded=false;Log.i("RezossGemma","engine_unloaded");}}
    private abstract class Job<T> implements Callable<T> {
        final TextBackend.Control control;final Object gate=new Object();
        volatile boolean aborted;long conversation;
        Job(TextBackend.Control c){control=c;}
        void check()throws IOException{
            if(aborted||control.cancelled()||SystemClock.elapsedRealtime()>=control.deadline())throw new IOException("deadline_or_cancelled");
        }
        void cancel(){synchronized(gate){aborted=true;if(conversation!=0)jni.nativeConversationCancelProcess(conversation);}}
        public final T call()throws Exception{
            try{check();lastUse=SystemClock.elapsedRealtime();return execute();}
            finally{
                synchronized(gate){if(conversation!=0){jni.nativeDeleteConversation(conversation);conversation=0;}}
                lastUse=SystemClock.elapsedRealtime();
            }
        }
        abstract T execute()throws Exception;
        void open(int limit)throws Exception{
            load();check();
            synchronized(gate){
                check();conversation=jni.nativeCreateConversation(engine,new SamplerConfig(1,1.0,0.0,0),"[]","[]",null,"{}",false,null,null,null,null,false,limit,new ThinkingConfig(false,0),false,Boolean.FALSE,null);
            }
        }
        String send(String prompt,int limit)throws Exception{
            open(limit);check();
            String message=new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","text").put("text",prompt))).toString();
            String raw=jni.nativeSendMessage(conversation,message,"{}",null,null,null,null,limit,new ThinkingConfig(false,0),0,null);
            check();
            if(limit>1&&jni.nativeConversationGetBenchmarkInfo(conversation).getLastDecodeTokenCount()>=limit)throw new IOException("gemma_output_limit");
            JSONArray parts=new JSONObject(raw).getJSONArray("content");StringBuilder text=new StringBuilder();
            for(int i=0;i<parts.length();i++){
                JSONObject part=parts.getJSONObject(i);
                if(!"text".equals(part.optString("type")))throw new IOException("gemma_nontext_output");
                text.append(part.getString("text"));
            }
            if(text.length()==0&&limit>1)throw new IOException("gemma_empty_output");
            return text.toString();
        }
    }
    private <T>T await(Job<T> job)throws Exception{
        Future<T> future;
        try{future=nativeWorker.submit(job);}catch(RejectedExecutionException e){throw new IOException("gemma_busy");}
        try{
            while(true){
                job.check();long remaining=job.control.deadline()-SystemClock.elapsedRealtime();
                try{T value=future.get(Math.max(1,Math.min(50,remaining)),TimeUnit.MILLISECONDS);job.check();return value;}
                catch(TimeoutException wait){}
            }
        }catch(InterruptedException e){job.cancel();future.cancel(false);Thread.currentThread().interrupt();throw new IOException("cancelled",e);}
        catch(ExecutionException e){Throwable cause=e.getCause();if(cause instanceof Exception)throw (Exception)cause;throw new IOException("gemma_native_failed",cause);}
        catch(Exception e){job.cancel();future.cancel(false);throw e;}
    }
    static TextBackend.Control budget(long deadline){return new TextBackend.Control(){public boolean cancelled(){return Thread.currentThread().isInterrupted();}public long deadline(){return deadline;}public void process(java.lang.Process p){}};}
    void prepare(long deadline)throws Exception{await(new Job<Boolean>(budget(deadline)){Boolean execute()throws Exception{load();check();return true;}});}
    String generate(String prompt,int tokens,TextBackend.Control control)throws Exception{
        if(prompt==null||prompt.isEmpty()||prompt.length()>20000)throw new IOException("gemma_prompt_size");
        long started=SystemClock.elapsedRealtime();
        String out=await(new Job<String>(control){String execute()throws Exception{return send(GemmaPrompts.adapt(prompt),tokens);}});
        Log.i("RezossGemma","generation backend="+GemmaBuild.BACKEND+" elapsedMs="+(SystemClock.elapsedRealtime()-started)+" chars="+out.length());
        return out;
    }
    int count(String prompt,TextBackend.Control control)throws Exception{
        TextBackend.Control actual=control==null?budget(SystemClock.elapsedRealtime()+15000):control;
        // SDK exposes no tokenizer-only JNI call. Native prefill plus one discarded
        // token provides a conservative count including Gemma's chat delimiters.
        return await(new Job<Integer>(actual){Integer execute()throws Exception{
            send(prompt,1);return jni.nativeConversationGetBenchmarkInfo(conversation).getLastPrefillTokenCount();
        }});
    }
    void importModel(Uri uri,TextBackend.Control control)throws Exception{
        await(new Job<Boolean>(control){Boolean execute()throws Exception{
            unload();File dir=model.getParentFile();if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("gemma_directory_unavailable");
            File part=new File(dir,"model-import.part");MessageDigest digest=MessageDigest.getInstance("SHA-256");long copied=0;
            try{
                try(InputStream in=context.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(part)){
                    if(in==null)throw new IOException("gemma_input_unavailable");
                    byte[] buf=new byte[1024*1024];int n;
                    while((n=in.read(buf))!=-1){check();copied+=n;if(copied>SIZE)throw new IOException("gemma_model_size");digest.update(buf,0,n);out.write(buf,0,n);}
                    out.getFD().sync();
                }
                StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
                if(copied!=SIZE||!HASH.equals(hash.toString()))throw new IOException("gemma_model_hash");
                check();Files.move(part.toPath(),model.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                JSONObject data=new JSONObject().put("sha256",HASH).put("size",SIZE).put("modified",model.lastModified());
                File pending=new File(dir,"verified.part");Files.write(pending.toPath(),data.toString().getBytes("UTF-8"));
                Files.move(pending.toPath(),marker.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
                load();check();return true;
            }finally{Files.deleteIfExists(part.toPath());}
        }});
    }
}
