/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.keybox;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Native Settings owner of all keybox writes. The repository receives no imported data. */
public final class KeyboxActivity extends Activity {
    private static final String SETTING="unica_keybox_data", KEYBOX_METADATA="unica_keybox_metadata";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<View> cards=new ArrayList<>();
    private LinearLayout content,card;
    private ScrollView scrollView;
    private boolean busy;
    private String page="main";
    private final ArrayList<String> backPages=new ArrayList<>();
    private final ArrayList<Integer> backProfiles=new ArrayList<>(),backScrolls=new ArrayList<>();
    private int profileIndex;
    private TextView backendView;
    private String statusChecked="";
    private final Handler refreshHandler=new Handler(Looper.getMainLooper());
    private final Runnable refreshStatus=new Runnable(){public void run(){if(backendView!=null)backendView.setText(statusDisplay());refreshHandler.postDelayed(this,3000);}};
    @Override protected void onResume(){super.onResume();refreshHandler.post(refreshStatus);}
    @Override protected void onPause(){refreshHandler.removeCallbacks(refreshStatus);super.onPause();}
    private String status="Check the current keybox against Google's revocation list.";
    private boolean red;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);setTitle("Manage Keybox");
        if(state!=null){page=state.getString("page","main");profileIndex=state.getInt("profile",0);
            ArrayList<String> pages=state.getStringArrayList("backPages");ArrayList<Integer> profiles=state.getIntegerArrayList("backProfiles"),scrolls=state.getIntegerArrayList("backScrolls");
            if(pages!=null&&profiles!=null&&scrolls!=null&&pages.size()==profiles.size()&&pages.size()==scrolls.size()){backPages.addAll(pages);backProfiles.addAll(profiles);backScrolls.addAll(scrolls);}}
        if(!Arrays.asList("main","profiles","profile","keys","system","keyboxes").contains(page)){page="main";backPages.clear();backProfiles.clear();backScrolls.clear();}
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{if(!back())finish();});
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if(getActionBar()!=null)getActionBar().setDisplayHomeAsUpEnabled(true);
        draw();checkStatus();
    }
    @Override public void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("page",page);state.putInt("profile",profileIndex);state.putStringArrayList("backPages",backPages);state.putIntegerArrayList("backProfiles",backProfiles);state.putIntegerArrayList("backScrolls",backScrolls);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==android.R.id.home){onBackPressed();return true;}return super.onOptionsItemSelected(item);}
    @Override public void onBackPressed(){if(!back())super.onBackPressed();}
    private boolean back(){
        if(busy){Toast.makeText(this,"Wait for the keybox operation to finish",Toast.LENGTH_SHORT).show();return true;}
        if(page.equals("main"))return false;
        int scroll=0;
        if(!backPages.isEmpty()){int last=backPages.size()-1;page=backPages.remove(last);profileIndex=backProfiles.remove(last);scroll=backScrolls.remove(last);}
        else{page=page.equals("profile")?"profiles":"main";profileIndex=0;}
        scrollView=null;draw();final int position=scroll;scrollView.post(()->scrollView.scrollTo(0,position));return true;
    }
    private String current(){return Settings.Secure.getString(getContentResolver(),SETTING);}
    private String currentName(){return Profiles.keyboxName(Settings.Secure.getString(getContentResolver(),KEYBOX_METADATA),current());}
    private String importFilename(Uri uri){
        try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){String name=cursor.getString(0);if(name!=null&&Profiles.validKeyboxName(name))return name;}
        }catch(Exception ignored){}
        String name=uri.getLastPathSegment();return name!=null&&Profiles.validKeyboxName(name)&&name.toLowerCase(Locale.ROOT).endsWith(".xml")?name:"keybox.xml";
    }
    private boolean loaded(){String xml=current();return xml!=null&&!xml.trim().isEmpty();}
    private boolean enabled(){try{return (Boolean)Class.forName("android.os.SemSystemProperties").getMethod("getBoolean",String.class,boolean.class).invoke(null,"persist.sys.unica.keybox",false);}catch(Exception e){return false;}}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private int themed(int attribute,int fallback){TypedValue value=new TypedValue();if(!getTheme().resolveAttribute(attribute,value,true))return fallback;return value.resourceId!=0?getColor(value.resourceId):value.data;}
    private TextView label(String value,int size,boolean secondary){
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);
        view.setTextColor(themed(secondary?android.R.attr.textColorSecondary:android.R.attr.textColorPrimary,secondary?(dark()?0xffbfc1c7:0xff62656b):(dark()?0xfff4f4f6:0xff17181b)));
        view.setGravity(Gravity.START);return view;
    }
    private void group(){card=new LinearLayout(this);card.setOrientation(1);card.setClipToOutline(true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(24);content.addView(card,p);cards.add(card);}
    private TextView text(String value,int size){TextView v=label(value,size,size<20);v.setPaddingRelative(dp(24),size>=20?dp(22):0,dp(24),dp(14));if(size>=20)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));card.addView(v);return v;}
    private void note(String value){TextView view=label(value,14,true);view.setPaddingRelative(dp(24),0,dp(24),dp(24));content.addView(view);}
    private void button(String title,String summary,Runnable action){
        if(card.getChildCount()>0){View divider=new View(this);divider.setBackgroundColor(themed(android.R.attr.textColorSecondary,dark()?0xffbfc1c7:0xff62656b));divider.setAlpha(0.14f);LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,dp(1));line.setMarginStart(dp(24));line.setMarginEnd(dp(24));card.addView(divider,line);}
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(88));row.setPaddingRelative(dp(24),dp(18),dp(20),dp(18));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        TextView heading=label(title,20,false);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));labels.addView(heading);
        if(title.equals("Clear imported keybox"))heading.setTextColor(dark()?0xffff8a80:0xffb3261e);
        TextView detail=label(summary,15,true);detail.setPadding(0,dp(5),0,0);labels.addView(detail);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=label(getResources().getConfiguration().getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?"‹":"›",26,true);arrow.setPaddingRelative(dp(16),0,0,0);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(arrow);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(themed(android.R.attr.colorControlHighlight,dark()?0x33ffffff:0x18000000)),null,new ColorDrawable(Color.WHITE)));
        row.setEnabled(!busy);row.setAlpha(busy?0.45f:1f);row.setFocusable(true);row.setContentDescription(title+". "+summary);row.setOnClickListener(v->action.run());
        labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        row.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());}});
        card.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private void draw(){
        if(isFinishing()||isDestroyed())return;
        setTitle(page.equals("main")?"Manage Keybox":page.equals("profiles")?"Profiles":page.equals("profile")?"Profile settings":page.equals("keys")?"Stored keys":page.equals("system")?"System":"Keyboxes");
        backendView=null;
        int previousScroll=scrollView==null?0:scrollView.getScrollY();
        cards.clear();ScrollView scroll=new ScrollView(this);scrollView=scroll;scroll.setFillViewport(true);scroll.setClipToPadding(false);content=new LinearLayout(this);content.setOrientation(1);content.setPadding(dp(16),dp(20),dp(16),dp(24));scroll.addView(content);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        if(!page.equals("main")){
            try{if(page.equals("profiles"))drawProfiles();else if(page.equals("profile"))drawProfile();else if(page.equals("keys"))drawKeys();else if(page.equals("system"))drawSystem();else if(page.equals("keyboxes"))drawKeyboxes();}
            catch(Exception error){group();text("Configuration unavailable",22);text("Restore default routing to recover. Your current imported keybox is retained.",16);button("Restore default routing","Back up existing configuration and use the current keybox",this::restore);}
            style();scroll.post(()->scroll.scrollTo(0,previousScroll));return;
        }
        group();text("Manage Keybox",26);backendView=text(statusDisplay(),16);
        button("Refresh status","Read current state and show confirmation",this::refreshBackendStatus);
        group();text("TEESimulator",22);
        button("Profiles","Keybox selection, attestation settings and target apps",this::profiles);
        button("Keyboxes","Import, inspect, rename and remove signing keyboxes",()->navigate("keyboxes"));
        button("Stored keys","Inspect and manage target-app keys",this::loadKeys);
        button("System","Daemon, interceptor and captured hardware state",this::loadSystem);
        note("Turning spoof hardware attestation OFF stops the control daemon and disables new spoofed requests. The injected library remains dormant for existing simulator keys until keystore exits. After rebooting while OFF, apps may need the backend enabled again to use those keys.");
        group();text("Recovery",22);
        button("Restore default routing","Back up profiles and use the current keybox",this::restore);
        button("Restore previous profiles","Undo the last restore of default routing",this::restoreBackup);
        style();
        scroll.post(()->scroll.scrollTo(0,previousScroll));
    }
    private boolean dark(){return (getResources().getConfiguration().uiMode&0x30)==0x20;}
    private int color(String name,int fallback){int id=getResources().getIdentifier(name,"color",getPackageName());return id==0?fallback:getColor(id);}
    private void style(){
        boolean transparent=Settings.System.getInt(getContentResolver(),"unica_settings_bg_transparency_enabled",0)!=0;
        int level=Math.max(70,Math.min(100,Settings.System.getInt(getContentResolver(),"unica_settings_bg_transparency_level",70)));
        int surface=color("sesl_background_color_"+(dark()?"dark":"light"),dark()?0xff17171a:0xffffffff);
        int background=color("sesl_round_and_bgcolor_"+(dark()?"dark":"light"),dark()?0xff000000:0xfff2f2f2);
        if(transparent){surface=(surface&0xffffff)|((255-level*255/100)<<24);background=0;getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);}else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setFormat(transparent?android.graphics.PixelFormat.TRANSLUCENT:android.graphics.PixelFormat.OPAQUE);getWindow().setBackgroundDrawable(new ColorDrawable(background));
        for(View v:cards){GradientDrawable shape=new GradientDrawable();shape.setColor(surface);shape.setCornerRadius(dp(30));v.setBackground(shape);}
    }
    private interface Job<T>{T run()throws Exception;}
    private interface Done<T>{void run(T value)throws Exception;}
    private <T> void background(Job<T> job,Done<T> done){
        if(busy)return;busy=true;draw();worker.submit(()->{try{T value=job.run();runOnUiThread(()->{busy=false;if(isDestroyed()||isFinishing())return;try{done.run(value);}catch(Exception e){failure("Operation failed. Current data was not replaced.");}});}catch(Exception e){runOnUiThread(()->{busy=false;if(!isDestroyed())failure("Operation failed. Current data was not replaced.");});}});
    }
    private void failure(String message){status=message;red=false;draw();}
    private String readAsset(String name)throws Exception{try(InputStream in=getAssets().open("unica-keybox/"+name)){return new String(Keybox.read(in,Keybox.MAX_BYTES),StandardCharsets.UTF_8);}}
    private String asset()throws Exception{return readAsset("aosp-keybox.txt");}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==43){if(result==RESULT_OK&&data!=null&&data.getData()!=null)additional(data.getData());return;}
        if(request==42&&result==RESULT_OK&&data!=null){String address=data.getStringExtra("url");try{URI uri=new URI(address);if(address.length()>4096||!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();new AlertDialog.Builder(this).setTitle("Download selected keybox?").setMessage("Source: "+uri.getHost()+"\nThe downloaded keybox will be validated before you can apply it.").setNegativeButton("Cancel",null).setPositiveButton("Download",(d,w)->stage(()->get(address,Keybox.MAX_BYTES),false,importFilename(Uri.parse(address)))).show();}catch(Exception e){failure("Repository selection unavailable");}return;}if(request==41&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();stage(()->{try(InputStream in=getContentResolver().openInputStream(uri)){return new String(Keybox.read(in,Keybox.MAX_BYTES),StandardCharsets.UTF_8).replaceFirst("^\\uFEFF","");}},false,importFilename(uri));}}
    private static final class Candidate {String xml,summary,metadata;boolean aosp,revoked,unknown;}
    private void stage(Job<String> source,boolean aosp,String label){
        background(()->{
            Candidate c=new Candidate();c.xml=source.run().replaceFirst("^\\uFEFF","");c.metadata=Profiles.keyboxMetadata(label,c.xml).toString();c.aosp=aosp;Keybox parsed=Keybox.parse(c.xml,aosp);
            if(parsed.expired&&!aosp)throw new IllegalArgumentException("Expired certificate");
            try{c.revoked=parsed.revoked(revocations());}catch(Exception e){c.unknown=true;}
            c.summary=aosp?"AOSP software test keybox"+(parsed.expired?" · expired test certificate":""):c.revoked?"Revoked — listed by Google":c.unknown?"Revocation status unknown — could not fetch Google's list":"Not listed as revoked by Google";
            return c;
        },c->{
            TextView warning=new TextView(this);warning.setPadding(dp(24),dp(16),dp(24),dp(16));warning.setText(c.summary+"\n\nReplace the current imported keybox with "+label+"?");if(c.revoked)warning.setTextColor(dark()?0xffff8a80:0xffb3261e);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Review keybox").setView(warning).setNegativeButton("Cancel",null).setPositiveButton(c.revoked?"Apply revoked keybox":"Apply",(d,w)->{
                if(!Settings.Secure.putString(getContentResolver(),SETTING,c.xml)){failure("Could not save keybox");return;}
                if(!Settings.Secure.putString(getContentResolver(),KEYBOX_METADATA,c.metadata))Toast.makeText(this,"Keybox imported; filename could not be saved",Toast.LENGTH_LONG).show();
                status=c.summary;red=c.revoked;draw();
            }).create();dialog.show();
        });
    }
    private void checkStatus(){
        String snapshot=current();if(snapshot==null||snapshot.trim().isEmpty()){status="No keybox imported";red=false;draw();return;}
        status="Checking certificate and revocation status…";red=false;
        background(()->{
            boolean aosp=snapshot.equals(asset());Keybox box=Keybox.parse(snapshot,aosp);
            Candidate c=new Candidate();c.aosp=aosp;
            try{c.revoked=box.revoked(revocations());}catch(Exception e){c.unknown=true;}
            c.summary=c.revoked?"Revoked — listed by Google":aosp?"AOSP software test keybox"+(box.expired?" · expired test certificate":"")+(c.unknown?"\nRevocation status unknown":"\nNot listed as revoked by Google"):box.expired?"Certificate expired or not yet valid":c.unknown?"Unknown — Google's revocation list is unavailable":"Not listed as revoked by Google";
            return c;
        },c->{if(!Objects.equals(snapshot,current())){status="Keybox changed. Check its status again.";red=false;}else{status=c.summary;red=c.revoked;}draw();});
    }
    private static Set<String> revocations()throws Exception{
        JSONObject entries=new JSONObject(get("https://android.googleapis.com/attestation/status",8*1024*1024)).getJSONObject("entries");
        Set<String> revoked=new HashSet<>();Iterator<String> keys=entries.keys();
        while(keys.hasNext()){String serial=keys.next();String state=entries.getJSONObject(serial).getString("status");if(!state.equals("REVOKED")&&!state.equals("SUSPENDED"))throw new IOException("Unknown certificate status");revoked.add(new java.math.BigInteger(serial,16).toString(16));}
        return revoked;
    }
    private static String get(String address,int limit)throws Exception{
        for(int redirects=0;redirects<4;redirects++){
            URI uri=new URI(address);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||(uri.getPort()!=-1&&uri.getPort()!=443))throw new IOException("HTTPS required");
            // Repository selections cannot turn a privileged fetch into a local-network request.
            for(InetAddress ip:InetAddress.getAllByName(uri.getHost()))if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isLinkLocalAddress()||ip.isSiteLocalAddress()||ip.isMulticastAddress()||(ip.getAddress().length==16&&(ip.getAddress()[0]&0xfe)==0xfc))throw new IOException("Public host required");
            HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(8000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("User-Agent","UN1CA Settings keybox");
            try{int code=connection.getResponseCode();if(code>=300&&code<=399){String next=connection.getHeaderField("Location");if(next==null)throw new IOException("Invalid redirect");address=uri.resolve(next).toString();continue;}if(code!=200)throw new IOException("Download failed");try(InputStream input=connection.getInputStream()){return new String(Keybox.read(input,limit),StandardCharsets.UTF_8);}}finally{connection.disconnect();}
        }
        throw new IOException("Too many redirects");
    }
    private String property(String name){try{return (String)Class.forName("android.os.SemSystemProperties").getMethod("get",String.class,String.class).invoke(null,name,"");}catch(Exception e){return "";}}
    private boolean fresh(){try{long age=SystemClock.elapsedRealtime()-Long.parseLong(property("sys.unica.teesim.heartbeat"));return age>=0&&age<12000;}catch(Exception e){return false;}}
    private String backend(String name){if(!enabled()&&property("init.svc.unica-teesim").equals("stopped"))return "No — daemon stopped";return fresh()?(property("sys.unica.teesim."+name).equals("1")?"Yes":"No"):"Unavailable — backend not reporting";}
    private String checkedTime(){return java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(new Date());}
    private String statusDisplay(){statusChecked=checkedTime();return backendSummary()+"\nLast checked: "+statusChecked;}
    private void refreshBackendStatus(){
        if(backendView!=null)backendView.setText(statusDisplay());
        Toast.makeText(this,fresh()?"Backend status refreshed":"Backend is not reporting fresh status",Toast.LENGTH_SHORT).show();
    }
    private String backendSummary(){return "Enabled: "+(enabled()?"Yes":"No")+"\nKeybox loaded by interceptor: "+backend("loaded")+"\nConfiguration acknowledged: "+backend("ack")+"\nInterceptor connected: "+backend("connected");}
    private JSONObject config()throws JSONException{return Profiles.load(Settings.Secure.getString(getContentResolver(),Profiles.SETTING));}
    private JSONObject selected(JSONObject root)throws JSONException{return root.getJSONArray("profiles").getJSONObject(profileIndex);}
    private void save(JSONObject root)throws Exception{
        Profiles.validate(root);
        // A shared UID is a single keystore caller, even if PackageManager lists several packages.
        Map<Integer,Integer> owners=new HashMap<>();JSONArray profiles=root.getJSONArray("profiles");
        for(int i=0;i<profiles.length();i++){JSONArray apps=profiles.getJSONObject(i).getJSONArray("apps");for(int n=0;n<apps.length();n++){
            try{int uid=getPackageManager().getApplicationInfo(apps.getString(n),0).uid;
                if(uid<10000||uid>19999)throw new IllegalArgumentException("Only owner-user application UIDs can be targeted");
                Integer old=owners.put(uid,i);if(old!=null&&old!=i)throw new IllegalArgumentException("Apps sharing a UID must use the same profile");
            }catch(android.content.pm.PackageManager.NameNotFoundException absent){/* Retain selections for temporarily uninstalled apps. */}
        }}
        if(!Settings.Secure.putString(getContentResolver(),Profiles.SETTING,root.toString()))throw new IOException("Could not save configuration");
        Toast.makeText(this,"Saved · waiting for backend acknowledgement",Toast.LENGTH_SHORT).show();draw();
    }
    private void changed(JSONObject root){try{save(root);}catch(Exception e){message("Configuration not saved",e instanceof JSONException||e instanceof IllegalArgumentException?e.getMessage():"Could not save configuration. Existing settings remain active.");}}
    private void message(String title,String value){TextView view=label(value,16,false);view.setTextIsSelectable(true);view.setPadding(dp(24),dp(20),dp(24),dp(20));ScrollView scroll=new ScrollView(this);scroll.addView(view);new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Close",null).show();}
    private interface ValueAction {void run(String value)throws Exception;}
    private void edit(String title,String initial,String hint,ValueAction action){
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(initial);input.setHint(hint);input.setPadding(dp(24),dp(18),dp(24),dp(18));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{action.run(input.getText().toString().trim());dialog.dismiss();}catch(Exception e){input.setError(e instanceof JSONException||e instanceof IllegalArgumentException?e.getMessage():"Could not save");}}));dialog.show();
    }
    private void dropdown(String title,String current,String[] choices,ValueAction action){
        LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPaddingRelative(dp(24),dp(16),dp(24),dp(12));
        row.addView(label(title,20,false));Spinner spinner=new Spinner(this,Spinner.MODE_DROPDOWN);
        List<String> values=new ArrayList<>(Arrays.asList(choices));if(!values.contains(current))values.add(current);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);spinner.setSelection(values.indexOf(current));spinner.setMinimumHeight(dp(48));spinner.setContentDescription(title);spinner.setEnabled(!busy);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> parent){}public void onItemSelected(AdapterView<?> parent,View view,int position,long id){String value=values.get(position);if(value.equals(current))return;try{action.run(value);}catch(Exception e){message("Setting not saved","Could not save "+title.toLowerCase(Locale.ROOT));spinner.setSelection(values.indexOf(current));}}});
        row.addView(spinner,new LinearLayout.LayoutParams(-1,-2));card.addView(row);
    }
    private void details(){
        try{JSONObject root=config();String xml=Profiles.xml(root,selected(root),current());
            if(xml==null||xml.trim().isEmpty()){message("Keybox details","No keybox configured for this profile.");return;}
            showDetailsXml(xml);
        }catch(Exception e){message("Keybox details","Configuration or certificate validation failed. No key material was changed.");}
    }
    private void showDetailsXml(String xml){
            background(()->{
                boolean aosp=xml.equals(asset());Keybox box=Keybox.parse(xml,aosp);Set<String> revoked=null;
                try{revoked=revocations();}catch(Exception unavailable){}
                StringBuilder out=new StringBuilder();java.text.DateFormat dates=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT);
                int chainNumber=0;
                for(List<java.security.cert.X509Certificate> chain:box.chains){
                    out.append("Chain ").append(++chainNumber).append("\nAlgorithm: ").append(chain.get(0).getPublicKey().getAlgorithm()).append("\nChain length: ").append(chain.size())
                       .append("\nSignatures: verified\nPrivate key match: verified\nRoot authority: ").append(aosp?"AOSP software test root":"Google attestation root").append('\n');
                    for(int i=0;i<chain.size();i++){java.security.cert.X509Certificate cert=chain.get(i);boolean valid=true;try{cert.checkValidity();}catch(Exception e){valid=false;}
                        out.append("\nCertificate ").append(i+1).append("\nValid from: ").append(dates.format(cert.getNotBefore())).append("\nValid until: ").append(dates.format(cert.getNotAfter()))
                           .append("\nValidity: ").append(valid?"Current":"Expired or not yet valid").append("\nRevocation: ").append(revoked==null?"Unknown — list unavailable":revoked.contains(cert.getSerialNumber().toString(16))?"Revoked or suspended":"Not listed").append('\n');
                    }out.append('\n');
                }
                return out.toString();
            },value->{draw();message("Keybox details",value);});
    }
    private static final class AppRow {int uid;String label;List<String> packages=new ArrayList<>();String title(){return label+(packages.size()>1?" · shared UID":"");}}
    private void targets(){
        try{JSONObject root=config();JSONObject p=selected(root);
            background(()->{
                Map<Integer,AppRow> grouped=new TreeMap<>();
                for(android.content.pm.ApplicationInfo info:getPackageManager().getInstalledApplications(0)){
                    if(info.uid<10000||info.uid>19999)continue;
                    AppRow row=grouped.get(info.uid);if(row==null){row=new AppRow();row.uid=info.uid;row.label=getPackageManager().getApplicationLabel(info).toString();grouped.put(info.uid,row);}row.packages.add(info.packageName);
                }
                List<AppRow> rows=new ArrayList<>(grouped.values());for(AppRow row:rows)Collections.sort(row.packages);
                Collections.sort(rows,(a,b)->a.label.compareToIgnoreCase(b.label));return rows;
            },rows->{draw();
                Set<String> chosen=new HashSet<>();JSONArray apps=p.getJSONArray("apps");for(int i=0;i<apps.length();i++)chosen.add(apps.getString(i));
                LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setPadding(dp(16),dp(8),dp(16),0);
                Switch all=new Switch(this);all.setText("All apps (advanced)");all.setPadding(dp(8),dp(12),dp(8),dp(12));all.setChecked(p.getString("scope").equals("all"));layout.addView(all);
                TextView help=label("Select only apps whose attestation requests you want handled by TEESimulator. Shared-UID packages are selected together. All apps includes remaining main-user application UIDs; other profiles take priority.",14,true);help.setPadding(dp(8),dp(8),dp(8),dp(12));layout.addView(help);
                EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Search apps or packages");layout.addView(search);
                ListView list=new ListView(this);list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);layout.addView(list,new LinearLayout.LayoutParams(-1,dp(300)));
                List<AppRow> visible=new ArrayList<>();
                Runnable refresh=()->{visible.clear();String query=search.getText().toString().toLowerCase(Locale.ROOT);List<String> labels=new ArrayList<>();for(AppRow row:rows)if((row.label+" "+row.packages.toString()).toLowerCase(Locale.ROOT).contains(query)){visible.add(row);labels.add(row.title()+"\n"+String.join(", ",row.packages));}
                    list.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_list_item_multiple_choice,labels));for(int i=0;i<visible.size();i++){boolean picked=false;for(String pkg:visible.get(i).packages)if(chosen.contains(pkg))picked=true;list.setItemChecked(i,picked);}list.setEnabled(!all.isChecked());list.setAlpha(all.isChecked()?0.45f:1f);};
                list.setOnItemClickListener((parent,view,pos,id)->{AppRow row=visible.get(pos);if(list.isItemChecked(pos))chosen.addAll(row.packages);else chosen.removeAll(row.packages);});
                all.setOnCheckedChangeListener((v,on)->refresh.run());search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){refresh.run();}public void afterTextChanged(android.text.Editable s){}});refresh.run();
                AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Target apps").setView(layout).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
                dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{try{p.put("scope",all.isChecked()?"all":"selected");p.put("apps",new JSONArray(all.isChecked()?Collections.emptyList():new TreeSet<>(chosen)));save(root);dialog.dismiss();}catch(Exception e){help.setText(e instanceof JSONException||e instanceof IllegalArgumentException?e.getMessage():"Could not save target apps");}}));dialog.show();
            });
        }catch(Exception e){message("Target apps","Saved configuration cannot be read. Restore default configuration to recover.");}
    }
    private void profiles(){navigate("profiles");}
    private void drawProfiles()throws Exception{
        JSONObject root=config();JSONArray profiles=root.getJSONArray("profiles");
        group();text("Profiles",22);text("Each profile has its own keybox, target apps and attestation settings.",16);
        for(int i=0;i<profiles.length();i++){final int index=i;JSONObject p=profiles.getJSONObject(i);button(p.getString("id"),p.getString("keybox")+" · "+p.optString("mode","patch"),()->{navigate("profile",index);});}
        button("Add profile","Start with no target apps",()->edit("Profile name","","Letters, numbers, dash or underscore",name->{JSONObject next=config();next.getJSONArray("profiles").put(Profiles.profile(name));save(next);}));
    }
    private void manageBox(String name){
        new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"Keybox details","Rename","Delete"},(dialog,which)->{
            if(which==0){try{JSONObject root=config();
                inspectSavedXml(root.getJSONObject("keyboxes").getString(name));}catch(Exception e){message("Keybox details","Could not read saved keybox");}}
            else if(which==1)edit("Rename keybox",name,"Unique name",value->{JSONObject root=config();JSONObject boxes=root.getJSONObject("keyboxes");if(value.equals(name))return;if(boxes.has(value))throw new IllegalArgumentException("Name already exists");String xml=boxes.getString(name);boxes.remove(name);boxes.put(value,xml);JSONArray profiles=root.getJSONArray("profiles");for(int i=0;i<profiles.length();i++){JSONObject p=profiles.getJSONObject(i);if(p.getString("keybox").equals(name))p.put("keybox",value);}save(root);});
            else new AlertDialog.Builder(this).setTitle("Delete saved keybox?").setMessage("Profiles still using this keybox must be changed first. Keep its original XML file to preserve access to existing keys.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{try{JSONObject root=config();JSONArray profiles=root.getJSONArray("profiles");for(int i=0;i<profiles.length();i++)if(profiles.getJSONObject(i).getString("keybox").equals(name))throw new IllegalArgumentException("This keybox is still used by a profile");root.getJSONObject("keyboxes").remove(name);save(root);}catch(Exception e){message("Keybox retained",e instanceof IllegalArgumentException?e.getMessage():"Could not delete keybox");}}).show();
        }).show();
    }
    private void inspectSavedXml(String xml){showDetailsXml(xml);}
    private void drawProfile()throws Exception{
        JSONObject root=config();JSONObject p=selected(root);
        group();text(p.getString("id"),22);
        button("Profile name",p.getString("id"),()->edit("Profile name",p.optString("id"),"Unique name",name->{selected(root).put("id",name);save(root);}));
        button("Keybox",p.getString("keybox").equals("current")?currentName():p.getString("keybox"),()->{List<String> names=new ArrayList<>();names.add("current");for(Iterator<String> it=root.optJSONObject("keyboxes").keys();it.hasNext();)names.add(it.next());List<String> labels=new ArrayList<>(names);labels.set(0,"Current · "+currentName());new AlertDialog.Builder(this).setTitle("Select keybox").setItems(labels.toArray(new String[0]),(d,n)->{try{p.put("keybox",names.get(n));changed(root);}catch(Exception e){message("Keybox","Could not select keybox");}}).show();});
        button("Keybox details","Certificates, signatures and revocation",this::details);
        button("Target apps",p.getString("scope").equals("all")?"All apps (advanced)":p.getJSONArray("apps").length()+" selected packages",this::targets);
        button("Operation mode",p.optString("mode","patch"),()->new AlertDialog.Builder(this).setTitle("Operation mode").setItems(new String[]{"Patch · keep hardware keys","Generation · create software keys"},(d,n)->new AlertDialog.Builder(this).setTitle("Change operation mode?").setMessage("This changes how new keys are created and attested. Existing keys are not deleted. App compatibility may change.").setNegativeButton("Cancel",null).setPositiveButton("Apply",(confirm,w)->{try{p.put("mode",n==0?"patch":"generation");changed(root);}catch(Exception e){message("Profile","Could not change operation mode");}}).show()).show());
        group();text("Patch & OS levels",22);
        dropdown("OS version",p.optString("osVersion","system_property"),new String[]{"system_property"},value->{p.put("osVersion",value);save(root);});
        JSONObject levels=p.optJSONObject("patchLevel");if(levels==null){levels=new JSONObject();p.put("patchLevel",levels);}final JSONObject patch=levels;
        for(String field:new String[]{"system","vendor","boot"})dropdown(friendly(field)+" patch",patch.optString(field,"system_property"),
            field.equals("system")?new String[]{"system_property","today","no"}:new String[]{"system_property","YYYY-MM-05","today","no"},value->{patch.put(field,value);save(root);});
        note("system_property uses the ROM build value. today uses the current date; YYYY-MM-05 uses the fifth of the current month. no sets the reported patch value to zero. These settings do not install security updates. Device identity uses captured defaults.");
        group();button("Delete profile","Keep at least one profile",()->new AlertDialog.Builder(this).setTitle("Delete profile?").setMessage("New attestation requests will use the remaining routing rules. Keep the keybox needed by existing simulator keys.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{try{JSONObject next=config();next.getJSONArray("profiles").remove(profileIndex);save(next);back();}catch(Exception e){message("Profile retained","Keep at least one profile.");}}).show());
    }
    private void additional(Uri uri){
        background(()->{String xml;try(InputStream in=getContentResolver().openInputStream(uri)){xml=new String(Keybox.read(in,Keybox.MAX_BYTES),StandardCharsets.UTF_8).replaceFirst("^\\uFEFF","");}boolean aosp=xml.equals(asset());Keybox parsed=Keybox.parse(xml,aosp);if(parsed.expired&&!aosp)throw new IllegalArgumentException("Expired certificate");return new String[]{importFilename(uri),xml};},result->{
            draw();new AlertDialog.Builder(this).setTitle("Save "+result[0]+"?").setMessage("Certificate signatures and private-key matching passed. Check revocation in Keybox details before assigning it to apps. The filename is retained as the keybox name.").setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
                try{JSONObject root=config();if(root.getJSONObject("keyboxes").has(result[0]))throw new IllegalArgumentException("A keybox with this filename already exists. Rename the saved keybox or the file before importing.");root.getJSONObject("keyboxes").put(result[0],result[1]);save(root);}catch(Exception e){message("Keybox not saved",e instanceof IllegalArgumentException?e.getMessage():"Could not save additional keybox");}
            }).show();});
    }
    private void restore(){new AlertDialog.Builder(this).setTitle("Restore default routing?").setMessage("Use the current imported keybox in patch mode for Key Attestation, Google Wallet, Google Play services and Play Store. Other apps stay on real hardware. Your previous profiles are backed up for restoration. Additional keyboxes stay saved.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->{try{String raw=Settings.Secure.getString(getContentResolver(),Profiles.SETTING);JSONObject root=Profiles.defaults();
                try{JSONObject old=Profiles.load(raw);root.put("keyboxes",old.getJSONObject("keyboxes"));}catch(JSONException invalid){/* Keep the raw configuration in the private backup for recovery. */}
                if(raw!=null&&!Settings.Secure.putString(getContentResolver(),"unica_keybox_profiles_backup",raw))throw new IOException("Could not back up profiles");save(root);page="main";profileIndex=0;backPages.clear();backProfiles.clear();backScrolls.clear();scrollView=null;draw();}catch(Exception e){message("Restore defaults","Could not restore configuration");}}).show();}
    private void restoreBackup(){try{String raw=Settings.Secure.getString(getContentResolver(),"unica_keybox_profiles_backup");if(raw==null){message("Restore previous profiles","No previous profile configuration saved.");return;}JSONObject root=Profiles.load(raw);new AlertDialog.Builder(this).setTitle("Restore previous profiles?").setMessage("Reapply the configuration saved before restoring defaults.").setNegativeButton("Cancel",null).setPositiveButton("Restore",(d,w)->changed(root)).show();}catch(Exception e){message("Restore previous profiles","Saved backup is unavailable or invalid.");}}
    private JSONObject systemData=new JSONObject(),keyData=new JSONObject();
    private String backendError="",keyQuery="";
    private boolean spoofedKeysOnly=true;
    private int keyOffset;
    private final Set<String> selectedKeys=new HashSet<>();
    private void navigate(String next){navigate(next,profileIndex);}
    private void navigate(String next,int index){
        if(!page.equals(next)){backPages.add(page);backProfiles.add(profileIndex);backScrolls.add(scrollView==null?0:scrollView.getScrollY());scrollView=null;}
        page=next;profileIndex=index;draw();
    }
    private void backendQuery(JSONObject request,Done<JSONObject> done){
        background(()->{try{return Backend.call(request);}catch(Exception e){return new JSONObject().put("ok",false).put("error",e instanceof IllegalStateException?e.getMessage():"Backend unavailable. Enable spoofing and check System status.");}},result->{
            if(!result.optBoolean("ok")){backendError=result.optString("error");draw();return;}
            backendError="";done.run(result);
        });
    }
    private void loadSystem(){navigate("system");try{backendQuery(new JSONObject().put("action","system"),result->{systemData=result;draw();});}catch(Exception e){message("System","Could not read backend state");}}
    private void loadKeys(){navigate("keys");try{backendQuery(new JSONObject().put("action","keys").put("query",keyQuery).put("spoofedOnly",spoofedKeysOnly).put("offset",keyOffset),result->{keyData=result;draw();});}catch(Exception e){message("Stored keys","Could not read keys");}}
    private String friendly(String name){String s=name.replaceAll("([a-z])([A-Z])","$1 $2").replace('_',' ');return s.isEmpty()?s:s.substring(0,1).toUpperCase(Locale.ROOT)+s.substring(1);}
    private String describe(JSONObject value){
        StringBuilder text=new StringBuilder();for(Iterator<String> keys=value.keys();keys.hasNext();){String key=keys.next();Object item=value.opt(key);
            text.append(friendly(key)).append(": ");
            if(item instanceof JSONObject)text.append('\n').append(describe((JSONObject)item));else text.append(item==JSONObject.NULL?"Not available":String.valueOf(item));
            text.append("\n\n");}return text.toString();
    }
    private void drawSystem(){
        group();text("System",22);backendView=text(statusDisplay(),16);
        button("Refresh system status","Read the backend, hook and captured state",this::loadSystem);
        if(!backendError.isEmpty())text(backendError,16);
        if(!enabled())note("TEESimulator is stopped. Enable spoof hardware attestation to inspect its runtime state.");
        group();text("Interceptor",22);text("Hook: "+systemData.optString("hook","Not available")+"\nAndroid API: "+systemData.optInt("api",0),16);
        JSONObject harvest=systemData.optJSONObject("harvest");
        group();text("Harvest & device state",22);
        if(harvest==null)text("No runtime harvest available",16);else{
            button("Captured hardware state","Values obtained from the hardware and their effective overrides",()->message("Harvest & device state",describe(harvest)));
            button("TrustedEnvironment / StrongBox","Inspect security level and attestation versions",()->{
                StringBuilder s=new StringBuilder();for(String key:new String[]{"harvestFailed","attestationSecurityLevel","keymasterSecurityLevel","attestationVersion","keymasterVersion","strongBoxAvailable","strongBoxAttestationVersion"})s.append(friendly(key)).append(": ").append(harvest.opt(key)).append('\n');
                message("Security levels",s.toString());});
        }
        if(harvest!=null){JSONObject overrides=harvest.optJSONObject("overrides");if(overrides!=null){
            for(String field:new String[]{"attestationSecurityLevel","keymasterSecurityLevel","attestationVersion","keymasterVersion"}){
                JSONObject entry=overrides.optJSONObject(field);if(entry==null||!entry.optBoolean("editable"))continue;
                button(friendly(field),entry.optString("value")+" · "+entry.optString("source"),()->edit(friendly(field),entry.optString("value"),"Empty restores the captured default",value->{
                    if(!value.isEmpty()){int n=Integer.parseInt(value);boolean level=field.endsWith("SecurityLevel");if((level&&(n<0||n>2))||(!level&&(n<1||n>100000)))throw new IllegalArgumentException("Value out of range");}
                    String raw=Settings.Secure.getString(getContentResolver(),"unica_teesim_overrides");JSONObject next=raw==null?new JSONObject():new JSONObject(raw);
                    if(value.isEmpty())next.remove(field);else next.put(field,value);
                    if(!Settings.Secure.putString(getContentResolver(),"unica_teesim_overrides",next.toString()))throw new IOException("Could not save override");
                    Toast.makeText(this,"Saved · refresh after the backend applies the change",Toast.LENGTH_SHORT).show();
                }));
            }
        }}
        note("The frozen boot key and hash protect existing simulator keys and cannot be edited here. Per-profile OS and patch fields are in Profiles. Device identity uses captured defaults.");
    }
    private void drawKeys()throws Exception{
        group();text("Stored keys",22);text("Only keys belonging to configured target apps are listed. Deleting a key can permanently remove access to app data protected by it.",16);
        if(!backendError.isEmpty())text(backendError,16);
        button("Refresh keys","Read current target-app key metadata",this::loadKeys);
        button("Search",keyQuery.isEmpty()?"Alias, app, keybox or class":keyQuery,()->edit("Search keys",keyQuery,"Alias, app, keybox or class",value->{keyQuery=value;keyOffset=0;loadKeys();}));
        button("Key scope",spoofedKeysOnly?"Spoofed keys":"All target-app keys",()->{spoofedKeysOnly=!spoofedKeysOnly;keyOffset=0;selectedKeys.clear();loadKeys();});
        JSONArray keys=keyData.optJSONArray("keys");int total=keyData.optInt("total",0);
        text(total+" keys · "+selectedKeys.size()+" selected",16);
        if(keys!=null&&keys.length()>0){
            button("Select displayed keys","Add the current page to the selection",()->{for(int i=0;i<keys.length();i++)selectedKeys.add(keys.optJSONObject(i).optString("id"));draw();});
            button("Clear selection","Keep all keys",()->{selectedKeys.clear();draw();});
        }
        if(!selectedKeys.isEmpty())button("Delete selected keys",selectedKeys.size()+" keys · requires confirmation",this::deleteSelectedKeys);
        group();text("Target-app keys",22);
        if(keys==null||keys.length()==0)text("No keys to display. Enable the backend and refresh; use All target-app keys to include untouched keys.",16);
        else for(int i=0;i<keys.length();i++){
            JSONObject key=keys.getJSONObject(i);String id=key.getString("id");String alias=key.optString("alias","Unnamed key");
            button((selectedKeys.contains(id)?"✓ ":"")+alias,key.optString("package")+" · "+key.optString("class","unknown")+" · "+key.optString("keyAlgorithm",""),()->new AlertDialog.Builder(this).setTitle(alias).setItems(new String[]{"Inspect key metadata",selectedKeys.contains(id)?"Unselect":"Select"},(d,n)->{if(n==0)message("Key metadata",describe(key));else{if(!selectedKeys.remove(id))selectedKeys.add(id);draw();}}).show());
        }
        group();
        if(keyOffset>0)button("Previous page","Previous 100 keys",()->{keyOffset=Math.max(0,keyOffset-100);loadKeys();});
        if(keyOffset+100<total)button("Next page","Next 100 keys",()->{keyOffset+=100;loadKeys();});
    }
    private void deleteSelectedKeys(){
        if(selectedKeys.size()>100){message("Delete keys","Select at most 100 keys per operation.");return;}
        new AlertDialog.Builder(this).setTitle("Permanently delete selected keys?").setMessage("This cannot be undone. Apps may lose access to encrypted data, saved credentials or registrations. Only the selected keys in target-app namespaces will be requested for deletion; the backend rechecks ownership. No direct database edits are made.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{try{backendQuery(new JSONObject().put("action","deleteKeys").put("confirmed",true).put("ids",new JSONArray(selectedKeys)),r->{selectedKeys.clear();loadKeys();Toast.makeText(this,r.optInt("deleted")+" of "+r.optInt("requested")+" keys deleted",Toast.LENGTH_LONG).show();});}catch(Exception e){message("Delete keys","Could not request deletion");}}).show();
    }
    private void drawKeyboxes()throws Exception{
        group();text("Current keybox",22);text(loaded()?currentName():"No keybox imported",16);
        text(status,16);button("Inspect current keybox","Algorithms, certificates, signatures, root authority and revocation",()->{String xml=current();if(xml==null)message("Keybox","No keybox imported");else showDetailsXml(xml);});
        button("Check revocation status","Fetch Google's certificate status list",this::checkStatus);
        group();text("Import keybox",22);
        button("Import from file","Validate XML before replacing the current keybox",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),41));
        button("Import additional keybox","Save a named keybox without replacing the current one",()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),43));
        button("AOSP test keybox","Public software test material",()->new AlertDialog.Builder(this).setTitle("Review AOSP test keybox?").setMessage("This public software keybox does not provide Google hardware attestation or guarantee Play Integrity. Test certificates may be expired.").setNegativeButton("Cancel",null).setPositiveButton("Review",(d,w)->stage(()->asset(),true,"aosp-keybox.xml")).show());
        button("Community repository","Browse and download a keybox for validation",this::browse);
        JSONObject root=config();group();text("Saved keyboxes",22);JSONObject boxes=root.getJSONObject("keyboxes");
        for(Iterator<String> names=boxes.keys();names.hasNext();){String name=names.next();button(name,"Inspect, rename or delete",()->manageBox(name));}
        button("Save current keybox","Keep a named copy for profiles",()->edit("Keybox name",loaded()?currentName():"keybox.xml","Unique filename or name",name->{JSONObject next=config();if(!loaded())throw new IllegalArgumentException("Import a keybox first");if(next.getJSONObject("keyboxes").has(name))throw new IllegalArgumentException("Name already exists");next.getJSONObject("keyboxes").put(name,current());save(next);}));
        group();button("Clear imported keybox","Remove the current XML; named keyboxes remain",()->new AlertDialog.Builder(this).setTitle("Clear current keybox?").setMessage("Existing simulator keys may need the same signer after a restart. Keep the original XML file. Profiles using the current keybox will have no signer.").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{if(Settings.Secure.putString(getContentResolver(),SETTING,null)){Settings.Secure.putString(getContentResolver(),KEYBOX_METADATA,null);status="No keybox imported";draw();}else message("Keybox","Could not clear current keybox");}).show());
        note("Keep original XML files. Renaming a saved keybox updates its profile references. A saved keybox cannot be deleted while a profile uses it.");
    }

    private void browse(){
        if(busy)return;
        // WebView is prohibited in Settings' system UID, including named processes.
        // Only this normal-UID browser returns a URL; XML validation and writes stay here.
        try{startActivityForResult(new Intent().setClassName("io.mesalabs.unica.keybox.browser","io.mesalabs.unica.keybox.browser.RepositoryActivity"),42);}
        catch(ActivityNotFoundException|SecurityException e){
            // A copied APK may not be registered by PackageManager. Keep browsing usable.
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://keybox.kowx712.cc/")).addCategory(Intent.CATEGORY_BROWSABLE));Toast.makeText(this,"Download a keybox from the repository, then import it with Local.",Toast.LENGTH_LONG).show();}
            catch(ActivityNotFoundException|SecurityException unavailable){failure("No web browser is available to open https://keybox.kowx712.cc/");}
        }
    }
}
