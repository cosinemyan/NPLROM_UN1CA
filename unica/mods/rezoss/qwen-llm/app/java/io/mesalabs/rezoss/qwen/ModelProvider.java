package io.mesalabs.rezoss.qwen;

import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.JSONObject;
import java.io.*;

/** Read-only model assets. Inference runs exclusively in AIOSKernelService. */
public final class ModelProvider extends ContentProvider {
    private static final String AIOS = "com.samsung.android.aioskernelservice";
    private JSONObject info;
    @Override public boolean onCreate() {
        try (InputStream in = getContext().getAssets().open("v73/model-info.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) != -1) { if (out.size() + n > 16384) return false; out.write(b, 0, n); }
            info = new JSONObject(out.toString("UTF-8"));
            return true;
        } catch (Exception e) { return false; }
    }
    private void authorize() {
        try {
            PackageManager pm = getContext().getPackageManager();
            if (Binder.getCallingUid() != pm.getApplicationInfo(AIOS, 0).uid)
                throw new SecurityException("Only AIOS may read the model");
            // The manifest additionally requires the ROM's signature/known-signer permission.
            if (getContext().checkCallingPermission("io.mesalabs.rezoss.permission.V73_MODELS")
                    != PackageManager.PERMISSION_GRANTED) throw new SecurityException("Model permission denied");
        } catch (PackageManager.NameNotFoundException e) { throw new SecurityException("AIOS unavailable"); }
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        authorize();
        if (!"model_info".equals(method) || info == null) return Bundle.EMPTY;
        Bundle b = new Bundle();
        for (String key : new String[]{"contract", "model_sha256", "tokenizer_sha256"})
            b.putString(key, info.optString(key));
        b.putLong("model_size", info.optLong("model_size"));
        b.putLong("tokenizer_size", info.optLong("tokenizer_size"));
        return b;
    }
    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        authorize();
        String name = uri.getLastPathSegment();
        if (!"r".equals(mode) || uri.getPathSegments().size() != 1 ||
                !("hybrid_llama_qnn.pte".equals(name) || "tokenizer.json".equals(name)))
            throw new FileNotFoundException("Unknown read-only model asset");
        try { return getContext().getAssets().openFd("v73/" + name); }
        catch (IOException e) { throw new FileNotFoundException("Model asset unavailable"); }
    }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
}
