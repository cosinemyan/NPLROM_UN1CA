// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

final class NowBarExtras {
    private static final String KEY = "dressroom_advanced_text";
    private static final ThreadLocal<Boolean> finishing = ThreadLocal.withInitial(() -> false);
    private static final Map<Object, WeakReference<View>> held = new WeakHashMap<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static WeakReference<Object> wrapper = new WeakReference<>(null);
    private static Context context;
    private static ClassLoader loader;
    private static Object textItem;
    private static String textSignature = "";
    private static boolean installed;

    private NowBarExtras() {}





    private static boolean plugged(Context c) {
        Intent battery = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
    }

    private static void bind(Context c) {
        if (context != null) return;
        context = c.getApplicationContext();
        RuntimeExtras.get(context);
        context.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent i) {
                        if (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0) {
                            releaseIfNeeded();
                            return;
                        }
                        for (WeakReference<View> ref : new ArrayList<>(held.values())) {
                            View view = ref.get();
                            if (view == null) continue;
                            sync(view, true);
                        }
                    }
                },
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                Context.RECEIVER_NOT_EXPORTED);
    }

    private static boolean isHeld(View view) {
        for (WeakReference<View> ref : held.values()) if (ref.get() == view) return true;
        return false;
    }

    private static void settle(View view) throws ReflectiveOperationException {
        Object animation = Reflect.field(view, "chargerAnimationSet");
        ChargingState.pause(animation);
        ChargingVisuals.finishLiquid((View) Reflect.call(view, "getBatteryProgressbar"));
        ChargingVisuals.restore(view);
        sync(view, true);
    }

    private static void sync(View view, boolean visual) {
        Intent battery =
                view.getContext()
                        .registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return;
        int percent =
                ChargingState.percent(
                        battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                        battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100));
        if (percent < 0) return;
        try {
            Reflect.set(view, "currentBatteryLevel", percent);
            if (!visual) return;
            Object lazy = Reflect.field(view, "batteryLevel$delegate");
            ((TextView) Reflect.call(lazy, "getValue"))
                    .setText(String.format(java.util.Locale.getDefault(), "%d", percent));
            View progress = (View) Reflect.call(view, "getBatteryProgressbar");
            if (view.getWidth() > 0)
                progress.setTranslationX(
                        ChargingState.translation(
                                view.getWidth(),
                                ((Number) Reflect.field(view, "currentLayoutDirection")).intValue(),
                                percent));
        } catch (ReflectiveOperationException error) {
            android.util.Log.w(DressroomModule.TAG, "Charging level refresh unavailable", error);
        }
    }

    private static void releaseIfNeeded() {
        if (context == null || (plugged(context) && RuntimeExtras.enabled(context, "keepCharging")))
            return;
        for (Object ui : new ArrayList<>(held.keySet()))
            try {
                Reflect.call(ui, "removeNowBarView");
            } catch (Exception e) {
                android.util.Log.w(DressroomModule.TAG, "Charging card cleanup failed", e);
            }
        held.clear();
    }

    private static void updateText() {
        Object host = wrapper.get();
        if (context == null || host == null) return;
        Bundle b = RuntimeExtras.get(context);
        try {
            if (!b.getBoolean("textCard")) {
                if (textItem != null) Reflect.call(host, "removeItem", textItem);
                textItem = null;
                textSignature = "";
                return;
            }
            String title = b.getString("title", "");
            if (title.isBlank()) title = "My Now Bar";
            String message = b.getString("message", "");
            String signature = title + "\u0000" + message;
            if (signature.equals(textSignature)) return;
            Class<?> item =
                    Class.forName(
                            "com.android.systemui.plugins.keyguardstatusview.NowBarItem",
                            false,
                            loader);
            Object card = item.getConstructor().newInstance();
            Reflect.call(card, "setNowBarKey", KEY);
            Reflect.call(card, "setNowBarPackage", "com.android.systemui");
            Reflect.call(card, "setNowBarViewStyle", 6);
            Reflect.call(card, "setUserId", android.os.Process.myUid() / 100000);
            Reflect.call(card, "setPrimaryInfo", title);
            Reflect.call(card, "setSecondaryInfo", message);
            Reflect.call(card, "setContentViewForNormalCard", textView(title, message, false));
            Reflect.call(card, "setContentViewForExpandCard", textView(title, message, true));
            Bundle extra = new Bundle();
            extra.putInt("nowbar_key_screen_type", 3);
            Reflect.call(card, "setExtraData", extra);
            Reflect.call(host, "updateItem", card);
            textItem = card;
            textSignature = signature;
        } catch (Exception e) {
            android.util.Log.w(DressroomModule.TAG, "Custom Now Bar unavailable", e);
        }
    }

    private static View textView(String title, String message, boolean expanded) {
        LinearLayout rows = new LinearLayout(context);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int p = Math.round(16 * context.getResources().getDisplayMetrics().density);
        rows.setPadding(p, p / 2, p, p / 2);
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextSize(15);
        heading.setTextColor(Color.WHITE);
        heading.setMaxLines(expanded ? 3 : 1);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        rows.addView(heading);
        if (!message.isEmpty()) {
            TextView body = new TextView(context);
            body.setText(message);
            body.setTextSize(12);
            body.setTextColor(0xffdddddd);
            body.setMaxLines(expanded ? 5 : 1);
            body.setEllipsize(android.text.TextUtils.TruncateAt.END);
            rows.addView(body);
        }
        rows.setContentDescription(title + " " + message);
        return rows;
    }

    static void nativeHost(Object host, Context c) {
        loader=host.getClass().getClassLoader(); wrapper=new WeakReference<>(host);
        textSignature=""; textItem=null; bind(c); RuntimeExtras.listen(NowBarExtras::changed);
        main.post(NowBarExtras::updateText);
    }
    private static void changed() { releaseIfNeeded(); updateText(); }
    static boolean nativeRemove(Object owner) {
        try {
            Context c=(Context)Reflect.field(owner,"context");
            if (!finishing.get() || !RuntimeExtras.enabled(c,"keepCharging") || !plugged(c)) {
                held.remove(owner); return false;
            }
            Object state=Reflect.call(Reflect.field(owner,"_uiState"),"getValue");
            if (!"NowBar".equals(String.valueOf(Reflect.field(state,"currentOverlay")))) return false;
            Object card=Reflect.field(owner,"chargerNowBarView");
            if (!(card instanceof View view)) return false;
            bind(c); held.put(owner,new WeakReference<>(view)); settle(view);
            view.setAlpha(1);view.setVisibility(View.VISIBLE);return true;
        }catch(Exception error){android.util.Log.w("DressroomAdvanced","Charging hold",error);return false;}
    }
    static void nativeFinish(boolean value) { finishing.set(value); }
    static void nativeAttached(View view) {
        if(RuntimeExtras.enabled(view.getContext(),"keepCharging")) sync(view,false);
        view.post(()-> { if(isHeld(view)) try {settle(view);}catch(Exception ignored){} });
    }
    static void nativeResources(View view) {
        if(isHeld(view))try{settle(view);}catch(Exception ignored){}
    }
}
