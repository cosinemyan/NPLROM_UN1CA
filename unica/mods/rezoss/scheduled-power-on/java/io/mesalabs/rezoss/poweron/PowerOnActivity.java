// SPDX-License-Identifier: GPL-3.0-or-later
package io.mesalabs.rezoss.poweron;

import android.app.*;
import android.content.*;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PowerOnActivity extends Activity {
    // Match the UN1CA Settings toolbar, typography and inset cards.
    private TextView styleText(String value,int size,boolean detail) { return text(value,size,detail?secondary:primary); }
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

    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private AlertDialog timeDialog;
    private long draftOn,draftOff;
    private boolean separate,busy;
    private String message="";
    private int primary,secondary,surface,accent;
    private interface Work { void run() throws Exception; }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private long rounded(long seconds) { return (seconds+59)/60*60; }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        try { PowerScheduler.migrate(this); }
        catch (Exception error) { message=error.getMessage(); }
        ScheduleEngine.State state=PowerScheduler.state(this);
        long now=System.currentTimeMillis()/1000;
        draftOff=state.off>=now+ScheduleRules.MIN_LEAD?state.off:rounded(now+ScheduleRules.MIN_LEAD);
        draftOn=state.on>=now+ScheduleRules.MIN_LEAD?state.on:rounded(now+ScheduleRules.MIN_LEAD);
        separate=state.off>0;
        if(separate) draftOn=Math.max(draftOn,draftOff+ScheduleRules.MIN_GAP);
        if (saved!=null) {
            draftOn=saved.getLong("on",draftOn); draftOff=saved.getLong("off",draftOff);
            separate=saved.getBoolean("separate",separate);
        }
    }
    @Override protected void onResume() { super.onResume(); draw(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("on",draftOn); state.putLong("off",draftOff); state.putBoolean("separate",separate);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if(timeDialog!=null)timeDialog.dismiss();
        worker.shutdown();super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (busy) { Toast.makeText(this,"Saving your schedule…",Toast.LENGTH_SHORT).show(); return; }
        super.onBackPressed();
    }
    private int settingsColor(String name,int fallback) {
        try {
            Resources res=getPackageManager().getResourcesForApplication("com.android.settings");
            int id=res.getIdentifier(name,"color","com.android.settings");
            if(id!=0) return res.getColor(id,null);
        } catch (Exception ignored) {}
        return fallback;
    }
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable out=new GradientDrawable();out.setColor(color);out.setCornerRadius(dp(radius));return out;
    }
    private TextView text(String label,int size,int color) {
        TextView view=new TextView(this);view.setText(label);view.setTextSize(size);view.setTextColor(color);view.setTextDirection(View.TEXT_DIRECTION_LOCALE);return view;
    }
    private void colors() {
        boolean dark=(getResources().getConfiguration().uiMode&0x30)==0x20;
        primary=dark?0xfff4f4f6:0xff161619; secondary=dark?0xffc0c1c7:0xff62636a;
        android.content.res.TypedArray palette=obtainStyledAttributes(new int[]{android.R.attr.textColorPrimary,android.R.attr.textColorSecondary,android.R.attr.colorAccent});
        primary=palette.getColor(0,primary);secondary=palette.getColor(1,secondary);accent=palette.getColor(2,primary);palette.recycle();
        surface=settingsColor("sesl_background_color_"+(dark?"dark":"light"),dark?0xff202125:Color.WHITE);
        int background=settingsColor("sesl_round_and_bgcolor_"+(dark?"dark":"light"),dark?Color.BLACK:0xfff2f2f5);
        boolean transparent=Settings.System.getInt(getContentResolver(),"unica_settings_bg_transparency_enabled",0)!=0;
        if (transparent) {
            int level=Math.max(70,Math.min(100,Settings.System.getInt(getContentResolver(),"unica_settings_bg_transparency_level",70)));
            surface=(surface&0xffffff)|((255-level*255/100)<<24); background=Color.TRANSPARENT;
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        } else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        getWindow().setFormat(transparent?android.graphics.PixelFormat.TRANSLUCENT:android.graphics.PixelFormat.OPAQUE);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background));
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    private LinearLayout card() {
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(24),dp(6),dp(24),dp(6));card.setBackground(shape(surface,28));card.setClipToOutline(true);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(20);
        content.addView(card,params);return card;
    }
    private void divider(LinearLayout parent) {
        View line=new View(this);line.setBackgroundColor((secondary&0xffffff)|0x22000000);
        parent.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private void feedback(View view) {
        android.util.TypedValue value=new android.util.TypedValue();
        if(getTheme().resolveAttribute(android.R.attr.selectableItemBackground,value,true)&&value.resourceId!=0)view.setForeground(getDrawable(value.resourceId));
        view.setFocusable(true);
    }
    private void row(LinearLayout parent,String label,String summary,Runnable action) {
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(17),0,dp(17));row.setMinimumHeight(dp(80));
        TextView title=text(label,20,primary);row.addView(title);
        if(summary!=null&&!summary.isEmpty()) {
            TextView detail=text(summary,14,secondary);detail.setPadding(0,dp(5),0,0);row.addView(detail);
        }
        if(action!=null) { feedback(row);row.setEnabled(!busy); row.setAlpha(busy?0.5f:1f);row.setOnClickListener(v->{if(!busy)action.run();}); }
        parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private void action(String label,Runnable action) {
        TextView button=text(label,17,accent);button.setTypeface(null,Typeface.BOLD);button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12),dp(14),dp(12),dp(14));button.setMinimumHeight(dp(52));
        button.setBackground(shape(surface,28));feedback(button);button.setEnabled(!busy);button.setAlpha(busy?0.5f:1f);
        button.setOnClickListener(v->{if(!busy)action.run();});
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(12);content.addView(button,params);
    }
    private String date(long seconds) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(seconds*1000));
    }
    private void draw() {
        if(isDestroyed()) return;
        colors();LinearLayout root=settingsRoot("Power Schedule");ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12),dp(12),dp(12),dp(24));scroll.addView(content);

        TextView intro=text("Choose when your phone turns off and starts again.",15,secondary);
        intro.setPadding(dp(18),0,dp(18),dp(24));content.addView(intro);
        if (!Build.DEVICE.startsWith("dm3q")) {
            row(card(),"Not available","Power scheduling is supported on S23 Ultra (dm3q).",null);setContentView(root);return;
        }
        LinearLayout mode=card();LinearLayout toggleRow=new LinearLayout(this);toggleRow.setGravity(Gravity.CENTER_VERTICAL);
        toggleRow.setPadding(0,dp(17),0,dp(17));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text("Separate power-off and power-on times",20,primary));
        TextView hint=text("Turn off automatically at one time and start again later.",14,secondary);hint.setPadding(0,dp(6),dp(12),0);labels.addView(hint);
        toggleRow.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);toggle.setContentDescription("Separate power-off and power-on times");toggle.setChecked(separate);toggle.setEnabled(!busy);
        toggle.setOnCheckedChangeListener((view,checked)->{
            separate=checked;
            if(checked) {
                long minimum=ScheduleRules.pickerMinimum(false,true,draftOff,System.currentTimeMillis()/1000);
                draftOff=Math.max(draftOff,minimum);
                draftOn=Math.max(draftOn,draftOff+ScheduleRules.MIN_GAP);
            }
            message="";draw();
        });toggleRow.addView(toggle);mode.addView(toggleRow);
        LinearLayout times=card();
        if(separate) {row(times,"Power off",date(draftOff),()->pick(false));divider(times);}
        row(times,"Power on",date(draftOn),()->pick(true));
        TextView note=text(separate?"Power off must be at least 5 minutes ahead. Power on must be at least 3 minutes after power off.":
                "Power on must be at least 5 minutes ahead. Choose to shut down now or later.",14,secondary);
        note.setPadding(dp(18),0,dp(18),dp(20));content.addView(note);
        if(separate) action("Save power-off and power-on schedule",()->confirmPair());
        else action("Save power-on time",()->saveChoices());
        ScheduleEngine.State state=PowerScheduler.state(this);
        if(state.on!=0 || !state.error.isEmpty() || !message.isEmpty()) {
            LinearLayout status=card();String detail=message.isEmpty()?state.error:message;
            if(detail.isEmpty()) {
                if(state.uncertain) detail="The last change did not complete. Save a new schedule before shutting down.";
                else if(state.on<=System.currentTimeMillis()/1000) detail="The previous one-time schedule has ended.";
                else detail=(state.off>0?"Power off: "+date(state.off)+"\n":"")+"Power on: "+date(state.on);
            }
            row(status,busy?"Saving…":"Saved schedule",detail,null);
        }
        if(state.off>0) action("Cancel automatic power off",()->confirmCancel(false));
        if(state.on>0 || state.uncertain) action("Cancel entire schedule",()->confirmCancel(true));
        TextView footer=text("Your PIN is required after startup. Keep enough battery charge for the scheduled power on.",14,secondary);
        footer.setPadding(dp(18),dp(12),dp(18),dp(18));content.addView(footer);
        setContentView(root);
    }
    private void pick(boolean powerOn) {
        long now=System.currentTimeMillis()/1000;
        long minimum=ScheduleRules.pickerMinimum(powerOn,separate,draftOff,now);
        long maximum=now+ScheduleRules.WEEK-(powerOn?0:ScheduleRules.MIN_GAP);
        if(minimum>maximum) {
            new AlertDialog.Builder(this).setTitle("Choose an earlier power-off time")
                    .setMessage("Keep both times within the next 7 days.").setPositiveButton("OK",null).show();return;
        }
        Calendar value=Calendar.getInstance();value.setTimeInMillis(minimum*1000);
        DatePickerDialog dateDialog=new DatePickerDialog(this,(view,y,m,d)->{
            value.set(y,m,d);
            pickTime(powerOn,value);
        },value.get(Calendar.YEAR),value.get(Calendar.MONTH),value.get(Calendar.DAY_OF_MONTH));
        dateDialog.getDatePicker().setMinDate(minimum*1000);
        dateDialog.getDatePicker().setMaxDate(maximum*1000);
        dateDialog.show();
    }
    private long selectedTime(Calendar day,TimePicker picker) {
        Calendar selected=(Calendar)day.clone();selected.setLenient(false);
        selected.set(Calendar.HOUR_OF_DAY,picker.getHour());selected.set(Calendar.MINUTE,picker.getMinute());
        selected.set(Calendar.SECOND,0);selected.set(Calendar.MILLISECOND,0);
        return selected.getTimeInMillis()/1000;
    }
    private void pickTime(boolean powerOn,Calendar day) {
        TimePicker picker=new TimePicker(this);
        picker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(this));
        picker.setHour(day.get(Calendar.HOUR_OF_DAY));picker.setMinute(day.get(Calendar.MINUTE));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(powerOn?"Power-on time":"Power-off time")
                .setMessage("Choose a time at least 5 minutes ahead.").setView(picker)
                .setPositiveButton("OK",null).setNegativeButton("Cancel",null).create();
        timeDialog=dialog;
        Handler handler=new Handler(Looper.getMainLooper());
        Runnable update=new Runnable() {
            public void run() {
                if(!dialog.isShowing())return;
                long now=System.currentTimeMillis()/1000;
                long minimum=ScheduleRules.pickerMinimum(powerOn,separate,draftOff,now);
                String hint="Current time: "+date(now)+"\nEarliest time: "+date(minimum);
                boolean valid=true;
                try {ScheduleRules.validateSelection(selectedTime(day,picker),powerOn,separate,draftOff,now);}
                catch(IllegalArgumentException error) {valid=false;hint+="\nChoose a time within the allowed range.";}
                dialog.setMessage(hint);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(valid);
            }
        };
        Runnable tick=new Runnable() {
            public void run() {update.run();if(dialog.isShowing())handler.postDelayed(this,1000);}
        };
        dialog.setOnShowListener(shown->{
            picker.setOnTimeChangedListener((view,h,m)->update.run());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
                try {
                    picker.clearFocus();
                    long selected=selectedTime(day,picker);
                    ScheduleRules.validateSelection(selected,powerOn,separate,draftOff,System.currentTimeMillis()/1000);
                    if(powerOn)draftOn=selected;
                    else {draftOff=selected;if(separate)draftOn=Math.max(draftOn,draftOff+ScheduleRules.MIN_GAP);}
                    dialog.dismiss();message="";draw();if(powerOn&&!separate)saveChoices();
                }catch(IllegalArgumentException invalid){update.run();}
            });
            tick.run();
        });
        dialog.setOnDismissListener(closed->{
            handler.removeCallbacks(tick);if(timeDialog==dialog)timeDialog=null;
        });
        dialog.show();
    }
    private boolean validDraft() {
        try {ScheduleRules.validate(draftOn,separate?draftOff:0,System.currentTimeMillis()/1000);return true;}
        catch(IllegalArgumentException invalid) {
            new AlertDialog.Builder(this).setTitle("Choose a later time").setMessage(invalid.getMessage())
                    .setPositiveButton("OK",null).show();return false;
        }
    }
    private void saveChoices() {
        if(!validDraft())return;
        new AlertDialog.Builder(this).setTitle("Power on: "+date(draftOn))
                .setItems(new String[]{"Save and Shutdown Now","Save and I'll shutdown manually"},(dialog,which)->{
                    if(which==0) new AlertDialog.Builder(this).setTitle("Shut down now?")
                            .setMessage("Save power on for "+date(draftOn)+" and shut down this phone now. Save any open work first.")
                            .setNegativeButton("Cancel",null).setPositiveButton("Save and Shutdown Now",(d,w)->save(true)).show();
                    else save(false);
                }).setNegativeButton("Cancel",null).show();
    }
    private void confirmPair() {
        if(!validDraft())return;
        new AlertDialog.Builder(this).setTitle("Save power schedule?")
                .setMessage("Power off: "+date(draftOff)+"\nPower on: "+date(draftOn)+
                        "\n\nYour phone will shut down automatically at the selected power-off time.")
                .setNegativeButton("Cancel",null).setPositiveButton("Save schedule",(dialog,which)->save(false)).show();
    }
    private void save(boolean now) {
        final long on=draftOn,off=separate?draftOff:0;
        perform(()->PowerScheduler.save(this,on,off,now),now?"Power-on time saved. Shutting down…":
                off>0?"Power-off and power-on times saved.":"Power-on time saved. Shut down your phone when you're ready.");
    }
    private void confirmCancel(boolean all) {
        new AlertDialog.Builder(this).setTitle(all?"Cancel entire schedule?":"Cancel automatic power off?")
                .setMessage(all?"Clear both the power-off and power-on schedule?":"Keep the saved power-on time and cancel automatic shutdown?")
                .setNegativeButton("Keep",null).setPositiveButton("Cancel schedule",(dialog,which)->
                        perform(()->PowerScheduler.cancel(this,all),all?"Power schedule cancelled.":"Automatic power off cancelled. Power on is still saved.")).show();
    }
    private void perform(Work work,String success) {
        if(busy) return;busy=true;message="";draw();
        worker.execute(()->{
            String result;
            try {work.run();result=success;}
            catch(Exception error) { result=error.getMessage()==null?"The schedule could not be saved.":error.getMessage(); }
            final String text=result;
            runOnUiThread(()->{busy=false;if(isDestroyed())return;message=text;draw();});
        });
    }
}
