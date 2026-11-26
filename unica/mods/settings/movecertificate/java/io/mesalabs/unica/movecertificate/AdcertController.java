/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.movecertificate;

import android.content.Context;
import android.os.Process;
import android.widget.Toast;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.*;

/** An opt-in switch for the owner's AdGuard Personal CA. */
public final class AdcertController extends TogglePreferenceController implements LifecycleObserver, OnStart {
    private static final File ROOT=new File("/data/misc/unica_movecert"), FLAG=new File(ROOT,"request/adcert_enabled"), USER=new File("/data/misc/user/0/cacerts-added");
    private final Context context; private TwoStatePreference preference;
    public AdcertController(Context c,String key){super(c,key);context=c;}
    @Override public int getAvailabilityStatus(){return 0;}
    @Override public int getSliceHighlightMenuRes(){return 0;}
    private String text(String name){int id=context.getResources().getIdentifier("unica_adcert_"+name,"string",context.getPackageName());return context.getString(id);}
    private boolean owner(){return Process.myUid()/100000==0;}
    static File personal(File dir){
        File[] files=dir.listFiles(f->f.isFile()&&f.getName().matches("0f4ed297\\.[0-9]{1,5}"));
        if(files==null)return null;
        Arrays.sort(files,(a,b)->Integer.compare(Integer.parseInt(b.getName().substring(9)),Integer.parseInt(a.getName().substring(9))));
        if(files.length==0)return null;
        // Match the upstream's highest-index candidate; never silently fall back to an older CA.
        try(FileInputStream in=new FileInputStream(files[0])){
            X509Certificate cert=Certificates.parse(Certificates.read(in));cert.checkValidity();
            String subject=cert.getSubjectX500Principal().getName();
            if(!subject.equalsIgnoreCase("CN=AdGuard Personal CA,O=AdGuard,C=EN"))return null;
            return files[0];
        }catch(Exception invalid){return null;}
    }
    private boolean configured(){try(FileInputStream in=new FileInputStream(FLAG)){return in.read()=='1';}catch(Exception e){return false;}}
    private void disableMissing(){if(configured())try{Certificates.atomic(FLAG,"0\n".getBytes(StandardCharsets.US_ASCII));}catch(IOException ignored){}}
    @Override public boolean isChecked(){return owner()&&ROOT.isDirectory()&&personal(USER)!=null&&configured();}
    @Override public void displayPreference(PreferenceScreen screen){super.displayPreference(screen);preference=(TwoStatePreference)screen.findPreference("unica_adcert");if(preference!=null)preference.setPersistent(false);render();}
    @Override public void updateState(Preference p){super.updateState(p);if(p instanceof TwoStatePreference)preference=(TwoStatePreference)p;render();}
    @Override public void onStart(){render();}
    private void render(){
        if(preference==null)return;boolean supported=owner()&&ROOT.isDirectory();boolean present=supported&&personal(USER)!=null;
        if(supported&&!present)disableMissing();
        boolean checked=present&&configured();preference.setEnabled(present);preference.setChecked(checked);
        preference.setSummary(text(!supported?"unavailable":!present?"missing":checked?"on":"off"));
    }
    @Override public boolean setChecked(boolean value){
        if(!owner()||!ROOT.isDirectory()||(value&&personal(USER)==null)){render();return false;}
        try{Certificates.atomic(FLAG,(value?"1\n":"0\n").getBytes(StandardCharsets.US_ASCII));Toast.makeText(context,text("reboot"),Toast.LENGTH_LONG).show();render();return true;}
        catch(IOException e){Toast.makeText(context,text("write_failed"),Toast.LENGTH_LONG).show();render();return false;}
    }
}
