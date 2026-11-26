package io.mesalabs.rezoss.nudge;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.concurrent.*;

/** Explicit local model import/warmup; never changes API provider or credentials. */
public final class GemmaSetupActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private Future<?> pending;
    private TextView status;
    private Button importButton,warmButton;
    private GemmaRuntime runtime;
    private final Runnable refresh=new Runnable(){public void run(){if(status!=null&&(pending==null||pending.isDone()))status.setText(runtime.status());main.postDelayed(this,3000);}};
    private boolean dark;
    @Override public void onCreate(Bundle state){
        dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);runtime=GemmaRuntime.get(this);
        boolean transparent=Settings.System.getInt(getContentResolver(),"unica_settings_bg_transparency_enabled",0)==1;
        if(transparent)getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setBackgroundColor(transparent?Color.TRANSPARENT:dark?0xff080808:0xfff2f2f2);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(24),dp(36),dp(24),dp(28));scroll.addView(page);
        TextView title=text("Gemma E2B test setup",30);title.setPadding(0,dp(12),0,dp(28));page.addView(title);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(20),dp(22),dp(20),dp(22));
        GradientDrawable shape=new GradientDrawable();shape.setCornerRadius(dp(28));shape.setColor(transparent?(dark?0xd0242630:0xd9ffffff):(dark?0xff202124:Color.WHITE));card.setBackground(shape);page.addView(card);
        status=text(runtime.status(),19);card.addView(status);
        importButton=new Button(this);importButton.setText("Import Gemma model");importButton.setAllCaps(false);importButton.setOnClickListener(v->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(pick,1);});card.addView(importButton);
        warmButton=new Button(this);warmButton.setText("Prepare engine for testing");warmButton.setAllCaps(false);warmButton.setOnClickListener(v->run(null));card.addView(warmButton);
        TextView note=text("Experimental "+GemmaBuild.BACKEND.toUpperCase()+" backend · AIOS 1.4.28\n\nImport gemma-4-E2B-it.litertlm from Download. Its size and SHA-256 are checked before use. CPU and GPU builds share this imported model.\n\nUse ROM default in AI backend settings to test Gemma. Existing cloud selections and API keys are preserved.\n\nPrepare the engine after switching builds. First GPU preparation may take over ten seconds. The engine releases model memory after a minute idle.",16);note.setPadding(dp(8),dp(24),dp(8),0);page.addView(note);setContentView(scroll);
    }
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(dark?0xffeeeeee:0xff202124);t.setPadding(0,0,0,dp(12));return t;}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==1&&result==RESULT_OK&&data!=null&&data.getData()!=null)run(data.getData());}
    private void run(Uri uri){
        if(pending!=null&&!pending.isDone())return;
        cancelled=false;importButton.setEnabled(false);warmButton.setEnabled(false);status.setText(uri==null?"Preparing engine…":"Importing and verifying model…");
        pending=worker.submit(()->{
            String message;long deadline=SystemClock.elapsedRealtime()+(uri==null?60000:180000);
            TextBackend.Control control=new TextBackend.Control(){public boolean cancelled(){return cancelled||Thread.currentThread().isInterrupted();}public long deadline(){return deadline;}public void process(java.lang.Process p){}};
            try{if(uri==null)runtime.prepare(deadline);else runtime.importModel(uri,control);message=runtime.status();}
            catch(Exception e){message="Preparation failed: "+e.getClass().getSimpleName()+"\n"+(e.getMessage()==null?"Try again":e.getMessage());}
            final String text=message;main.post(()->{if(!isDestroyed()&&!isFinishing()){status.setText(text);importButton.setEnabled(true);warmButton.setEnabled(true);}});
        });
    }
    @Override public void onResume(){super.onResume();main.removeCallbacks(refresh);main.post(refresh);}
    @Override public void onPause(){main.removeCallbacks(refresh);super.onPause();}
    @Override public void onDestroy(){cancelled=true;if(pending!=null)pending.cancel(true);worker.shutdownNow();super.onDestroy();}
}
