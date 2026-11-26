package dev.denver.dressroomadvanced;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

final class FontPicker {
    interface Result {
        void accept(Bundle result);
    }

    private FontPicker() {}

    static void open(Context context, Result callback) {
        Bundle ticket =
                context.getContentResolver()
                        .call(SettingsProvider.URI, "beginFontPick", null, null);
        if (ticket == null) throw new IllegalStateException("Font picker unavailable");
        String token = ticket.getString("token");
        Context application = context.getApplicationContext();
        Handler handler = new Handler(Looper.getMainLooper());
        var observer =
                new ContentObserver(handler) {
                    boolean delivered;
                    final Runnable expiry =
                            () -> application.getContentResolver().unregisterContentObserver(this);

                    @Override
                    public void onChange(boolean self) {
                        if (delivered) return;
                        Bundle result =
                                application
                                        .getContentResolver()
                                        .call(SettingsProvider.URI, "fontPickResult", token, null);
                        if (result == null || !result.getBoolean("done")) return;
                        delivered = true;
                        handler.removeCallbacks(expiry);
                        application.getContentResolver().unregisterContentObserver(this);
                        callback.accept(result);
                    }

                    void cancel() {
                        handler.removeCallbacks(expiry);
                        application.getContentResolver().unregisterContentObserver(this);
                    }

                    void expireLater() {
                        handler.postDelayed(expiry, 30 * 60_000L);
                    }
                };
        application
                .getContentResolver()
                .registerContentObserver(FontFiles.resultUri(token), false, observer);
        observer.expireLater();
        try {
            Intent intent =
                    new Intent()
                            .setClassName(
                                    "com.samsung.android.app.dressroom",
                                    "dev.denver.dressroomadvanced.FontPickerActivity")
                            .putExtra("token", token);
            context.startActivity(intent);
        } catch (RuntimeException error) {
            observer.cancel();
            throw error;
        }
    }
}
