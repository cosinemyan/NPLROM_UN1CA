/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.troubleshooting;
import java.util.*;

/** In-memory conversation; only complete successful pairs enter follow-up context. */
final class ChatHistory {
    static final class Turn {
        final String user,answer;
        Turn(String user,String answer){this.user=user;this.answer=answer;}
    }
    static final class Request {
        final String wire,question,log,preview;final int included,omitted;
        Request(String wire,String question,String log,String preview,int included,int omitted){this.wire=wire;this.question=question;this.log=log;this.preview=preview;this.included=included;this.omitted=omitted;}
    }
    final ArrayList<Turn> turns=new ArrayList<>();
    final Map<Integer,String> edits=new HashMap<>();
    String draft="",attachment="",attachmentName="";int section,sectionChars=800;
    boolean includeLog=true;
    void add(String question,String log,String answer){
        // Retain the evidence with its question so follow-ups can refer to exact lines.
        turns.add(new Turn(question+(log.isEmpty()?"":"\nLog evidence:\n"+log),answer));
        while(turns.size()>12||size()>64000)turns.remove(0);
    }
    private int size(){int n=0;for(Turn t:turns)n+=t.user.length()+t.answer.length();return n;}
    void clear(){turns.clear();edits.clear();draft="";attachment="";attachmentName="";section=0;includeLog=true;}
    Request prepare(String question,String log,int limit){
        question=question.trim();if(question.isEmpty())throw new IllegalArgumentException("Describe the problem or ask a question first.");
        int start=turns.size();String wire=encode(start,question,log);
        if(wire.length()>limit)throw new IllegalArgumentException("Message and log exceed this backend's budget. Shorten the question or log section.");
        for(int i=turns.size()-1;i>=0&&turns.size()-i<=6;i--){
            String candidate=encode(i,question,log);if(candidate.length()>limit)break;start=i;wire=candidate;
        }
        StringBuilder preview=new StringBuilder();
        for(int i=start;i<turns.size();i++){Turn t=turns.get(i);preview.append("Previous question / evidence:\n").append(t.user).append("\n\nPrevious AI answer (unverified):\n").append(t.answer).append("\n\n");}
        preview.append("Current question:\n").append(question).append("\n\nCurrent log section:\n").append(log.isEmpty()?"None":log);
        return new Request(wire,question,log,preview.toString(),turns.size()-start,start);
    }
    private String encode(int start,String question,String log){
        StringBuilder b=new StringBuilder("{\"history\":[");
        for(int i=start;i<turns.size();i++){
            if(i>start)b.append(',');Turn t=turns.get(i);
            b.append("{\"user\":").append(quote(t.user)).append(",\"assistant\":").append(quote(t.answer)).append('}');
        }
        return b.append("],\"question\":").append(quote(question)).append(",\"log\":").append(quote(log)).append('}').toString();
    }
    static String quote(String s){
        StringBuilder b=new StringBuilder("\"");
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c=='"'||c=='\\')b.append('\\').append(c);
            else if(c<' ')b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
            else b.append(c);
        }
        return b.append('"').toString();
    }
    static int sectionCount(String text,int chunk){return Math.max(1,(text.length()+chunk-1)/chunk);}
    static String section(String text,int index,int chunk){
        int start=Math.min(text.length(),Math.max(0,index)*chunk),end=Math.min(text.length(),start+chunk);
        // Avoid splitting UTF-16 surrogate pairs between sections.
        if(start>0&&start<text.length()&&Character.isLowSurrogate(text.charAt(start)))start--;
        if(end<text.length()&&end>start&&Character.isHighSurrogate(text.charAt(end-1)))end--;
        return text.substring(start,end);
    }
}
