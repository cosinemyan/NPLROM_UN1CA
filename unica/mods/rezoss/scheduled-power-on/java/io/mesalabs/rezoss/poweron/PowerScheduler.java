// SPDX-License-Identifier: GPL-3.0-or-later
package io.mesalabs.rezoss.poweron;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import java.io.IOException;

final class PowerScheduler implements ScheduleEngine.Ports {
    static final String FIRE="io.mesalabs.rezoss.poweron.FIRE";
    private static final Object LOCK=new Object();
    private final Context context;
    private final SharedPreferences prefs;
    private final AlarmManager alarms;
    PowerScheduler(Context context) {
        this.context=context.getApplicationContext();
        prefs=context.createDeviceProtectedStorageContext().getSharedPreferences("power_schedule",Context.MODE_PRIVATE);
        alarms=context.getSystemService(AlarmManager.class);
    }
    static void migrate(Context context) throws Exception {
        synchronized (LOCK) {
            PowerScheduler io=new PowerScheduler(context);
            if (io.prefs.getBoolean("migrated",false)) return;
            SharedPreferences old=context.getSharedPreferences("schedule",Context.MODE_PRIVATE);
            io.store(new ScheduleEngine.State(old.getLong("target",0),0,old.getLong("offset",0),
                    old.getInt("boot",-1),"",old.getBoolean("uncertain",false),""));
        }
    }
    static ScheduleEngine.State state(Context context) {
        synchronized (LOCK) { return new PowerScheduler(context).load(); }
    }
    static void save(Context context,long on,long off,boolean now) throws Exception {
        synchronized (LOCK) { new ScheduleEngine(new PowerScheduler(context)).save(on,off,now); }
    }
    static void cancel(Context context,boolean all) throws Exception {
        synchronized (LOCK) { new ScheduleEngine(new PowerScheduler(context)).cancel(all); }
    }
    static void receive(Context context,Intent intent) throws Exception {
        synchronized (LOCK) {
            PowerScheduler io=new PowerScheduler(context);
            ScheduleEngine engine=new ScheduleEngine(io);
            if (FIRE.equals(intent.getAction())) engine.fire(intent.getStringExtra("token"));
            else {
                try { engine.restore(); }
                catch (Exception failure) {
                    io.cancelAlarm();
                    io.store(io.load().withoutOff("The schedule could not be restored. Save the times again before shutting down."));
                    throw failure;
                }
            }
        }
    }
    public ScheduleEngine.State load() {
        return new ScheduleEngine.State(prefs.getLong("target",0),prefs.getLong("off",0),
                prefs.getLong("offset",0),prefs.getInt("boot",-1),prefs.getString("token",""),
                prefs.getBoolean("uncertain",false),prefs.getString("error",""));
    }
    public void store(ScheduleEngine.State state) throws IOException {
        if (!prefs.edit().putLong("target",state.on).putLong("off",state.off)
                .putLong("offset",state.offset).putInt("boot",state.boot).putString("token",state.token)
                .putBoolean("uncertain",state.uncertain).putString("error",state.error)
                .putBoolean("migrated",true).commit())
            throw new IOException("The schedule could not be saved. Automatic shutdown was not requested.");
    }
    public long now() { return System.currentTimeMillis()/1000; }
    public int boot() { return Settings.Global.getInt(context.getContentResolver(),Settings.Global.BOOT_COUNT,-1); }
    public long[] snapshot() throws IOException {
        try { return AlarmHardware.snapshot(); }
        catch (LinkageError error) { throw new IOException("Power scheduling is not available in this ROM.",error); }
    }
    public void program(long target) throws IOException {
        try { AlarmHardware.program(target); }
        catch (LinkageError error) { throw new IOException("Power scheduling is not available in this ROM.",error); }
    }
    public void checkShutdown() throws Exception { PowerControl.method(context); }
    public void checkExactAlarm() {
        if (alarms==null || !alarms.canScheduleExactAlarms())
            throw new SecurityException("Exact alarms are unavailable. The power-off schedule was not changed.");
    }
    private PendingIntent pending(String token,int flags) {
        Intent intent=new Intent(context,PowerScheduleReceiver.class).setAction(FIRE).putExtra("token",token);
        return PendingIntent.getBroadcast(context,301,intent,flags|PendingIntent.FLAG_IMMUTABLE);
    }
    public void arm(long off,String token) {
        checkExactAlarm();
        long trigger=SystemClock.elapsedRealtime()+Math.max(0,off-now())*1000;
        alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,trigger,
                pending(token,PendingIntent.FLAG_CANCEL_CURRENT));
    }
    public void cancelAlarm() {
        PendingIntent pending=pending("",PendingIntent.FLAG_NO_CREATE);
        if (pending!=null) { if (alarms!=null) alarms.cancel(pending); pending.cancel(); }
    }
    public void shutdown() throws Exception { PowerControl.shutdown(context); }
}
