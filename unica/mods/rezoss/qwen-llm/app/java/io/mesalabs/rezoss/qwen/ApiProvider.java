package io.mesalabs.rezoss.qwen;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import java.io.IOException;

/** Private bridge from the ROM inference service to the user's configured API. */
public final class ApiProvider extends ContentProvider {
    private static final String AIOS = "com.samsung.android.aioskernelservice";
    private static final String PERMISSION = "io.mesalabs.rezoss.permission.QWEN_API";

    @Override public boolean onCreate() { return true; }

    private void authorize() {
        try {
            PackageManager pm = getContext().getPackageManager();
            if (Binder.getCallingUid() != pm.getApplicationInfo(AIOS, 0).uid)
                throw new SecurityException("Only AIOS may use the API bridge");
            if (getContext().checkCallingPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED)
                throw new SecurityException("API bridge permission denied");
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException("AIOS unavailable");
        }
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        authorize();
        Bundle result = new Bundle();
        if("backend_config".equals(method)){
            result.putString("backend",ApiSettingsStore.backend(getContext(),false));
            result.putString("screening_backend",ApiSettingsStore.backend(getContext(),true));
            result.putString("memory_backend",ApiSettingsStore.memoryBackend(getContext()));
            ApiSettingsStore.Settings display=ApiSettingsStore.display(getContext());
            result.putInt("diagnostic_protocol",1);
            result.putString("endpoint",display.endpoint);result.putString("model",display.model);return result;
        }
        if ("api_ready".equals(method)) {
            try { result.putBoolean("configured", ApiSettingsStore.configured(getContext())); }
            catch (Exception unavailable) { result.putBoolean("configured", false); }
            return result;
        }
        if ("cancel".equals(method)) {
            ApiClient.cancel(extras == null ? null : extras.getString("request_id"));
            result.putBoolean("cancelled", true);
            return result;
        }
        if (!"generate".equals(method) || extras == null) {
            result.putString("status", "unsupported_request");
            return result;
        }

        String prompt = extras.getString("prompt", "");
        String requestId = extras.getString("request_id", "");
        long deadline = extras.getLong("deadline", 0);
        int maxTokens = extras.getInt("max_tokens", 96);
        try {
            String backend=extras.getString("backend","default");
            boolean memory="memory".equals(extras.getString("task"));
            if(memory&&!backend.equals(ApiSettingsStore.memoryBackend(getContext())))throw new ApiClient.ApiException("backend_changed");
            if("default".equals(backend)||(!memory&&!backend.equals(ApiSettingsStore.backend(getContext(),false))&&!backend.equals(ApiSettingsStore.backend(getContext(),true))))
                throw new ApiClient.ApiException("backend_changed");
            if(!ApiSettingsStore.matchesEndpoint(backend,ApiSettingsStore.display(getContext()).endpoint))throw new ApiClient.ApiException("provider_mismatch");
            String content = ApiClient.complete(getContext(), null, prompt, maxTokens, deadline, requestId, backend, extras.getString("destination"),memory);
            result.putString("content", content);
            result.putString("status", "generated");
        } catch (ApiClient.ApiException failure) {
            result.putString("status", failure.status);
        } catch (IOException failure) {
            result.putString("status", "api_unavailable");
        } catch (Exception failure) {
            result.putString("status", "api_unavailable");
        }
        return result;
    }

    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        throw new UnsupportedOperationException();
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
