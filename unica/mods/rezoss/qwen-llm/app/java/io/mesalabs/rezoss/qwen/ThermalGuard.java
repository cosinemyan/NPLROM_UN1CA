package io.mesalabs.rezoss.qwen;

import android.content.*;
import android.os.*;

/** Conservative operational limits, not a device safety certification. */
final class ThermalGuard {
    static String blocked(Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        if (power == null) return "thermal_unknown";
        // Let interactive replies run through normal device warmth. Only stop at severe throttling.
        if (power.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE)
            return "thermal_status";
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null || !battery.hasExtra(BatteryManager.EXTRA_TEMPERATURE))
            return "temperature_unknown";
        int temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        if (temperature < 0) return "temperature_unknown";
        if (temperature >= 890) return "battery_temperature";
        if (power.isPowerSaveMode()) return "power_save";
        return null;
    }
}
