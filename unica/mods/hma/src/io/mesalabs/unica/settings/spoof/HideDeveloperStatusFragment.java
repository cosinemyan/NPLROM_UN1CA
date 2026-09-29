/* SPDX-License-Identifier: AGPL-3.0-or-later */
package io.mesalabs.unica.settings.spoof;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.net.Uri;
import android.provider.Settings;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import io.mesalabs.unica.utils.Utils;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

/** Retains the registered fragment name so existing Settings gateway links remain valid. */
public final class HideDeveloperStatusFragment extends SettingsPreferenceFragment implements Preference.OnPreferenceClickListener {
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER = new BaseSearchIndexProvider(Utils.getResourceId("xml", "unica_hma_privacy_settings"));
    private static final String KEY="unica_hma_policy";
    private JSONObject config;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private static final String[] APP_KEYS={"accessibility_apps","custom_rom","detector_apps","root_apps","shizuku_dhizuku","sus_apps","xposed"};
    private static final String[] APP_LABELS={"Accessibility apps","Custom ROM apps","Detector / checker apps","Root managers and rooted apps","Shizuku / Dhizuku apps","Suspicious apps","LSPosed / Xposed modules"};
    private static final String[] SETTING_KEYS={"accessibility","dev_options","input_method"};
    private static final String[] SETTING_LABELS={"Accessibility — hide settings and service lists","Developer options — hide ADB, developer and mock-location settings","Input method — report Gboard and Google TTS; filter keyboard lists"};
    private static final String[] GID_KEYS={"1015","1023","1032","1077","1078","1079","3003","9997"};
    private static final String[] GID_LABELS={"SDCARD_RW (1015) — media write access","MEDIA_RW (1023) — media write access","PACKAGE_INFO (1032) — package details","EXTERNAL_STORAGE (1077) — USB storage access","EXT_DATA_RW (1078) — external app data","EXT_OBB_RW (1079) — external OBB data","INET (3003) — removes network access; apps may crash","SHARED_USER (9997) — may block all shared storage access"};
    private static final String ENABLE_ALL="__enable_all_user_apps__";
    private static final String[] STORE_KEYS={"com.android.vending","com.sec.android.app.samsungapps"};
    private static final String[] STORE_LABELS={"Google Play Store","Samsung Galaxy Store"};
    private static final int REQ_BACKUP=9101, REQ_RESTORE=9102;

    public int getMetricsCategory() { return 744; }
    public void onCreate(Bundle state) {
        super.onCreate(state);
        addPreferencesFromResource(Utils.getResourceId("xml","unica_hma_privacy_settings"));
        try {
            String raw=Settings.Secure.getString(getActivity().getContentResolver(),KEY);
            config=new JSONObject(raw==null ? "{}" : raw);
            if(raw==null) {
                // Preserve lists without silently enabling new restrictions for every installed app.
                config.put("targets",legacyList("HideDeveloperStatusUtils", "unica_hide_dev_list"));
                config.put("hidden",legacyList("HideAppListUtils", "unica_hma_list"));
                config.put("enabled",false);
                config.put("activity",true);
                config.put("risky",true);
            }
        } catch(JSONException e) { config=new JSONObject(); }
        for(String key:new String[]{"enabled","targets","hidden","playUpdate","unknown","activity","invertActivity","apps","settings","risky","gids","backup","restore"}) findPreference("hma_"+key).setOnPreferenceClickListener(this);
        refresh();
    }
    private JSONArray csv(String text) {
        JSONArray a=new JSONArray();
        if(text!=null) for(String name:text.split(",")) if(!name.trim().isEmpty()) a.put(name.trim());
        return a;
    }
    private JSONArray legacyList(String helper, String key) {
        try {
            Object list=Class.forName("io.mesalabs.unica."+helper).getMethod("getApps",ContentResolver.class).invoke(null,getActivity().getContentResolver());
            return new JSONArray((Collection<?>)list);
        } catch(Exception e) { return csv(Settings.Secure.getString(getActivity().getContentResolver(),key)); }
    }
    private Set<String> selected(String key) {
        Set<String> result=new HashSet<String>();
        JSONArray a=config.optJSONArray(key);
        if(a!=null) for(int i=0;i<a.length();i++) result.add(a.optString(i));
        return result;
    }
    private JSONObject map(String key) {
        JSONObject map=config.optJSONObject(key);
        return map==null ? new JSONObject() : map;
    }
    private boolean defaultOn(String key) {
        return "activity".equals(key) || "risky".equals(key);
    }
    private boolean userApp(ApplicationInfo a) {
        int appId=a.uid%100000;
        return appId>=10000 && appId<20000 && (a.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))==0 && !a.packageName.equals("com.android.settings") && !a.packageName.equals("com.android.systemui");
    }
    private boolean save() {
        boolean ok=Settings.Secure.putString(getActivity().getContentResolver(),KEY,config.toString());
        if(!ok) new AlertDialog.Builder(getActivity()).setTitle("Could not save HMA settings").setMessage("The settings provider rejected this change.").setPositiveButton(android.R.string.ok,null).show();
        refresh();
        return ok;
    }
    private void refresh() {
        for(String key:new String[]{"enabled","activity","risky"}) {
            Preference pref=findPreference("hma_"+key);
            boolean checked=config.optBoolean(key,defaultOn(key));
            pref.setSummary(checked ? "On" : "Off");
            if(pref instanceof TwoStatePreference) ((TwoStatePreference)pref).setChecked(checked);
        }
        for(String key:new String[]{"targets","hidden","playUpdate","invertActivity","apps","settings","gids"}) findPreference("hma_"+key).setSummary(selected(key).size()+" selected");
        findPreference("hma_unknown").setSummary(map("installerSpoof").length()+" selected");
    }
    private void toggle(String key, Preference pref) {
        try {
            boolean checked=pref instanceof TwoStatePreference ? ((TwoStatePreference)pref).isChecked() : !config.optBoolean(key,defaultOn(key));
            config.put(key,checked);
            save();
        }
        catch(JSONException ignored) {}
    }
    public boolean onPreferenceClick(Preference p) {
        String key=p.getKey().substring(4);
        if("backup".equals(key)) backupConfig();
        else if("restore".equals(key)) restoreConfig();
        else if(Arrays.asList("enabled","activity","risky").contains(key)) toggle(key,p);
        else if("apps".equals(key)) choices(key,"Application presets",APP_KEYS,APP_LABELS,null);
        else if("settings".equals(key)) choices(key,"Settings presets",SETTING_KEYS,SETTING_LABELS,null);
        else if("gids".equals(key)) new AlertDialog.Builder(getActivity()).setTitle("Restrict Zygote permissions").setMessage("Experimental. These choices remove supplementary groups from selected apps at their next process start. Network or storage access may stop working and apps may crash. Restart selected apps after changing this setting.").setNegativeButton(android.R.string.cancel,null).setPositiveButton("Continue",new DialogInterface.OnClickListener(){ public void onClick(DialogInterface d,int w){ choices("gids","Permissions to remove",GID_KEYS,GID_LABELS,null); }}).show();
        else if("unknown".equals(key)) unknownChoices();
        else appChoices(key);
        return true;
    }
    private void choices(final String key,String title,final String[] values,String[] labels, final Set<String> retained) {
        choices(key,title,values,labels,retained,false);
    }
    private void choices(final String key,String title,final String[] values,String[] labels, final Set<String> retained, final boolean enableAll) {
        choices(key,title,values,labels,retained,enableAll,false);
    }
    private void choices(final String key,String title,final String[] values,final String[] labels, final Set<String> retained, final boolean enableAll, boolean searchable) {
        final Set<String> chosen=selected(key);
        final boolean[] checked=new boolean[values.length];
        for(int i=0;i<values.length;i++) checked[i]=enableAll && i==0 ? chosen.containsAll(Arrays.asList(values).subList(1,values.length)) : chosen.contains(values[i]);
        if(searchable) {
            final android.widget.EditText search=new android.widget.EditText(getActivity());
            search.setSingleLine(true);
            search.setHint("Search apps");
            final android.widget.ListView list=new android.widget.ListView(getActivity());
            list.setChoiceMode(android.widget.ListView.CHOICE_MODE_MULTIPLE);
            final ArrayList<Integer> visible=new ArrayList<Integer>();
            final ArrayList<String> shown=new ArrayList<String>();
            final android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<String>(getActivity(),android.R.layout.simple_list_item_multiple_choice,shown);
            list.setAdapter(adapter);
            android.widget.LinearLayout layout=new android.widget.LinearLayout(getActivity());
            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad=(int)(16*getActivity().getResources().getDisplayMetrics().density);
            layout.setPadding(pad,0,pad,0);
            layout.addView(search,new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT,android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
            layout.addView(list,new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT,0,1));
            final Runnable refreshList=new Runnable(){public void run(){
                String query=search.getText().toString().toLowerCase(Locale.ROOT);
                visible.clear();shown.clear();
                for(int i=0;i<labels.length;i++) if((enableAll && i==0) || query.length()==0 || labels[i].toLowerCase(Locale.ROOT).contains(query)){visible.add(Integer.valueOf(i));shown.add(labels[i]);}
                adapter.notifyDataSetChanged();
                for(int pos=0;pos<visible.size();pos++) list.setItemChecked(pos,checked[visible.get(pos).intValue()]);
            }};
            search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){} public void onTextChanged(CharSequence s,int start,int before,int count){refreshList.run();} public void afterTextChanged(android.text.Editable e){}});
            list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener(){public void onItemClick(android.widget.AdapterView<?> parent, android.view.View view, int pos, long id){
                int i=visible.get(pos).intValue();
                checked[i]=list.isItemChecked(pos);
                if(enableAll && i==0) for(int j=1;j<checked.length;j++) checked[j]=checked[0];
                else if(enableAll) { boolean all=true; for(int j=1;j<checked.length;j++) if(!checked[j]) all=false; checked[0]=all; }
                refreshList.run();
            }});
            refreshList.run();
            new AlertDialog.Builder(getActivity()).setTitle(title).setView(layout).setNegativeButton(android.R.string.cancel,null).setPositiveButton("Apply",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
                Set<String> result=retained==null ? new TreeSet<String>() : new TreeSet<String>(retained);
                for(int i=enableAll ? 1 : 0;i<values.length;i++) if(checked[i]) result.add(values[i]);
                try { config.put(key,new JSONArray(result)); save(); } catch(JSONException ignored) {}
            }}).show();
            return;
        }
        new AlertDialog.Builder(getActivity()).setTitle(title).setMultiChoiceItems(labels,checked,new DialogInterface.OnMultiChoiceClickListener(){public void onClick(DialogInterface d,int i,boolean value){checked[i]=value;}}).setNegativeButton(android.R.string.cancel,null).setPositiveButton("Apply",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
            Set<String> result=retained==null ? new TreeSet<String>() : new TreeSet<String>(retained);
            for(int i=enableAll ? 1 : 0;i<values.length;i++) if(checked[i]) result.add(values[i]);
            try { config.put(key,new JSONArray(result)); save(); } catch(JSONException ignored) {}
        }}).show().getListView().setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener(){public void onItemClick(android.widget.AdapterView<?> parent, android.view.View view, int i, long id){
            checked[i]=((android.widget.ListView)parent).isItemChecked(i);
            if(enableAll && i==0) for(int j=1;j<checked.length;j++){checked[j]=checked[0];((android.widget.ListView)parent).setItemChecked(j,checked[0]);}
            else if(enableAll) { boolean all=true; for(int j=1;j<checked.length;j++) if(!checked[j]) all=false; checked[0]=all; ((android.widget.ListView)parent).setItemChecked(0,all); }
        }});
    }
    private void appChoices(final String key) {
        final Context c=getActivity();
        final Set<String> targets=selected("targets");
        final Set<String> previous=selected(key);
        new Thread(new Runnable(){public void run(){
            try {
                final PackageManager pm=c.getPackageManager();
                List<ApplicationInfo> apps=new ArrayList<ApplicationInfo>();
                for(ApplicationInfo a:pm.getInstalledApplications(0)) {
                    if(!userApp(a)) continue;
                    if("invertActivity".equals(key) && !targets.contains(a.packageName)) continue;
                    apps.add(a);
                }
                Collections.sort(apps,new Comparator<ApplicationInfo>(){public int compare(ApplicationInfo a,ApplicationInfo b){return pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString());}});
                final boolean enableAll="targets".equals(key);
                final int offset=enableAll ? 1 : 0;
                final String[] values=new String[apps.size()+offset],labels=new String[apps.size()+offset];
                if(enableAll) { values[0]=ENABLE_ALL; labels[0]="Enable All"; }
                final Set<String> retained=new HashSet<String>(previous);
                for(int i=0;i<apps.size();i++){ApplicationInfo a=apps.get(i); values[i+offset]=a.packageName; labels[i+offset]=pm.getApplicationLabel(a)+"\n"+a.packageName; retained.remove(a.packageName);}
                handler.post(new Runnable(){public void run(){if(isAdded()) choices(key,"hidden".equals(key)?"App to hide":"playUpdate".equals(key)?"Prevent Play Store update":"invertActivity".equals(key)?"Invert activity protection for":"App to spoof",values,labels,retained,enableAll,true);}});
            } catch(final RuntimeException e) { handler.post(new Runnable(){public void run(){if(isAdded()) new AlertDialog.Builder(getActivity()).setMessage("Could not load installed apps.").setPositiveButton(android.R.string.ok,null).show();}}); }
        }},"HMA-app-picker").start();
    }
    private String installSource(PackageManager pm, String name) {
        try {
            Object info=pm.getClass().getMethod("getInstallSourceInfo",String.class).invoke(pm,name);
            if(info==null) return null;
            Object installing=info.getClass().getMethod("getInstallingPackageName").invoke(info);
            Object initiating=info.getClass().getMethod("getInitiatingPackageName").invoke(info);
            return installing instanceof String ? (String)installing : initiating instanceof String ? (String)initiating : null;
        } catch(Exception e) { return ""; }
    }

    public void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=-1 || data==null || data.getData()==null) return;
        if(requestCode==REQ_BACKUP) writeBackup(data.getData());
        else if(requestCode==REQ_RESTORE) readBackup(data.getData());
    }
    private void backupConfig() {
        try {
            Intent intent=new Intent("android.intent.action.CREATE_DOCUMENT");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE,"HMA-OSS_config_"+new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss",Locale.US).format(new Date())+".json");
            startActivityForResult(intent,REQ_BACKUP);
        } catch(RuntimeException e) {
            new AlertDialog.Builder(getActivity()).setTitle("Could not open file picker").setMessage(e.toString()).setPositiveButton(android.R.string.ok,null).show();
        }
    }
    private void restoreConfig() {
        try {
            Intent intent=new Intent("android.intent.action.OPEN_DOCUMENT");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent,REQ_RESTORE);
        } catch(RuntimeException e) {
            new AlertDialog.Builder(getActivity()).setTitle("Could not open file picker").setMessage(e.toString()).setPositiveButton(android.R.string.ok,null).show();
        }
    }
    private void writeBackup(Uri uri) {
        OutputStream out=null;
        try {
            out=getActivity().getContentResolver().openOutputStream(uri);
            if(out==null) throw new IOException("No output stream");
            out.write(exportPayload().toString(2).getBytes("UTF-8"));
            new AlertDialog.Builder(getActivity()).setTitle("HMA config backed up").setMessage("The current HMA configuration was exported as an HMA-OSS style JSON file.").setPositiveButton(android.R.string.ok,null).show();
        } catch(Exception e) {
            new AlertDialog.Builder(getActivity()).setTitle("Could not back up HMA config").setMessage(e.toString()).setPositiveButton(android.R.string.ok,null).show();
        } finally { closeQuietly(out); }
    }
    private void readBackup(Uri uri) {
        InputStream in=null;
        try {
            in=getActivity().getContentResolver().openInputStream(uri);
            if(in==null) throw new IOException("No input stream");
            JSONObject root=new JSONObject(readAll(in));
            JSONObject policy=root.optJSONObject(KEY);
            if(policy==null) policy=(root.has("scope") || root.has("templates")) ? fromHmaOss(root) : root;
            config=normalize(policy);
            if(save()) new AlertDialog.Builder(getActivity()).setTitle("HMA config restored").setMessage("Restart protected apps so restored package visibility and process permission changes apply.").setPositiveButton(android.R.string.ok,null).show();
        } catch(Exception e) {
            new AlertDialog.Builder(getActivity()).setTitle("Could not restore HMA config").setMessage(e.toString()).setPositiveButton(android.R.string.ok,null).show();
        } finally { closeQuietly(in); }
    }
    private String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[8192];
        for(int n;(n=in.read(buf))!=-1;) out.write(buf,0,n);
        return new String(out.toByteArray(),"UTF-8");
    }
    private void closeQuietly(Closeable c) {
        if(c!=null) try { c.close(); } catch(IOException ignored) {}
    }
    private JSONObject exportPayload() throws JSONException {
        JSONObject policy=normalize(config);
        JSONArray hidden=array(policy,"hidden");
        JSONObject root=new JSONObject();
        root.put("configVersion",93);
        root.put("detailLog",false);
        root.put("errorOnlyLog",false);
        root.put("maxLogSize",256);
        root.put("forceMountData",false);
        root.put("disableActivityLaunchProtection",!policy.optBoolean("activity",true));
        root.put("altAppDataIsolation",false);
        root.put("altVoldAppDataIsolation",false);
        root.put("skipSystemAppDataIsolation",true);
        root.put("packageQueryWorkaround",false);
        root.put("webViewProtection",false);
        root.put("defaultConfig",JSONObject.NULL);
        JSONObject templates=new JSONObject();
        if(hidden.length()>0) { JSONObject t=new JSONObject(); t.put("isWhitelist",false); t.put("appList",cloneArray(hidden)); templates.put("UN1CA App to hide",t); }
        root.put("templates",templates);
        root.put("settingsTemplates",new JSONObject());
        root.put("disabledHooks",new JSONArray());
        JSONObject scope=new JSONObject();
        JSONArray targets=array(policy,"targets");
        for(int i=0;i<targets.length();i++) scope.put(targets.optString(i),scopeConfig(targets.optString(i),policy));
        root.put("scope",scope);
        root.put("unicaPolicyVersion",1);
        root.put(KEY,policy);
        return root;
    }
    private JSONObject scopeConfig(String target, JSONObject policy) throws JSONException {
        JSONObject c=new JSONObject();
        c.put("useWhitelist",false);
        c.put("excludeSystemApps",false);
        c.put("hideInstallationSource",false);
        c.put("hideSystemInstallationSource",false);
        c.put("excludeTargetInstallationSource",false);
        c.put("invertActivityLaunchProtection",set(array(policy,"invertActivity")).contains(target));
        c.put("excludeVoldIsolation",false);
        c.put("restrictedZygotePermissions",cloneArray(array(policy,"gids")));
        JSONArray applyTemplates=new JSONArray();
        if(array(policy,"hidden").length()>0) applyTemplates.put("UN1CA App to hide");
        c.put("applyTemplates",applyTemplates);
        c.put("applyPresets",cloneArray(array(policy,"apps")));
        c.put("applySettingTemplates",new JSONArray());
        c.put("applySettingsPresets",cloneArray(array(policy,"settings")));
        c.put("extraAppList",cloneArray(array(policy,"hidden")));
        c.put("extraOppositeAppList",new JSONArray());
        return c;
    }
    private JSONObject fromHmaOss(JSONObject root) throws JSONException {
        Set<String> targets=new TreeSet<String>(),hidden=new TreeSet<String>(),apps=new TreeSet<String>(),settings=new TreeSet<String>(),gids=new TreeSet<String>(),invert=new TreeSet<String>();
        JSONObject templates=root.optJSONObject("templates");
        JSONObject scope=root.optJSONObject("scope");
        if(scope!=null) {
            Iterator<String> it=scope.keys();
            while(it.hasNext()) {
                String pkg=it.next(); targets.add(pkg);
                JSONObject c=scope.optJSONObject(pkg);
                if(c==null) continue;
                if(c.optBoolean("invertActivityLaunchProtection",false)) invert.add(pkg);
                addStrings(hidden,c.optJSONArray("extraAppList"));
                addStrings(apps,c.optJSONArray("applyPresets"));
                addStrings(settings,c.optJSONArray("applySettingsPresets"));
                addStrings(gids,c.optJSONArray("restrictedZygotePermissions"));
                JSONArray applied=c.optJSONArray("applyTemplates");
                for(int i=0;applied!=null && i<applied.length();i++) {
                    JSONObject t=templates==null ? null : templates.optJSONObject(applied.optString(i));
                    if(t!=null) addStrings(hidden,t.optJSONArray("appList"));
                }
            }
        }
        JSONObject out=new JSONObject();
        out.put("enabled",targets.size()>0);
        out.put("targets",new JSONArray(targets));
        out.put("hidden",new JSONArray(hidden));
        out.put("playUpdate",new JSONArray());
        out.put("installerSpoof",new JSONObject());
        out.put("activity",!root.optBoolean("disableActivityLaunchProtection",false));
        out.put("invertActivity",new JSONArray(invert));
        out.put("apps",new JSONArray(apps));
        out.put("settings",new JSONArray(settings));
        out.put("risky",true);
        out.put("gids",new JSONArray(gids));
        return out;
    }
    private JSONObject normalize(JSONObject source) throws JSONException {
        JSONObject out=new JSONObject();
        out.put("enabled",source.optBoolean("enabled",false));
        out.put("activity",source.optBoolean("activity",true));
        out.put("risky",source.optBoolean("risky",true));
        for(String key:new String[]{"targets","hidden","playUpdate","invertActivity","apps","settings","gids"}) out.put(key,cloneArray(source.optJSONArray(key)));
        JSONObject installers=new JSONObject();
        JSONObject src=source.optJSONObject("installerSpoof");
        if(src!=null) { Iterator<String> it=src.keys(); while(it.hasNext()) { String k=it.next(); Object v=src.opt(k); if(v instanceof String) installers.put(k,(String)v); } }
        out.put("installerSpoof",installers);
        return out;
    }
    private JSONArray array(JSONObject o,String key) { return cloneArray(o.optJSONArray(key)); }
    private JSONArray cloneArray(JSONArray in) {
        JSONArray out=new JSONArray();
        for(int i=0;in!=null && i<in.length();i++) { String v=in.optString(i,null); if(v!=null && v.length()>0) out.put(v); }
        return out;
    }
    private Set<String> set(JSONArray a) {
        Set<String> s=new HashSet<String>();
        addStrings(s,a);
        return s;
    }
    private void addStrings(Set<String> s, JSONArray a) {
        for(int i=0;a!=null && i<a.length();i++) { String v=a.optString(i,null); if(v!=null && v.length()>0) s.add(v); }
    }
    private void unknownChoices() {
        final Context c=getActivity();
        final JSONObject current=map("installerSpoof");
        new Thread(new Runnable(){public void run(){
            try {
                final PackageManager pm=c.getPackageManager();
                List<ApplicationInfo> apps=new ArrayList<ApplicationInfo>();
                for(ApplicationInfo a:pm.getInstalledApplications(0)) if(userApp(a) && installSource(pm,a.packageName)==null) apps.add(a);
                Collections.sort(apps,new Comparator<ApplicationInfo>(){public int compare(ApplicationInfo a,ApplicationInfo b){return pm.getApplicationLabel(a).toString().compareToIgnoreCase(pm.getApplicationLabel(b).toString());}});
                final String[] values=new String[apps.size()],labels=new String[apps.size()];
                final boolean[] checked=new boolean[apps.size()];
                for(int i=0;i<apps.size();i++){ApplicationInfo a=apps.get(i); values[i]=a.packageName; String store=current.optString(a.packageName,null); checked[i]=store!=null; labels[i]=pm.getApplicationLabel(a)+"\n"+a.packageName+(store==null?"":"\n"+("com.sec.android.app.samsungapps".equals(store)?STORE_LABELS[1]:STORE_LABELS[0]));}
                handler.post(new Runnable(){public void run(){if(!isAdded()) return; new AlertDialog.Builder(getActivity()).setTitle("Unknown Installed").setMultiChoiceItems(labels,checked,new DialogInterface.OnMultiChoiceClickListener(){public void onClick(DialogInterface d,int i,boolean value){checked[i]=value;}}).setNegativeButton(android.R.string.cancel,null).setPositiveButton("Apply",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
                    final Set<String> result=new TreeSet<String>();
                    for(int i=0;i<values.length;i++) if(checked[i]) result.add(values[i]);
                    if(result.isEmpty()) { try{config.put("installerSpoof",new JSONObject());save();}catch(JSONException ignored){}; return; }
                    new AlertDialog.Builder(getActivity()).setTitle("Spoof installation source").setItems(STORE_LABELS,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){
                        try{JSONObject map=new JSONObject(); for(String pkg:result) map.put(pkg,STORE_KEYS[which]); config.put("installerSpoof",map); save();}catch(JSONException ignored){}
                    }}).show();
                }}).show();}});
            } catch(final RuntimeException e) { handler.post(new Runnable(){public void run(){if(isAdded()) new AlertDialog.Builder(getActivity()).setMessage("Could not load unknown installed apps.").setPositiveButton(android.R.string.ok,null).show();}}); }
        }},"HMA-unknown-picker").start();
    }
}
