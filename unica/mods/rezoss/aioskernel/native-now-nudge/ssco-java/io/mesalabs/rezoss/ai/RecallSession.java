package io.mesalabs.rezoss.ai;

/** One prediction per classifier thread; consumed only by its matching Samsung MT5 prompt. */
public final class RecallSession {
    private static final ThreadLocal<Entry> pending=new ThreadLocal<>();
    private static final class Entry {
        final String input; final RecallPrediction prediction; final long expires;
        Entry(String input,RecallPrediction prediction,long expires){this.input=input.trim();this.prediction=prediction;this.expires=expires;}
    }
    public static void clear(){pending.remove();}
    public static void put(String input,RecallPrediction prediction,long expires){clear();if(prediction!=null&&!prediction.action.equals("Unsafe"))pending.set(new Entry(input,prediction,expires));}
    public static String consume(String prompt,long now){
        Entry entry=pending.get();clear();
        if(entry==null||prompt==null||now>=entry.expires)return "";
        String expected=entry.input+" <Action> "+entry.prediction.action;
        if(!prompt.equals(expected)&&!prompt.startsWith(expected+" <상대> "))return "";
        return entry.prediction.parameters;
    }
}
