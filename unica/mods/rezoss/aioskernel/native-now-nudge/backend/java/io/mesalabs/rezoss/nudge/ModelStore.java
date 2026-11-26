package io.mesalabs.rezoss.nudge;

import android.content.Context;
import android.content.pm.*;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;

/** Imports signed, compatible model-pack assets into AIOS-owned storage atomically. */
final class ModelStore {
    static final String PACKAGE = "io.mesalabs.rezoss.qwen";
    private static final Uri URI = Uri.parse("content://io.mesalabs.rezoss.qwen.models");
    private static final String CONTRACT = "qwen3-0.6b-sm8550-v73-1024-v1";
    private final Context context;
    private Info cached;
    static final class Info {
        long version, updated, modelSize, tokenizerSize;
        String modelHash, tokenizerHash;
        String revision() { return modelHash + "-" + tokenizerHash; }
    }
    ModelStore(Context context) { this.context = context; }
    static boolean trusted(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        if (pm.checkSignatures(context.getPackageName(), pkg) == PackageManager.SIGNATURE_MATCH) return true;
        // Real AOSP platform signer used for test data APKs; ROM preloads may expose Samsung identity.
        String hash = "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8";
        byte[] expected = new byte[32];
        for (int i = 0; i < 32; i++) expected[i] = (byte) Integer.parseInt(hash.substring(i*2, i*2+2), 16);
        return pm.hasSigningCertificate(pkg, expected, PackageManager.CERT_INPUT_SHA256);
    }
    synchronized Info info() {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pkg = pm.getPackageInfo(PACKAGE, 0);
            if (!pkg.applicationInfo.enabled || !trusted(context, PACKAGE)) { cached = null; return null; }
            ProviderInfo provider = pm.resolveContentProvider(URI.getAuthority(), 0);
            if (provider == null || !PACKAGE.equals(provider.packageName)) { cached = null; return null; }
            if (cached != null && cached.version == pkg.getLongVersionCode() && cached.updated == pkg.lastUpdateTime) return cached;
            Bundle b = context.getContentResolver().call(URI, "model_info", null, null);
            if (b == null || !CONTRACT.equals(b.getString("contract"))) { cached = null; return null; }
            Info next = new Info(); next.version = pkg.getLongVersionCode(); next.updated = pkg.lastUpdateTime;
            next.modelHash = b.getString("model_sha256", ""); next.tokenizerHash = b.getString("tokenizer_sha256", "");
            next.modelSize = b.getLong("model_size"); next.tokenizerSize = b.getLong("tokenizer_size");
            if (!next.modelHash.matches("[a-f0-9]{64}") || !next.tokenizerHash.matches("[a-f0-9]{64}") ||
                next.modelSize < 100000000 || next.modelSize > 2000000000L ||
                next.tokenizerSize < 1000000 || next.tokenizerSize > 50000000) { cached = null; return null; }
            cached = next; return next;
        } catch (Exception e) { cached = null; return null; }
    }
    synchronized File ensure(long deadline) throws Exception {
        Info meta = info(); if (meta == null) throw new IOException("model_missing_or_incompatible");
        File root = new File(context.getFilesDir(), "v73-models"), folder = new File(root, meta.revision());
        File ready = new File(folder, "verified");
        if (ready.isFile() && new File(folder,"hybrid_llama_qnn.pte").length() == meta.modelSize &&
                new File(folder,"tokenizer.json").length() == meta.tokenizerSize) return folder;
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Model directory unavailable");
        File staging = Files.createTempDirectory(root.toPath(), "import-").toFile();
        try {
            copy("hybrid_llama_qnn.pte", meta.modelSize, meta.modelHash, staging, deadline);
            copy("tokenizer.json", meta.tokenizerSize, meta.tokenizerHash, staging, deadline);
            Info after = info();
            if (after == null || after.updated != meta.updated || after.version != meta.version)
                throw new IOException("model_package_changed");
            Files.write(new File(staging,"verified").toPath(), meta.revision().getBytes("UTF-8"));
            if (folder.exists()) remove(folder);
            Files.move(staging.toPath(), folder.toPath(), StandardCopyOption.ATOMIC_MOVE);
            File[] previous = root.listFiles();
            if (previous != null) for (File p : previous) if (!p.equals(folder)) remove(p);
            return folder;
        } finally { if (staging.exists()) remove(staging); }
    }
    private void copy(String name, long size, String hash, File folder, long deadline) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); long total = 0;
        try (AssetFileDescriptor fd = context.getContentResolver().openAssetFileDescriptor(URI.buildUpon().appendPath(name).build(), "r")) {
            if (fd == null || fd.getLength() != size) throw new IOException("model_asset_size");
            try (InputStream in = fd.createInputStream(); FileOutputStream out = new FileOutputStream(new File(folder,name))) {
                byte[] b = new byte[1024*1024]; int n;
                while ((n=in.read(b)) != -1) {
                    if (Thread.currentThread().isInterrupted() || SystemClock.elapsedRealtime() >= deadline) throw new InterruptedIOException("model_import_cancelled");
                    total += n; if (total > size) throw new IOException("model_asset_overflow");
                    digest.update(b,0,n); out.write(b,0,n);
                }
                out.getFD().sync();
            }
        }
        StringBuilder actual = new StringBuilder(); for (byte b : digest.digest()) actual.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if (total != size || !hash.equals(actual.toString())) throw new IOException("model_asset_hash");
    }
    private static void remove(File file) throws IOException {
        // Only children of AIOS's own import directory are passed here; do not follow symlinks.
        if (file.isDirectory() && !Files.isSymbolicLink(file.toPath())) {
            File[] children=file.listFiles(); if (children!=null) for(File child:children) remove(child);
        }
        Files.deleteIfExists(file.toPath());
    }
}
