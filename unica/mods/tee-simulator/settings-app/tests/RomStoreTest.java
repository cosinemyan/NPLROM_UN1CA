/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.keybox;
import android.provider.Settings;
import org.json.*;

public final class RomStoreTest {
    interface Action { void run()throws Exception; }
    static void reject(Action action)throws Exception {try{action.run();throw new AssertionError("Accepted unsafe edit");}catch(IllegalArgumentException|JSONException expected){}}
    static JSONObject request(String key,Object value)throws Exception{return new JSONObject().put(key,value);}
    public static void main(String[] args)throws Exception {
        RomStore store=new RomStore(new android.content.Context());
        JSONObject original=Profiles.defaults();original.put("futureMetadata","keep");
        original.getJSONObject("keyboxes").put("My Keybox (1).xml","PRIVATE-SIGNER-CONTENT");
        JSONObject profile=original.getJSONArray("profiles").getJSONObject(0);
        profile.put("keybox","My Keybox (1).xml").put("scope","all").put("deviceIds",new JSONObject().put("model","legacy-preserved"));
        Settings.Secure.values.put(Profiles.SETTING,original.toString());
        Settings.Secure.values.put("unica_keybox_data","LEGACY-CURRENT-PRIVATE-CONTENT");
        JSONObject loaded=store.call("configLoad",new JSONObject()).getJSONObject("config");
        if(loaded.toString().contains("PRIVATE"))throw new AssertionError("Keybox secret exported to WebView");
        JSONObject edit=loaded.getJSONObject("profiles").getJSONObject("unica");
        if(!edit.getBoolean("autoIncludeNewApps"))throw new AssertionError("All-app scope lost");
        edit.getJSONObject("patchLevel").put("system","2026-11-05");
        JSONObject saved=store.call("configSave",request("config",loaded));
        JSONObject comparison=store.call("patchStatus",new JSONObject()).getJSONObject("profiles").getJSONObject("unica");
        if(!comparison.getString("state").equals("match")||!comparison.getString("effective").equals("2026-11"))throw new AssertionError("Explicit patch comparison failed");
        JSONObject after=new JSONObject(Settings.Secure.values.get(Profiles.SETTING));
        if(!after.getString("futureMetadata").equals("keep")||!after.getJSONObject("keyboxes").getString("My Keybox (1).xml").equals("PRIVATE-SIGNER-CONTENT"))throw new AssertionError("Storage clobbered");
        if(!after.getJSONArray("profiles").getJSONObject(0).getJSONObject("deviceIds").getString("model").equals("legacy-preserved"))throw new AssertionError("Existing identity lost");
        reject(()->store.call("configSave",request("config",loaded)));
        JSONObject fresh=store.call("configLoad",new JSONObject()).getJSONObject("config");
        store.call("keyboxRename",new JSONObject().put("from","My Keybox (1).xml").put("to","Renamed.xml"));
        after=new JSONObject(Settings.Secure.values.get(Profiles.SETTING));
        if(!after.getJSONArray("profiles").getJSONObject(0).getString("keybox").equals("Renamed.xml"))throw new AssertionError("Rename broke reference");
        final JSONObject stale=fresh;reject(()->store.call("configSave",request("config",stale)));
        reject(()->store.call("keyboxDelete",request("name","Renamed.xml")));
        reject(()->store.call("keyboxDelete",request("name",RomStore.CURRENT)));
        reject(()->store.call("keyboxRename",new JSONObject().put("from","Renamed.xml").put("to","../escape.xml")));
        reject(()->store.call("canaryInstall",new JSONObject()));
        reject(()->store.call("rkpDelete",new JSONObject()));
        reject(()->store.call("overridesSave",new JSONObject()));
        if(!Settings.Secure.values.get("unica_keybox_data").equals("LEGACY-CURRENT-PRIVATE-CONTENT"))throw new AssertionError("Current keybox modified");
        fresh=store.call("configLoad",new JSONObject()).getJSONObject("config");
        JSONObject renamed=fresh.getJSONObject("profiles");renamed.put("renamedProfile",renamed.getJSONObject("unica"));renamed.remove("unica");
        store.call("configSave",request("config",fresh));
        JSONObject renamedStored=new JSONObject(Settings.Secure.values.get(Profiles.SETTING)).getJSONArray("profiles").getJSONObject(0);
        if(!renamedStored.getString("id").equals("renamedProfile")||!renamedStored.getJSONObject("deviceIds").getString("model").equals("legacy-preserved"))throw new AssertionError("Profile rename lost metadata");
        fresh=store.call("configLoad",new JSONObject()).getJSONObject("config");
        fresh.getJSONObject("profiles").getJSONObject("renamedProfile").getJSONObject("patchLevel").put("system","2026-02-31");
        final JSONObject invalid=fresh;String before=Settings.Secure.values.get(Profiles.SETTING);
        reject(()->store.call("configSave",request("config",invalid)));
        if(!before.equals(Settings.Secure.values.get(Profiles.SETTING)))throw new AssertionError("Failed edit mutated settings");
        System.out.println("PASS ROM adapter: secret-free load, schema preservation, atomic rename, stale-edit rejection, invalid-date rejection, protected operations and current signer preservation");
    }
}
