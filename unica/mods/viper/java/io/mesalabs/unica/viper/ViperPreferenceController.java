// SPDX-License-Identifier: GPL-3.0-or-later
package io.mesalabs.unica.viper;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;
import com.android.settings.core.TogglePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import java.io.File;

/** The app owns the switch and profiles; Settings never edits another app's data. */
public final class ViperPreferenceController extends TogglePreferenceController
        implements LifecycleObserver, OnStart, OnStop {
    private static final Uri CONTROL = Uri.parse("content://com.llsl.viper4android.unica");
    private final Context context;
    private Preference preference;
    private final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange) {
            if (preference != null) updateState(preference);
        }
    };

    public ViperPreferenceController(Context context, String key) {
        super(context, key);
        this.context = context;
    }

    @Override public int getAvailabilityStatus() {
        return context.getPackageManager().resolveContentProvider(CONTROL.getAuthority(), 0) != null
                && new File("/vendor/lib64/soundfx/libv4a_re.so").isFile() ? 0 : 3;
    }

    @Override public void onStart() {
        if (getAvailabilityStatus() != 0) return;
        context.getContentResolver().registerContentObserver(CONTROL, true, observer);
        if (preference != null) updateState(preference);
    }

    @Override public void onStop() {
        context.getContentResolver().unregisterContentObserver(observer);
    }

    private Bundle call(String method, Bundle extras) {
        Bundle result = context.getContentResolver().call(CONTROL, method, null, extras);
        if (result == null || !result.containsKey("enabled")) {
            throw new IllegalStateException("ViPER control unavailable");
        }
        return result;
    }

    @Override public boolean isChecked() {
        try { return call("get", null).getBoolean("enabled"); }
        catch (RuntimeException failure) { return false; }
    }

    @Override public boolean setChecked(boolean checked) {
        try {
            Bundle extras = new Bundle();
            extras.putBoolean("enabled", checked);
            if (call("set", extras).getBoolean("enabled") != checked) return false;
            if (preference != null) updateState(preference);
            return true;
        } catch (RuntimeException failure) {
            Toast.makeText(context, text("unica_viper_error"), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    @Override public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        preference = screen.findPreference("unica_viper_enabled");
        if (preference != null) updateState(preference);
    }

    @Override public void updateState(Preference pref) {
        super.updateState(pref);
        try {
            boolean enabled = call("get", null).getBoolean("enabled");
            if (pref instanceof TwoStatePreference) ((TwoStatePreference) pref).setChecked(enabled);
            pref.setSummary(text(enabled ? "unica_viper_on" : "unica_viper_off"));
        } catch (RuntimeException failure) {
            if (pref instanceof TwoStatePreference) ((TwoStatePreference) pref).setChecked(false);
            pref.setSummary(text("unica_viper_error"));
        }
    }

    private String text(String name) {
        int id = context.getResources().getIdentifier(name, "string", context.getPackageName());
        return id == 0 ? name : context.getString(id);
    }

    @Override public int getSliceHighlightMenuRes() { return 0; }
}
