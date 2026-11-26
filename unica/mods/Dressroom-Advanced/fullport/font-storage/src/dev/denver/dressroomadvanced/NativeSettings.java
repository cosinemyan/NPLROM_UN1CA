/* Dressroom Advanced native transport; see LICENSE.upstream. */
package dev.denver.dressroomadvanced;
import android.app.WallpaperManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
public final class NativeSettings {
    public static final Uri URI = Uri.parse("content://io.mesalabs.unica.dressroomadvanced.settings");
    private NativeSettings() {}
    public static Bundle read(Context context, String scope) { return read(context, scope, new Bundle()); }
    public static Bundle read(Context context, String scope, Bundle identity) {
        Bundle result = context.getContentResolver().call(URI, operation(scope, false), null, stamp(context, scope, identity));
        return result == null ? new Bundle() : result;
    }
    /** Invoke only from Samsung's successful Apply callback; Cancel never calls this. */
    public static boolean write(Context context, String scope, Bundle profile) {
        Bundle result = context.getContentResolver().call(URI, operation(scope, true), null, stamp(context, scope, profile));
        return result != null && result.getBoolean("saved");
    }
    private static Bundle stamp(Context context, String scope, Bundle source) {
        Bundle result = source == null ? new Bundle() : new Bundle(source);
        if (!"extras".equals(scope)) {
            if (!result.containsKey("wallpaperId")) result.putInt("wallpaperId", WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_LOCK));
            if (!result.containsKey("layoutOrientation")) result.putInt("layoutOrientation", context.getResources().getConfiguration().orientation);
            if (!result.containsKey("layoutScreen")) result.putInt("layoutScreen", 0); // dm3q is non-folding.
        }
        return result;
    }
    private static String operation(String scope, boolean write) {
        if ("clock".equals(scope)) return write ? "commit" : "read";
        if ("widgets".equals(scope)) return write ? "commitWidgets" : "readWidgets";
        if ("extras".equals(scope)) return write ? "commitExtras" : "readExtras";
        throw new IllegalArgumentException("Unknown settings scope");
    }
}
