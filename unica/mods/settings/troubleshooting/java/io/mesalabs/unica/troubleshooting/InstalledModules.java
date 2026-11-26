/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.troubleshooting;
import android.content.Context;
import android.content.pm.*;
import org.json.*;
import java.io.*;
import java.util.*;
/** Explicit rules only: duplicate purpose is separate from verified conflicts. */
final class InstalledModules {
    static JSONArray scan(Context context,JSONObject state)throws Exception {
        JSONArray conflicts=new JSONArray();if(state==null||!state.optBoolean("available"))return conflicts;
        return scan(context,state,new File("/system/etc/unica/troubleshooting-lsposed-conflicts.json"),new File("/system/etc/unica/troubleshooting-lsposed-purposes.json"));
    }
    static JSONArray scan(Context context,JSONObject state,File registry,File purposes)throws Exception {
        JSONArray conflicts=new JSONArray();if(state==null||!state.optBoolean("available"))return conflicts;
        scanPurposes(context,state,purposes,conflicts);
        if(!registry.isFile())return conflicts;
        JSONObject registryData;try(InputStream in=new FileInputStream(registry)){registryData=new JSONObject(LogTools.read(in,262144));}
        if(registryData.optInt("schema")!=1)return conflicts;
        JSONArray rules=registryData.optJSONArray("rules"),modules=state.optJSONArray("modules");if(rules==null||modules==null)return conflicts;
        state.put("confirmed_rule_count",rules.length());
        PackageManager pm=context.getPackageManager();Set<String> reported=new HashSet<>();
        for(int i=0;i<modules.length();i++){
            JSONObject module=modules.getJSONObject(i);if(!module.optBoolean("enabled"))continue;
            String pkg=module.optString("package");PackageInfo info;try{info=pm.getPackageInfo(pkg,0);}catch(PackageManager.NameNotFoundException missing){continue;}
            for(int j=0;j<rules.length();j++){
                JSONObject rule=rules.getJSONObject(j);if(!rule.optBoolean("confirmed")||!pkg.equals(rule.optString("package"))||info.getLongVersionCode()!=rule.optLong("version_code",-1))continue;
                String cert=rule.optString("signer_sha256"),scope=rule.optString("scope"),feature=rule.optString("unica_mod"),gate=rule.optString("feature_class");
                if(!cert.matches("[0-9a-f]{64}")||scope.isEmpty()||feature.isEmpty()||gate.isEmpty())continue;
                byte[] digest=new byte[32];for(int k=0;k<32;k++)digest[k]=(byte)Integer.parseInt(cert.substring(k*2,k*2+2),16);
                if(!pm.hasSigningCertificate(pkg,digest,PackageManager.CERT_INPUT_SHA256))continue;
                try{Class.forName(gate,false,context.getClassLoader());}catch(ClassNotFoundException|LinkageError missing){continue;}
                JSONArray scopes=module.optJSONArray("scopes");boolean targeted=false;if(scopes!=null)for(int k=0;k<scopes.length();k++)targeted|=scope.equals(scopes.optString(k));
                if(!targeted||!reported.add(pkg+"/"+feature))continue;
                conflicts.put(new JSONObject().put("package",pkg).put("name",pm.getApplicationLabel(info.applicationInfo).toString()).put("confirmed",true).put("unica_mod",feature));
            }
        }
        return conflicts;
    }
    private static void scanPurposes(Context context,JSONObject state,File registry,JSONArray findings)throws Exception {
        state.put("purpose_rule_count",0);if(!registry.isFile())return;
        JSONObject data;try(InputStream in=new FileInputStream(registry)){data=new JSONObject(LogTools.read(in,262144));}
        if(data.optInt("schema")!=1)return;
        JSONArray rules=data.optJSONArray("rules"),modules=state.optJSONArray("modules");if(rules==null||modules==null)return;
        state.put("purpose_rule_count",rules.length());Set<String> reported=new HashSet<>();PackageManager pm=context.getPackageManager();
        for(int i=0;i<modules.length();i++){
            JSONObject module=modules.getJSONObject(i);if(!module.optBoolean("enabled"))continue;
            String pkg=module.optString("package");PackageInfo info;try{info=pm.getPackageInfo(pkg,0);}catch(PackageManager.NameNotFoundException missing){continue;}
            for(int j=0;j<rules.length();j++){
                JSONObject rule=rules.getJSONObject(j);String feature=rule.optString("unica_mod"),gate=rule.optString("feature_class"),scope=rule.optString("scope");
                if(!pkg.equals(rule.optString("package"))||feature.isEmpty()||gate.isEmpty()||scope.isEmpty())continue;
                JSONArray scopes=module.optJSONArray("scopes");boolean targeted=false;if(scopes!=null)for(int k=0;k<scopes.length();k++)targeted|=scope.equals(scopes.optString(k));
                if(!targeted)continue;
                try{Class.forName(gate,false,context.getClassLoader());}catch(ClassNotFoundException|LinkageError missing){continue;}
                if(!reported.add(pkg+"/"+feature))continue;
                findings.put(new JSONObject().put("package",pkg).put("name",pm.getApplicationLabel(info.applicationInfo).toString()).put("confirmed",false).put("duplicate",true).put("unica_mod",feature).put("scope",scope));
            }
        }
    }

}
