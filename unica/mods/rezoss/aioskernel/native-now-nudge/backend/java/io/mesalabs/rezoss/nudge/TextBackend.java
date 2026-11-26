package io.mesalabs.rezoss.nudge;

import android.app.Service;
import android.content.Context;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import java.io.*;
import java.lang.Process;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in replacement profile. Never loads or claims to execute Samsung Gauss weights. */
public final class TextBackend {
    private static final String TAG="RezossNudgeV73", PREFIX="com.samsung.android.aioskernelservice.";
    private static final Map<Service,TextBackend> INSTANCES=new HashMap<>();
    private static TextBackend shared;
    private final Context context;
    private final ModelStore store;
    private final ApiProviderClient api;
    private File modelDirectory;
    private final java.util.concurrent.locks.ReentrantLock runnerLock=new java.util.concurrent.locks.ReentrantLock(true);
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(1));
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor();
    private final Map<Integer,String> profiles=new ConcurrentHashMap<>();
    private final Set<Task> tasks=Collections.synchronizedSet(new HashSet<Task>());
    private volatile boolean validated;
    private volatile Process auxiliary;
    private TextBackend(Service service) { context=service.getApplicationContext(); store=new ModelStore(context); api=new ApiProviderClient(context); }
    public static synchronized boolean create(Service service) {
        if(Settings.Global.getInt(service.getContentResolver(),"rezoss_aios_v73_backend",1)!=1) return false;
        if(shared==null)shared=new TextBackend(service);
        INSTANCES.put(service,shared);
        Log.i(TAG,"Replacement Qwen3 V73 profile enabled; Samsung weights are not used"); return true;
    }
    public static synchronized IBinder bind(Service service) { TextBackend b=INSTANCES.get(service); return b==null?null:b.endpoint; }
    static synchronized TextBackend local(Service service) { return INSTANCES.get(service); }
    public static synchronized boolean close(Service service) {
        TextBackend b=INSTANCES.remove(service); if(b==null)return false;
        if(!INSTANCES.isEmpty())return true;
        synchronized(b.tasks) { for(Task task:new ArrayList<Task>(b.tasks))task.binderDied(); }
        if(b.auxiliary!=null)b.auxiliary.destroyForcibly(); b.worker.shutdownNow(); b.deadlines.shutdownNow(); b.profiles.clear();shared=null;return true;
    }
    static Object get(Object value,String name)throws Exception{return value.getClass().getMethod(name).invoke(value);}
    static Object read(Parcel p,String name)throws Exception {
        Parcelable.Creator<?> c=(Parcelable.Creator<?>)Class.forName(PREFIX+name).getField("CREATOR").get(null);return p.readTypedObject(c);
    }
    static Parcelable result(List<?> ids,String name,String text)throws Exception {
        Object prompt=Class.forName(PREFIX+"PromptTensor").getConstructor(String.class,String.class).newInstance(name,text);
        return (Parcelable)Class.forName(PREFIX+"TaskIOTensor").getConstructor(List.class,List.class,List.class,List.class)
            .newInstance(ids,Collections.singletonList(prompt),Collections.emptyList(),Collections.emptyList());
    }
    private File models(){
        if(modelDirectory==null)throw new IllegalStateException("Model package not imported");
        return modelDirectory;
    }
    boolean filesPresent(){return store.info()!=null;}
    boolean configured(){try{return "default".equals(api.route(false))?filesPresent():api.configured();}catch(Exception e){return false;}}
    void cancel(String id){api.cancel(id);}
    private boolean ready(String route){return "default".equals(route)?filesPresent():api.configured();}
    private void prepareModel(long deadline)throws Exception {
        File next=store.ensure(deadline);
        if(!next.equals(modelDirectory)){validated=false;modelDirectory=next;}
    }
    void submit(Runnable job){worker.execute(job);}
    void remove(Runnable job){worker.remove(job);}
    private List<String> command(File job,String prompt,String mode,int maxTokens)throws Exception {
        File input=new File(job,"prompt.txt");Files.write(input.toPath(),prompt.getBytes("UTF-8"));
        String libs=context.getApplicationInfo().nativeLibraryDir;
        List<String> args=new ArrayList<>(Arrays.asList(libs+"/libnudge_runner.so","--model_path",new File(models(),"hybrid_llama_qnn.pte").toString(),
            "--tokenizer_path",new File(models(),"tokenizer.json").toString(),"--decoder_model_version","qwen3","--prompt_file",input.toString(),
            "--seq_len","1024","--max_new_tokens",Integer.toString(maxTokens),"--temperature","0","--reply_only","--output_path",new File(job,"output.txt").toString(),
            "--performance_output_path",new File(job,"performance.txt").toString()));
        if(mode!=null)args.add(mode);return args;
    }
    private Process launch(File job,String prompt,String mode)throws Exception { return launch(job,prompt,mode,96); }
    private Process launch(File job,String prompt,String mode,int maxTokens)throws Exception {
        ProcessBuilder pb=new ProcessBuilder(command(job,prompt,mode,maxTokens)).directory(job).redirectErrorStream(true).redirectOutput(new File(job,"runner.txt"));
        String libs=context.getApplicationInfo().nativeLibraryDir;
        pb.environment().put("LD_LIBRARY_PATH",libs+":/vendor/lib64");pb.environment().put("ADSP_LIBRARY_PATH",libs);
        pb.environment().put("REZOSS_LEGACY_FLOAT_IO","1");return pb.start();
    }
    private boolean validate() {
        if(!filesPresent())return false;
        runnerLock.lock();
        try {
            prepareModel(SystemClock.elapsedRealtime()+45000);
            if(validated)return true;
            File job=Files.createTempDirectory(context.getCacheDir().toPath(),"load-").toFile();
            auxiliary=launch(job,"test","--load_only");
            if(!auxiliary.waitFor(45,TimeUnit.SECONDS)){auxiliary.destroyForcibly();return false;}
            validated=auxiliary.exitValue()==0;auxiliary=null;Log.i(TAG,"V73 model validation="+validated);return validated;
        }catch(Exception e){Log.e(TAG,"Model validation failed",e);return false;}
        finally{if(auxiliary!=null){auxiliary.destroyForcibly();auxiliary=null;}runnerLock.unlock();}
    }
    private int count(String prompt)throws Exception {
        return count(prompt,null);
    }
    private int count(String prompt,Control control)throws Exception {
        if(control!=null&&(control.cancelled()||SystemClock.elapsedRealtime()>=control.deadline()))throw new IOException("deadline");
        File job=Files.createTempDirectory(context.getCacheDir().toPath(),"count-").toFile();
        try{auxiliary=launch(job,prompt,"--count_only");
        if(control!=null)control.process(auxiliary);
        long timeout=control==null?10000:Math.min(10000,Math.max(1,control.deadline()-SystemClock.elapsedRealtime()));
        if(!auxiliary.waitFor(timeout,TimeUnit.MILLISECONDS)){auxiliary.destroyForcibly();throw new IOException(control==null?"Tokenizer timeout":"deadline");}
        if(auxiliary.exitValue()!=0)throw new IOException("Tokenizer failure");auxiliary=null;
        int n=Integer.parseInt(new String(Files.readAllBytes(new File(job,"output.txt").toPath()),"UTF-8").trim());
        return n;
        }finally{if(auxiliary!=null){auxiliary.destroyForcibly();auxiliary=null;}if(control!=null)control.process(null);for(File f:job.listFiles())f.delete();job.delete();}
    }
    private final Binder endpoint=new Binder(){
        public String getInterfaceDescriptor(){return PREFIX+"IAIOSKernelService";}
        protected boolean onTransact(int code,Parcel in,Parcel out,int flags)throws RemoteException {
            if(code==INTERFACE_TRANSACTION){out.writeString(getInterfaceDescriptor());return true;}
            in.enforceInterface(getInterfaceDescriptor()); int uid=Binder.getCallingUid();byte status=3;
            long identity=Binder.clearCallingIdentity();
            try {
                switch(code){
                    case 1: status=(byte)(configured()?0:3);break;
                    case 11:in.readTypedObject(Bundle.CREATOR);status=(byte)(configured()?0:3);break;
                    case 2:{String prompt=in.readString();int length=in.readInt();if(length<1||length>16)throw new IllegalArgumentException("Invalid token count array");
                        int[] values=new int[length];runnerLock.lock();try{prepareModel(SystemClock.elapsedRealtime()+15000);values[0]=count(prompt);}finally{runnerLock.unlock();}out.writeNoException();out.writeByte((byte)0);out.writeIntArray(values);return true;}
                    case 4:case 5:profiles.remove(uid);for(Task task:snapshot())if(task.owner==uid){task.stop();task.finish(4,null);}status=0;break;
                    case 6:{Object model=read(in,"ModelInfo"),adapter=read(in,"AdapterInfo");status=select(uid,adapter);break;}
                    case 7:status=select(uid,read(in,"AdapterInfo"));break;
                    case 8:{Object input=read(in,"TaskIOTensor");read(in,"TaskPriority");IBinder callback=in.readStrongBinder();in.enforceNoDataAvail();
                        String profile=profiles.get(uid);
                        if(profile==null||"initialization".equals(profile))throw new IllegalStateException("Prepare replacement profile first");
                        Task task=new Task(uid,input,callback,profile);tasks.add(task);callback.linkToDeath(task,0);task.armTimeout();
                        try{worker.execute(task);}catch(RejectedExecutionException ex){task.finish(3,null);}
                        out.writeNoException();out.writeStrongBinder(task);return true;}
                    default:throw new UnsupportedOperationException("Unsupported replacement transaction; transaction="+code);
                }
                in.enforceNoDataAvail();out.writeNoException();out.writeByte(status);
            }catch(Exception ex){Log.e(TAG,"Rejected transaction="+code,ex);out.writeException(new IllegalArgumentException(ex.getMessage()));}
            finally{Binder.restoreCallingIdentity(identity);}
            return true;
        }
    };
    private byte select(int uid,Object adapter)throws Exception {
        if(adapter==null)return 3;String name=(String)get(adapter,"getLoraName");
        if(!CallScreeningContract.PROFILE.equals(name)&&!"initialization".equals(name)&&!"contextual_reply".equals(name)&&!"smart_reply".equals(name)){Log.w(TAG,"Unsupported adapter="+name);return 3;}
        // Screening prepares its profile before submitting a task. Preempt
        // Memory here as well, before model validation can wait on its engine.
        if(CallScreeningContract.PROFILE.equals(name)){Thread memory=memoryThread;if(memory!=null)memory.interrupt();}
        String route=api.route(CallScreeningContract.PROFILE.equals(name));if(!ready(route)||("default".equals(route)&&!validate()))return 3;profiles.put(uid,name);Log.i(TAG,"Prepared replacement profile="+name+" uid="+uid);return 0;
    }
    private List<Task> snapshot(){synchronized(tasks){return new ArrayList<Task>(tasks);}}
    private final class Task extends Binder implements Runnable,IBinder.DeathRecipient {
        final String route; volatile String requestId;final long submitted=SystemClock.elapsedRealtime();final CallScreeningContract screening;final ContextReplyContract replyContract;final int owner;final IBinder callback;final List<?> ids;final String name,prompt,source;final ScreeningDeadline done=new ScreeningDeadline(submitted);volatile Process process;volatile ScheduledFuture<?> timeout;
        Task(int owner,Object input,IBinder cb,String profile)throws Exception {
            this.owner=owner;callback=cb;if(cb==null||input==null)throw new IllegalArgumentException("Missing task input/callback");
            ids=new ArrayList<Object>((List<?>)get(input,"getPromptIDs"));List<?> p=(List<?>)get(input,"getMPrompts"),images=(List<?>)get(input,"getMTensors");
            if(p.size()!=1||!images.isEmpty())throw new IllegalArgumentException("Requires one text prompt and no images");
            name=(String)get(p.get(0),"getMPromptName");String raw=(String)get(p.get(0),"getMPromptString");source=raw;
            if(raw==null||raw.length()>16000)throw new IllegalArgumentException("Invalid prompt size");screening=CallScreeningContract.PROFILE.equals(profile)?new CallScreeningContract(raw):null;replyContract=screening==null&&PromptAdapter.structured(raw)?new ContextReplyContract(raw):null;prompt=screening!=null?screening.prompt:replyContract!=null?replyContract.prompt:adapt(raw);route=api.route(screening!=null);
        }
        public String getInterfaceDescriptor(){return PREFIX+"IAIOSTask";}
        protected boolean onTransact(int code,Parcel in,Parcel out,int flags)throws RemoteException {
            if(code==INTERFACE_TRANSACTION){out.writeString(getInterfaceDescriptor());return true;}
            if(Binder.getCallingUid()!=owner)throw new SecurityException("Task UID mismatch");in.enforceInterface(getInterfaceDescriptor());
            if(code==1){in.enforceNoDataAvail();stop();finish(4,null);out.writeNoException();out.writeByte((byte)0);return true;}
            out.writeException(new UnsupportedOperationException("Only cancellation supported"));return true;
        }
        synchronized void armTimeout(){
            if(screening!=null){Thread memory=memoryThread;if(memory!=null)memory.interrupt();}
            if(screening!=null&&!done.done())timeout=deadlines.schedule(new Runnable(){public void run(){
                if(done.done())return;
                Log.w(TAG,"Call Screening deadline expired ageMs="+(SystemClock.elapsedRealtime()-submitted));
                stop();worker.remove(Task.this);finish(3,null);
            }},Math.max(1,done.expires()-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS);
        }
        synchronized void clearTimeout(){if(timeout!=null)timeout.cancel(false);}
        void stop(){api.cancel(requestId);worker.remove(this);Process p=process;if(p!=null)p.destroyForcibly();}
        public void binderDied(){if(done.finish()){clearTimeout();stop();worker.remove(this);tasks.remove(this);}}
        void finish(int event,Parcelable output){
            // Claim success only while the original submission deadline is live.
            // A delayed timer must never allow late native output to succeed.
            if(event==1&&screening!=null){
                if(!done.complete(SystemClock.elapsedRealtime())){
                    if(!done.done())finish(3,null);
                    return;
                }
            }else if(!done.finish())return;
            clearTimeout();tasks.remove(this);callback.unlinkToDeath(this,0);
            if(event==1)Log.i(TAG,"COMPLETE uid="+owner+" ageMs="+(SystemClock.elapsedRealtime()-submitted));
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{data.writeInterfaceToken(PREFIX+"ITaskCallback");if(event==1)data.writeTypedObject(output,0);
                if(!callback.transact(event,data,reply,0))throw new RemoteException("Callback rejected");reply.readException();reply.readByte();
            }catch(Exception ex){Log.e(TAG,"Callback failed",ex);}finally{data.recycle();reply.recycle();}
        }
        public void run(){
            File job=null;boolean locked=false;final long deadline=screening==null?submitted+45000:done.expires();
            Control control=new Control(){public boolean cancelled(){return done.done();}public long deadline(){return deadline;}public void process(Process p){process=p;if(done.done()&&p!=null)p.destroyForcibly();}};
            try{if(screening==null){runnerLock.lockInterruptibly();locked=true;}else{locked=runnerLock.tryLock(Math.max(1,deadline-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS);if(!locked)throw new IOException("Call Screening queue timeout");}if(done.done())return;
                if(!"default".equals(route)){
                    requestId=UUID.randomUUID().toString();
                    String text=api.generate(prompt,screening==null?96:CallScreeningContract.MAX_NEW_TOKENS,deadline,requestId,route);
                    text=screening==null?(replyContract==null?ReplyText.validated(text,source):replyContract.output(text)):screening.output(text);
                    if(text.isEmpty()||text.contains("<think>"))throw new IOException("invalid_output");
                    if(done.done()||SystemClock.elapsedRealtime()>=deadline)throw new IOException("deadline");
                    finish(1,result(ids,name,text));return;
                }
                Log.i(TAG,"reply route=local queueMs="+(SystemClock.elapsedRealtime()-submitted)+" structured="+PromptAdapter.structured(source));
                if(replyContract!=null){
                    prepareModel(deadline);String[] replies=new String[3];
                    for(int tone=0;tone<3;tone++){
                        if(done.done()||SystemClock.elapsedRealtime()>=deadline)throw new IOException("deadline");
                        File part=Files.createTempDirectory(context.getCacheDir().toPath(),"reply-tone-").toFile();
                        try{
                            String input=replyContract.promptForTone(tone);
                            if(count(input,control)>850)throw new IOException("context_limit");
                            synchronized(this){if(done.done())return;process=launch(part,input,null,32);if(done.done()){stop();return;}}
                            if(!process.waitFor(Math.max(1,deadline-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS))throw new IOException("deadline");
                            if(process.exitValue()!=0)throw new IOException("runner_failed");
                            replies[tone]=ReplyText.clean(ModelOutput.completion(new String(Files.readAllBytes(new File(part,"output.txt").toPath()),"UTF-8")).trim());
                            Log.i(TAG,"reply tone="+tone+" chars="+replies[tone].length()+" ageMs="+(SystemClock.elapsedRealtime()-submitted));
                        }finally{stop();process=null;File[] files=part.listFiles();if(files!=null)for(File file:files)file.delete();part.delete();}
                    }
                    String text=replyContract.lines(replies);
                    if(done.done()||SystemClock.elapsedRealtime()>=deadline)throw new IOException("deadline");
                    finish(1,result(ids,name,text));return;
                }
                prepareModel(Math.min(deadline,SystemClock.elapsedRealtime()+45000));job=Files.createTempDirectory(context.getCacheDir().toPath(),"reply-").toFile();
                String localPrompt=screening==null?PromptAdapter.adaptLocal(source):prompt;
                long countStarted=SystemClock.elapsedRealtime();
                if(count(localPrompt,screening==null?null:control)>850)throw new IllegalArgumentException("Prompt exceeds replacement 1024-token context budget");
                if(screening!=null){
                    done.remaining(SystemClock.elapsedRealtime());
                    Log.i(TAG,"SCREENING tokenizeMs="+(SystemClock.elapsedRealtime()-countStarted)+" ageMs="+(SystemClock.elapsedRealtime()-submitted)+" maxTokens="+CallScreeningContract.MAX_NEW_TOKENS);
                }
                synchronized(this){if(done.done())return;process=launch(job,localPrompt,null,screening==null?96:CallScreeningContract.MAX_NEW_TOKENS);if(done.done()){stop();return;}}
                long generationWait=screening==null?Math.max(1,deadline-SystemClock.elapsedRealtime()):done.remaining(SystemClock.elapsedRealtime());
                if(!process.waitFor(generationWait,TimeUnit.MILLISECONDS)){stop();throw new IOException("Reply timeout");}if(done.done())return;
                if(process.exitValue()!=0)throw new IOException("Runner exit="+process.exitValue()+" job="+job);
                String text=new String(Files.readAllBytes(new File(job,"output.txt").toPath()),"UTF-8").replace("<|im_end|>","").trim();
                int think=text.lastIndexOf("</think>");if(think>=0)text=text.substring(think+8).trim();
                text=screening==null?(replyContract==null?ReplyText.validated(text,source):replyContract.output(text)):screening.output(text);
                if(SystemClock.elapsedRealtime()>=deadline)throw new IOException("Call Screening deadline");
                if(text.isEmpty()||text.contains("<think>"))throw new IOException("Empty/incomplete response");
                Log.i(TAG,"VALIDATED uid="+owner+" chars="+text.length()+" ageMs="+(SystemClock.elapsedRealtime()-submitted));finish(1,result(ids,name,text));
            }catch(Exception ex){Log.e(TAG,"Task failed job="+job,ex);finish(3,null);}finally{stop();process=null;if(job!=null)new File(job,"prompt.txt").delete();if(locked)runnerLock.unlock();}
        }
    }
    interface Control { boolean cancelled(); long deadline(); void process(Process process); default void request(String id){} }
    Bundle diagnosticConfig() throws Exception {
        Bundle config=api.diagnosticConfig();boolean online=config.getBoolean("online");
        config.putInt("chat_protocol",1);
        // Conservative transport budgets; local tokenization remains authoritative.
        config.putInt("chat_max_chars",online?12000:2200);
        config.putInt("chat_section_chars",online?8000:800);
        config.putInt("chat_output_tokens",online?1024:256);
        config.putInt("chat_context_tokens",online?0:1024);
        return config;
    }
    String generateChat(String input,String destination,boolean consent,Control control)throws Exception {
        if(!runnerLock.tryLock(Math.max(1,control.deadline()-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS))throw new IOException("busy");
        try {
            Bundle config=diagnosticConfig();
            if(!destination.equals(config.getString("destination"))||(config.getBoolean("online")&&!consent))throw new IOException("backend_changed");
            String prompt=DiagnosticPrompt.chat(input,config.getInt("chat_max_chars"));
            if(control.cancelled())throw new InterruptedIOException();
            String route=config.getString("backend","default");
            if(!"default".equals(route)){
                String id=UUID.randomUUID().toString();control.request(id);
                try{return api.generate(prompt,config.getInt("chat_output_tokens"),control.deadline(),id,route,destination);}finally{control.request(null);}
            }
            return diagnosticLocal(prompt,control);
        } finally { runnerLock.unlock(); }
    }
    String generateDiagnostic(String excerpt,String destination,boolean consent,Control control)throws Exception {
        if(!runnerLock.tryLock(Math.max(1,control.deadline()-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS))throw new IOException("busy");
        try {
            Bundle config=diagnosticConfig();
            if(!destination.equals(config.getString("destination"))||(config.getBoolean("online")&&!consent))throw new IOException("backend_changed");
            String prompt=DiagnosticPrompt.create(excerpt);
            if(control.cancelled())throw new InterruptedIOException();
            String route=config.getString("backend","default");
            if(!"default".equals(route)) {
                String id=UUID.randomUUID().toString();control.request(id);
                try{return api.generate(prompt,256,control.deadline(),id,route,destination);}finally{control.request(null);}
            }
            return diagnosticLocal(prompt,control);
        } finally { runnerLock.unlock(); }
    }
    private String diagnosticLocal(String prompt,Control control)throws Exception {
        prepareModel(control.deadline());
        if(count(prompt,control)>700)throw new IOException("context_limit");
        File job=Files.createTempDirectory(context.getCacheDir().toPath(),"diagnostic-").toFile();
        Process process=null;
        try {
            process=launch(job,prompt,null,256);control.process(process);
            if(control.cancelled())throw new InterruptedIOException();
            if(!process.waitFor(Math.max(1,control.deadline()-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS))throw new IOException("deadline");
            if(process.exitValue()!=0)throw new IOException("runner_failed");
            return ModelOutput.completion(new String(Files.readAllBytes(new File(job,"output.txt").toPath()),"UTF-8")).trim();
        } finally {
            if(process!=null)process.destroyForcibly();control.process(null);
            File[] files=job.listFiles();if(files!=null)for(File file:files)file.delete();job.delete();
        }
    }
    String generateLocal(String input,boolean summary,Control control)throws Exception {
        return generateControlled(input,summary,false,control);
    }
    String generateRecall(String input,Control control)throws Exception {
        if(input!=null&&PromptAdapter.latestIncoming(input,true)==null)return "Unsafe\t";
        String output=generateControlled(recallPrompt(input),false,true,control);
        int tab=output.indexOf('\t');
        Log.i(TAG,"recall wireAction="+(tab<0?"invalid":output.substring(0,tab)));
        return output;
    }
    // No keyword intent classification here: Samsung receives model predictions
    // and retains responsibility for validating parameters and routing actions.
    static String recallPrompt(String input){
        if(input==null||input.isEmpty()||input.length()>12000)throw new IllegalArgumentException("invalid_input");
        return ActionContract.prompt(input);
    }
    private String generateControlled(String prompt,boolean summary,boolean recall,Control control)throws Exception {
        long queued=SystemClock.elapsedRealtime();
        if(!runnerLock.tryLock(Math.max(1,control.deadline()-queued),TimeUnit.MILLISECONDS))throw new IOException("deadline");
        File job=null;
        try{
            if(control.cancelled())throw new InterruptedIOException();
            String route=api.route(false);
            if(!recall)prompt=LocalFormat.prompt(prompt,summary,"default".equals(route));
            Log.i(TAG,"local task="+(recall?"recall":summary?"summary":"replies")+" route="+route+" lockMs="+(SystemClock.elapsedRealtime()-queued)+" remainingMs="+(control.deadline()-SystemClock.elapsedRealtime()));
            if(!"default".equals(route)){
                String id=UUID.randomUUID().toString();control.request(id);
                try{String raw=api.generate(prompt,recall?ActionContract.MAX_NEW_TOKENS:summary?64:96,control.deadline(),id,route);
                    if(control.cancelled()||SystemClock.elapsedRealtime()>=control.deadline())throw new IOException("deadline");
                    return recall?ActionContract.output(raw):LocalFormat.output(raw,summary);
                }finally{control.request(null);}
            }
            prepareModel(control.deadline());
            long countStarted=SystemClock.elapsedRealtime();
            int tokens=count(prompt,control);
            Log.i(TAG,"local tokenizeMs="+(SystemClock.elapsedRealtime()-countStarted)+" tokens="+tokens+" recall="+recall);
            if(tokens>850)throw new IllegalArgumentException("context_limit");
            if(control.cancelled()||SystemClock.elapsedRealtime()>=control.deadline())throw new InterruptedIOException();
            job=Files.createTempDirectory(context.getCacheDir().toPath(),"local-").toFile();
            Process process=launch(job,prompt,null,recall?ActionContract.MAX_NEW_TOKENS:96);control.process(process);
            try{
                if(control.cancelled()){process.destroyForcibly();throw new InterruptedIOException();}
                if(!process.waitFor(Math.max(1,control.deadline()-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS))throw new IOException("deadline");
                if(control.cancelled()||SystemClock.elapsedRealtime()>=control.deadline())throw new IOException("deadline");
                if(process.exitValue()!=0)throw new IOException("runner_failed");
                String raw=new String(Files.readAllBytes(new File(job,"output.txt").toPath()),"UTF-8");
                // Preserve tabs/pipes and trailing empty Unsafe payload exactly.
                // The SSCO client validates this protocol before Samsung sees it.
                String output=recall?ActionContract.output(raw):LocalFormat.output(raw,summary);
                if(control.cancelled()||SystemClock.elapsedRealtime()>=control.deadline())throw new IOException("deadline");
                return output;
            }finally{process.destroyForcibly();control.process(null);}
        }finally{if(job!=null){File[] files=job.listFiles();if(files!=null)for(File f:files)f.delete();job.delete();}runnerLock.unlock();}
    }
    static String adapt(String raw){return PromptAdapter.adapt(raw);}
    private volatile Thread memoryThread;
    boolean memoryConfigured(){try{return ready(api.memoryRoute());}catch(Exception e){return false;}}
    String generateMemory(String input,Control control)throws Exception {
        if(!runnerLock.tryLock(Math.max(1,control.deadline()-SystemClock.elapsedRealtime()),TimeUnit.MILLISECONDS))throw new IOException("busy");
        try {
            if(control.cancelled()||SystemClock.elapsedRealtime()>=control.deadline())throw new IOException("cancelled");
            for(Task task:snapshot())if(task.screening!=null&&!task.done.done())throw new IOException("busy");
            memoryThread=Thread.currentThread();
            String prompt=MemoryContract.prompt(input),route=api.memoryRoute(),raw;
            if("default".equals(route))raw=memoryLocal(prompt,control);
            else {
                String id=UUID.randomUUID().toString();control.request(id);
                try{raw=api.generateMemory(prompt,MemoryContract.OUTPUT_TOKENS,control.deadline(),id,route);}finally{control.request(null);}
            }
            if(control.cancelled()||SystemClock.elapsedRealtime()>=control.deadline())throw new IOException("deadline");
            return MemoryContract.output(raw,input);
        }finally{memoryThread=null;runnerLock.unlock();}
    }
    private String memoryLocal(String prompt,Control control)throws Exception {throw new IOException("memory_gemma_required");}
}
