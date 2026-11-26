package io.mesalabs.rezoss.nudge;

/** Keep Samsung contracts; replace Qwen-only generation controls with LiteRT options. */
final class GemmaPrompts {
    static String adapt(String prompt){
        String text=prompt.replaceFirst("(?:\\r?\\n)?/no_think\\s*$","").trim();
        if(text.contains("JSON"))text+="\nOutput raw JSON only, without Markdown or code fences. The first character must be { and the last character must be }.";
        if(text.startsWith("Screen a call"))text+=" You are the phone owner's screening assistant, not the caller. Acknowledge receiving the purpose without making commitments on behalf of the owner.";
        return text;
    }
}
