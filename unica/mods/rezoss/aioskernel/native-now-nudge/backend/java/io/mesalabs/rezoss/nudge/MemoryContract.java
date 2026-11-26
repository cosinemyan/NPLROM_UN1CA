package io.mesalabs.rezoss.nudge;

import java.io.IOException;
import org.json.*;

/** Text-only Memory analysis. Never repair malformed output or invent a result. */
final class MemoryContract {
    static final int MAX_INPUT_CHARS=100000, CONTEXT_TOKENS=20000, OUTPUT_TOKENS=4096, CONTEXT_MARGIN=256;
    private static String text(JSONObject object,String key)throws Exception {
        Object value=object.opt(key);
        if(value==null||value==JSONObject.NULL)return "";
        if(!(value instanceof String))throw new IOException("memory_invalid_input");
        return (String)value;
    }
    static String prompt(String input)throws Exception {
        if(input==null||input.length()>MAX_INPUT_CHARS)throw new IOException("memory_input_size");
        JSONObject request=new JSONObject(input);
        required(request.optString("prompt_type"));
        String system=text(request,"system_instruction"),prompt=text(request,"prompt"),source=text(request,"text");
        if(source.trim().isEmpty())throw new IOException("memory_text_unavailable");
        if(system.isEmpty()||prompt.isEmpty())throw new IOException("memory_missing_prompt");
        // The original prompt already includes OCR/transcribed user content. The
        // separate text is carried only when it is absent from that prompt.
        String result=system+"\n\n"+prompt;
        if(!source.isEmpty()&&!prompt.contains(source))result+="\n\nUser content (untrusted data):\n"+source;
        if(result.length()>MAX_INPUT_CHARS)throw new IOException("memory_input_size");
        return result;
    }
    static int required(String type)throws IOException {
        if("EXTRACT_MEMORY_SUMMARY_COLLECTION_PROMPT".equals(type)||"EXTRACT_MEMORY_FULL_PROMPT".equals(type))return 3;
        if("EXTRACT_MEMORY_SUMMARY_PROMPT".equals(type))return 1;
        if("EXTRACT_MEMORY_COLLECTION_PROMPT".equals(type))return 2;
        throw new IOException("memory_unsupported_prompt");
    }
    static boolean fits(int tokens){return tokens>0&&tokens<=CONTEXT_TOKENS-OUTPUT_TOKENS-CONTEXT_MARGIN;}
    private static void string(JSONObject object,String key,boolean required)throws Exception {
        Object value=object.opt(key);
        if(value==null||value==JSONObject.NULL){if(required)throw new IOException("memory_invalid_json");return;}
        if(!(value instanceof String)||(required&&((String)value).trim().isEmpty()))throw new IOException("memory_invalid_json");
    }
    private static JSONArray array(JSONObject object,String key)throws Exception {
        Object value=object.opt(key);if(value==null||value==JSONObject.NULL)return null;
        if(!(value instanceof JSONArray))throw new IOException("memory_invalid_json");return (JSONArray)value;
    }
    private static void strings(JSONArray values)throws Exception {
        if(values==null)return;
        for(int i=0;i<values.length();i++)if(!(values.get(i) instanceof String))throw new IOException("memory_invalid_json");
    }
    static String output(String raw,String input)throws Exception {
        if(raw==null||raw.length()>32768)throw new IOException("memory_invalid_json");
        String value=raw.trim();
        if(!value.startsWith("{")||!value.endsWith("}"))throw new IOException("memory_invalid_json");
        // JSONTokener rejects trailing prose/objects rather than accepting the
        // first object and accidentally returning an incomplete generation.
        JSONTokener parser=new JSONTokener(value);Object parsed=parser.nextValue();
        if(!(parsed instanceof JSONObject)||parser.nextClean()!=0)throw new IOException("memory_invalid_json");
        JSONObject result=(JSONObject)parsed;
        if(result.length()==0)throw new IOException("memory_invalid_json");
        int required=required(new JSONObject(input).optString("prompt_type"));
        string(result,"title",(required&1)!=0);string(result,"summary",(required&1)!=0);string(result,"category",(required&2)!=0);
        for(String key:new String[]{"suggested_category","secondary_category","secondary_suggested_category"})string(result,key,false);
        strings(array(result,"tags"));strings(array(result,"location"));
        JSONArray assets=array(result,"information_assets");
        if(assets!=null)for(int i=0;i<assets.length();i++){JSONObject item=assets.getJSONObject(i);string(item,"subtitle",true);strings(array(item,"contents"));}
        JSONArray actions=array(result,"action");
        if(actions!=null)for(int i=0;i<actions.length();i++){JSONObject item=actions.getJSONObject(i);string(item,"action_type",true);string(item,"title",true);for(String key:new String[]{"start_date","end_date","start_time","end_time"})string(item,key,false);if(!item.isNull("contents"))throw new IOException("memory_invalid_json");}
        Object seed=result.opt("sub_collection_seed");
        if(seed!=null&&seed!=JSONObject.NULL){if(!(seed instanceof JSONObject))throw new IOException("memory_invalid_json");JSONObject item=(JSONObject)seed;for(String key:new String[]{"type","canonical","source_category"})string(item,key,true);strings(array(item,"aliases"));}
        JSONArray metadata=array(result,"tag_metadata");
        if(metadata!=null)for(int i=0;i<metadata.length();i++){JSONObject item=metadata.getJSONObject(i);string(item,"canonical",true);string(item,"language",true);strings(array(item,"synonyms"));strings(array(item,"hypernyms"));}
        return value;
    }
}
