/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.movecertificate;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.concurrent.*;

public final class MoveCertificateActivity extends Activity {
    private static final File ROOT=new File("/data/misc/unica_movecert"), REQUEST=new File(ROOT,"request"), CERTS=new File(REQUEST,"certs"), ENABLED=new File(REQUEST,"enabled");
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout content,card; private boolean busy;
    private boolean dark(){return (getResources().getConfiguration().uiMode&0x30)==0x20;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private int color(String name,int fallback){int id=getResources().getIdentifier(name,"color",getPackageName());return id==0?fallback:getColor(id);}
    private String str(String key){return getString(getResources().getIdentifier("unica_movecert_"+key,"string",getPackageName()));}
    @Override public void onCreate(Bundle state){super.onCreate(state);if(android.os.Process.myUid()/100000!=0){Toast.makeText(this,str("owner_only"),Toast.LENGTH_LONG).show();finish();return;}setTitle(str("title"));if(getActionBar()!=null)getActionBar().setDisplayHomeAsUpEnabled(true);draw();}
    @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(dark()?0xfff4f4f6:0xff17181b);return v;}
    private void group(){
        card=new LinearLayout(this);card.setOrientation(1);card.setClipToOutline(true);
        boolean transparent=Settings.System.getInt(getContentResolver(),"unica_settings_bg_transparency_enabled",0)!=0;
        int level=Math.max(70,Math.min(100,Settings.System.getInt(getContentResolver(),"unica_settings_bg_transparency_level",70)));
        int surface=color("sesl_background_color_"+(dark()?"dark":"light"),dark()?0xff17171a:0xffffffff);
        GradientDrawable bg=new GradientDrawable();bg.setColor(transparent?(surface&0xffffff)|((255-level*255/100)<<24):surface);bg.setCornerRadius(dp(30));card.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(24);content.addView(card,p);
    }
    private void row(String title,String summary,Runnable action){
        if(card.getChildCount()>0){View line=new View(this);line.setBackgroundColor(dark()?0x22ffffff:0x18000000);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.setMargins(dp(24),0,dp(24),0);card.addView(line,p);}
        LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setPaddingRelative(dp(24),dp(20),dp(24),dp(20));
        box.addView(label(title,20));TextView detail=label(summary,15);detail.setTextColor(dark()?0xffbfc1c7:0xff62656b);detail.setPadding(0,dp(6),0,0);box.addView(detail);
        if(action!=null){box.setBackground(new RippleDrawable(ColorStateList.valueOf(dark()?0x33ffffff:0x18000000),null,new ColorDrawable(Color.WHITE)));box.setFocusable(true);box.setEnabled(!busy);box.setAlpha(busy?0.5f:1f);box.setOnClickListener(v->action.run());}
        card.addView(box,new LinearLayout.LayoutParams(-1,-2));
    }
    private boolean enabled(){try(FileInputStream in=new FileInputStream(ENABLED)){return in.read()=='1';}catch(IOException e){return false;}}
    private String status(){try(FileInputStream in=new FileInputStream(new File(ROOT,"status"));FileInputStream boot=new FileInputStream("/proc/sys/kernel/random/boot_id")){
        String result=new String(Certificates.read(in),StandardCharsets.UTF_8),id=new String(Certificates.read(boot),StandardCharsets.UTF_8).trim();
        int split=result.indexOf('\n');return split>0&&!id.isEmpty()&&id.equals(result.substring(0,split))?result.substring(split+1).trim():str("unavailable");
    }catch(Exception e){return str("unavailable");}}
    private void draw(){
        if(isDestroyed()||isFinishing())return;
        boolean transparent=Settings.System.getInt(getContentResolver(),"unica_settings_bg_transparency_enabled",0)!=0;
        if(transparent)getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setFormat(transparent?android.graphics.PixelFormat.TRANSLUCENT:android.graphics.PixelFormat.OPAQUE);
        getWindow().setBackgroundDrawable(new ColorDrawable(transparent?0:color("sesl_round_and_bgcolor_"+(dark()?"dark":"light"),dark()?Color.BLACK:0xfff2f2f2)));
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(1);content.setPadding(dp(16),dp(20),dp(16),dp(24));scroll.addView(content);setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        if(!ROOT.isDirectory()){group();row(str("unavailable"),str("unsupported"),null);return;}
        group();row(str("enabled"),str(enabled()?"on":"off"),()->{
            if(enabled())saveEnabled(false);else new AlertDialog.Builder(this).setTitle(str("enable_question")).setMessage(str("trust_warning")).setNegativeButton(android.R.string.cancel,null).setPositiveButton(str("enable"),(d,w)->saveEnabled(true)).show();
        });row(str("status"),status(),null);row(str("refresh"),str("refresh_summary"),this::draw);
        group();row(str("import"),str("import_summary"),()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(i,41);}catch(ActivityNotFoundException e){error(str("picker_missing"));}});
        File[] files=Certificates.files(CERTS);
        if(files.length==0)row(str("empty"),str("empty_summary"),null);
        for(File file:files){try(FileInputStream in=new FileInputStream(file)){X509Certificate c=Certificates.parse(Certificates.read(in));row(c.getSubjectX500Principal().getName(),file.getName(),()->details(file,c));}catch(Exception e){row(file.getName(),str("invalid"),()->remove(file));}}
        group();row(str("about"),str("about_summary"),()->startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://github.com/ys1231/MoveCertificate"))));
    }
    private void saveEnabled(boolean on){try{Certificates.atomic(ENABLED,(on?"1\n":"0\n").getBytes(StandardCharsets.US_ASCII));changed();}catch(Exception e){error(e.getMessage());}}
    private String describe(X509Certificate c)throws Exception{return c.getSubjectX500Principal().getName()+"\n\n"+str("issuer")+": "+c.getIssuerX500Principal().getName()+"\n\n"+c.getNotBefore()+" — "+c.getNotAfter()+"\n\nSHA-256\n"+Certificates.fingerprint(c);}
    private void details(File file,X509Certificate c){try{new AlertDialog.Builder(this).setTitle(str("details")).setMessage(describe(c)).setNegativeButton(android.R.string.cancel,null).setNeutralButton(str("remove"),(d,w)->remove(file)).show();}catch(Exception e){error(e.getMessage());}}
    private void remove(File file){new AlertDialog.Builder(this).setTitle(str("remove_question")).setMessage(str("reboot_summary")).setNegativeButton(android.R.string.cancel,null).setPositiveButton(str("remove"),(d,w)->{if(file.delete())changed();else error(str("remove_failed"));}).show();}
    private void changed(){draw();new AlertDialog.Builder(this).setTitle(str("saved")).setMessage(str("reboot_summary")).setPositiveButton(android.R.string.ok,null).show();}
    private void error(String message){new AlertDialog.Builder(this).setTitle(str("failed")).setMessage(message==null?str("unavailable"):message).setPositiveButton(android.R.string.ok,null).show();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=41||result!=RESULT_OK||data==null||data.getData()==null||busy)return;
        busy=true;draw();worker.submit(()->{try(InputStream in=getContentResolver().openInputStream(data.getData())){
            X509Certificate c=Certificates.parse(Certificates.read(in));c.checkValidity();String description=describe(c);
            runOnUiThread(()->{busy=false;if(isDestroyed()||isFinishing())return;draw();new AlertDialog.Builder(this).setTitle(str("import_question")).setMessage(description+"\n\n"+str("trust_warning")).setNegativeButton(android.R.string.cancel,null).setPositiveButton(str("import"),(d,w)->{try{Certificates.install(CERTS,c);changed();}catch(Exception e){error(e.getMessage());}}).show();});
        }catch(Exception e){runOnUiThread(()->{busy=false;if(!isDestroyed()){draw();error(e.getMessage());}});}});
    }
}
