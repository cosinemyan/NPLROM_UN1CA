/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.keybox;

import android.content.Context;
import android.content.pm.*;
import android.provider.Settings;
import android.os.SystemClock;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.*;

/** Allowlisted operations over existing ROM Settings storage; never standalone module files. */
final class RomStore {
    static final String CURRENT="ROM-current.xml";
    private final Context context;
    private static final String CURRENT_SETTING="unica_keybox_data";
    private final List<String> logRows=new ArrayList<>();
    private long logSeq=0;
    private long nextLogs=0;
    RomStore(Context context){this.context=context;}
    private JSONObject ok() throws JSONException{return new JSONObject().put("ok",true);}
    private String get(String key){return Settings.Secure.getString(context.getContentResolver(),key);}
    private void put(JSONObject root)throws Exception {
        Profiles.validate(root);
        PackageManager pm=context.getPackageManager();
        if(pm!=null){
            Map<Integer,String> claimed=new HashMap<>();JSONArray profiles=root.getJSONArray("profiles");
            for(int i=0;i<profiles.length();i++){
                JSONObject p=profiles.getJSONObject(i);JSONArray apps=p.getJSONArray("apps");
                for(int n=0;n<apps.length();n++){
                    int uid;try{uid=pm.getApplicationInfo(apps.getString(n),0).uid;}catch(PackageManager.NameNotFoundException missing){continue;}
                    if(uid<10000||uid>19999)throw new IllegalArgumentException("Only owner-user app UIDs can be targeted");
                    String previous=claimed.put(uid,p.getString("id"));
                    if(previous!=null&&!previous.equals(p.getString("id")))throw new IllegalArgumentException("Apps sharing a UID must belong to the same profile");
                }
            }
        }
        if(!Settings.Secure.putString(context.getContentResolver(),Profiles.SETTING,root.toString()))throw new IllegalStateException("Settings save failed");
    }
    private JSONObject root()throws Exception{return Profiles.load(get(Profiles.SETTING));}
    private String revision()throws Exception{return Profiles.fingerprint(String.valueOf(get(Profiles.SETTING))+"\n"+String.valueOf(get(CURRENT_SETTING)));}
    static String prop(String key){try{return (String)Class.forName("android.os.SystemProperties").getMethod("get",String.class,String.class).invoke(null,key,"");}catch(Exception e){return "";}}
    private String xml(String name,JSONObject root)throws Exception {
        String value=name.equals(CURRENT)?get(CURRENT_SETTING):root.getJSONObject("keyboxes").getString(name);
        if(value==null||value.trim().isEmpty())throw new IllegalArgumentException("No imported current ROM keybox");return value;
    }
    private void name(String value){if(value.equals(CURRENT)||!Profiles.validKeyboxName(value)||!value.endsWith(".xml"))throw new IllegalArgumentException("Reserved or invalid keybox name");}
    private JSONArray names(JSONObject root)throws Exception {
        if(root.getJSONObject("keyboxes").has(CURRENT))throw new IllegalArgumentException("A saved keybox uses the reserved ROM-current.xml alias; rename it in legacy Settings before using this app");
        JSONArray names=new JSONArray();String current=get(CURRENT_SETTING);if(current!=null&&!current.trim().isEmpty())names.put(CURRENT);
        for(Iterator<String> it=root.getJSONObject("keyboxes").keys();it.hasNext();)names.put(it.next());return names;
    }
    private JSONObject loadConfig()throws Exception {
        JSONObject map=new JSONObject(),root=root();JSONArray rows=root.getJSONArray("profiles");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i),p=Profiles.copy(row);p.remove("id");p.remove("scope");p.remove("deviceIds");p.put("_romSourceId",row.getString("id"));
            p.put("keybox",row.getString("keybox").equals("current")?CURRENT:row.getString("keybox"));
            p.put("autoIncludeNewApps",row.getString("scope").equals("all"));
            if(!p.has("patchLevel"))p.put("patchLevel",new JSONObject().put("system","system_property").put("vendor","system_property").put("boot","system_property"));
            if(!p.has("osVersion"))p.put("osVersion","system_property");
            map.put(row.getString("id"),p);
        }
        return ok().put("config",new JSONObject().put("version",1).put("profiles",map).put("_romRevision",revision()));
    }
    private JSONObject saveConfig(JSONObject config)throws Exception {
        // Reject stale editors, including imports/renames performed in another screen or Settings.
        if(!revision().equals(config.optString("_romRevision")))throw new IllegalArgumentException("Configuration changed. Reload before saving.");
        JSONObject root=root(),profiles=config.getJSONObject("profiles");JSONArray before=root.getJSONArray("profiles"),rows=new JSONArray();
        if(config.getInt("version")!=1||profiles.length()<1||profiles.length()>8)throw new IllegalArgumentException("Keep one to eight profiles");
        for(Iterator<String> it=profiles.keys();it.hasNext();){
            String id=it.next();JSONObject src=profiles.getJSONObject(id),p=Profiles.profile(id);
            for(int i=0;i<before.length();i++)if(before.getJSONObject(i).getString("id").equals(src.optString("_romSourceId",id)))p=Profiles.copy(before.getJSONObject(i));
            p.put("id",id);
            for(String field:Profiles.IDS)if(!src.optString(field,"").isEmpty())throw new IllegalArgumentException("ROM device identity is read-only");
            String key=src.getString("keybox");p.put("keybox",key.equals(CURRENT)?"current":key);
            p.put("mode",src.optString("mode","patch")).put("scope",src.optBoolean("autoIncludeNewApps")?"all":"selected");
            p.put("apps",src.getJSONArray("apps")).put("osVersion",src.optString("osVersion","system_property"));
            if(src.has("patchLevel"))p.put("patchLevel",src.getJSONObject("patchLevel"));rows.put(p);
        }
        root.put("profiles",rows);put(root);
        return ok().put("revision",revision());
    }
    private JSONObject packages()throws Exception {
        PackageManager pm=context.getPackageManager();Map<Integer,JSONArray> grouped=new TreeMap<>();Map<Integer,ApplicationInfo> reps=new HashMap<>();
        for(ApplicationInfo app:pm.getInstalledApplications(0)){if(app.uid<10000||app.uid>19999)continue;
            grouped.computeIfAbsent(app.uid,k->new JSONArray()).put(app.packageName);reps.putIfAbsent(app.uid,app);}
        JSONArray apps=new JSONArray();
        for(Map.Entry<Integer,JSONArray> e:grouped.entrySet()){
            ApplicationInfo a=reps.get(e.getKey());long installed=0;try{installed=pm.getPackageInfo(a.packageName,0).firstInstallTime;}catch(Exception ignored){}
            apps.put(new JSONObject().put("uid",a.uid).put("userId",0).put("packages",e.getValue()).put("label",pm.getApplicationLabel(a).toString())
                .put("system",(a.flags&ApplicationInfo.FLAG_SYSTEM)!=0).put("launchable",pm.getLaunchIntentForPackage(a.packageName)!=null).put("enabled",a.enabled)
                .put("installTime",installed).put("freq",0).put("lastUsed",0).put("recent",false));
        }
        return ok().put("firstAppUid",10000).put("users",new JSONArray().put(new JSONObject().put("id",0).put("name","Owner").put("managed",false))).put("apps",apps);
    }
    private JSONObject patchStatus()throws Exception {
        JSONObject root=root(),map=new JSONObject();JSONArray ps=root.getJSONArray("profiles");String pif=prop("persist.sys.pif.security_patch");
        for(int i=0;i<ps.length();i++){
            JSONObject p=ps.getJSONObject(i),patch=p.optJSONObject("patchLevel");String selected=patch==null?"system_property":patch.optString("system","system_property");
            String effective=resolveSystemPatch(selected);String expected=pif.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")?pif.substring(0,7):"";
            map.put(p.getString("id"),new JSONObject().put("selected",selected).put("effective",effective).put("pif",pif)
                .put("state",effective.isEmpty()||expected.isEmpty()?"unverified":effective.equals(expected)?"match":"mismatch"));
        }
        return ok().put("profiles",map).put("enabled",prop("persist.sys.unica.keybox")).put("pifEnabled",prop("persist.sys.unica.pif"))
            .put("service",prop("init.svc.unica-teesim")).put("ready",prop("sys.unica.teesim.ready"));
    }
    private String resolveSystemPatch(String value)throws Exception {
        if(value.equals("system_property")){String s=prop("ro.build.version.security_patch");return s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")?s.substring(0,7):"";}
        if(value.equals("no"))return "";
        if(value.equals("harvested")||value.isEmpty()){
            try{String s=Backend.call(new JSONObject().put("action","system")).getJSONObject("harvest").optString("osPatchLevel","");return s.matches("[0-9]{6}")?s.substring(0,4)+"-"+s.substring(4,6):"";}catch(Exception e){return "";}
        }
        java.time.LocalDate now=java.time.LocalDate.now();
        if(value.equals("today"))return now.toString().substring(0,7);
        if(value.equals("YYYY-MM-05")){if(now.getDayOfMonth()<5)now=now.minusMonths(1);return now.toString().substring(0,7);}
        return value.matches("[0-9]{4}-[0-9]{2}(-[0-9]{2})?")?value.substring(0,7):"";
    }
    private JSONObject status()throws Exception {
        JSONObject source=Backend.call(new JSONObject().put("action","system")),h=source.getJSONObject("harvest");
        JSONObject overrides=h.optJSONObject("overrides");if(overrides!=null)for(Iterator<String> it=overrides.keys();it.hasNext();){JSONObject v=overrides.optJSONObject(it.next());if(v!=null)v.put("editable",false);}
        // Frozen root-of-trust values remain visible but never writable through the app.
        long ready=0;try{ready=Long.parseLong(prop("sys.unica.teesim.ready"));}catch(Exception ignored){}
        boolean active=ready>0&&SystemClock.elapsedRealtime()-ready>=0&&SystemClock.elapsedRealtime()-ready<45000&&prop("sys.unica.teesim.connected").equals("1")&&prop("sys.unica.teesim.ack").equals("1")&&prop("sys.unica.teesim.loaded").equals("1");
        return ok().put("version","UN1CA ROM backend").put("harvest",h).put("scope",source.optJSONArray("scope"))
            .put("lib",new JSONObject().put("hook",active?source.optString("hook","unknown"):"unknown").put("api",source.opt("api"))).put("routingReady",active);
    }
    private JSONObject logs(JSONObject args)throws Exception {
        if(SystemClock.elapsedRealtime()>=nextLogs){logRows.clear();logRows.addAll(Diagnostics.read());nextLogs=SystemClock.elapsedRealtime()+3000;logSeq++;}
        long after=args.optLong("after",0);JSONArray rows=new JSONArray();
        if(after<logSeq*1000)for(int i=0;i<logRows.size();i++){
            String line=logRows.get(i);rows.put(new JSONObject().put("seq",logSeq*1000+i+1).put("level",line.substring(0,1)).put("tag","ROM").put("text",line.substring(Math.min(4,line.length()))));
        }
        return ok().put("lines",rows).put("nextAfter",logSeq*1000+logRows.size());
    }
    private JSONObject inspect(String name)throws Exception {
        String text=xml(name,root());Keybox key;try{key=Keybox.parse(text,false);}catch(Exception google){key=Keybox.parse(text,true);}JSONArray keys=new JSONArray();
        for(List<X509Certificate> chain:key.chains){JSONArray certs=new JSONArray();for(int i=0;i<chain.size();i++){
            X509Certificate c=chain.get(i);boolean valid=true;try{c.checkValidity();}catch(Exception e){valid=false;}
            certs.put(new JSONObject().put("index",i).put("subject",c.getSubjectX500Principal().toString()).put("issuer",c.getIssuerX500Principal().toString())
                .put("serial",c.getSerialNumber().toString(16)).put("notBefore",c.getNotBefore().toInstant().toString()).put("notAfter",c.getNotAfter().toInstant().toString())
                .put("expired",!valid).put("sigAlg",c.getSigAlgName()).put("keyAlgorithm",c.getPublicKey().getAlgorithm()).put("isCa",c.getBasicConstraints()>=0));
        }
        keys.put(new JSONObject().put("algorithm",chain.get(0).getPublicKey().getAlgorithm().equals("EC")?"ecdsa":"rsa").put("privateKeyPresent",true)
            .put("chainLength",chain.size()).put("chainVerified",true).put("revocationChecked",false).put("rootAuthority",key.aosp?"aosp":"google").put("certs",certs));}
        return ok().put("name",name).put("revocationListAvailable",false).put("keys",keys);
    }
    JSONObject call(String action,JSONObject args)throws Exception {
        switch(action){
        case "configLoad":return loadConfig();
        case "configSave":return saveConfig(args.getJSONObject("config"));
        case "keyboxList":return ok().put("names",names(root()));
        case "keyboxImport":{
            String n=args.getString("name"),text=args.getString("text");name(n);Keybox.parse(text,false);JSONObject r=root(),boxes=r.getJSONObject("keyboxes");
            if(boxes.has(n))throw new IllegalArgumentException("Keybox already exists; import under a new name to preserve existing signing material");
            boxes.put(n,text);put(r);return ok().put("name",n);
        }
        case "keyboxRename":{
            String from=args.getString("from"),to=args.getString("to");name(from);name(to);JSONObject r=root(),boxes=r.getJSONObject("keyboxes");
            if(!boxes.has(from)||boxes.has(to))throw new IllegalArgumentException("Missing source or duplicate name");boxes.put(to,boxes.getString(from));boxes.remove(from);
            JSONArray ps=r.getJSONArray("profiles");for(int i=0;i<ps.length();i++)if(ps.getJSONObject(i).getString("keybox").equals(from))ps.getJSONObject(i).put("keybox",to);
            put(r);return ok().put("name",to);
        }
        case "keyboxDelete":{
            String n=args.getString("name");name(n);JSONObject r=root();JSONArray ps=r.getJSONArray("profiles");
            for(int i=0;i<ps.length();i++)if(ps.getJSONObject(i).getString("keybox").equals(n))throw new IllegalArgumentException("Keybox is assigned to a profile; unassign it first");
            r.getJSONObject("keyboxes").remove(n);put(r);return ok();
        }
        case "patchStatus":return patchStatus();
        case "status":return status();
        case "packages":return packages();
        case "scope":return ok().put("baselineReady",true).put("profiles",Backend.call(new JSONObject().put("action","system")).getJSONArray("scope"));
        case "rescan":return Backend.call(new JSONObject().put("action","rescan"));
        case "keysDb":{
            JSONObject out=Backend.call(new JSONObject().put("action","keys").put("spoofedOnly",false).put("offset",args.optInt("offset",0)));
            return out.put("available",true).put("apiLevel",android.os.Build.VERSION.SDK_INT);
        }
        case "keysDbDelete":{
            JSONArray ids=args.getJSONArray("ids");if(!args.optBoolean("confirmed"))throw new IllegalArgumentException("Explicit confirmation required");
            return Backend.call(new JSONObject().put("action","deleteKeys").put("ids",ids).put("confirmed",true));
        }
        case "logs":return logs(args);
        case "keyboxInspect":return inspect(args.getString("name"));
        case "canary":return ok().put("installedVersion","UN1CA ROM backend").put("updateAvailable",false);
        case "overridesLoad":return ok().put("overrides",new JSONObject());
        default:throw new IllegalArgumentException("This operation is unavailable in the ROM interface");
        }
    }
}
