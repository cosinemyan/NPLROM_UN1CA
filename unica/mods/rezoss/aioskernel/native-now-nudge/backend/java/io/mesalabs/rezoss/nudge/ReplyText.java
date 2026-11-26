package io.mesalabs.rezoss.nudge;

import java.util.regex.Pattern;

/** Remove known generated tone/speaker markers only at reply boundaries. */
public final class ReplyText {
    private static final String LABEL="(?:me|user|assistant|friend|others|positive|postive|uncertain|negative|neutral)\\s*:";
    private static final Pattern START=Pattern.compile("^"+LABEL+"\\s*",Pattern.CASE_INSENSITIVE);
    private static final Pattern END=Pattern.compile("(?:^|\\s+)"+LABEL+"\\s*$",Pattern.CASE_INSENSITIVE);
    public static String clean(String value) {
        String text=value.trim(),previous;
        do {
            previous=text;
            text=text.replaceFirst("^(?:[-*•]|[0-9]+[.)])\\s+", "").trim();
            if(text.length()>=2&&text.startsWith("\"")&&text.endsWith("\""))text=text.substring(1,text.length()-1).trim();
            text=START.matcher(text).replaceFirst("");
            text=END.matcher(text).replaceFirst("").trim();
        } while(!text.equals(previous));
        return text;
    }
    private static String comparable(String value){
        return java.text.Normalizer.normalize(clean(value),java.text.Normalizer.Form.NFKC)
            .toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{M}\\p{N}]+"," ").trim();
    }
    /** Reject unusable generations rather than fabricating replacement replies. */
    static String validated(String value,String source){
        String output=lines(value);java.util.Set<String> unique=new java.util.HashSet<>(),messages=new java.util.HashSet<>();
        String dialogue=PromptAdapter.dialogue(source);
        if(dialogue!=null)for(String line:PromptAdapter.normalizeRoles(dialogue).split("\\R")){
            String message=comparable(line);if(!message.isEmpty())messages.add(message);
        }
        String[] replies=output.isEmpty()?new String[0]:output.split("\\R");
        if(replies.length==0||(PromptAdapter.structured(source)&&replies.length!=3))throw new IllegalArgumentException("invalid_reply_count");
        for(String line:replies){String key=comparable(line);
            if(key.isEmpty()||!unique.add(key))throw new IllegalArgumentException("duplicate_or_empty_reply");
            // Very short acknowledgements may legitimately match an earlier turn.
            if(key.split(" ").length>=4&&messages.contains(key))throw new IllegalArgumentException("conversation_echo");
        }
        return output;
    }
    static String lines(String value) {
        StringBuilder output=new StringBuilder();
        for(String line:value.split("\\R")) {
            String text=clean(line);
            if(!text.isEmpty()){if(output.length()>0)output.append('\n');output.append(text);}
        }
        return output.toString();
    }
}
