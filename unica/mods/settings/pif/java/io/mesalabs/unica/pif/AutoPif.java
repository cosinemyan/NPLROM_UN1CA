/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.pif;

import org.json.*;
import java.io.IOException;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.*;

/** Native data-only equivalent of KOWX712's inject_s/module/autopif.sh.
 * Device selection is supplied by the same list used by the GitHub button.
 * No shell, downloaded code, module files or device properties are touched here.
 */
public final class AutoPif {
    public static final class Result {
        public final Map<String,String> profile;
        public final boolean inferredPatch;
        Result(Map<String,String> profile,boolean inferredPatch){this.profile=profile;this.inferredPatch=inferredPatch;}
    }
    public interface Fetcher {String get(String address,String referer,int limit)throws Exception;}
    public static Result fetch(String model,String product)throws Exception {return fetch(model,product,PixelSource::download);}
    public static Result fetch(String model,String product,Fetcher fetcher)throws Exception {
        if(!model.startsWith("Pixel ")||!product.matches("[a-z0-9]+_beta"))throw new IOException("Invalid Pixel selection");
        String device=product.substring(0,product.length()-5);
        int api=firstApi(device); // Unknown devices require an explicit mapping, never a guessed API.
        String flash=fetcher.get("https://flash.android.com",null,262144);
        Matcher config=Pattern.compile("data-client-config=[\"']([^\"']*)[\"']").matcher(flash);
        if(!config.find())throw new IOException("Google Flash Tool configuration format changed");
        Matcher key=Pattern.compile("AIza[\\w-]{20,}").matcher(config.group(1).replace("&quot;","\""));
        if(!key.find())throw new IOException("Google Flash Tool API configuration unavailable");
        String builds=fetcher.get("https://content-flashstation-pa.googleapis.com/v1/builds?product="+product+"&key="+key.group(),"https://flash.android.com",4194304);
        JSONObject selected=latest(builds,product);
        String id=selected.getString("releaseCandidateName"),incremental=selected.getString("buildId");
        if(!id.matches("[A-Za-z0-9.]+")||!incremental.matches("[0-9]+"))throw new IOException("Invalid Google Canary build identity");
        String canary=selected.getJSONObject("previewMetadata").getString("id");
        if(!canary.matches("canary-[0-9]{6}"))throw new IOException("Invalid Google Canary release month");
        String month=canary.substring(7,11)+"-"+canary.substring(11,13);
        YearMonth.parse(month);
        String patch=null;
        // Upstream falls back to the Canary month's fifth day if the bulletin cannot supply an exact SPL.
        try{patch=securityPatch(fetcher.get("https://source.android.com/docs/security/bulletin/pixel?hl=en",null,2097152),month);}catch(IOException unavailable){/* Report inferred SPL in the draft status below. */}
        boolean inferred=patch==null;if(inferred)patch=month+"-05";
        LinkedHashMap<String,String> identity=new LinkedHashMap<>();
        identity.put("FINGERPRINT","google/"+product+"/"+device+":CANARY/"+id+"/"+incremental+":user/release-keys");
        identity.put("MANUFACTURER","Google");identity.put("MODEL",model);
        identity.put("SECURITY_PATCH",patch);identity.put("DEVICE_INITIAL_SDK_INT",Integer.toString(api));
        Profile.validate(identity);
        return new Result(identity,inferred);
    }
    static JSONObject latest(String text,String product)throws Exception {
        JSONArray builds=new JSONObject(text).getJSONArray("flashstationBuild");JSONObject best=null;long newest=-1;
        for(int i=0;i<builds.length();i++) {
            JSONObject build=builds.getJSONObject(i),metadata=build.optJSONObject("previewMetadata");
            if(!product.equals(build.optString("product"))||metadata==null||!metadata.optBoolean("canary",false))continue;
            String number=build.optString("buildId");if(!number.matches("[0-9]{1,18}"))continue;
            long value=Long.parseLong(number);if(value>newest){best=build;newest=value;}
        }
        if(best==null)throw new IOException("No Google Canary build available for the selected Pixel");
        return best;
    }
    static String securityPatch(String html,String month)throws Exception {
        Matcher rows=Pattern.compile("<tr\\b[^>]*>(.*?)</tr>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(html);
        while(rows.find()) {
            String row=rows.group(1);
            if(!row.contains("/pixel/"+month.substring(0,4)+"/"+month)&&!row.matches("(?s).*<td\\b[^>]*>\\s*"+Pattern.quote(month)+"\\s*</td>.*"))continue;
            Matcher dates=Pattern.compile("<td\\b[^>]*>\\s*([0-9]{4}-[0-9]{2}-[0-9]{2})\\s*</td>",Pattern.CASE_INSENSITIVE).matcher(row);
            String patch=null;while(dates.find())patch=dates.group(1);
            if(patch!=null&&patch.startsWith(month+"-")){java.time.LocalDate.parse(patch);return patch;}
        }
        return null;
    }
    static int firstApi(String device)throws IOException {
        switch(device) {
            case "bluejay":return 32;
            case "panther":case "cheetah":case "lynx":case "felix":case "tangorpro":return 33;
            case "shiba":case "husky":case "akita":case "tokay":case "caiman":case "komodo":case "comet":return 34;
            case "tegu":case "stallion":return 35;
            case "frankel":case "blazer":case "mustang":case "rango":return 36;
            case "cubs":case "grizzly":case "kodiak":case "yogi":return 37;
            default:throw new IOException("Autopif first API mapping unavailable for "+device+"; use GitHub instead");
        }
    }
}
