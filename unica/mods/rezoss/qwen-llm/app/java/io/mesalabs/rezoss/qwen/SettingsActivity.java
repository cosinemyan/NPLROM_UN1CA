package io.mesalabs.rezoss.qwen;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.InputType;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.*;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Grouped, theme-aware settings launched from UN1CA Extra Settings. Keys stay in this app. */
public final class SettingsActivity extends Activity {
    private static final String[] IDS={"default","openai","gemini","openrouter","custom"};
    private final String[] LABELS={"ROM default (offline)","OpenAI API","Google Gemini API","OpenRouter API","Custom API"};
    private static final String[] URLS={"","https://api.openai.com/v1/chat/completions","https://generativelanguage.googleapis.com/v1beta/openai/chat/completions","https://openrouter.ai/api/v1/chat/completions",""};
    private final ExecutorService storage=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private LinearLayout page;
    private TextView active,backend,screening,memory,endpointRow,modelRow,keyState;
    private String endpoint,model;
    private boolean dark;
    private String localModelName(){
        try{
            android.content.pm.ApplicationInfo app=getPackageManager().getApplicationInfo("com.samsung.android.aioskernelservice",android.content.pm.PackageManager.GET_META_DATA);
            if(app.metaData!=null){String name=app.metaData.getString("unica.local_model_name");if(name!=null&&!name.isEmpty())return name;}
        }catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        return "Qwen3";
    }
    @Override public void onCreate(Bundle state){
        boolean night=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        setTheme(night?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        String localModel=localModelName();LABELS[0]="ROM default ("+localModel+")";
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(20),dp(24),dp(20),dp(32));scroll.addView(page);
        boolean transparent=Settings.System.getInt(getContentResolver(),"unica_settings_bg_transparency_enabled",0)==1;
        if(transparent)getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        scroll.setBackgroundColor(transparent?Color.TRANSPARENT:dark?0xff080808:0xfff2f2f2);
        TextView back=new TextView(this);back.setText("‹  Extra settings");back.setTextSize(16);back.setPadding(dp(8),dp(8),dp(8),dp(20));back.setTextColor(text());back.setOnClickListener(v->finish());page.addView(back);
        TextView title=new TextView(this);title.setText("AI backend settings");title.setTextSize(30);title.setTextColor(text());title.setPadding(dp(8),dp(12),dp(8),dp(28));page.addView(title);
        LinearLayout routing=card(transparent);
        active=row(routing,"",null);
        backend=row(routing,"Now Nudge, replies and summaries",()->choose(false));
        screening=row(routing,"Call Screening",()->choose(true));
        memory=row(routing,"Memory backend",()->chooseMemory());
        if(localModel.startsWith("Gemma")){
            row(routing,"Local model setup\nImport or prepare "+localModel,()->{
                try{startActivity(new android.content.Intent().setClassName("com.samsung.android.aioskernelservice","io.mesalabs.rezoss.nudge.GemmaSetupActivity"));}
                catch(android.content.ActivityNotFoundException e){error("Local model setup is unavailable. Check the AIOS installation.");}
            });
            note("ROM default runs "+localModel+" offline after importing the separate model file. First GPU preparation can take over ten seconds; prepare it before testing.");
        }else note("ROM default uses Qwen3 offline after installing the separate QWEN model APK.");
        note("Cloud providers receive conversation, notification or Memory text only after you explicitly select an API backend. Call Screening has a short response deadline.");
        LinearLayout config=card(transparent);
        row(config,"Configure API provider",this::provider);
        endpointRow=row(config,"",()->edit(false));modelRow=row(config,"",()->edit(true));
        keyState=row(config,"",this::saveKey);
        row(config,"Remove saved API key",()->new AlertDialog.Builder(this).setMessage("Remove the API key and return all backends to ROM default?").setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->storage.execute(()->{ApiSettingsStore.clearKey(this);main.post(this::refresh);})).show());
        note("Saving API configuration returns all backends to ROM default. Select a cloud backend afterward to enable transmission. Endpoint changes require a new key. The saved key is encrypted with Android Keystore and is never displayed.");
        setContentView(scroll);refresh();
    }
    private int text(){return dark?Color.WHITE:0xff111111;}
    private LinearLayout card(boolean transparent){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg=new GradientDrawable();int alpha=255;
        if(transparent){int level=Settings.System.getInt(getContentResolver(),"unica_settings_bg_transparency_level",70);alpha=255*(100-Math.max(70,Math.min(100,level)))/100;}
        bg.setColor((alpha<<24)|(dark?0x242424:0xffffff));bg.setCornerRadius(dp(28));c.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);page.addView(c,lp);return c;
    }
    private TextView row(LinearLayout card,String label,Runnable action){
        if(card.getChildCount()>0){android.view.View divider=new android.view.View(this);divider.setBackgroundColor(dark?0x33777777:0x22777777);LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,dp(1));line.setMargins(dp(22),0,dp(22),0);card.addView(divider,line);}
        TextView v=new TextView(this);setRow(v,label);v.setTextSize(17);v.setTextColor(text());v.setPadding(dp(22),dp(20),dp(22),dp(20));v.setMinHeight(dp(64));
        if(action!=null){v.setOnClickListener(w->action.run());v.setFocusable(true);android.util.TypedValue tv=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackground,tv,true);v.setForeground(getDrawable(tv.resourceId));}
        card.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private void note(String value){TextView v=new TextView(this);v.setText(value);v.setTextSize(14);v.setTextColor(text());v.setPadding(dp(16),dp(2),dp(16),dp(22));page.addView(v);}
    private void setRow(TextView view,String value){
        android.text.SpannableString styled=new android.text.SpannableString(value);int split=value.indexOf('\n');
        if(split>=0&&split+1<value.length()){
            styled.setSpan(new android.text.style.RelativeSizeSpan(0.85f),split+1,value.length(),0);
            styled.setSpan(new android.text.style.ForegroundColorSpan(dark?0xffcccccc:0xff555555),split+1,value.length(),0);
        }
        view.setText(styled);
    }
    private String label(String id){for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return LABELS[i];return "Unavailable backend";}
    private void refresh(){
        ApiSettingsStore.Settings s=ApiSettingsStore.display(this);endpoint=s.endpoint;model=s.model;
        String host;try{host=URI.create(endpoint).getHost();}catch(Exception e){host="Not configured";}
        setRow(active,"Selected providers\n"+label(ApiSettingsStore.backend(this,false))+"\nCall Screening: "+label(ApiSettingsStore.backend(this,true))+"\nMemory: "+label(ApiSettingsStore.memoryBackend(this)));
        setRow(backend,"Now Nudge, replies and summaries\n"+label(ApiSettingsStore.backend(this,false)));
        setRow(screening,"Call Screening\n"+label(ApiSettingsStore.backend(this,true)));
        setRow(memory,"Memory backend\n"+label(ApiSettingsStore.memoryBackend(this)));
        setRow(endpointRow,"API endpoint\n"+host+"\n"+endpoint);setRow(modelRow,"API model\n"+(model.isEmpty()?"Not configured":model));
        setRow(keyState,"Save API configuration / key\n"+(ApiSettingsStore.hasSavedKey(this)?"Key saved; never displayed":"No API key saved"));
    }
    private void chooseMemory(){
        new AlertDialog.Builder(this).setTitle("Memory backend").setItems(LABELS,(d,i)->{
            if(i==0){ApiSettingsStore.selectMemory(this,IDS[i]);refresh();return;}
            new AlertDialog.Builder(this).setTitle("Use "+LABELS[i]+" for Memory?").setMessage("Memory text, OCR and transcripts will be sent to "+endpoint+" using model "+model+". Network access and provider charges may apply.").setNegativeButton("Cancel",null).setPositiveButton("Use API",(a,b)->storage.execute(()->{try{ApiSettingsStore.selectMemory(this,IDS[i]);main.post(this::refresh);}catch(Exception e){error(e.getMessage());}})).show();
        }).show();
    }
    private void choose(boolean call){
        new AlertDialog.Builder(this).setTitle(call?"Call Screening backend":"AI backend").setItems(LABELS,(d,i)->{
            if(i==0){ApiSettingsStore.select(this,IDS[i],call);refresh();return;}
            new AlertDialog.Builder(this).setTitle("Use "+LABELS[i]+"?").setMessage("Relevant conversation text will be sent to "+endpoint+" using model "+model+". Network access and provider charges may apply.").setNegativeButton("Cancel",null).setPositiveButton("Use API",(a,b)->storage.execute(()->{try{ApiSettingsStore.select(this,IDS[i],call);main.post(this::refresh);}catch(Exception e){error(e.getMessage());}})).show();
        }).show();
    }
    private void provider(){new AlertDialog.Builder(this).setTitle("API provider preset").setItems(new String[]{"OpenAI","Google Gemini","OpenRouter","Custom"},(d,i)->{if(i<3)endpoint=URLS[i+1];else endpoint="";edit(false);}).show();}
    private void edit(boolean isModel){
        EditText field=new EditText(this);field.setSingleLine(true);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);field.setText(isModel?model:endpoint);
        new AlertDialog.Builder(this).setTitle(isModel?"Model ID":"HTTPS chat-completions endpoint").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Next",(d,w)->{if(isModel)model=field.getText().toString().trim();else endpoint=field.getText().toString().trim();saveKey();}).show();
    }
    private void saveKey(){
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(dp(20),dp(8),dp(20),0);
        EditText url=new EditText(this);url.setSingleLine(true);url.setText(endpoint);url.setHint("HTTPS endpoint");fields.addView(url);
        EditText name=new EditText(this);name.setSingleLine(true);name.setText(model);name.setHint("Model ID");fields.addView(name);
        EditText key=new EditText(this);key.setSingleLine(true);key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);key.setHint("API key (blank keeps key for same endpoint)");fields.addView(key);
        new AlertDialog.Builder(this).setTitle("Save API configuration").setView(fields).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            String u=url.getText().toString(),m=name.getText().toString(),k=key.getText().toString();key.setText("");storage.execute(()->{try{ApiSettingsStore.save(this,u,m,k);main.post(this::refresh);}catch(Exception e){error(e instanceof IllegalArgumentException?e.getMessage():"Could not save configuration.");}});
        }).show();
    }
    private void error(String s){main.post(()->{if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK",null).show();});}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onDestroy(){storage.shutdown();super.onDestroy();}
}
