package io.mesalabs.rezoss.nudge;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.IOException;

/** Calls the signed Rezoss AI provider without exposing the saved API key to AIOS. */
final class ApiProviderClient {
    static final String PACKAGE = "io.mesalabs.rezoss.qwen";
    private static final Uri URI = Uri.parse("content://io.mesalabs.rezoss.qwen.api");
    private final Context context;

    ApiProviderClient(Context context) { this.context = context.getApplicationContext(); }

    static boolean trusted(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        if (pm.checkSignatures(context.getPackageName(), pkg) == PackageManager.SIGNATURE_MATCH) return true;
        byte[] expected = new byte[32];
        String hash = "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8";
        for (int i = 0; i < expected.length; i++)
            expected[i] = (byte) Integer.parseInt(hash.substring(i * 2, i * 2 + 2), 16);
        return pm.hasSigningCertificate(pkg, expected, PackageManager.CERT_INPUT_SHA256);
    }

    boolean available() {
        try {
            PackageManager pm = context.getPackageManager();
            if (!pm.getApplicationInfo(PACKAGE, 0).enabled || !trusted(context, PACKAGE)) return false;
            ProviderInfo provider = pm.resolveContentProvider(URI.getAuthority(), 0);
            return provider != null && PACKAGE.equals(provider.packageName);
        } catch (Exception unavailable) {
            return false;
        }
    }

    boolean configured() {
        if (!available()) return false;
        try {
            Bundle result = context.getContentResolver().call(URI, "api_ready", null, null);
            return result != null && result.getBoolean("configured", false);
        } catch (Exception unavailable) {
            return false;
        }
    }

    String route(boolean screening) throws IOException {
        if(!available())return "default";
        try{Bundle b=context.getContentResolver().call(URI,"backend_config",null,null);
            if(b==null)throw new IOException("backend_unavailable");
            return b.getString(screening?"screening_backend":"backend","default");
        }catch(Exception e){throw new IOException("backend_unavailable",e);}
    }

    String memoryRoute() throws IOException {
        if(!available())return "default";
        try { Bundle config=context.getContentResolver().call(URI,"backend_config",null,null);
            if(config==null)throw new IOException("backend_unavailable");
            return config.getString("memory_backend","default");
        } catch(Exception e){throw new IOException("backend_unavailable",e);}
    }

    Bundle diagnosticConfig() throws Exception {
        String route=route(false); Bundle result=new Bundle();
        result.putString("backend",route);result.putBoolean("online",!"default".equals(route));
        String model="ROM local AI",endpoint="";
        if(!"default".equals(route)) {
            Bundle config=context.getContentResolver().call(URI,"backend_config",null,null);
            if(config==null||!route.equals(config.getString("backend")))throw new IOException("backend_changed");
            if(config.getInt("diagnostic_protocol",0)!=1)throw new IOException("diagnostic_provider_update_required");
            model=config.getString("model","");endpoint=config.getString("endpoint","");
            if(model.isEmpty()||endpoint.isEmpty())throw new IOException("api_unconfigured");
        } else {
            android.content.pm.ApplicationInfo info=context.getPackageManager().getApplicationInfo(context.getPackageName(),PackageManager.GET_META_DATA);
            if(info.metaData!=null)model=info.metaData.getString("unica.local_model_name",model);
        }
        result.putString("model",model);result.putString("endpoint",endpoint);
        byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest((route+"\n"+model+"\n"+endpoint).getBytes("UTF-8"));
        StringBuilder fingerprint=new StringBuilder();for(byte b:hash)fingerprint.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        result.putString("destination",fingerprint.toString());return result;
    }

    String generate(String prompt, int maxTokens, long deadline, String requestId, String backend) throws Exception {
        return generate(prompt,maxTokens,deadline,requestId,backend,null);
    }
    String generate(String prompt,int maxTokens,long deadline,String requestId,String backend,String destination)throws Exception {
        return generate(prompt,maxTokens,deadline,requestId,backend,destination,false);
    }
    String generateMemory(String prompt,int maxTokens,long deadline,String requestId,String backend)throws Exception {
        return generate(prompt,maxTokens,deadline,requestId,backend,null,true);
    }
    private String generate(String prompt,int maxTokens,long deadline,String requestId,String backend,String destination,boolean memory)throws Exception {
        if (SystemClock.elapsedRealtime() >= deadline) throw new IOException("deadline");
        Bundle extras = new Bundle();
        extras.putString("backend",backend);
        if(memory)extras.putString("task","memory");
        if(destination!=null)extras.putString("destination",destination);
        extras.putString("prompt", prompt);
        extras.putString("request_id", requestId);
        extras.putLong("deadline", deadline);
        extras.putInt("max_tokens", maxTokens);
        java.util.concurrent.Future<Bundle> pending=REQUESTS.submit(()->context.getContentResolver().call(URI,"generate",null,extras));
        Bundle result;
        try{result=pending.get(Math.max(1,deadline-SystemClock.elapsedRealtime()),java.util.concurrent.TimeUnit.MILLISECONDS);}
        catch(java.util.concurrent.TimeoutException e){pending.cancel(true);cancel(requestId);throw new IOException("deadline");}
        catch(InterruptedException e){pending.cancel(true);cancel(requestId);Thread.currentThread().interrupt();throw new IOException("cancelled");}
        if (result == null) throw new IOException("api_unavailable");
        String status = result.getString("status", "api_unavailable");
        String content = result.getString("content");
        if (!"generated".equals(status) || content == null) throw new IOException(status);
        return content;
    }

    private static final java.util.concurrent.ExecutorService REQUESTS=new java.util.concurrent.ThreadPoolExecutor(0,2,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.SynchronousQueue<Runnable>());
    private static final java.util.concurrent.ExecutorService CANCEL=java.util.concurrent.Executors.newSingleThreadExecutor();
    void cancel(String requestId) {
        if (requestId == null || requestId.isEmpty()) return;
        Bundle extras = new Bundle();
        extras.putString("request_id", requestId);
        CANCEL.execute(()->{try { context.getContentResolver().call(URI, "cancel", null, extras); } catch (Exception ignored) { }});
    }
}
