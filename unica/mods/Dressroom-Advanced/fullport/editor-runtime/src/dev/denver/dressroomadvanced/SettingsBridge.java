package dev.denver.dressroomadvanced;

import android.app.WallpaperManager;
import android.content.Context;
import android.os.Bundle;

final class SettingsBridge {
    private SettingsBridge() {}

    static int wallpaperId(Context context) {
        return wallpaperId(context, ScreenIdentity.current());
    }

    static int wallpaperId(Context context, int screen) {
        try {
            WallpaperManager manager = WallpaperManager.getInstance(context);
            int lock = manager.getWallpaperId(ScreenIdentity.wallpaperWhich(screen, true));
            return lock >= 0 ? lock : manager.getWallpaperId(ScreenIdentity.wallpaperWhich(screen, false));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    static Bundle read(Context context, Bundle identity) {
        Bundle query = new Bundle(identity);
        LayoutOrientation.stamp(context, query);
        query.putInt("wallpaperId", wallpaperId(context, ScreenIdentity.value(query)));
        query.putInt("legacyWallpaperId", wallpaperId(context, 0));
        if (query.getInt("wallpaperId", -1) < 0) return null;
        try {
            return DepthStorage.unpack(context,
                    context.getContentResolver().call(SettingsProvider.URI, "read", null, query));
        } catch (RuntimeException unavailableBeforeUnlock) {
            return null;
        }
    }

    static Bundle commit(Context context, Bundle snapshot) {
        java.util.ArrayList<Bundle> layouts =
                snapshot.getParcelableArrayList("layoutProfiles", Bundle.class);
        if (layouts != null) {
            Bundle result = null;
            for (Bundle layout : layouts) result = commit(context, layout);
            return result;
        }
        Bundle saved = new Bundle(snapshot);
        LayoutOrientation.stamp(context, saved);
        saved = committedIdentity(saved, screen -> wallpaperId(context, screen));
        return context.getContentResolver()
                .call(SettingsProvider.URI, "commit", null, DepthStorage.pack(context, saved));
    }

    static Bundle committedIdentity(Bundle snapshot, java.util.function.IntUnaryOperator wallpaper) {
        Bundle saved = new Bundle(snapshot);
        // Samsung may assign a new wallpaper ID during Apply. Resolve it after native
        // completion, using the frozen screen rather than the current fold posture.
        int id = wallpaper.applyAsInt(ScreenIdentity.value(saved));
        if (id < 0) throw new IllegalStateException("Committed wallpaper identity is unavailable");
        saved.putInt("wallpaperId", id);
        return saved;
    }
}
