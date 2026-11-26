/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.keybox;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Allowlisted event summaries only. Never returns raw messages, packages, aliases or key data. */
final class Diagnostics {
    private static final Pattern HEADER=Pattern.compile("^([VDIWEF])/TEESimulator\\s*\\(\\s*\\d+\\):\\s*(.*)$");
    static List<String> read()throws Exception {
        // -t limits the global buffer BEFORE the tag filter; busy Settings activity can
        // otherwise hide every backend event. Drain the tagged snapshot, retaining only
        // the final 400 redacted summaries in memory.
        Process process=new ProcessBuilder("/system/bin/logcat","-d","-v","brief","-s","TEESimulator:V","*:S").redirectErrorStream(true).start();
        java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{
            java.util.concurrent.Future<List<String>> result=executor.submit(()->collect(process.getInputStream()));
            List<String> rows=result.get(8,TimeUnit.SECONDS);
            if(!process.waitFor(2,TimeUnit.SECONDS)||process.exitValue()!=0)throw new IOException("Logs unavailable");
            return rows;
        }finally{process.destroyForcibly();executor.shutdownNow();}
    }
    static List<String> collect(InputStream input)throws IOException {
        ArrayDeque<String> rows=new ArrayDeque<>();
        // Bound individual lines as well as retained history. Oversized raw lines are
        // discarded without ever entering the exportable event list.
        Reader reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));
        StringBuilder line=new StringBuilder();boolean oversized=false;int ch;
        while((ch=reader.read())!=-1){
            if(ch=='\n'){
                if(!oversized){String safe=sanitize(line.toString());if(safe!=null){if(rows.size()==400)rows.removeFirst();rows.addLast(safe);}}
                line.setLength(0);oversized=false;
            }else if(!oversized){if(line.length()<16384)line.append((char)ch);else{line.setLength(0);oversized=true;}}
        }
        if(!oversized&&line.length()>0){String safe=sanitize(line.toString());if(safe!=null){if(rows.size()==400)rows.removeFirst();rows.addLast(safe);}}
        return new ArrayList<>(rows);
    }
    static String sanitize(String line){
        Matcher m=HEADER.matcher(line.endsWith("\r")?line.substring(0,line.length()-1):line);if(!m.matches())return null;
        String raw=m.group(2),event="Backend event (details redacted)";
        String area="Backend";
        if(raw.startsWith("dmn RomApp:")){area="Configuration";
            if(raw.contains("config queued"))event="Configuration queued";
            else if(raw.contains("refresh failed"))event="Configuration refresh failed; new interception disabled";
            else if(raw.contains("bridge started"))event="Settings bridge started";
        }else if(raw.startsWith("dmn Control:")){area="Connection";
            if(raw.contains("ack epoch="))event=raw.contains("ok=true")?"Configuration acknowledged":"Configuration rejected";
            else if(raw.contains("connected to"))event="Interceptor connection established";
            else if(raw.contains("disconnect")||raw.contains("connection lost"))event="Interceptor disconnected";
            else if(raw.contains("hello"))event="Interceptor handshake received";
        }else if(raw.startsWith("dmn Injector:")||raw.startsWith("inj ")){area="Injector";event="Injection event (details redacted)";}
        else if(raw.startsWith("dmn Harvester:")){area="Harvest";event="Hardware harvest event (details redacted)";}
        else if(raw.startsWith("km")||raw.startsWith("ta/")){area="Attestation";event="Attestation event (details redacted)";}
        return m.group(1)+" · "+area+" · "+event;
    }
    interface Listener {void event(String summary);}
    static final class Stream implements AutoCloseable {
        private volatile boolean closed;
        private volatile Process process;
        Stream(Listener listener){new Thread(()->{
            try{
                process=new ProcessBuilder("/system/bin/logcat","-v","brief","-T","1","-s","TEESimulator:V","*:S").redirectErrorStream(true).start();
                if(closed){process.destroyForcibly();return;}
                Reader reader=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));
                StringBuilder line=new StringBuilder();boolean oversized=false;int ch;
                while(!closed&&(ch=reader.read())!=-1){
                    if(ch=='\n'){if(!oversized){String safe=sanitize(line.toString());if(safe!=null)listener.event(safe);}line.setLength(0);oversized=false;}
                    else if(!oversized){if(line.length()<16384)line.append((char)ch);else{line.setLength(0);oversized=true;}}
                }
            }catch(Exception error){if(!closed)listener.event("W · Backend · Live log reader unavailable");}
            finally{if(process!=null)process.destroyForcibly();}
        },"keybox-live-logs").start();}
        public void close(){closed=true;if(process!=null)process.destroyForcibly();}
    }
    static String filter(List<String> rows,String severity,String area,String query){
        StringBuilder result=new StringBuilder();String ranks="VDIWEF";
        for(String row:rows)if(ranks.indexOf(row.charAt(0))>=ranks.indexOf(severity)&&
            (area.equals("All")||row.contains(" · "+area+" · "))&&row.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))result.append(row).append('\n');
        return result.length()==0?"No matching backend events.\n":result.toString();
    }
}
