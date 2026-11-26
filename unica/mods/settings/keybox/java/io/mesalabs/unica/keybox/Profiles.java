/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.keybox;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** One atomic Settings.Secure value; signing material is never exported or logged. */
final class Profiles {
    static final String SETTING="unica_keybox_profiles";
    static final String[] DEFAULT_APPS={"io.github.vvb2060.keyattestation","com.google.android.apps.walletnfcrel","com.google.android.gms","com.android.vending"};
    static boolean validKeyboxName(String name){return !name.equals("current")&&!name.equals(".")&&!name.equals("..")&&!name.trim().isEmpty()&&name.length()<=255&&!name.matches("(?s).*[\\x00-\\x1f\\x7f/\\\\].*");}
    static String fingerprint(String xml)throws Exception {
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(xml.getBytes(StandardCharsets.UTF_8));
        StringBuilder result=new StringBuilder();for(byte b:digest)result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();
    }
    static JSONObject keyboxMetadata(String name,String xml)throws Exception{return new JSONObject().put("name",name).put("sha256",fingerprint(xml));}
    static String keyboxName(String metadata,String xml){
        try{JSONObject value=new JSONObject(metadata);String name=value.getString("name");if(validKeyboxName(name)&&value.getString("sha256").equals(fingerprint(xml)))return name;}catch(Exception ignored){}
        return "Imported keybox (filename unavailable)";
    }
    static final String[] IDS={"brand","device","product","manufacturer","model","serial","imei","meid","imei2"};
    static JSONObject profile(String id)throws JSONException {
        return new JSONObject().put("id",id).put("keybox","current").put("scope","selected")
            .put("apps",new JSONArray()).put("mode","patch");
    }
    static JSONObject defaults()throws JSONException {
        return new JSONObject().put("version",1).put("keyboxes",new JSONObject())
            .put("profiles",new JSONArray().put(profile("unica").put("apps",new JSONArray(Arrays.asList(DEFAULT_APPS)))));
    }
    static JSONObject load(String raw)throws JSONException {
        JSONObject root=raw==null||raw.trim().isEmpty()?defaults():new JSONObject(raw);
        validate(root);return root;
    }
    static void validate(JSONObject root)throws JSONException {
        if(root.toString().getBytes(StandardCharsets.UTF_8).length>Keybox.MAX_BYTES||root.getInt("version")!=1)throw new JSONException("Configuration too large or unsupported");
        JSONObject boxes=root.getJSONObject("keyboxes");
        if(boxes.length()>8)throw new JSONException("At most eight saved keyboxes");
        for(Iterator<String> it=boxes.keys();it.hasNext();){String key=it.next();if(!validKeyboxName(key))throw new JSONException("Invalid keybox name");boxes.getString(key);}
        JSONArray rows=root.getJSONArray("profiles");
        if(rows.length()<1||rows.length()>8)throw new JSONException("Keep between one and eight profiles");
        Set<String> ids=new HashSet<>(),packages=new HashSet<>();int all=0;
        for(int i=0;i<rows.length();i++){
            JSONObject p=rows.getJSONObject(i);String id=p.getString("id");
            if(!id.matches("[A-Za-z0-9_-]{1,40}")||!ids.add(id))throw new JSONException("Use a unique name: letters, numbers, underscore or dash");
            String box=p.getString("keybox");if(!box.equals("current")&&!boxes.has(box))throw new JSONException("Missing keybox");
            String mode=p.optString("mode","patch");if(!mode.equals("patch")&&!mode.equals("generation"))throw new JSONException("Invalid operation mode");
            String scope=p.getString("scope");if(scope.equals("all")){if(++all>1)throw new JSONException("Only one profile can target remaining apps");}else if(!scope.equals("selected"))throw new JSONException("Invalid target scope");
            Set<String> local=new HashSet<>();JSONArray apps=p.getJSONArray("apps");
            if(apps.length()>4096)throw new JSONException("Too many target packages");
            for(int j=0;j<apps.length();j++){String pkg=apps.getString(j);if(!pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))throw new JSONException("Invalid app");if(local.add(pkg)&&!packages.add(pkg))throw new JSONException("An app belongs to another profile");}
            String os=p.optString("osVersion","system_property");if(!os.equals("system_property")&&!os.equals("harvested")&&!os.isEmpty()&&!os.matches("[0-9]{1,6}(\\.[0-9]{1,2}){0,2}"))throw new JSONException("Use an Android version or encoded integer");
            JSONObject levels=p.optJSONObject("patchLevel");if(levels!=null)for(String field:new String[]{"system","vendor","boot"}){String v=levels.optString(field,"system_property");if(!Arrays.asList("system_property","harvested","","no","today","YYYY-MM-05").contains(v)){if(!v.matches("[0-9]{4}-[0-9]{2}(-[0-9]{2})?"))throw new JSONException("Use YYYY-MM-DD, harvested, system_property or no");try{LocalDate.parse(v.length()==7?v+"-01":v);}catch(Exception e){throw new JSONException("Invalid patch date");}}}
            JSONObject identity=p.optJSONObject("deviceIds");if(identity!=null)for(String field:IDS){String v=identity.optString(field,"");if(v.length()>128||v.matches("(?s).*[\\x00-\\x1f].*"))throw new JSONException("Invalid identity value");}
        }
    }
    static JSONObject copy(JSONObject source)throws JSONException{return new JSONObject(source.toString());}
    static String xml(JSONObject root,JSONObject profile,String current)throws JSONException {
        String key=profile.getString("keybox");return key.equals("current")?current:root.getJSONObject("keyboxes").getString(key);
    }
}
