package io.mesalabs.rezoss.ai;

import android.content.Context;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;

/** Neutral-gate fallback into Samsung's existing recall model, limited to Maps. */
public final class NativeMapGate {
    private NativeMapGate() {}
    private static boolean enabled() {
        try {
            Context context = (Context) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            return context != null
                    && Settings.Global.getInt(context.getContentResolver(), "rezoss_now_nudge_native", 0) == 1
                    && Settings.Global.getInt(context.getContentResolver(), "rezoss_now_nudge_context_actions", 0) == 1
                    && Settings.Global.getInt(context.getContentResolver(), "rezoss_aios_v73_backend", 0) == 1;
        } catch (Exception | LinkageError unavailable) { return false; }
    }
    public static List<String> beforeFilter(List<?> labels) {
        List<String> result = MapGateContract.beforeFilter(labels, enabled(),
                Looper.myLooper() != Looper.getMainLooper());
        if (result != null) Log.i("RezossMapGate", "neutral prediction retained before Samsung filtering");
        return result;
    }
    public static List<String> extend(String input, List<String> labels) {
        List<String> result = MapGateContract.extend(input, labels, enabled(),
                Looper.myLooper() != Looper.getMainLooper());
        if (result != labels) Log.i("RezossMapGate", "neutral candidate forwarded to Samsung recall");
        return result;
    }
    public static List<?> filter(String gatingJson, List<?> actions) {
        try {
            JSONArray json = new JSONArray(gatingJson);
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < json.length(); i++) labels.add(json.getString(i));
            if (!MapGateContract.marked(labels)) return actions;
            if (!enabled() || !MapGateContract.fallback(labels) || actions == null) return Collections.emptyList();
            List<Object> maps = new ArrayList<>();
            for (Object action : actions) {
                if (action == null) continue;
                String type = (String) action.getClass().getMethod("getActionType").invoke(action);
                Map<?, ?> parameters = (Map<?, ?>) action.getClass().getMethod("getParameters").invoke(action);
                if (MapGateContract.mapAction(type, parameters)) maps.add(action);
            }
            Log.i("RezossMapGate", "validated map actions=" + maps.size());
            return maps;
        } catch (Exception | LinkageError unavailable) {
            Log.i("RezossMapGate", "fallback rejected");
            return Collections.emptyList();
        }
    }
}
