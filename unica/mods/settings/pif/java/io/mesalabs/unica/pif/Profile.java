/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.pif;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/** The keys actually consumed by the ROM backend. Unknown keys never silently succeed. */
public final class Profile {
    public static final LinkedHashMap<String,String> KEYS = new LinkedHashMap<>();
    static {
        for(String key:new String[]{"FINGERPRINT","MANUFACTURER","MODEL","BRAND","PRODUCT","DEVICE","RELEASE","ID","INCREMENTAL","TYPE","TAGS","SECURITY_PATCH"})
            KEYS.put(key,key.toLowerCase(Locale.ROOT));
        KEYS.put("DEVICE_INITIAL_SDK_INT","first_api_level");
        KEYS.put("spoofBuild","spoof_build"); KEYS.put("spoofProps","spoof_props"); KEYS.put("spoofVendingBuild","vending_build");
    }
    public static boolean flag(String key) {return key.startsWith("spoof");}
    public static LinkedHashMap<String,String> parse(String text, boolean downloaded) throws Exception {
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        try(BufferedReader reader=new BufferedReader(new StringReader(text))) {
            String line;
            while((line=reader.readLine())!=null) {
                line=line.trim(); if(line.isEmpty()||line.startsWith("#"))continue;
                int split=line.indexOf('=');
                if(split<1)throw new IllegalArgumentException("Expected KEY=value: "+line);
                String key=line.substring(0,split).trim(), value=line.substring(split+1).trim();
                if(!KEYS.containsKey(key))throw new IllegalArgumentException("Unsupported key: "+key);
                // Fetch only identity. Preserve the user's current switches.
                if(downloaded&&flag(key))continue;
                if(result.put(key,value)!=null)throw new IllegalArgumentException("Duplicate key: "+key);
            }
        }
        validate(result); return result;
    }
    public static void validate(Map<String,String> data) throws Exception {
        for(Map.Entry<String,String> entry:data.entrySet()) {
            String key=entry.getKey(), value=entry.getValue();
            if(!KEYS.containsKey(key))throw new IllegalArgumentException("Unsupported key: "+key);
            if(value.getBytes(StandardCharsets.UTF_8).length>=92||value.chars().anyMatch(c->c<32||c==127))
                throw new IllegalArgumentException(key+" must fit 91 UTF-8 bytes without control characters");
            if(flag(key)&&!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException(key+" must be true or false");
        }
        String fp=data.getOrDefault("FINGERPRINT","");
        String[] parts=fp.split("[/:]",-1);
        if(parts.length!=8||Arrays.stream(parts).anyMatch(String::isEmpty)||!fp.matches("[^\\s]+"))
            throw new IllegalArgumentException("FINGERPRINT must have eight nonempty components");
        String patch=data.getOrDefault("SECURITY_PATCH","null");
        if(!patch.isEmpty()&&!patch.equals("null"))LocalDate.parse(patch);
        String api=data.getOrDefault("DEVICE_INITIAL_SDK_INT","null");
        if(!api.isEmpty()&&!api.equals("null")&&(!api.matches("[0-9]{1,2}")||Integer.parseInt(api)<1))
            throw new IllegalArgumentException("DEVICE_INITIAL_SDK_INT must be 1–99 or null");
    }
    public static String format(Map<String,String> data) {
        StringBuilder text=new StringBuilder();
        for(String key:KEYS.keySet())if(data.containsKey(key))text.append(key).append('=').append(data.get(key)).append('\n');
        return text.toString();
    }
    public static LinkedHashMap<String,String> replacement(Map<String,String> identity,Map<String,String> current) {
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        for(String key:KEYS.keySet()) result.put(key,flag(key)?identity.getOrDefault(key,current.getOrDefault(key,key.equals("spoofVendingBuild")?"false":"true")):identity.getOrDefault(key,"null"));
        return result;
    }
}
