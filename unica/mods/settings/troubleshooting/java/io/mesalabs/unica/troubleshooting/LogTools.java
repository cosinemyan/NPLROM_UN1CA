/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.troubleshooting;
import java.io.*;
import java.util.*;
import java.util.regex.*;
final class LogTools {
    static final int MAX_IMPORT=8*1024*1024,CHUNK=1400,MAX_CHUNKS=8;
    static String read(InputStream in,int limit)throws IOException {
        if(in==null)throw new IOException("Cannot open file");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("File exceeds "+limit/1024+" KiB; import a smaller log file");out.write(b,0,n);}return out.toString("UTF-8");
    }
    static String redact(String raw) {
        ArrayList<String> prefixes=new ArrayList<>();
        Matcher timestamp=Pattern.compile("(?m)^(?:\\d{2}-\\d{2}|\\d{4}-\\d{2}-\\d{2}) \\d{2}:\\d{2}:\\d{2}\\.\\d{3}(?:[ \\t]+\\d+[ \\t]+\\d+[ \\t]+[VDIWEFAS][ \\t]+)?").matcher(raw);
        StringBuffer protectedText=new StringBuffer();
        while(timestamp.find()){prefixes.add(timestamp.group());timestamp.appendReplacement(protectedText,"UNICALOGPREFIX"+(prefixes.size()-1)+"END");}timestamp.appendTail(protectedText);
        String s=protectedText.toString().replaceAll("(?is)-----BEGIN [^-]*(?:PRIVATE KEY|CERTIFICATE)-----.*?-----END [^-]+-----","[REDACTED KEY/CERTIFICATE]");
        s=s.replaceAll("(?i)(authorization\\s*[:=]\\s*)(?:bearer\\s+)?[^\\r\\n]+","$1[REDACTED]");
        s=s.replaceAll("(?i)((?:api[_-]?key|access[_-]?token|refresh[_-]?token|password|passwd|cookie|android_id|imei|imsi|serial(?:no|number)?)\\s*[\"']?\\s*[:=]\\s*)[^\\s,;]+","$1[REDACTED]");
        s=s.replaceAll("(?i)https?://[^\\s<>\"]+","[REDACTED URL]");
        s=s.replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}","[REDACTED EMAIL]");
        s=s.replaceAll("(?i)\\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\\b","[REDACTED MAC]");
        s=s.replaceAll("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b","[REDACTED IP]");
        s=s.replaceAll("(?<![A-Za-z0-9])\\+?\\d[\\d ()-]{8,}\\d(?![A-Za-z0-9])","[REDACTED NUMBER]");
        for(int i=0;i<prefixes.size();i++)s=s.replace("UNICALOGPREFIX"+i+"END",prefixes.get(i));
        return s.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]","?");
    }
    static final class Selection {
        final ArrayList<String> chunks=new ArrayList<>();final ArrayList<String> titles=new ArrayList<>();int totalLines,selectedLines;boolean truncated;
        String coverage(){return selectedLines+" of "+totalLines+" lines selected; "+chunks.size()+" log sections. "+(truncated?"Coverage limited. ":"")+"Other sections have not been analyzed.";}
    }
    static Selection reviewed(String text,Selection original){
        Selection result=new Selection();result.totalLines=original.totalLines;result.selectedLines=original.selectedLines;result.truncated=original.truncated;
        for(int start=0;start<text.length()&&result.chunks.size()<MAX_CHUNKS;){
            int end=Math.min(text.length(),start+CHUNK);
            if(end<text.length()){int newline=text.lastIndexOf('\n',end-1);if(newline>start)end=newline+1;}
            result.chunks.add(text.substring(start,end));start=end;if(start<text.length()&&result.chunks.size()==MAX_CHUNKS)result.truncated=true;
        }
        return result;
    }
    static String failureTitle(String line){
        String text=line.toLowerCase(java.util.Locale.ROOT);
        if(text.matches(".*(?:kernel panic|panic - not syncing|internal error: oops|watchdog killing system process|fatal exception in system process|fatal exception.*system_server|system_server.*(?:fatal|crash|watchdog)|system_server_crash|system_server_watchdog).*") )return "System crash";
        if(text.matches(".*(?:fatal exception\\s*:|am_crash\\s*:|force finishing activity).*") )return "App crash";
        if(text.matches(".*(?:fatal signal\\s+\\d+|signal\\s+\\d+\\s*\\(sig(?:segv|abrt|bus|ill|fpe|trap)\\)|>>>.*<<<|abort message:).*") )return "Native crash";
        if(text.matches(".*(?:anr in\\s+|am_anr\\s*:|input dispatching timed out|application.*not responding).*") )return "App not responding";
        if(text.matches(".*(?:unable to (?:start|instantiate|resume|launch) (?:activity|application)|activitynotfoundexception).*") )return "App could not open";
        return null;
    }
    static Selection select(String text) {
        String[] lines=text.split("\\r?\\n",-1);Selection result=new Selection();result.totalLines=lines.length;
        ArrayList<int[]> windows=new ArrayList<>();ArrayList<String> names=new ArrayList<>();
        for(int i=0;i<lines.length;i++){
            String title=failureTitle(lines[i]);if(title==null)continue;
            int first=Math.max(0,i-3),last=Math.min(lines.length-1,i+40);
            if(!windows.isEmpty()&&first<=windows.get(windows.size()-1)[1])windows.get(windows.size()-1)[1]=Math.max(last,windows.get(windows.size()-1)[1]);
            else{windows.add(new int[]{first,last});names.add(title);}
        }
        // Prefer recent incidents. No fallback to normal warnings/errors.
        for(int w=windows.size()-1;w>=0;w--){
            int[] window=windows.get(w);StringBuilder chunk=new StringBuilder();
            for(int i=window[0];i<=window[1];i++){
                String line="L"+(i+1)+": "+lines[i];
                if(line.length()>CHUNK){line=line.substring(0,CHUNK-20)+" [line shortened]";result.truncated=true;}
                if(chunk.length()+line.length()+1>CHUNK){result.chunks.add(chunk.toString());result.titles.add(names.get(w));chunk.setLength(0);}
                if(result.chunks.size()==MAX_CHUNKS){result.truncated=true;return result;}
                chunk.append(line).append('\n');result.selectedLines++;
            }
            if(chunk.length()>0){result.chunks.add(chunk.toString());result.titles.add(names.get(w));}
            if(result.chunks.size()==MAX_CHUNKS){result.truncated|=w>0;break;}
        }
        return result;
    }
}
