/* Copyright (c) 2026 Rezoss
 * SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.unica.keybox;

import android.os.IBinder;
import android.os.Parcel;
import org.json.JSONObject;

/** UID-checked binder bridge; no WebView or root shell is used by Settings. */
final class Backend {
    static JSONObject call(JSONObject request)throws Exception {
        IBinder service=(IBinder)Class.forName("android.os.ServiceManager").getMethod("checkService",String.class).invoke(null,"unica.teesim");
        if(service==null)throw new IllegalStateException("TEESimulator is stopped. Enable spoof hardware attestation to use this operation.");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();
        try{
            data.writeInterfaceToken("io.mesalabs.unica.teesim.Admin");data.writeString(request.toString());
            if(!service.transact(IBinder.FIRST_CALL_TRANSACTION,data,reply,0))throw new IllegalStateException("Backend interface unavailable");
            reply.readException();JSONObject result=new JSONObject(reply.readString());
            if(!result.optBoolean("ok"))throw new IllegalStateException(result.optString("error","Backend operation failed"));
            return result;
        }finally{data.recycle();reply.recycle();}
    }
}
