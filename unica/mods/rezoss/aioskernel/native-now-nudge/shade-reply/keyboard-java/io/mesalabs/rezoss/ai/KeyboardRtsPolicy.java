package io.mesalabs.rezoss.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Enables Telegram-family packages in HoneyBoard's RTS app policy. */
public final class KeyboardRtsPolicy {
    private static final String[] PACKAGES = {
            "org.telegram.messenger",
            "org.telegram.messenger.web"
    };

    public static void enable(Object policy) {
        if (policy == null) return;
        try {
            Context context = (Context) field(policy, "a");
            String prefix = String.valueOf(field(policy, "c"));
            SharedPreferences.Editor editor =
                    PreferenceManager.getDefaultSharedPreferences(context).edit();
            for (String pkg : PACKAGES) editor.putBoolean(prefix + pkg, true);
            editor.apply();
            Method refresh = policy.getClass().getMethod("d", String.class);
            for (String pkg : PACKAGES) refresh.invoke(policy, pkg);
            Log.i("RezossKeyboard", "rts_policy status=enabled telegram=true ayugram=true");
        } catch (Exception error) {
            Log.w("RezossKeyboard", "rts_policy status=" + error.getClass().getSimpleName());
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private KeyboardRtsPolicy() { }
}
