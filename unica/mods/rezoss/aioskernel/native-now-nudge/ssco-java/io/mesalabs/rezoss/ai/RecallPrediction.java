package io.mesalabs.rezoss.ai;

import java.util.*;

/** Wire-format validation only. Intent and parameter inference belong to the model. */
public final class RecallPrediction {
    public final String action, parameters;
    private RecallPrediction(String action, String parameters) { this.action=action; this.parameters=parameters; }
    /** S26U SSCO lacks Mail although SmartSuggestions implements its mail handler. */
    public static void addMailSchema(Map<String,Set<String>> required) {
        if(!required.containsKey("Mail"))required.put("Mail",Collections.singleton("mail"));
    }
    public static RecallPrediction parse(String input, String output) {
        if(input==null||output==null||input.length()>12000||output.length()>2048)return null;
        int first=0,last=output.length();
        while(first<last&&" \r\n".indexOf(output.charAt(first))>=0)first++;
        while(last>first&&" \r\n".indexOf(output.charAt(last-1))>=0)last--;
        String line=output.substring(first,last);
        if(line.equals("Unsafe")||line.equals("Unsafe\t"))return new RecallPrediction("Unsafe", "");
        if(line.indexOf('\n')>=0||line.indexOf('\r')>=0)return null;
        int tab=line.indexOf('\t');
        if(tab<1||tab!=line.lastIndexOf('\t'))return null;
        String action=line.substring(0,tab), raw=line.substring(tab+1);
        if(action.equals("View Calendar")&&raw.isEmpty())return new RecallPrediction(action, "");
        Set<String> allowed;
        if(action.equals("Share Photo"))allowed=new HashSet<>(Arrays.asList("photo"));
        else if(action.equals("Create Calendar"))allowed=new HashSet<>(Arrays.asList("type","title","start","end","location","per"));
        else if(action.equals("View Calendar"))allowed=new HashSet<>(Arrays.asList("start"));
        else if(action.equals("Browse Location"))allowed=new HashSet<>(Arrays.asList("location"));
        else if(action.equals("Call"))allowed=new HashSet<>(Arrays.asList("number"));
        else if(action.equals("Mail"))allowed=new HashSet<>(Arrays.asList("mail"));
        else if(action.equals("Set Reminder"))allowed=new HashSet<>(Arrays.asList("title","start"));
        else return null;
        Map<String,String> fields=new LinkedHashMap<>();
        for(String segment:raw.split("\\|",-1)) {
            if(segment.matches("(?s).*[\\p{Cc}\\p{Cf}].*"))return null;
            int space=segment.indexOf(' ');
            if(space<1)return null;
            String key=segment.substring(0,space),value=segment.substring(space+1).trim();
            if(!allowed.contains(key)||fields.containsKey(key)||value.isEmpty()||value.length()>256)return null;
            // The stock schema checks names, but does not ground photo/title/start.
            if(value.indexOf('\t')>=0||value.matches("(?s).*[\\p{Cc}\\p{Cf}].*"))return null;
            // Photo values are model-proposed Gallery search terms, including spelling
            // corrections and translations. Other values still require exact grounding.
            if(!action.equals("Share Photo")&&!key.equals("type")&&!key.equals("per")&&!input.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT)))return null;
            if(key.equals("type")&&!value.equals("event"))return null;
            if(action.equals("Call")&&!phone(value))return null;
            if(action.equals("Mail")&&!value.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{}~-]+@[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?\\.[A-Za-z]{2,63}"))return null;
            fields.put(key,value);
        }
        if(action.equals("Share Photo")&&!fields.containsKey("photo"))return null;
        if(action.equals("Create Calendar")&&!fields.keySet().containsAll(Arrays.asList("type","title","start")))return null;
        if(action.equals("Browse Location")&&!fields.containsKey("location"))return null;
        if(action.equals("Call")&&!fields.containsKey("number"))return null;
        if(action.equals("Mail")&&!fields.containsKey("mail"))return null;
        if(action.equals("Set Reminder")&&!fields.containsKey("title"))return null;
        StringJoiner joined=new StringJoiner("|");
        for(Map.Entry<String,String> field:fields.entrySet())joined.add(field.getKey()+" "+field.getValue());
        return new RecallPrediction(action,joined.toString());
    }
    private static boolean phone(String value) {
        int digits=0;
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(Character.isDigit(c))digits++;
            else if("+-()#* ".indexOf(c)<0)return false;
        }
        return digits>=3;
    }
}
