// SPDX-License-Identifier: AGPL-3.0-or-later
package io.mesalabs.unica;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.lang.reflect.*;
import android.content.pm.ApplicationInfo;
public class PolicyHostTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static ApplicationInfo app(String name,String manifest,String asset,boolean utf16)throws Exception {
        Path file=Files.createTempFile("hma-test-",".apk");
        try(ZipOutputStream z=new ZipOutputStream(Files.newOutputStream(file))){
            z.putNextEntry(new ZipEntry("AndroidManifest.xml"));z.write(manifest.getBytes(utf16?"UTF-16LE":"UTF-8"));z.closeEntry();
            if(asset!=null){z.putNextEntry(new ZipEntry(asset));z.write(0);z.closeEntry();}
        }
        ApplicationInfo a=new ApplicationInfo();a.packageName=name;a.sourceDir=file.toString();return a;
    }
    static void expect(String key,String db,String name,String value,boolean present)throws Exception {
        Class<?> cfg=Class.forName("io.mesalabs.unica.HmaPolicy$Config");
        Constructor<?> c=cfg.getDeclaredConstructor(String.class);c.setAccessible(true);
        Object config=c.newInstance("{\"settings\":[\""+key+"\"]}");
        Method m=HmaPolicy.class.getDeclaredMethod("replacement",cfg,String.class,String.class);m.setAccessible(true);
        String[] result=(String[])m.invoke(null,config,db,name);
        check((result!=null)==present,"replacement presence "+db+":"+name);
        if(present)check(Objects.equals(result[0],value),"replacement value "+name);
    }
    public static void main(String[] args)throws Exception {
        for(boolean utf16:new boolean[]{false,true}) {
            ApplicationInfo a=app("test.access","android.permission.BIND_ACCESSIBILITY_SERVICE",null,utf16);
            check(HmaPresets.scan(a).presets.contains("accessibility_apps"),"accessibility encoding");
            a.flags=1;check(!HmaPresets.scan(a).presets.contains("accessibility_apps"),"exclude system accessibility");
            check(HmaPresets.scan(app("test.gms","com.google.android.gms.version",null,utf16)).risky,"GMS marker");
            check(HmaPresets.scan(app("test.firebase","com.google.firebase.provider",null,utf16)).risky,"Firebase marker");
            check(HmaPresets.scan(app("test.shizuku","rikka.shizuku.ShizukuProvider",null,utf16)).presets.contains("shizuku_dhizuku"),"Shizuku marker");
            check(HmaPresets.scan(app("test.root","android.permission.ACCESS_SUPERUSER",null,utf16)).presets.contains("root_apps"),"root permission");
            check(!HmaPresets.scan(app("test.mozilla","android.permission.ACCESS_SUPERUSER org.mozilla.gecko",null,utf16)).presets.contains("root_apps"),"root false positive exclusion");
        }
        check(HmaPresets.scan(app("test.xposed","","assets/xposed_init",false)).presets.contains("xposed"),"legacy Xposed");
        check(HmaPresets.scan(app("test.xposed","","META-INF/xposed/module.prop",false)).presets.contains("xposed"),"modern Xposed");
        check(HmaPresets.scan(app("com.topjohnwu.magisk","",null,false)).presets.contains("root_apps"),"known manager");
        check(HmaPresets.scan(app("test.native","","lib/arm64-v8a/libkernelsu.so",false)).presets.contains("root_apps"),"native manager detection");
        check(HmaPresets.scan(app("test.editor","","assets/key/testkey.pk8",false)).presets.contains("sus_apps"),"APK editor");
        check(HmaPresets.scan(app("org.lineageos.test","",null,false)).presets.contains("custom_rom"),"ROM prefix");
        check(HmaPresets.scan(app("com.reveny.nativecheck","",null,false)).presets.contains("detector_apps"),"known detector");
        check(!HmaPresets.scan(app("test.clean","",null,false)).risky,"clean app risk");
        check(HmaPresets.scan(app("com.anydesk.anydeskandroid","",null,false)).risky,"explicit risky app");
        ApplicationInfo split=app("test.split","",null,false);split.splitSourceDirs=new String[]{app("unused","com.google.firebase.test","META-INF/xposed/module.prop",true).sourceDir};
        check(HmaPresets.scan(split).risky,"split risk scan");
        check(HmaPresets.scan(split).presets.contains("xposed"),"split module scan");
        expect("dev_options","global","adb_enabled","0",true);
        expect("dev_options","global","adb_wifi_enabled","0",true);
        expect("dev_options","global","development_settings_enabled","0",true);
        expect("dev_options","global","hidden_api_policy",null,true);
        expect("dev_options","secure","mock_location","0",true);
        expect("dev_options","secure","adb_enabled",null,false);
        expect("accessibility","secure","accessibility_enabled","0",true);
        expect("accessibility","secure","enabled_accessibility_services","",true);
        expect("accessibility","global","accessibility_enabled",null,false);
        expect("input_method","secure","default_input_method","com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME",true);
        expect("input_method","secure","tts_default_synth","com.google.android.tts",true);
        expect("","global","adb_enabled",null,false);
        expect("dev_options","global","unica_hma_policy",null,false);
        Method uid=HmaPolicy.class.getDeclaredMethod("appUid",int.class);uid.setAccessible(true);
        for(int excluded:new int[]{-1,0,1000,2000,9999,20000,99000,101000})check(!(Boolean)uid.invoke(null,excluded),"exclude UID "+excluded);
        for(int included:new int[]{10000,19999,110000,119999})check((Boolean)uid.invoke(null,included),"include UID "+included);
        System.out.println("PASS: "+checks+" host policy/preset checks (not Android runtime tests)");
    }
}
