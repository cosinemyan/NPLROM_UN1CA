/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.troubleshooting;
import android.os.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
final class ModuleTransport {
    static final File BASE=new File("/data/misc/unica_troubleshooting");
    static synchronized JSONObject request(String op,JSONObject module)throws Exception {
        if(android.os.Process.myUid()!=1000)
            throw new IOException("Available to the device owner's Settings only");
        if(!op.matches("scan|disable|restore"))throw new IOException("Invalid operation");
        File dir=new File(BASE,"requests");if(!dir.isDirectory())throw new IOException("Troubleshooting service is not installed. Install the updated ROM.");
        String id=UUID.randomUUID().toString(),message=id+"\n"+op+"\n";
        if(module!=null){
            for(String key:new String[]{"stage","id","token"}){String value=module.getString(key);if(!value.matches("[A-Za-z0-9._-]{1,100}"))throw new IOException("Invalid module identity");message+=value+"\n";}
        }
        File temp=new File(dir,"request.tmp");
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(message.getBytes("UTF-8"));android.system.Os.fchmod(out.getFD(),0640);out.getFD().sync();}
        Files.move(temp.toPath(),new File(dir,"request").toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        Class.forName("android.os.SystemProperties").getMethod("set",String.class,String.class).invoke(null,"sys.unica.troubleshooting.request",id);
        long end=SystemClock.elapsedRealtime()+20000;
        while(SystemClock.elapsedRealtime()<end){
            File response=new File(BASE,"responses/response.json");
            if(response.isFile())try(InputStream in=new FileInputStream(response)){
                JSONObject result=new JSONObject(LogTools.read(in,1024*1024));
                if(id.equals(result.optString("id"))){if(result.has("error"))throw new IOException(result.getString("error"));return result.getJSONObject("result");}
            }
            Thread.sleep(100);
        }
        throw new IOException("Module service did not respond. Scan again before retrying.");
    }
}
