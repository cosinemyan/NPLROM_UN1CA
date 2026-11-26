/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.troubleshooting;
import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.Process;
import java.util.*;
import java.util.concurrent.*;

/** User-reviewed diagnostics. AI output is plain text and never becomes an action request. */
public final class TroubleshootingActivity extends Activity {
    // Match the UN1CA Settings toolbar, typography and inset cards.
    private TextView styleText(String value,int size,boolean detail) { return label(value,size,detail); }
    private LinearLayout settingsRoot(String title) {
        if(getActionBar()!=null)getActionBar().hide();
        getWindow().setDecorFitsSystemWindows(false);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPaddingRelative(dp(8),dp(4),dp(24),dp(4));bar.setMinimumHeight(dp(56));
        TextView back=styleText(getResources().getConfiguration().getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?"›":"‹",36,false);
        back.setGravity(Gravity.CENTER);back.setContentDescription("Navigate up");back.setFocusable(true);
        android.util.TypedValue ripple=new android.util.TypedValue();
        if(getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,ripple,true)&&ripple.resourceId!=0)back.setBackgroundResource(ripple.resourceId);
        back.setOnClickListener(v->onBackPressed());bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView toolbarTitle=styleText(title,22,false);toolbarTitle.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        toolbarTitle.setAccessibilityHeading(true);
        bar.addView(toolbarTitle,new LinearLayout.LayoutParams(0,-2,1));root.addView(bar,new LinearLayout.LayoutParams(-1,-2));
        return root;
    }

    private final android.window.OnBackInvokedCallback back=()->finish();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<View> cards=new ArrayList<>();
    private LinearLayout content,page;private ScrollView rootScroll;
    private TextView scanStatusView,logStatusView,setupStatusView;private ProgressBar scanProgress,logProgress,setupProgress;
    private String reviewSource="";private boolean setupReview;
    private String setupStatus="Run a read-only check of PIF and TEE settings.",setupReport="";
    private String scanStatus="Tap Scan installed modules to begin.",logStatus="Collect or import a log to find crashes and app failures.",activeSection="scan";
    private JSONObject lsposedState=new JSONObject();
    private final ArrayList<String> analysisParts=new ArrayList<>(),analysisTitles=new ArrayList<>();
    private final Set<Integer> expandedAnalysis=new HashSet<>();private JSONArray modules=new JSONArray(),apps=new JSONArray();
    private String status="Scan installed modules to check for overlaps.",warning="",raw="",source="",analysis="";
    private boolean busy,scanned;private volatile boolean cancelled;private LogTools.Selection selection;
    private final ArrayList<ActionRow> controls=new ArrayList<>();
    private boolean owner(){return android.os.Process.myUid()==1000;}
    @Override public void onCreate(Bundle state){super.onCreate(state);setTitle("Troubleshooting");if(getActionBar()!=null)getActionBar().setDisplayHomeAsUpEnabled(true);
        if(!owner()){new AlertDialog.Builder(this).setMessage("Troubleshooting is available to the device owner only.").setPositiveButton("Close",(d,w)->finish()).setOnCancelListener(d->finish()).show();return;}
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,back);
        scanned=getPreferences(0).getBoolean("scanned",false);try{modules=new JSONArray(getPreferences(0).getString("modules","[]"));apps=new JSONArray(getPreferences(0).getString("apps","[]"));}catch(JSONException ignored){}
        if(getPreferences(0).getInt("scan_schema",0)!=5){modules=new JSONArray();apps=new JSONArray();scanned=false;}
        try{lsposedState=new JSONObject(getPreferences(0).getString("lsposed_state","{}"));}catch(JSONException ignored){}
        draw();
    }
    @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);}
    @Override protected void onDestroy(){cancelled=true;getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);worker.shutdownNow();super.onDestroy();}
    @Override protected void onResume(){super.onResume();if(content!=null)background();}
    private void openChat(){if(busy)return;PrivateLm.open(this);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void ui(Runnable work){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())work.run();});}
    private boolean dark(){return (getResources().getConfiguration().uiMode&0x30)==0x20;}
    private int themed(int attr,int fallback){android.util.TypedValue value=new android.util.TypedValue();if(!getTheme().resolveAttribute(attr,value,true))return fallback;return value.resourceId!=0?getColor(value.resourceId):value.data;}
    private int color(String name,int fallback){int id=getResources().getIdentifier(name,"color",getPackageName());return id==0?fallback:getColor(id);}
    private TextView label(String value,int size,boolean secondary){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(themed(secondary?android.R.attr.textColorSecondary:android.R.attr.textColorPrimary,secondary?(dark()?0xffbfc1c7:0xff62656b):(dark()?0xfff4f4f6:0xff17181b)));return t;}
    private void card(String title){page=new LinearLayout(this);page.setOrientation(1);page.setClipToOutline(true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(20);content.addView(page,lp);cards.add(page);TextView heading=text(title,20);heading.setTypeface(Typeface.create("sans-serif-medium",0));heading.setAccessibilityHeading(true);}
    private TextView text(String value,int size){TextView t=label(value,size,size<20);t.setPaddingRelative(dp(24),dp(10),dp(24),dp(14));t.setTextIsSelectable(true);page.addView(t);return t;}
    private CharSequence highlightDuplicate(String summary){
        String prefix="Same purpose as UN1CA’s ";int start=summary.indexOf(prefix);
        if(start<0)return summary;
        int end=summary.indexOf('\n',start);if(end<0)end=summary.length();
        android.text.SpannableString styled=new android.text.SpannableString(summary);
        styled.setSpan(new android.text.style.ForegroundColorSpan(dark()?0xffff8a80:0xffb3261e),start,end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styled;
    }
    private final class ActionRow extends LinearLayout {
        ActionRow(String title,String summary,Runnable action){super(TroubleshootingActivity.this);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setMinimumHeight(dp(80));setPaddingRelative(dp(24),dp(18),dp(20),dp(18));
            LinearLayout labels=new LinearLayout(TroubleshootingActivity.this);labels.setOrientation(VERTICAL);TextView name=label(title,20,false);name.setTypeface(Typeface.create("sans-serif-medium",0));labels.addView(name);
            if(!summary.isEmpty()){TextView detail=label(summary,15,true);detail.setText(highlightDuplicate(summary));detail.setPadding(0,dp(5),0,0);labels.addView(detail);}
            addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=label(getResources().getConfiguration().getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?"‹":"›",26,true);arrow.setPaddingRelative(dp(16),0,0,0);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);addView(arrow);
            labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);setContentDescription(title+(summary.isEmpty()?"":". "+summary));setFocusable(true);
            setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(themed(android.R.attr.colorControlHighlight,0x18000000)),null,new ColorDrawable(android.graphics.Color.WHITE)));setOnClickListener(v->action.run());
            setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());}});
        }
        @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);setAlpha(enabled?1f:0.45f);}
    }
    private ActionRow button(String value,Runnable action){return button(value,"",action);}
    private ActionRow button(String value,String summary,Runnable action){View divider=new View(this);divider.setBackgroundColor(themed(android.R.attr.textColorSecondary,0xff62656b));divider.setAlpha(0.14f);LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,dp(1));line.setMarginStart(dp(24));line.setMarginEnd(dp(24));page.addView(divider,line);ActionRow b=new ActionRow(value,summary,action);b.setEnabled(!busy);page.addView(b);controls.add(b);return b;}
    private void info(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show();}
    private void background(){boolean transparent=android.provider.Settings.System.getInt(getContentResolver(),"unica_settings_bg_transparency_enabled",0)!=0;
        int level=Math.max(70,Math.min(100,android.provider.Settings.System.getInt(getContentResolver(),"unica_settings_bg_transparency_level",70)));
        if(transparent)getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setFormat(transparent?android.graphics.PixelFormat.TRANSLUCENT:android.graphics.PixelFormat.OPAQUE);
        getWindow().setBackgroundDrawable(new ColorDrawable(transparent?0:color("sesl_round_and_bgcolor_"+(dark()?"dark":"light"),dark()?0xff000000:0xfff2f2f2)));
        for(View v:cards){GradientDrawable shape=new GradientDrawable();int surface=color("sesl_background_color_"+(dark()?"dark":"light"),dark()?0xff17171a:0xffffffff);if(transparent)surface=(surface&0xffffff)|((255-level*255/100)<<24);shape.setColor(surface);shape.setCornerRadius(dp(28));v.setBackground(shape);}
    }
    private boolean hasConfirmedApps(){for(int i=0;i<apps.length();i++)if(apps.optJSONObject(i).optBoolean("confirmed"))return true;return false;}
    private boolean duplicate(JSONObject m){JSONArray items=m.optJSONArray("duplicates");return items!=null&&items.length()>0;}
    private boolean finding(JSONObject m){return duplicate(m)||!m.optString("evidence").isEmpty();}
    private void draw(){
        if(getPreferences(0).getBoolean("restart",false)&&getPreferences(0).getInt("restart_boot",-1)!=android.provider.Settings.Global.getInt(getContentResolver(),"boot_count",-1))getPreferences(0).edit().putBoolean("restart",false).apply();
        final int scrollY=rootScroll==null?0:rootScroll.getScrollY();
        controls.clear();cards.clear();
        if(rootScroll==null){LinearLayout root=settingsRoot("Troubleshooting");rootScroll=new ScrollView(this);rootScroll.setFillViewport(true);rootScroll.setClipToPadding(false);content=new LinearLayout(this);content.setOrientation(1);content.setPadding(dp(12),dp(12),dp(12),dp(24));rootScroll.addView(content);root.addView(rootScroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);}
        else content.removeAllViews();
        ScrollView scroll=rootScroll;

        int suspects=0;for(int i=0;i<modules.length();i++){JSONObject m=modules.optJSONObject(i);if(!m.optBoolean("disabled")&&!m.optBoolean("removed")&&finding(m))suspects++;}
        for(int i=0;i<apps.length();i++)if(apps.optJSONObject(i).optBoolean("confirmed")||apps.optJSONObject(i).optBoolean("duplicate"))suspects++;
        card("Status");TextView heading=text(suspects>0?"Review recommended":!warning.isEmpty()?"Scan incomplete":scanned?"No known conflict detected":"Not scanned",22);heading.setTypeface(null,Typeface.BOLD);if(suspects>0)heading.setTextColor(dark()?0xffffcc80:0xff895100);
        if(declinedActive()){TextView unresolved=text("⚠ PROBLEM DETECTED — review pending",18);unresolved.setTypeface(null,Typeface.BOLD);unresolved.setTextColor(0xffd32f2f);text("You kept a flagged module enabled. Review the recommendation below.",14);}
        text("Last completed scan: "+getPreferences(0).getString("last_scan","Never"),13);
        if(!warning.isEmpty())text("Coverage: "+warning,14);
        button("About this check","What the scan can detect",()->info("About this check","Checks known duplicate-purpose root modules, selected modified files, known LSPosed duplicate purposes, and verified LSPosed conflicts. Duplicate purpose does not prove a crash or a failed integrity check. Built-in files must be present, but this does not prove that feature is enabled or working. Dynamic hooks and scripts may be missed. LSPosed checks require an exact package and matching enabled scope. Duplicate-purpose rules also require the built-in framework class. They do not establish runtime hook execution. Confirmed conflicts require additional verified evidence."));
        button("Scan installed modules","Check root modules and enabled LSPosed conflicts",this::scan);
        scanProgress=progress("scan");scanStatusView=text(scanStatus,15);
        if(getPreferences(0).getBoolean("restart",false)){text("Restart required. A loaded module can remain active until restart.",15);button("Restart now",this::restart);button("Restart later",()->{setStatus("Restart postponed. Changes are pending.");draw();});}
        card("Root modules");
        for(int i=0;i<modules.length();i++){
            final JSONObject m=modules.optJSONObject(i);String state=m.optBoolean("removed")?"Pending removal":m.optBoolean("disabled")?"Disabled":"Enabled · restart state unverified";
            String summary=state+(m.optString("stage").equals("modules_update")?" · Pending update":"")+"\n"+m.optString("version");
            if(duplicate(m))summary+="\nSame purpose as UN1CA’s "+relatedMods(m);
            else if(finding(m))summary+="\nChanges relate to UN1CA’s "+relatedMods(m);
            final String detail=summary;
            if(m.optBoolean("owned"))button(m.optString("name",m.optString("id")),summary+"\nTap to restore",()->confirmModule("restore",m));
            else if(!m.optBoolean("disabled")&&!m.optBoolean("removed")&&finding(m))button(m.optString("name",m.optString("id")),summary+"\nReview disabling this module",()->confirmModule("disable",m));
            else button(m.optString("name",m.optString("id")),summary,()->info(m.optString("name"),detail+"\n\nModule ID: "+m.optString("id")+"\nNo automatic changes were made."));
        }
        if(modules.length()==0)text(scanned?"No modules listed. Check scan coverage above if root access was unavailable.":"Scan to view installed modules.",15);
        card("LSPosed conflicts");
        if(!scanned)text("Run a scan to check LSPosed conflicts.",14);
        else if(!lsposedState.optBoolean("available"))text("LSPosed module state could not be verified. No conflict claim is made. "+lsposedState.optString("warning"),14);
        else{
            int enabled=0;JSONArray stateRows=lsposedState.optJSONArray("modules");if(stateRows!=null)for(int i=0;i<stateRows.length();i++)if(stateRows.optJSONObject(i).optBoolean("enabled"))enabled++;
            text("Checked "+enabled+" modules enabled in LSPosed configuration. Disabled modules are excluded.",13);
            if(lsposedState.optInt("confirmed_rule_count",0)==0)text("LSPosed configuration was read. This ROM currently has no verified LSPosed conflict cases to check; compatibility remains unverified.",14);
            else if(!hasConfirmedApps())text("No confirmed LSPosed conflicts found. This does not establish compatibility with every module.",14);
        }
        for(int i=0;i<apps.length();i++){JSONObject app=apps.optJSONObject(i);if(!app.optBoolean("confirmed"))continue;
            text(app.optString("name"),17).setTypeface(null,Typeface.BOLD);
            text("Conflicts with UN1CA mod: "+app.optString("unica_mod"),14).setTextColor(dark()?0xffff8a80:0xffb3261e);
            button("Review in LSPosed Manager",()->reviewLsposed(app));
        }
        card("LSPosed duplicate features");
        if(!scanned)text("Run a scan to check for duplicate features.",15);
        else if(!lsposedState.optBoolean("available"))text("LSPosed enabled state and scope could not be read. Duplicate features could not be checked.",15);
        else {
            boolean found=false;
            for(int i=0;i<apps.length();i++){final JSONObject app=apps.optJSONObject(i);if(!app.optBoolean("duplicate"))continue;found=true;
                button(app.optString("name"),"Same purpose as UN1CA’s "+app.optString("unica_mod")+"\nReview in LSPosed Manager",()->reviewLsposed(app));
            }
            if(!found)text("No known duplicate features found among enabled modules with matching scopes. Unlisted modules remain unchecked.",15);
        }
        if(apps.length()>0)button("Restart after changing LSPosed scope",this::restart);
        card("PIF and TEE setup");
        button("Check PIF and TEE setup","Validate configuration and check backend readiness",this::checkSetup);
        setupProgress=progress("setup");setupStatusView=text(setupStatus,15);
        if(!setupReport.isEmpty()){
            button("View setup results","Configuration, runtime evidence and verification steps",()->info("PIF and TEE setup",setupReport));
            button("Explain setup results with AI","Review the report before copying it into PrivateLM",this::explainSetup);
        }
        card("Ask Ai");button("Ask Ai","Chat with PrivateLM AI, paste or attach the log, generate image, so on",this::openChat);
        card("Analyze logs");text("Find system crashes, app crashes and apps that fail to open. Review selected details, then copy them into PrivateLM.",15);
        button("Collect recent device log","Read recent logs from this device",this::collect);button("Import log file","Choose a text log to review",this::importLog);
        logProgress=progress("logs");logStatusView=text(logStatus,15);
        if(!raw.isEmpty()){text("Source: "+source+" · "+raw.length()+" characters. Original retained in this screen until it closes.",13);button("Save original log",this::saveOriginal);button("Review crash details for PrivateLM",this::prepareAnalysis);}
        if(!analysis.isEmpty()){text("AI findings — verify before acting",17).setTypeface(null,Typeface.BOLD);text(analysis,14);
            for(int i=0;i<analysisParts.size();i++){
                final int index=i;final TextView details=text(analysisParts.get(i),15);details.setMaxLines(expandedAnalysis.contains(i)?Integer.MAX_VALUE:5);
                ActionRow toggle=button((expandedAnalysis.contains(i)?"▾ ":"▸ ")+analysisTitles.get(i),()->{boolean show=details.getMaxLines()==5;details.setMaxLines(show?Integer.MAX_VALUE:5);if(show)expandedAnalysis.add(index);else expandedAnalysis.remove(index);});
                page.removeView(details);page.addView(details);
            }}
        card("History and restoration");button("Action history","Review previous module actions",()->info("Action history",getPreferences(0).getString("history","No actions recorded.")));button("Restoration help","Undo changes made by Troubleshooting",()->info("Restoration help","Restore removes only unchanged disable markers created by this menu. For recovery restoration, remove only the selected module’s disable file whose contents start with ‘UN1CA troubleshooting v1’. Module files and app data are preserved."));
        background();rootScroll.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){rootScroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);rootScroll.scrollTo(0,scrollY);}});
    }
    private ProgressBar progress(String section){ProgressBar bar=new ProgressBar(this);bar.setIndeterminate(true);bar.setVisibility(busy&&activeSection.equals(section)?View.VISIBLE:View.GONE);page.addView(bar,new LinearLayout.LayoutParams(dp(32),dp(32)));return bar;}
    private void setStatus(String message){status=message;if(activeSection.equals("setup")){setupStatus=message;if(setupStatusView!=null)setupStatusView.setText(message);}else if(activeSection.equals("logs")){logStatus=message;if(logStatusView!=null)logStatusView.setText(message);}else{scanStatus=message;if(scanStatusView!=null)scanStatusView.setText(message);}}
    private void busyViews(){if(setupProgress!=null)setupProgress.setVisibility(busy&&activeSection.equals("setup")?View.VISIBLE:View.GONE);for(ActionRow control:controls)control.setEnabled(!busy);if(scanProgress!=null)scanProgress.setVisibility(busy&&activeSection.equals("scan")?View.VISIBLE:View.GONE);if(logProgress!=null)logProgress.setVisibility(busy&&activeSection.equals("logs")?View.VISIBLE:View.GONE);}
    private String relatedMods(JSONObject module){
        String evidence=module.optString("evidence");LinkedHashSet<String> names=new LinkedHashSet<>();JSONArray duplicates=module.optJSONArray("duplicates");if(duplicates!=null)for(int i=0;i<duplicates.length();i++)names.add(duplicates.optJSONObject(i).optString("feature"));
        if(evidence.contains("HMA"))names.add("HMA");if(evidence.contains("PIF"))names.add("PIF");if(evidence.contains("Boot property"))names.add("Prophide");if(evidence.contains("TEE"))names.add("TEE Simulator");if(evidence.contains("AI backend"))names.add("AI backend");if(evidence.contains("UN1CA Settings"))names.add("UN1CA Settings");
        return names.isEmpty()?"Check incomplete":android.text.TextUtils.join(", ",names);
    }
    private interface Work {void run()throws Exception;}
    private void run(String section,String message,Work task){
        if(busy)return;busy=true;activeSection=section;setStatus(message);busyViews();
        worker.execute(()->{try{task.run();}catch(Exception e){ui(()->{setStatus("Unable to complete: "+describe(e));history("Failed: "+describe(e));});}finally{ui(()->{busy=false;draw();});}});
    }
    private static String describe(Exception e){Throwable root=e;while(root.getCause()!=null)root=root.getCause();String m=root.getMessage();return m==null?root.getClass().getSimpleName():m;}
    private void scan(){run("scan","Scanning installed modules…",()->{
        JSONObject inventory;String failed="";
        try{inventory=ModuleTransport.request("scan",null);}catch(Exception e){inventory=new JSONObject().put("modules",new JSONArray());failed=describe(e);}
        JSONObject savedState=inventory.optJSONObject("lsposed");if(savedState==null)savedState=new JSONObject();JSONArray installed=InstalledModules.scan(this,savedState);JSONObject result=inventory,state=savedState;String issue=failed;
        ui(()->{modules=result.optJSONArray("modules");if(modules==null)modules=new JSONArray();apps=installed;lsposedState=state;warning=issue.isEmpty()?result.optString("warning"):issue;scanned=true;setStatus("Scan completed. Review evidence below.");
            getPreferences(0).edit().putBoolean("scanned",true).putInt("scan_schema",5).putString("lsposed_state",lsposedState.toString()).putString("modules",modules.toString()).putString("apps",apps.toString()).putString("last_scan",java.text.DateFormat.getDateTimeInstance().format(new Date())).apply();});
    });}
    private void history(String text){String old=getPreferences(0).getString("history","");String value=java.text.DateFormat.getDateTimeInstance().format(new Date())+" — "+text+"\n"+old;if(value.length()>16000)value=value.substring(0,16000);getPreferences(0).edit().putString("history",value).apply();}
    private Set<String> declined(){return new HashSet<>(getPreferences(0).getStringSet("declined_modules",Collections.emptySet()));}
    private boolean declinedActive(){
        Set<String> ids=declined();
        for(int i=0;i<modules.length();i++){JSONObject m=modules.optJSONObject(i);if(!m.optBoolean("disabled")&&!m.optBoolean("removed")&&finding(m)&&ids.contains("root:"+m.optString("stage")+"/"+m.optString("id")))return true;}
        for(int i=0;i<apps.length();i++){JSONObject app=apps.optJSONObject(i);if((app.optBoolean("confirmed")||app.optBoolean("duplicate"))&&ids.contains("lsposed:"+app.optString("package")))return true;}
        return false;
    }
    private void accepted(String id){Set<String> ids=declined();ids.remove(id);getPreferences(0).edit().putStringSet("declined_modules",ids).apply();}
    private void decline(String what){activeSection="scan";Set<String> ids=declined();ids.add(what);getPreferences(0).edit().putStringSet("declined_modules",ids).apply();history("Kept enabled: "+what);setStatus("Suggestion declined; finding remains for review.");draw();}
    private void confirmModule(String op,JSONObject m){boolean disable=op.equals("disable");new AlertDialog.Builder(this).setTitle(disable?"Disable this module?":"Restore this module?")
        .setMessage(m.optString("id")+" in "+m.optString("stage")+"\n\nRelated UN1CA mod: "+relatedMods(m)+".\n"+(disable?(duplicate(m)?"This module serves the same purpose as a built-in UN1CA feature.":"This module changes files related to a built-in UN1CA feature."):"Restore this module by removing this menu’s disable marker."))
        .setPositiveButton(disable?"Disable":"Restore",(d,w)->run("scan","Updating module…",()->{
            ModuleTransport.request(op,m);accepted("root:"+m.optString("stage")+"/"+m.optString("id"));getPreferences(0).edit().putBoolean("restart",true).putInt("restart_boot",android.provider.Settings.Global.getInt(getContentResolver(),"boot_count",-1)).apply();
            history(op+": "+m.optString("stage")+"/"+m.optString("id"));JSONObject fresh=ModuleTransport.request("scan",null);
            ui(()->{modules=fresh.optJSONArray("modules");warning=fresh.optString("warning");getPreferences(0).edit().putString("modules",modules.toString()).apply();setStatus("Module action verified. Restart required.");restartOffer();});
        })).setNegativeButton(disable?"Keep enabled":"Cancel",(d,w)->{if(disable)decline("root:"+m.optString("stage")+"/"+m.optString("id"));}).show();}
    private void restartOffer(){new AlertDialog.Builder(this).setTitle("Restart required").setMessage("Restart to apply the module change. Loaded hooks may still be active.").setPositiveButton("Restart now",(d,w)->restart()).setNegativeButton("Later",null).show();}
    private void restart(){new AlertDialog.Builder(this).setTitle("Restart device?").setMessage("Save your work before restarting.").setPositiveButton("Restart",(d,w)->{try{((PowerManager)getSystemService(POWER_SERVICE)).reboot(null);}catch(Exception e){setStatus(describe(e));draw();}}).setNegativeButton("Cancel",null).show();}
    private void reviewLsposed(JSONObject app){new AlertDialog.Builder(this).setTitle("Review LSPosed scope.")
        .setMessage(app.optBoolean("duplicate")?"This module serves the same purpose as a built-in UN1CA feature.":"This module conflicts with a built-in UN1CA feature.")
        .setPositiveButton("Open manager",(d,w)->{
            accepted("lsposed:"+app.optString("package"));history("Accepted LSPosed review: "+app.optString("package")+"; actual scope remains unverified");
            String[] candidates={"org.lsposed.manager"};boolean opened=false;
            for(String pkg:candidates){Intent intent=getPackageManager().getLaunchIntentForPackage(pkg);if(intent!=null){startActivity(intent);opened=true;break;}}
            if(!opened)new AlertDialog.Builder(this).setMessage("Open LSPosed Manager from its notification or shortcut, disable the module/scope, then restart. No compatible manager launch activity was found.").setPositiveButton("OK",null).show();
        }).setNegativeButton("Keep enabled",(d,w)->decline("lsposed:"+app.optString("package"))).show();}
    private void saveOriginal(){
        activeSection="logs";Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"unica-original-log.txt");
        try{startActivityForResult(intent,42);}catch(ActivityNotFoundException e){setStatus("No document picker available");draw();}
    }
    private void importLog(){activeSection="logs";Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(intent,41);}catch(ActivityNotFoundException e){setStatus("No document picker available");draw();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request==42&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();String original=raw;run("logs","Saving original log…",()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Cannot write selected document");out.write(original.getBytes("UTF-8"));}ui(()->{setStatus("Original log saved. It may contain sensitive information.");});});return;}
        if(request==41&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();run("logs","Reading selected log…",()->{String value;try(InputStream in=getContentResolver().openInputStream(uri)){value=LogTools.read(in,LogTools.MAX_IMPORT);}if(value.indexOf('\0')>=0)throw new IOException("Select a plain text log; extract compressed or binary files first");ui(()->{raw=value;source="Imported file (device provenance unverified)";analysis="";analysisParts.clear();analysisTitles.clear();expandedAnalysis.clear();setStatus("File imported. Review crash details before analysis.");});});}}
    private void collect(){run("logs","Collecting recent logcat…",()->{
        File folder=new File(getCacheDir(),"troubleshooting");if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot prepare log storage");
        File file=File.createTempFile("logcat-",".txt",folder);Process process=new ProcessBuilder("/system/bin/logcat","-b","all","-d","-t","20000","-v","threadtime").redirectErrorStream(true).redirectOutput(file).start();
        try{if(!process.waitFor(8,TimeUnit.SECONDS))throw new IOException("Log collection timed out");if(process.exitValue()!=0)throw new IOException("Logcat access unavailable");String value;try(InputStream in=new FileInputStream(file)){value=LogTools.read(in,LogTools.MAX_IMPORT);}
            File crash=File.createTempFile("crash-",".txt",folder);
            Process crashProcess=new ProcessBuilder("/system/bin/logcat","-b","crash","-d","-t","6000","-v","threadtime").redirectErrorStream(true).redirectOutput(crash).start();
            try{
                if(!crashProcess.waitFor(8,TimeUnit.SECONDS))throw new IOException("Crash buffer collection timed out");
                if(crashProcess.exitValue()!=0)throw new IOException("Crash buffer access unavailable");
                String crashText;try(InputStream in=new FileInputStream(crash)){crashText=LogTools.read(in,LogTools.MAX_IMPORT);}
                value=value+"\n=== Dedicated crash buffer (may duplicate recent logs) ===\n"+crashText;
            }finally{crashProcess.destroyForcibly();crash.delete();}
            final String captured=value;ui(()->{raw=captured;source="This device — recent logcat and dedicated crash buffer";analysis="";analysisParts.clear();analysisTitles.clear();expandedAnalysis.clear();setStatus("Recent log collected; older or persistent crash evidence may be absent.");});}
        finally{process.destroyForcibly();file.delete();}
    });}
    private void checkSetup(){run("setup","Checking PIF settings, TEE profiles and runtime evidence…",()->{
        String report=SetupChecks.collect(this);ui(()->{setupReport=report;setStatus("Check completed. Review configuration and runtime results below.");});
    });}
    private void explainSetup(){run("setup","Preparing setup report for review…",()->{
        LogTools.Selection selected=LogTools.reviewed(LogTools.redact(setupReport),new LogTools.Selection());selected.totalLines=setupReport.split("\n").length;selected.selectedLines=selected.totalLines;for(int i=0;i<selected.chunks.size();i++)selected.titles.add("PIF and TEE setup results");
        ui(()->{selection=selected;reviewSource="This device — PIF and TEE setup report";setupReview=true;setStatus("Report ready. Review the selected sections before analysis.");preview();});
    });}
    private void prepareAnalysis(){run("logs","Finding crashes and app failures…",()->{
        LogTools.Selection selected=LogTools.select(LogTools.redact(raw));
        if(selected.chunks.isEmpty()){ui(()->{setStatus("No system crash, app crash, app-start failure or app-not-responding report found. Nothing was sent for analysis.");});return;}
        ui(()->{selection=selected;reviewSource=source;setupReview=false;setStatus("Found "+selected.chunks.size()+" crash log sections. Review and select what to analyze.");preview();});
    });}
    private void preview(){
        String destination="PrivateLM — choose the model and local or cloud mode in the app.\n"+reviewSource+"\n"+selection.coverage();
        LinearLayout list=new LinearLayout(this);list.setOrientation(1);list.setPadding(dp(12),0,dp(12),0);
        ArrayList<CheckBox> selected=new ArrayList<>();ArrayList<EditText> editors=new ArrayList<>();
        for(int i=0;i<selection.chunks.size();i++){
            final int index=i;CheckBox check=new CheckBox(this);check.setText("Include log section "+(i+1));check.setChecked(true);list.addView(check);selected.add(check);
            String name=i<selection.titles.size()?selection.titles.get(i):"Crash details";
            EditText editor=new EditText(this);editor.setText(selection.chunks.get(i));editor.setTextSize(13);editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);editor.setMaxLines(5);editors.add(editor);
            ActionRow expand=new ActionRow(name+" — section "+(index+1),setupReview?"Tap to expand or collapse setup details":"Tap to expand or collapse crash details",()->{boolean show=editor.getMaxLines()==5;editor.setMaxLines(show?Integer.MAX_VALUE:5);});list.addView(expand);list.addView(editor);
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(list);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(setupReview?"Review setup report for PrivateLM":"Review crash details for PrivateLM").setView(scroll)
            .setMessage(destination+"\n\nExpand a section to review or edit it. Uncheck sections you do not want analyzed. Redaction may miss sensitive information. Copy and open places the selected text on your clipboard; paste it into PrivateLM yourself. Nothing is submitted for inference here. If you select a cloud provider in PrivateLM, sending the message shares it with that provider.")
            .setPositiveButton("Copy and open PrivateLM",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            LogTools.Selection reviewed=new LogTools.Selection();reviewed.totalLines=selection.totalLines;reviewed.selectedLines=0;reviewed.truncated=selection.truncated;
            for(int i=0;i<selected.size();i++)if(selected.get(i).isChecked()){
                LogTools.Selection part=LogTools.reviewed(editors.get(i).getText().toString(),selection);
                for(String chunk:part.chunks){if(reviewed.chunks.size()==LogTools.MAX_CHUNKS){reviewed.truncated=true;break;}reviewed.chunks.add(chunk);reviewed.selectedLines+=chunk.split("\n").length;reviewed.titles.add(i<selection.titles.size()?selection.titles.get(i):"Crash details");}
            }
            if(reviewed.chunks.isEmpty()){Toast.makeText(this,"Select at least one non-empty log section",Toast.LENGTH_SHORT).show();return;}
            if(PrivateLm.copyAndOpen(this,reviewSource,reviewed))dialog.dismiss();
        }));dialog.show();
    }
}
