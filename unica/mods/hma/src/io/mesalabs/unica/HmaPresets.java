/* SPDX-License-Identifier: AGPL-3.0-or-later
 * Preset definitions adapted from frknkrc44/HMA-OSS, d1cfcbce72ac07eb998cd49be1d5385ade48f713.
 */
package io.mesalabs.unica;
import android.content.pm.ApplicationInfo;
import java.util.*;
import java.util.zip.*;
import java.io.*;
final class HmaPresets {
    private static final Map<String, Set<String>> EXACT = new HashMap<String, Set<String>>();
    static {
        EXACT.put("detector_apps", new HashSet<String>(Arrays.asList("com.reveny.nativecheck", "icu.nullptr.nativetest", "io.github.rabehx.securify", "com.zhenxi.hunter", "io.github.vvb2060.mahoshojo", "io.github.huskydg.memorydetector", "org.akanework.checker", "icu.nullptr.applistdetector", "com.byxiaorun.detector", "com.kimchangyoun.rootbeerFresh.sample", "com.androidfung.drminfo", "com.kikyps.crackme", "org.matrix.demo", "com.rem01gaming.disclosure", "luna.safe.luna", "com.AndroLua", "com.detect.mt", "io.liankong.riskdetector", "com.suisho.rc", "com.ahmed.security_tester", "id.my.pjm.qbcd_okr_dvii", "wu.Zygisk.Detector", "com.atominvention.rootchecker", "com.joeykrim.rootcheck", "com.studio.duckdetector", "com.eltavine.duckdetector", "com.chuqniudetector", "com.chunqiudetector", "com.longz.detector", "com.anycheck.app", "com.lingqing.detector", "com.android.nativetest", "com.youhu.laifu", "chunqiu.safe.detector", "chunqiu.safe", "wu.Rookie.Detector", "com.fkjc.zcro", "wu.keyChain.test", "at.persie0.root_detection_app", "at.austriao.fake_gps_detector_app", "io.ngankbakaa.lineage.detector", "com.dexprotector.detector.envchecks", "krypton.tbsafetychecker", "gr.nikolasspyr.integritycheck", "com.henrikherzig.playintegritychecker", "com.thend.integritychecker", "com.flinkapps.safteynet", "com.bryancandi.knoxcheck")));
        EXACT.put("root_apps", new HashSet<String>(Arrays.asList("io.github.a13e300.ksuwebui", "com.fox2code.mmm", "id.kuato.diskhealth", "com.sunilpaulmathew.debloater", "com.garyodernichts.downgrader", "eu.roggstar.getmitokens", "io.github.domi04151309.powerapp", "eu.roggstar.luigithehunter.batterycalibrate", "at.or.at.plugoffairplane", "tk.giesecke.phoenix", "com.corphish.nightlight.generic", "com.zinaro.cachecleanerwidget", "de.buttercookie.simbadroid", "simple.reboot.com", "ru.evgeniy.dpitunnel", "ca.mudar.fairphone.peaceofmind", "com.gitlab.giwiniswut.rwremount", "com.machiav3lli.backup", "com.bartixxx.opflashcontrol", "org.nuntius35.wrongpinshutdown", "ru.nsu.bobrofon.easysshfs", "x1125io.initdlight", "com.byyoung.setting", "web1n.stopapp", "org.adaway", "com.mrsep.ttlchanger", "mattecarra.accapp", "io.github.saeeddev94.pixelnr", "com.js.nowakelock", "me.twrp.twrpapp", "com.slash.batterychargelimit", "com.valhalla.thor", "me.itejo443.bindhosts", "com.softwarebakery.drivedroid", "com.tester.wpswpatester", "com.paget96.lsandroid", "ua.polodarb.gmsflags", "com.tortel.syslog", "com.jhc.detach", "com.sunilpaulmathew.debloater", "com.rk.taskmanager", "be.mygod.vpnhotspot", "com.emanuelef.remote_capture", "com.mayank.rucky", "org.csploit.android", "whid.usb.injector", "de.tu_darmstadt.seemoo.nexmon", "remote.hid.keyboard.client", "de.srlabs.snoopsnitch", "com.hijacker", "su.sniff.cepter", "me.jsonet.jshook", "com.simo.fhook", "Core Edition", "com.omarea.vtools", "james.dsp", "flar2.exkernelmanager", "com.franco.kernel", "com.lybxlpsv.kernelmanager", "com.html6405.boefflakernelconfig", "ccc71.st.cpu", "com.umang96.radon", "com.rve.rvkernelmanager", "id.xms.xtrakernelmanager", "com.topjohnwu.magisk", "io.github.huskydg.magisk", "io.github.vvb2060.magisk", "me.weishu.kernelsu", "com.rifsxd.ksunext", "com.sukisu.ultra", "me.bmax.apatch", "org.lsposed.manager")));
        EXACT.put("shizuku_dhizuku", new HashSet<String>(Arrays.asList("com.rosan.dhizuku")));
        EXACT.put("sus_apps", new HashSet<String>(Arrays.asList("com.reveny.vbmetafix.service", "berserker.android.apps.sshdroid", "com.iamaner.oneclickfreeze", "com.shamanland.privatescreenshots", "org.connectbot", "com.devolutions.remotedesktopmanager", "com.anydesk.anydeskandroid", "com.carriez.flutter_hbb", "com.happymod.apk", "com.pd.pdhelper", "cm.aptoide.pt", "com.speedsoftware.rootexplorer", "me.zhanghai.android.files", "com.lonelycatgames.Xplore", "org.fossify.filemanager", "com.amaze.filemanager", "com.ceco.gravitybox.unlocker")));
        EXACT.put("custom_rom", new HashSet<String>(Arrays.asList("io.chaldeaprjkt.gamespace", "powersaver.pro")));
        EXACT.put("xposed", new HashSet<String>(Arrays.asList("org.frknkrc44.hma_oss")));
    }
    private static boolean starts(String name, String... prefixes) { for (String p:prefixes) if(name.startsWith(p)) return true; return false; }
    private static boolean ends(String name, String... suffixes) { for (String p:suffixes) if(name.endsWith(p)) return true; return false; }
    private static boolean has(String text, String... values) { for(String v:values) if(text.contains(v)) return true; return false; }
    static HmaPolicy.Facts scan(ApplicationInfo app) throws Exception {
        HmaPolicy.Facts f = new HmaPolicy.Facts();
        String n=app.packageName;
        for(Map.Entry<String,Set<String>> e:EXACT.entrySet()) if(e.getValue().contains(n)) f.presets.add(e.getKey());
        if(starts(n,"me.garfieldhan.") || ends(n,".keyattestation")) f.presets.add("detector_apps");
        if(starts(n,"dev.ukanth.ufirewall","xzr.","moe.xzr.","org.lsposed","com.drdisagree.iconify","com.dergoogler.mmrl","com.xayah.databackup","com.smartpack.","org.fdroid.fdroid.privileged") || ends(n,".viper4android",".viperfx",".magisk",".apatch") || has(n,".busybox",".apatch.")) f.presets.add("root_apps");
        if(starts(n,"moe.shizuku.")) f.presets.add("shizuku_dhizuku");
        if(starts(n,"com.offsec.","com.termux","com.realvnc.","nextapp.fx","com.ghisler.","ru.zdevs.","com.mixplorer","bin.mt.","com.x0.strai.","com.microsoft.rdc.","com.teamviewer.")) f.presets.add("sus_apps");
        if(starts(n,"lineageos.","org.lineageos.","com.caf.","org.calyxos.","co.aospa.","org.omnirom.","org.protonaosp.","org.evolution.","org.evolutionx.","com.android.system.switch.","com.accents.","com.alpha.","com.android.systemui.","com.android.theme.","com.bootleggers.","com.custom.overlay.","com.gnonymous.gvisualmod.","com.libremobileos.","com.nikgapps.","com.potato.","eu.xiaomi.") || ends(n,".evolution",".evolutionx",".overlay.fog") || has(app.sourceDir,"_lineage","lineage_")) f.presets.add("custom_rom");
        if("com.anydesk.anydeskandroid".equals(n)) f.risky=true;
        List<String> paths=new ArrayList<String>(); paths.add(app.sourceDir);
        try { String[] splits=(String[])ApplicationInfo.class.getField("splitSourceDirs").get(app); if(splits!=null) paths.addAll(Arrays.asList(splits)); } catch(ReflectiveOperationException ignored) {}
        for(String path:paths) {
            ZipFile zip=new ZipFile(path);
            try {
                ZipEntry entry=zip.getEntry("AndroidManifest.xml");
                String manifest="";
                if(entry!=null) {
                    InputStream input=zip.getInputStream(entry);
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    try { byte[] b=new byte[8192]; int count; while((count=input.read(b))!=-1) { if(bytes.size()+count>4194304) throw new IOException("Manifest too large"); bytes.write(b,0,count); } }
                    finally { input.close(); }
                    // Android binary XML uses UTF-8 or UTF-16 string pools; removing NULs covers ASCII markers in both.
                    manifest=new String(bytes.toByteArray(),"ISO-8859-1").replace("\u0000", "");
                }
                if(has(manifest,"com.google.android.gms.","com.google.firebase.")) f.risky=true;
                if(!f.presets.contains("detector_apps") && (app.flags & 129)==0 && has(manifest,"android.permission.BIND_ACCESSIBILITY_SERVICE")) f.presets.add("accessibility_apps");
                if(has(manifest,"rikka.shizuku.ShizukuProvider","com.rosan.dhizuku.server.provider")) f.presets.add("shizuku_dhizuku");
                if(zip.getEntry("assets/xposed_init")!=null || zip.getEntry("META-INF/xposed/module.prop")!=null) f.presets.add("xposed");
                if(!f.presets.contains("detector_apps")) {
                    if(has(manifest,"android.permission.ACCESS_SUPERUSER") && !has(manifest,"org.mozilla.gecko","MEIZUPUSH","hk.alipay.wallet","com.tencent.mm","com.heytap.","com.netmera.Netmera")) f.presets.add("root_apps");
                    for(String lib:new String[]{"libkernelsu.so","libapd.so","libmagisk.so","libmagiskboot.so","libmmrl-file-manager.so","libmmrl-kernelsu.so","libzakoboot.so"}) for(String arch:new String[]{"arm64-v8a","armeabi-v7a"}) if(zip.getEntry("lib/"+arch+"/"+lib)!=null) f.presets.add("root_apps");
                    if(zip.getEntry("assets/gamma_profiles.json")!=null || zip.getEntry("assets/main.jar")!=null) f.presets.add("root_apps");
                }
                for(String asset:new String[]{"APKEditor.pk8","testkey.pk8","key/testkey.pk8"}) if(zip.getEntry("assets/"+asset)!=null) f.presets.add("sus_apps");
            } finally { zip.close(); }
        }
        return f;
    }
}
