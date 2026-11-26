/* Dressroom Advanced native transport; see LICENSE.upstream and PROVENANCE.txt. */
package dev.denver.dressroomadvanced;
import android.content.Context;
import android.os.Bundle;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Atomic, credential-encrypted JSON in DressRoom private /data/user/<user>/files. */
final class NativeProfileStore {
    private final AtomicFile file;
    NativeProfileStore(Context context) { file = new AtomicFile(new File(context.getFilesDir(), "dressroom_advanced_profiles_v1.json")); }
    private JSONObject load() {
        try (FileInputStream input = file.openRead(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] block = new byte[8192]; int size;
            while ((size = input.read(block)) != -1) {
                if (out.size() + size > 8 * 1024 * 1024) throw new IOException("Profile database too large");
                out.write(block, 0, size);
            }
            JSONObject result = new JSONObject(out.toString("UTF-8"));
            if (result.optInt("schema") != 1) throw new IllegalStateException("Unknown profile schema");
            return result;
        } catch (FileNotFoundException missing) {
            JSONObject empty = new JSONObject();
            try { empty.put("schema", 1); empty.put("profiles", new JSONObject()); }
            catch (JSONException impossible) { throw new IllegalStateException(impossible); }
            return empty;
        } catch (IOException | JSONException error) {
            // Never replace an unreadable database with an empty one on the next write.
            throw new IllegalStateException("Could not read Dressroom Advanced profiles", error);
        }
    }
    private void save(JSONObject database) {
        FileOutputStream output = null;
        try {
            byte[] bytes = database.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 8 * 1024 * 1024) throw new IOException("Profile database too large");
            output = file.startWrite(); output.write(bytes); file.finishWrite(output);
        } catch (IOException error) { if (output != null) file.failWrite(output); throw new IllegalStateException("Profile commit failed", error); }
    }
    static String identity(String scope, Bundle data) {
        if ("extras".equals(scope)) return "extras:" + (data == null ? 0 : data.getInt("layoutScreen", 0));
        if (!"clock".equals(scope) && !"widgets".equals(scope)) throw new IllegalArgumentException("Unknown profile scope");
        if (data == null || data.getInt("wallpaperId", -1) < 0) throw new IllegalArgumentException("Wallpaper identity required");
        int screen = data.getInt("layoutScreen", 0), orientation = data.getInt("layoutOrientation", 0);
        if (screen < 0 || screen > 2 || orientation < 0 || orientation > 2) throw new IllegalArgumentException("Invalid display identity");
        String key = scope + ":" + data.getInt("wallpaperId") + ":" + screen + ":" + orientation;
        if ("clock".equals(scope)) {
            int type = data.getInt("type", -1), font = data.getInt("font", -1);
            if (type < 0 || font < 0) throw new IllegalArgumentException("Clock and font identity required");
            key += ":" + type + ":" + font;
        }
        return key;
    }
    synchronized Bundle read(String scope, Bundle identity) {
        try {
            JSONObject profile = load().getJSONObject("profiles").optJSONObject(identity(scope, identity));
            if (profile == null && "widgets".equals(scope)) profile=load().getJSONObject("profiles").optJSONObject("widget-depth:"+identity.getInt("wallpaperId")+":"+identity.getInt("layoutScreen",0));
            if (profile == null) return new Bundle();
            Bundle result = decode(profile);
            String requested = identity == null ? "" : identity.getString("scene", "");
            String saved = result.getString("scene", "");
            if (!requested.isEmpty() && !saved.isEmpty() && !requested.equals(saved)) return new Bundle();
            if ("clock".equals(scope) && identity != null) {
                String fontPath = identity.getString("fontPath", "");
                String imported = result.getString("fontFile", "");
                String axisKey = result.getBoolean("followDisplayFont") ? "display:"+result.getString("fontSourceIdentity", "") : imported.isEmpty() ? fontPath : "file:" + imported;
                Bundle profiles = result.getBundle("fontAxisProfiles");
                result.remove("fontAxes");
                Bundle widths = result.getBundle("fontWidths");
                result.remove("fontWidth");
                if (widths != null && widths.containsKey(fontPath)) result.putFloat("fontWidth", ((Number)widths.get(fontPath)).floatValue());
                if (profiles != null && profiles.containsKey(axisKey)) result.putBundle("fontAxes", profiles.getBundle(axisKey));
                if (!fontPath.isEmpty()) result.putString("fontPath", fontPath);
            }
            if ("widgets".equals(scope)) {
                JSONObject policy=load().getJSONObject("profiles").optJSONObject("widget-depth:"+identity.getInt("wallpaperId")+":"+identity.getInt("layoutScreen",0));
                if(policy!=null){Bundle shared=decode(policy);result.putInt("depth",shared.getInt("depth"));result.putInt("depthLayouts",shared.getInt("depthLayouts"));}
            }
            result.putBoolean("found", true); return result;
        } catch (JSONException error) { throw new IllegalStateException("Invalid stored profile", error); }
    }
    synchronized Bundle commit(String scope,Bundle profile) {
        JSONObject database=load();put(database,scope,profile);save(database);
        Bundle result=new Bundle();result.putBoolean("saved",true);return result;
    }
    synchronized Bundle commitBatch(java.util.ArrayList<Bundle> operations) {
        if(operations==null||operations.isEmpty()||operations.size()>64)throw new IllegalArgumentException("Invalid Apply batch");
        JSONObject database=load();
        for(Bundle operation:operations)put(database,operation.getString("scope"),operation.getBundle("profile"));
        save(database);Bundle result=new Bundle();result.putBoolean("saved",true);return result;
    }
    private static void put(JSONObject database,String scope,Bundle profile) {
        if (profile == null) throw new IllegalArgumentException("Missing profile");
        try {
            String key = identity(scope, profile);
            Bundle saved = new Bundle(profile);
            if ("clock".equals(scope)) {
                JSONObject previous = database.getJSONObject("profiles").optJSONObject(key);
                Bundle axes = previous == null ? null : decode(previous).getBundle("fontAxisProfiles");
                axes = axes == null ? new Bundle() : new Bundle(axes);
                String imported = saved.getString("fontFile", "");
                String axisKey = saved.getBoolean("followDisplayFont") ? "display:"+saved.getString("fontSourceIdentity", "") : imported.isEmpty() ? saved.getString("fontPath", "") : "file:" + imported;
                if (!axisKey.isEmpty() && saved.containsKey("fontAxes")) axes.putBundle(axisKey, saved.getBundle("fontAxes"));
                saved.putBundle("fontAxisProfiles", axes);
                Bundle widths = previous == null ? null : decode(previous).getBundle("fontWidths");
                widths = widths == null ? new Bundle() : new Bundle(widths);
                String fontPath = saved.getString("fontPath", "");
                Object width = saved.get("fontWidth");
                if (!fontPath.isEmpty() && width instanceof Number && Float.isFinite(((Number)width).floatValue()))
                    widths.putFloat(fontPath, ((Number)width).floatValue());
                saved.putBundle("fontWidths", widths);
                if (!imported.isEmpty() || saved.getBoolean("followDisplayFont") || (saved.containsKey("fontNativeStretch") && !saved.getBoolean("fontNativeStretch"))) {
                    saved.putBoolean("manual", false); saved.putBoolean("depthOwnsManual", false);
                    saved.remove("manualHeights"); saved.remove("manualProgress");
                }
            }
            database.getJSONObject("profiles").put(key, encode(saved));
            if("widgets".equals(scope)) {
                String policy="widget-depth:"+profile.getInt("wallpaperId")+":"+profile.getInt("layoutScreen",0);
                if(saved.getInt("depthLayouts")==0) database.getJSONObject("profiles").remove(policy);
                else {Bundle shared=new Bundle();shared.putInt("depth",saved.getInt("depth"));shared.putInt("depthLayouts",saved.getInt("depthLayouts"));database.getJSONObject("profiles").put(policy,encode(shared));}
            }
        } catch (JSONException error) { throw new IllegalArgumentException("Invalid profile", error); }
    }
    private static JSONObject encode(Bundle data) throws JSONException {
        JSONObject object = new JSONObject();
        for (String key : data.keySet()) {
            Object value = data.get(key); if (value == null) continue;
            JSONObject typed = new JSONObject();
            if (value instanceof Bundle) { typed.put("type", "bundle"); typed.put("value", encode((Bundle)value)); }
            else if (value instanceof String || value instanceof Boolean || value instanceof Number) {
                if (value instanceof Float && !Float.isFinite((Float)value) || value instanceof Double && !Double.isFinite((Double)value)) continue;
                typed.put("type", value instanceof String ? "string" : value instanceof Boolean ? "boolean" : value instanceof Float ? "float" : value instanceof Double ? "double" : value instanceof Long ? "long" : "int");
                typed.put("value", value);
            } else if (value instanceof java.util.ArrayList) {
                JSONArray array = new JSONArray();
                for (Object entry : (java.util.ArrayList<?>)value) {
                    if (!(entry instanceof Bundle)) throw new IllegalArgumentException("Only Bundle lists are supported");
                    array.put(encode((Bundle)entry));
                }
                typed.put("type", "bundles"); typed.put("value", array);
            } else if (value instanceof float[] || value instanceof int[] || value instanceof String[] || value instanceof long[]) {
                JSONArray array = new JSONArray(); int count = java.lang.reflect.Array.getLength(value);
                if (count > 16384) throw new IllegalArgumentException("Profile array too large");
                for (int i = 0; i < count; i++) array.put(java.lang.reflect.Array.get(value, i));
                typed.put("type", value instanceof float[] ? "floats" : value instanceof int[] ? "ints" : value instanceof long[] ? "longs" : "strings"); typed.put("value", array);
            } else throw new IllegalArgumentException("Unsupported profile field: " + key);
            object.put(key, typed);
        }
        return object;
    }
    private static Bundle decode(JSONObject data) throws JSONException {
        Bundle out = new Bundle(); java.util.Iterator<String> keys = data.keys();
        while (keys.hasNext()) {
            String key = keys.next(); JSONObject item = data.getJSONObject(key); String type = item.getString("type");
            switch (type) {
                case "bundle": out.putBundle(key, decode(item.getJSONObject("value"))); break;
                case "string": out.putString(key, item.getString("value")); break;
                case "boolean": out.putBoolean(key, item.getBoolean("value")); break;
                case "float": out.putFloat(key, (float)item.getDouble("value")); break;
                case "double": out.putDouble(key, item.getDouble("value")); break;
                case "long": out.putLong(key, item.getLong("value")); break;
                case "int": out.putInt(key, item.getInt("value")); break;
                case "bundles": { JSONArray a=item.getJSONArray("value"); java.util.ArrayList<Bundle> v=new java.util.ArrayList<>(); for(int i=0;i<a.length();i++)v.add(decode(a.getJSONObject(i)));out.putParcelableArrayList(key,v);break; }
                case "floats": { JSONArray a=item.getJSONArray("value"); float[] v=new float[a.length()]; for(int i=0;i<v.length;i++)v[i]=(float)a.getDouble(i);out.putFloatArray(key,v);break; }
                case "ints": { JSONArray a=item.getJSONArray("value"); int[] v=new int[a.length()]; for(int i=0;i<v.length;i++)v[i]=a.getInt(i);out.putIntArray(key,v);break; }
                case "longs": { JSONArray a=item.getJSONArray("value"); long[] v=new long[a.length()]; for(int i=0;i<v.length;i++)v[i]=a.getLong(i);out.putLongArray(key,v);break; }
                case "strings": { JSONArray a=item.getJSONArray("value"); String[] v=new String[a.length()]; for(int i=0;i<v.length;i++)v[i]=a.getString(i);out.putStringArray(key,v);break; }
                default: throw new JSONException("Unknown profile value type");
            }
        }
        return out;
    }
}
