/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.troubleshooting;
import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Read-only checks. Only fixed summaries leave this class; never XML, identifiers or log lines. */
final class SetupChecks {
    static boolean on(String v){return "true".equals(v)||"1".equals(v);}
    static boolean fresh(String heartbeat,long now){try{long stamp=Long.parseLong(heartbeat);return stamp>0&&now>=stamp&&now-stamp<12000;}catch(Exception e){return false;}}
    static String pifErrors(Map<String,String> values){
        StringBuilder issues=new StringBuilder();
        for(String name:new String[]{"unica.pif","pif.spoof_build","pif.spoof_props","pif.vending_build"}){String v=values.get(name);if(v!=null&&!v.isEmpty()&&!Arrays.asList("true","false","1","0").contains(v))issues.append("Invalid PIF toggle. ");}
        String fingerprint=values.get("pif.fingerprint");if(fingerprint!=null&&!fingerprint.isEmpty()){
            String[] parts=fingerprint.split("[/:]",-1);boolean invalid=parts.length!=8||fingerprint.getBytes(StandardCharsets.UTF_8).length>=92;for(String part:parts)invalid|=part.isEmpty();if(invalid)issues.append("Invalid fingerprint structure or size. ");}
        for(Map.Entry<String,String> item:values.entrySet())if(item.getKey().startsWith("pif.")&&item.getValue().getBytes(StandardCharsets.UTF_8).length>=92)issues.append("PIF field exceeds native property storage. ");
        String date=values.get("pif.security_patch");if(date!=null&&!date.isEmpty()&&!date.equals("null"))try{if(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException();LocalDate.parse(date);}catch(Exception e){issues.append("Invalid security patch date. ");}
        String api=values.get("pif.first_api_level");if(api!=null&&!api.isEmpty()&&!api.equals("null")&&(!api.matches("[0-9]{1,2}")||Integer.parseInt(api)<1))issues.append("Invalid first API level. ");
        return issues.toString();
    }
    static String runtime(boolean enabled,boolean fresh,boolean running,boolean connected,boolean ack,boolean loaded){
        if(!enabled)return "Not verified — TEE is switched off; this is not a configuration failure.";
        if(!fresh)return "Not verified — no fresh backend heartbeat. Check Manage Keybox → System and collect logs after reproducing the problem.";
        if(!running||!connected||!ack||!loaded)return "Problem found — live backend reports incomplete daemon/interceptor/configuration/keybox readiness. Check Manage Keybox → System.";
        return "Runtime verified — fresh backend reports connected interceptor, acknowledged configuration and loaded keybox. App-specific interception and Google acceptance remain unverified.";
    }
    private static String property(String name)throws Exception{return (String)Class.forName("android.os.SemSystemProperties").getMethod("get",String.class,String.class).invoke(null,name,"");}
    private static boolean feature(Context c,String name){try{Class.forName(name,false,c.getClassLoader());return true;}catch(ClassNotFoundException|LinkageError e){return false;}}
    private static Object call(Class<?> c,String name,Class<?>[] args,Object... values)throws Exception{Method m=c.getDeclaredMethod(name,args);m.setAccessible(true);return m.invoke(null,values);}
    private static String profileCheck(Context c){
        try{
            Class<?> profiles=Class.forName("io.mesalabs.unica.keybox.Profiles",false,c.getClassLoader()),keybox=Class.forName("io.mesalabs.unica.keybox.Keybox",false,c.getClassLoader());
            String raw=Settings.Secure.getString(c.getContentResolver(),"unica_keybox_profiles");
            if(raw!=null&&raw.getBytes(StandardCharsets.UTF_8).length>262144)return "Problem found — profile configuration exceeds its size limit.";
            JSONObject root=(JSONObject)call(profiles,"load",new Class<?>[]{String.class},raw);JSONArray rows=root.getJSONArray("profiles");String current=Settings.Secure.getString(c.getContentResolver(),"unica_keybox_data");int active=0,targets=0;boolean all=false,expired=false,aosp=false;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);JSONArray apps=row.getJSONArray("apps");boolean applies=row.getString("scope").equals("all")||apps.length()>0;if(!applies)continue;active++;targets+=apps.length();all|=row.getString("scope").equals("all");
                String xml=(String)call(profiles,"xml",new Class<?>[]{JSONObject.class,JSONObject.class,String.class},root,row,current);
                if(xml==null||xml.trim().isEmpty())return "Problem found — a targeted profile has no selected keybox. Select a keybox in Manage Keybox → Profiles.";
                Object parsed;try{parsed=call(keybox,"parse",new Class<?>[]{String.class,boolean.class},xml,false);}catch(InvocationTargetException invalid){parsed=call(keybox,"parse",new Class<?>[]{String.class,boolean.class},xml,true);aosp=true;}
                Field validity=keybox.getDeclaredField("expired");validity.setAccessible(true);expired|=validity.getBoolean(parsed);
            }
            if(active==0)return "Not verified — no profile targets any app. Review target apps in Manage Keybox → Profiles.";
            if(expired)return "Problem found — a targeted keybox contains an expired or not-yet-valid certificate.";
            if(aosp)return "Problem found — a targeted profile uses an AOSP software test keybox; it is not a hardware attestation identity.";
            return "Configured — "+active+" targeted profiles, "+targets+" explicit target entries"+(all?" and a remaining-apps scope":"")+". Profile structure, selected keybox chains and signing keys passed local validation. Certificate revocation and app-specific use are not checked.";
        }catch(ClassNotFoundException|NoSuchMethodException e){return "Not verified — the compatible keybox/profile validator is unavailable.";}
        catch(SecurityException e){return "Not verified — profile or keybox settings could not be read.";}
        catch(Exception e){return "Problem found — profile or selected keybox validation failed. Review profiles and keyboxes in Manage Keybox. No signing material was included in this report.";}
    }
    static String collect(Context context){
        StringBuilder out=new StringBuilder("PIF and TEE setup check — this device, read-only\nChecked: "+java.text.DateFormat.getDateTimeInstance().format(new Date())+"\n\n");
        boolean pif=feature(context,"io.mesalabs.unica.PlayIntegrityHooks");out.append("PIF configuration: ");
        try{
            Map<String,String> values=new LinkedHashMap<>();for(String key:new String[]{"unica.pif","pif.spoof_build","pif.spoof_props","pif.vending_build","pif.fingerprint","pif.security_patch","pif.first_api_level","pif.brand","pif.product","pif.device","pif.release","pif.id","pif.incremental","pif.type","pif.tags","pif.model","pif.manufacturer"})values.put(key,property("persist.sys."+key));
            String errors=pifErrors(values);boolean enabled=values.get("unica.pif").isEmpty()||on(values.get("unica.pif"));
            if(!pif)out.append("Not verified — built-in PIF class is unavailable.");else if(!errors.isEmpty())out.append("Problem found — ").append(errors);else if(!enabled)out.append("Not verified — PIF is switched off.");else out.append("Configured — PIF enabled; explicit overrides passed format/size checks. Empty fields use ROM defaults, whose effective values are not independently validated.");
            if("false".equals(values.get("pif.spoof_build"))||"0".equals(values.get("pif.spoof_build"))||"false".equals(values.get("pif.spoof_props"))||"0".equals(values.get("pif.spoof_props")))out.append(" One or more spoofing options are off; check whether this is intentional.");
        }catch(Exception e){out.append("Not verified — PIF properties could not be read.");}
        out.append("\nPIF runtime: ").append(pifLogCheck(context));
        out.append("\n\nTEE configuration: ").append(profileCheck(context));
        try{out.append("\nTEE runtime: ").append(runtime(on(property("persist.sys.unica.keybox")),fresh(property("sys.unica.teesim.heartbeat"),SystemClock.elapsedRealtime()),"running".equals(property("init.svc.unica-teesim"))&&fresh(property("sys.unica.teesim.ready"),SystemClock.elapsedRealtime()),"1".equals(property("sys.unica.teesim.connected")),"1".equals(property("sys.unica.teesim.ack")),"1".equals(property("sys.unica.teesim.loaded"))));}catch(Exception e){out.append("\nTEE runtime: Not verified — backend properties unavailable.");}
        return out.append("\n\nVerification: reproduce the affected app action, run this check again, and collect recent device logs for crashes. Use Manage Keybox to check certificate revocation separately. A fresh external Play Integrity verdict is required to establish Google acceptance. No configuration, keys, apps or processes were changed.\n").toString();
    }
    static String pifEvidence(String logs){
        boolean success=false,error=false;
        for(String line:logs.split("\\r?\\n")){
            if(!line.matches(".*\\sUnicaPIF\\s*:.*"))continue;
            success|=line.contains("Initialized com.google.android.gms.unstable: Build=1 nativeProps=1");
            error|=line.matches(".*(?:Native property virtualization failed|Build update failed|Startup error:|Native startup failed|Invalid security patch date|Invalid first API level|Fingerprint must have|Empty fingerprint component|Identity field exceeds).*" );
        }
        if(error)return "Problem found — recent PIF log buffer contains startup/configuration failures. Reproduce with the affected app and collect device logs. Old errors can remain after a configuration change.";
        if(success)return "Not verified — successful GMS unstable-process initialization was observed in the recent buffer. This is historical evidence, not proof that the current process uses the current settings or that Google accepts it.";
        return "Not verified — no matching GMS initialization evidence in the recent log buffer. Missing logs do not prove failure.";
    }
    private static String pifLogCheck(Context c){
        File file=null;Process process=null;
        try{file=File.createTempFile("setup-check-",".log",c.getCacheDir());process=new ProcessBuilder("/system/bin/logcat","-b","all","-d","-t","2000","-v","threadtime").redirectErrorStream(true).redirectOutput(file).start();if(!process.waitFor(8,TimeUnit.SECONDS)||process.exitValue()!=0)return "Not verified — recent device logs are unavailable.";try(InputStream in=new FileInputStream(file)){return pifEvidence(LogTools.read(in,LogTools.MAX_IMPORT));}}
        catch(Exception e){return "Not verified — recent device logs are unavailable.";}
        finally{if(process!=null)process.destroyForcibly();if(file!=null)file.delete();}
    }
}
