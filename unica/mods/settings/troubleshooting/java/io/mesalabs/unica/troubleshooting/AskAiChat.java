/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.troubleshooting;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** One UI grouped chat page. Requests always pass through an explicit payload review. */
final class AskAiChat implements AutoCloseable {
    private final Activity host;private final ChatHistory history;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<View> surfaces=new ArrayList<>(),controls=new ArrayList<>();
    private final ArrayList<AlertDialog> dialogs=new ArrayList<>();
    private LinearLayout layout,messages;private ScrollView scroll;private EditText question,log;
    private TextView status,backend,coverage;private ProgressBar progress;private Button stop;
    private CheckBox includeLog;
    private AiClient client;private Bundle config;private boolean busy,closed;private volatile int generation;
    private Uri pendingFile;
    private final Runnable leave;
    AskAiChat(Activity host,ChatHistory history,Runnable leave){this.host=host;this.history=history;this.leave=leave;build();refresh();}
    private int dp(int n){return Math.round(n*host.getResources().getDisplayMetrics().density);}
    private boolean dark(){return (host.getResources().getConfiguration().uiMode&0x30)==0x20;}
    private int attr(int id,int fallback){android.util.TypedValue v=new android.util.TypedValue();if(!host.getTheme().resolveAttribute(id,v,true))return fallback;return v.resourceId==0?v.data:host.getColor(v.resourceId);}
    private int color(String name,int fallback){int id=host.getResources().getIdentifier(name,"color",host.getPackageName());return id==0?fallback:host.getColor(id);}
    private TextView text(String value,int size,boolean secondary){TextView t=new TextView(host);t.setText(value);t.setTextSize(size);t.setTextColor(attr(secondary?android.R.attr.textColorSecondary:android.R.attr.textColorPrimary,secondary?(dark()?0xffc4c6cc:0xff62656b):(dark()?0xfff4f4f6:0xff17181b)));t.setPadding(0,dp(6),0,dp(6));t.setTextIsSelectable(true);return t;}
    private LinearLayout card(LinearLayout parent,String title){
        LinearLayout box=new LinearLayout(host);box.setOrientation(1);box.setPaddingRelative(dp(24),dp(16),dp(24),dp(16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(24);parent.addView(box,p);surfaces.add(box);
        if(!title.isEmpty()){TextView heading=text(title,22,false);heading.setTypeface(Typeface.create("sans-serif-medium",0));box.addView(heading);}return box;
    }
    private Button button(LinearLayout parent,String title,Runnable action){
        Button b=new Button(host);b.setText(title);b.setTextSize(16);b.setAllCaps(false);b.setMinHeight(dp(64));b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setPaddingRelative(dp(16),dp(16),dp(16),dp(16));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(attr(android.R.attr.colorControlHighlight,0x18000000)),null,new ColorDrawable(0xffffffff)));
        b.setOnClickListener(v->action.run());LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,-2);row.topMargin=dp(8);row.bottomMargin=dp(8);parent.addView(b,row);controls.add(b);return b;
    }
    private EditText editor(String hint,int minLines,int maxLines){EditText e=new EditText(host);e.setHint(hint);e.setTextSize(16);e.setGravity(Gravity.TOP|Gravity.START);e.setMinLines(minLines);e.setMaxLines(maxLines);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);e.setPaddingRelative(dp(8),dp(12),dp(8),dp(12));controls.add(e);return e;}
    private void build(){
        host.setTitle("Ask aI");host.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        layout=new LinearLayout(host);layout.setOrientation(1);
        layout.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        scroll=new ScrollView(host);scroll.setFillViewport(true);LinearLayout body=new LinearLayout(host);body.setOrientation(1);body.setPadding(dp(16),dp(20),dp(16),dp(24));scroll.addView(body);layout.addView(scroll,new LinearLayout.LayoutParams(-1,-1));host.setContentView(layout);
        LinearLayout source=card(body,"Ask aI");backend=text("Checking your active AI backend…",15,true);source.addView(backend);
        source.addView(text("Ask about a problem, paste a log, or attach a text file. Up to 12 recent exchanges stay in memory until Troubleshooting closes. AI suggestions require verification.",15,true));
        button(source,"Refresh AI backend",this::refresh);
        messages=new LinearLayout(host);messages.setOrientation(1);body.addView(messages);renderHistory();
        LinearLayout evidence=card(body,"Log evidence");
        coverage=text("No log attached",14,true);evidence.addView(coverage);
        button(evidence,"Paste log",this::pasteLog);button(evidence,"Attach text / log file",this::pickFile);
        log=editor("Review or edit the selected log section",3,5);evidence.addView(log);
        button(evidence,"Expand / collapse log",()->{boolean expanded=log.getMaxLines()>5;log.setMaxLines(expanded?5:Integer.MAX_VALUE);log.setVerticalScrollBarEnabled(!expanded);});
        includeLog=new CheckBox(host);includeLog.setText("Include this log section in the next message");includeLog.setTextSize(16);includeLog.setMinHeight(dp(48));includeLog.setChecked(history.includeLog);includeLog.setOnCheckedChangeListener((b,checked)->history.includeLog=checked);evidence.addView(includeLog);controls.add(includeLog);
        button(evidence,"Previous section",()->section(-1));button(evidence,"Next section",()->section(1));
        button(evidence,"Remove log",()->{history.attachment="";history.attachmentName="";history.edits.clear();history.section=0;includeLog.setChecked(true);loadSection();});
        LinearLayout composer=card(body,"Your message");question=editor("Describe the problem or ask a follow-up question",3,8);question.setText(history.draft);composer.addView(question);
        progress=new ProgressBar(host);progress.setVisibility(View.GONE);composer.addView(progress,new LinearLayout.LayoutParams(dp(32),dp(32)));
        status=text("Review each request before sending. No device changes are made by the chat.",14,true);composer.addView(status);
        button(composer,"Review and send",this::review);
        stop=button(composer,"Cancel request",this::cancel);controls.remove(stop);stop.setVisibility(View.GONE);
        button(composer,"Clear conversation",()->show(new AlertDialog.Builder(host).setTitle("Clear conversation?").setMessage("Clear chat history, your draft and the attached log from this session.").setPositiveButton("Clear",(d,w)->{history.clear();question.setText("");includeLog.setChecked(true);loadSection();renderHistory();status.setText("Conversation cleared.");}).setNegativeButton("Cancel",null).create()));
        button(composer,"Back to Troubleshooting",leave);
        loadSection();background();
    }
    void background(){
        boolean transparent=android.provider.Settings.System.getInt(host.getContentResolver(),"unica_settings_bg_transparency_enabled",0)!=0;
        int level=Math.max(70,Math.min(100,android.provider.Settings.System.getInt(host.getContentResolver(),"unica_settings_bg_transparency_level",70)));
        if(transparent)host.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);else host.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        host.getWindow().setFormat(transparent?android.graphics.PixelFormat.TRANSLUCENT:android.graphics.PixelFormat.OPAQUE);
        host.getWindow().setBackgroundDrawable(new ColorDrawable(transparent?0:color("sesl_round_and_bgcolor_"+(dark()?"dark":"light"),dark()?0xff000000:0xfff2f2f2)));
        for(View surface:surfaces){GradientDrawable shape=new GradientDrawable();int c=color("sesl_background_color_"+(dark()?"dark":"light"),dark()?0xff17171a:0xffffffff);if(transparent)c=(c&0xffffff)|((255-level*255/100)<<24);shape.setColor(c);shape.setCornerRadius(dp(30));surface.setBackground(shape);}
    }
    private void renderHistory(){
        for(int i=surfaces.size()-1;i>=0;i--)if(surfaces.get(i).getParent()==messages)surfaces.remove(i);
        messages.removeAllViews();
        for(ChatHistory.Turn t:history.turns){LinearLayout turn=card(messages,"You");collapsible(turn,t.user,16);TextView name=text("Ask aI",18,false);name.setTypeface(Typeface.create("sans-serif-medium",0));turn.addView(name);collapsible(turn,t.answer,16);buttonCopy(turn,t.answer);}
        background();
    }
    private void collapsible(LinearLayout parent,String value,int size){
        TextView view=text(value,size,false);view.setMaxLines(5);parent.addView(view);
        if(value.length()>180||value.split("\\n",-1).length>5){
            Button toggle=button(parent,"Show more",()->{});controls.remove(toggle);
            toggle.setOnClickListener(v->{boolean expand=view.getMaxLines()==5;view.setMaxLines(expand?Integer.MAX_VALUE:5);toggle.setText(expand?"Show less":"Show more");});
        }
    }
    private void buttonCopy(LinearLayout parent,String answer){Button copy=new Button(host);copy.setAllCaps(false);copy.setText("Copy answer");copy.setMinHeight(dp(48));copy.setOnClickListener(v->{((ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Ask aI",answer));Toast.makeText(host,"Answer copied",Toast.LENGTH_SHORT).show();});parent.addView(copy);}
    private void show(AlertDialog dialog){dialogs.add(dialog);dialog.setOnDismissListener(d->dialogs.remove(dialog));dialog.show();}
    private void setBusy(boolean value){busy=value;for(View v:controls)v.setEnabled(!value);progress.setVisibility(value?View.VISIBLE:View.GONE);stop.setVisibility(value?View.VISIBLE:View.GONE);}
    private void ui(int ticket,Runnable task){host.runOnUiThread(()->{if(!closed&&!host.isFinishing()&&!host.isDestroyed()&&ticket==generation)task.run();});}
    private interface Task {void run(int ticket)throws Exception;}
    private void run(String message,Task task){
        if(busy||closed)return;setBusy(true);status.setText(message);final int ticket=++generation;
        worker.execute(()->{try{task.run(ticket);}catch(Exception e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();final String error=error(cause.getMessage());ui(ticket,()->status.setText(error));}finally{ui(ticket,()->{setBusy(false);if(pendingFile!=null){Uri next=pendingFile;pendingFile=null;file(next);}});}});
    }
    private static String error(String reason){
        if(reason!=null&&(reason.contains("context_limit")||reason.equals("gemma_prompt_size")))return "This request exceeds the local model's token budget. Shorten the log or question, clear older chat history, or select an online backend in AI Settings.";
        if("backend_changed".equals(reason))return "The AI backend changed. Refresh it and review the request again before sending.";
        return "Unable to complete: "+(reason==null?"AI unavailable":reason)+". Your draft and log remain available.";
    }
    private void refresh(){
        if(busy||closed)return;
        saveSection();if(client!=null)client.close();final AiClient candidate=new AiClient(host);client=candidate;config=null;backend.setText("Checking your active AI backend…");
        run("Checking active backend…",ticket->{
        Bundle current=candidate.config();
        if(current.getInt("chat_protocol")!=1)throw new IOException("Install the updated ROM AI backend to enable chat");
        if(!current.getBoolean("ready"))throw new IOException("Prepare the selected model or provider in AI Settings first");
        ui(ticket,()->{config=current;backend.setText(destination(current)+"\nRequest budget: "+current.getInt("chat_max_chars")+" characters including recent history; up to "+current.getInt("chat_output_tokens")+" output tokens."+(current.getBoolean("online")?" Conservative online budget; provider limits also apply.":" Local context: "+current.getInt("chat_context_tokens")+" tokens. Exact token checks may require a shorter section."));
            if(history.sectionChars!=chunk()){history.edits.clear();history.section=0;history.sectionChars=chunk();}loadSection();status.setText("Backend ready. Review your message and log before sending.");});
    });}
    private String destination(Bundle b){return (b.getBoolean("online")?"Online — ":"On device — ")+b.getString("model","")+(b.getBoolean("online")?"\n"+b.getString("endpoint",""):"");}
    private int chunk(){return config==null?history.sectionChars:config.getInt("chat_section_chars",800);}
    private void loadSection(){
        int count=ChatHistory.sectionCount(history.attachment,chunk());history.section=Math.max(0,Math.min(history.section,count-1));
        if(log!=null)log.setText(history.edits.containsKey(history.section)?history.edits.get(history.section):ChatHistory.section(history.attachment,history.section,chunk()));
        if(coverage!=null)coverage.setText(history.attachment.isEmpty()?"No log attached. You can also paste directly below.":history.attachmentName+" · "+history.attachment.length()+" characters\nSection "+(history.section+1)+" of "+count+". Only this section and the history shown in the send preview are submitted. Edits below apply to this request.");
    }
    private void saveSection(){if(log!=null)history.edits.put(history.section,log.getText().toString());}
    private void section(int direction){if(history.attachment.isEmpty())return;saveSection();history.section+=direction;includeLog.setChecked(true);loadSection();}
    private void attach(String value,String name){history.attachment=LogTools.redact(value);history.attachmentName=name;history.edits.clear();history.section=0;includeLog.setChecked(true);loadSection();status.setText("Log loaded and redacted. Review for sensitive information; redaction may miss it. Choose a section and ask a question.");}
    private void pasteLog(){
        ClipboardManager clipboard=(ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE);ClipData clip=clipboard.getPrimaryClip();
        if(clip==null||clip.getItemCount()==0){status.setText("Clipboard has no text.");return;}
        // Read text only; do not resolve clipboard URIs or HTML resources.
        CharSequence text=clip.getItemAt(0).getText();if(text==null){status.setText("Copy plain text before pasting a log.");return;}
        if(text.length()>256*1024){status.setText("Pasted log exceeds 256 KiB of characters. Use a smaller excerpt.");return;}
        attach(text.toString(),"Pasted log");
    }
    private void pickFile(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);try{host.startActivityForResult(intent,43);}catch(ActivityNotFoundException e){status.setText("No document picker available.");}}
    void file(Uri uri){if(busy){pendingFile=uri;return;}run("Reading text file…",ticket->{
        String value;try(InputStream in=host.getContentResolver().openInputStream(uri)){value=LogTools.read(in,1024*1024);}
        if(value.indexOf('\0')>=0||value.indexOf('\ufffd')>=0)throw new IOException("Select a UTF-8 text/log file; extract compressed or binary files first");
        String redacted=LogTools.redact(value);ui(ticket,()->{history.attachment=redacted;history.attachmentName="Imported text file (device provenance unverified)";history.edits.clear();history.section=0;includeLog.setChecked(true);loadSection();status.setText("File loaded. Review the selected section; other sections are not sent. Redaction can miss sensitive information.");});
    });}
    private void review(){
        if(config==null){status.setText("Refresh the AI backend first.");return;}
        try{
            history.draft=question.getText().toString();
            ChatHistory.Request request=history.prepare(LogTools.redact(history.draft),includeLog.isChecked()?LogTools.redact(log.getText().toString()):"",config.getInt("chat_max_chars"));
            Bundle selected=new Bundle(config);
            LinearLayout preview=new LinearLayout(host);preview.setOrientation(1);preview.setPadding(dp(24),dp(12),dp(24),dp(16));
            preview.addView(text(destination(selected)+"\n\n"+request.included+" previous exchanges included; "+request.omitted+" older exchanges omitted to fit the budget. Only the payload below is sent. Review for sensitive data.",15,true));
            collapsible(preview,request.preview,14);ScrollView pane=new ScrollView(host);pane.addView(preview);
            show(new AlertDialog.Builder(host).setTitle(selected.getBoolean("online")?"Send this conversation online?":"Review local AI request").setView(pane)
                .setPositiveButton(selected.getBoolean("online")?"Agree and send online":"Ask on device",(d,w)->send(request,selected)).setNegativeButton("Back to edit",null).create());
        }catch(IllegalArgumentException e){status.setText(e.getMessage());}
    }
    private void send(ChatHistory.Request request,Bundle selected){final AiClient target=client;run("Asking your selected AI backend…",ticket->{
        // Consent applies to the exact reviewed destination; the service/provider recheck it.
        if(target==null)throw new IOException("Refresh the backend before sending");
        String answer=target.chat(request.wire,selected);
        if(answer.length()>8192)throw new IOException("Answer exceeds display limit");
        ui(ticket,()->{history.add(request.question,request.log,answer);history.draft="";question.setText("");includeLog.setChecked(false);renderHistory();status.setText("Answer received. Ask a follow-up using the included history, or select another log section. The current section is not repeated unless you check Include. Verify suggestions before applying changes.");});
    });}
    private void cancel(){generation++;if(client!=null){client.close();client=null;}config=null;setBusy(false);backend.setText("Refresh the backend before sending again.");status.setText("Request cancelled. Your draft and log remain available.");}
    @Override public void close(){if(closed)return;history.draft=question.getText().toString();saveSection();closed=true;generation++;for(AlertDialog dialog:new ArrayList<>(dialogs))dialog.dismiss();if(client!=null)client.close();worker.shutdownNow();}
}
