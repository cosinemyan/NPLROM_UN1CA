/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.keybox;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.view.View;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Local assets only. No root, remote pages, arbitrary commands or file-URL access. */
public final class RomSettingsActivity extends Activity {
    private static final String ORIGIN="https://teesim.local";
    private WebView web;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private ValueCallback<Uri[]> picker;
    private String exportText;
    private volatile Uri importedDocument;
    private volatile boolean destroyed;
    private RomStore store;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(android.os.Process.myUid()/100000!=0){
            new AlertDialog.Builder(this).setMessage("TEE profiles are managed by the device owner.").setPositiveButton("Close",(d,w)->finish()).setOnDismissListener(d->finish()).show();return;
        }
        store=new RomStore(this);
        web=new WebView(this);setContentView(web);
        web.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(false);
        s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportMultipleWindows(false);s.setJavaScriptCanOpenWindowsAutomatically(false);
        web.addJavascriptInterface(new Bridge(),"RomBridge");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){
                Uri u=request.getUrl();String path=u.getPath();
                if("GET".equals(request.getMethod())&&"content".equals(u.getScheme())&&u.equals(importedDocument)){
                    try(InputStream input=getContentResolver().openInputStream(u)){
                        return new WebResourceResponse("application/xml","UTF-8",new ByteArrayInputStream(Keybox.read(input,Keybox.MAX_BYTES)));
                    }catch(Exception e){return blocked();}
                }
                if(!"GET".equals(request.getMethod())||!"https".equals(u.getScheme())||!"teesim.local".equals(u.getHost())||u.getPort()!=-1||path==null||path.contains("..")||path.contains("\\"))return blocked();
                if(path.equals("/"))path="/index.html";
                try{
                    String mime=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":path.endsWith(".svg")?"image/svg+xml":path.endsWith(".html")?"text/html":"application/octet-stream";
                    java.util.Map<String,String> headers=new java.util.HashMap<>();
                    headers.put("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'");
                    headers.put("X-Content-Type-Options","nosniff");
                    return new WebResourceResponse(mime,"UTF-8",200,"OK",headers,getAssets().open("webui"+path));
                }catch(IOException e){return blocked();}
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(picker!=null)picker.onReceiveValue(null);picker=callback;
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                try{startActivityForResult(i,41);}catch(Exception e){picker.onReceiveValue(null);picker=null;}return true;
            }
        });
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,()->web.evaluateJavascript("window.romBack ? window.romBack() : false",r->{if(!"true".equals(r))finish();}));
        web.loadUrl(ORIGIN+"/index.html");
    }
    private static WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
    private final class Bridge {
        @JavascriptInterface public void request(String id,String action,String raw){
            if(destroyed||id==null||!id.matches("[0-9]{1,10}")||raw==null||raw.length()>512*1024)return;
            try{worker.execute(()->{
                JSONObject response;
                try{response=store.call(action,new JSONObject(raw));}
                catch(Exception e){response=new JSONObject();try{response.put("ok",false).put("error",e instanceof IllegalArgumentException?e.getMessage():"Operation unavailable; the ROM backend may be stopped or out of date.");}catch(Exception ignored){}}
                final String encoded=JSONObject.quote(response.toString());
                runOnUiThread(()->{if(!destroyed)web.evaluateJavascript("window.romReply("+JSONObject.quote(id)+","+encoded+")",null);});
            });}catch(RejectedExecutionException ignored){}
        }
        @JavascriptInterface public void saveLogs(String text){
            if(text==null||text.length()>512*1024)return;
            runOnUiThread(()->{if(destroyed)return;exportText=text;
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"TEE-Simulator-events.txt");
                try{startActivityForResult(i,42);}catch(Exception e){exportText=null;}
            });
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==41&&picker!=null){importedDocument=result==RESULT_OK&&data!=null?data.getData():null;picker.onReceiveValue(importedDocument!=null?new Uri[]{importedDocument}:null);picker=null;}
        if(request==42){String text=exportText;exportText=null;if(result==RESULT_OK&&data!=null&&data.getData()!=null&&text!=null){Uri uri=data.getData();worker.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out!=null)out.write(text.getBytes(StandardCharsets.UTF_8));}catch(IOException ignored){}});}}
    }
    @Override protected void onDestroy(){destroyed=true;if(picker!=null)picker.onReceiveValue(null);worker.shutdownNow();if(web!=null){web.removeJavascriptInterface("RomBridge");web.destroy();}super.onDestroy();}
}
