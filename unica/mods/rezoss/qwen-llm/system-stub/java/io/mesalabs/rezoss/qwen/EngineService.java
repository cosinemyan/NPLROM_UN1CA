package io.mesalabs.rezoss.qwen;

import android.app.Service;
import android.content.Intent;
import android.os.*;

/** Base system package used when the separately updatable inference engine is absent. */
public final class EngineService extends Service {
    private final Messenger endpoint = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message request) {
            if (request.what != 1 || request.replyTo == null) return;
            Message response = Message.obtain(null, 1);
            Bundle data = new Bundle();
            data.putString("status", "engine_update_required");
            response.setData(data);
            try { request.replyTo.send(response); } catch (RemoteException ignored) { }
        }
    });

    @Override public IBinder onBind(Intent intent) { return endpoint.getBinder(); }
}
