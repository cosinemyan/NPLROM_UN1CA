/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.keybox.browser;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Normal application UID, INTERNET only. Never receives or writes keybox material. */
public final class RepositoryActivity extends Activity {
    private static final String ORIGIN="https://unica-keybox.invalid/";
    private WebView browser;
    private WebMessagePort channel;
    private boolean returned;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);setResult(RESULT_CANCELED);
        if(!"com.android.settings".equals(getCallingPackage())||android.os.Process.myUid()%100000<10000){finish();return;}
        setTitle("Keybox repository");if(getActionBar()!=null)getActionBar().setDisplayHomeAsUpEnabled(true);
        try{open();}catch(RuntimeException|IOException e){closeBrowser();TextView error=new TextView(this);error.setPadding(32,32,32,32);error.setText("Repository browser unavailable. Check that Android System WebView is enabled, then try again.");setContentView(error);}
    }
    @Override public boolean onOptionsItemSelected(MenuItem item){if(item.getItemId()==android.R.id.home){finish();return true;}return super.onOptionsItemSelected(item);}
    @Override protected void onDestroy(){closeBrowser();super.onDestroy();}
    private void closeBrowser(){if(channel!=null){channel.close();channel=null;}if(browser!=null){browser.stopLoading();browser.destroy();browser=null;}}
    private void open()throws IOException{
        browser=new WebView(this);WebView view=browser;
        view.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);
        view.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView web,WebResourceRequest request){return request.isForMainFrame()||!"https".equals(request.getUrl().getScheme());}
            @Override public void onPageFinished(WebView web,String url){
                if(!ORIGIN.equals(url)||channel!=null)return;
                WebMessagePort[] ports=web.createWebMessageChannel();channel=ports[0];
                channel.setWebMessageCallback(new WebMessagePort.WebMessageCallback(){@Override public void onMessage(WebMessagePort port,WebMessage message){
                    if(returned)return;
                    try{JSONObject selection=new JSONObject(message.getData());
                        if("error".equals(selection.optString("type"))){Toast.makeText(RepositoryActivity.this,"The repository could not download that keybox",Toast.LENGTH_LONG).show();return;}
                        if(!"download".equals(selection.optString("type")))return;
                        String address=selection.getString("url");URI uri=new URI(address);
                        if(address.length()>4096||!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)return;
                        returned=true;setResult(RESULT_OK,new Intent().putExtra("url",address));finish();
                    }catch(Exception e){Toast.makeText(RepositoryActivity.this,"Repository selection unavailable",Toast.LENGTH_SHORT).show();}
                }});
                web.postWebMessage(new WebMessage("connect",new WebMessagePort[]{ports[1]}),Uri.parse(ORIGIN));
            }
        });
        String html;
        try(InputStream input=getAssets().open("repository.html");ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1){if(output.size()+count>65536)throw new IOException("Oversized wrapper");output.write(buffer,0,count);}
            html=new String(output.toByteArray(),StandardCharsets.UTF_8);
        }
        setContentView(view);view.loadDataWithBaseURL(ORIGIN,html,"text/html","UTF-8",null);
    }
}
