/* Native provider adapted from Dressroom Advanced by BlueOutside.
 * https://github.com/BlueOutside/Dressroom-Advanced - see LICENSE.upstream.
 */
package dev.denver.dressroomadvanced;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
public final class SettingsProvider extends ContentProvider {
    public static final Uri URI = NativeSettings.URI;
    private NativeProfileStore store;
    private final Object maskReady = new Object();
    private android.os.HandlerThread maskThread;
    private android.os.Handler maskHandler;
    // Bounded diagnostics; a new upload clears a prior failure for its content ID.
    private final java.util.Map<String, Exception> maskFailures =
            new java.util.LinkedHashMap<String, Exception>() {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Exception> entry) {
                    return size() > 32;
                }
            };
    private static final Set<String> READERS = new HashSet<>(Arrays.asList(
        "com.android.systemui", "com.samsung.android.app.aodservice", "com.samsung.android.app.clockpack",
        "com.samsung.android.app.dressroom", "com.samsung.android.wallpaper.live"));
    @Override public boolean onCreate() {
        store = new NativeProfileStore(getContext());
        // DressRoom calls its own provider synchronously from Apply on the UI thread.
        // Its close listener must not use that UI Looper: awaitMasks would block the
        // callback that publishes the mask until the wait times out.
        maskThread = new android.os.HandlerThread("DressroomMaskUploads");
        maskThread.start();
        maskHandler = new android.os.Handler(maskThread.getLooper());
        return true;
    }
    @Override public void shutdown() {
        if (maskThread != null) maskThread.quitSafely();
        super.shutdown();
    }
    private void checkCaller(boolean write) {
        if (Binder.getCallingUid() == android.os.Process.myUid()) return;
        String[] names = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (names != null) for (String name : names)
            if (write ? name.equals("com.samsung.android.app.dressroom") : READERS.contains(name)) return;
        throw new SecurityException("Unsupported settings caller");
    }
    @Override public Bundle call(String method, String argument, Bundle extras) {
        if ("beginFontPick".equals(method) || "fontPickResult".equals(method)) {
            checkCaller(true);
            return "beginFontPick".equals(method) ? FontFiles.begin(getContext()) : FontFiles.result(getContext(), argument);
        }
        if("commitBatch".equals(method)) {
            checkCaller(true);
            java.util.ArrayList<Bundle> operations=extras==null?null:extras.getParcelableArrayList("operations",Bundle.class);
            if(operations==null)throw new IllegalArgumentException("Missing Apply batch");
            for(Bundle operation:operations) {
                Bundle profile=operation.getBundle("profile");
                if(profile==null)throw new IllegalArgumentException("Missing profile");
                awaitMasks(profile);
                String font=profile.getString("fontFile", "");
                if(!font.isEmpty()&&!FontFiles.file(getContext(),font).isFile())throw new IllegalArgumentException("Imported font unavailable");
            }
            Bundle result=store.commitBatch(operations);getContext().getContentResolver().notifyChange(URI,null);return result;
        }
        boolean write = "commit".equals(method) || "commitWidgets".equals(method) || "commitExtras".equals(method);
        checkCaller(write);
        String scope;
        if ("read".equals(method) || "commit".equals(method)) scope = "clock";
        else if ("readWidgets".equals(method) || "commitWidgets".equals(method)) scope = "widgets";
        else if ("readExtras".equals(method) || "commitExtras".equals(method)) scope = "extras";
        else throw new IllegalArgumentException("Unsupported settings operation");
        if (!write) {
            Bundle result = store.read(scope, extras);
            // Upstream clock readers distinguish no saved override from an override
            // whose fields are false/zero. Preserve that native-default sentinel.
            if ("clock".equals(scope) && !result.containsKey("found")) return null;
            return result;
        }
        if (extras == null) throw new IllegalArgumentException("Missing profile");
        awaitMasks(extras);
        String font = extras.getString("fontFile", "");
        if (!font.isEmpty() && !FontFiles.file(getContext(), font).isFile()) throw new IllegalArgumentException("Imported font unavailable");
        Bundle result = store.commit(scope, extras);
        getContext().getContentResolver().notifyChange(URI, null);
        return result;
    }
    @Override
    public android.os.ParcelFileDescriptor openFile(Uri uri, String mode)
            throws java.io.FileNotFoundException {
        boolean write = "w".equals(mode);
        if (!write && !"r".equals(mode))
            throw new java.io.FileNotFoundException("Unsupported mode");
        checkCaller(write);
        java.util.List<String> segments = uri.getPathSegments();
        if (segments.size() == 2 && segments.get(0).equals("fonts") && !write) {
            try {
                return android.os.ParcelFileDescriptor.open(
                        FontFiles.file(getContext(), segments.get(1)),
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY);
            } catch (IllegalArgumentException error) {
                throw new java.io.FileNotFoundException("Invalid font path");
            }
        }
        if (segments.size() != 2
                || !segments.get(0).equals("masks")
                || !segments.get(1).matches("[0-9a-f]{64}"))
            throw new java.io.FileNotFoundException("Invalid mask path");
        java.io.File directory = new java.io.File(getContext().getFilesDir(), "depth-masks");
        if (write && !directory.isDirectory() && !directory.mkdirs())
            throw new java.io.FileNotFoundException("Could not create mask directory");
        java.io.File file = new java.io.File(directory, segments.get(1));
        if (!write)
            return android.os.ParcelFileDescriptor.open(
                    file, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
        if (file.isFile())
            return android.os.ParcelFileDescriptor.open(
                    new java.io.File("/dev/null"), android.os.ParcelFileDescriptor.MODE_WRITE_ONLY);
        synchronized (maskReady) { maskFailures.remove(file.getName()); }
        java.io.File temporary = null;
        try {
            temporary = java.io.File.createTempFile("upload-", ".tmp", directory);
            final java.io.File upload = temporary;
            return android.os.ParcelFileDescriptor.open(
                    upload,
                    android.os.ParcelFileDescriptor.MODE_WRITE_ONLY,
                    maskHandler,
                    error -> {
                        Exception failure = null;
                        try {
                            if (error != null) throw error;
                            if (upload.length() == 0 || upload.length() > 16 * 1024 * 1024)
                                throw new java.io.IOException("Invalid subject mask size");
                            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                            try (java.io.InputStream input = new java.io.FileInputStream(upload)) {
                                byte[] chunk = new byte[16384]; int count;
                                while ((count = input.read(chunk)) != -1) digest.update(chunk, 0, count);
                            }
                            if (!DepthStorage.hex(digest.digest()).equals(file.getName()))
                                throw new java.io.IOException("Invalid mask checksum");
                            if (!file.isFile() && !upload.renameTo(file))
                                throw new java.io.IOException("Could not publish mask");
                        } catch (Exception rejected) {
                            failure = rejected;
                            android.util.Log.w(DressroomModule.TAG, "Mask upload rejected", rejected);
                        } finally {
                            upload.delete();
                            synchronized (maskReady) {
                                if (failure != null) maskFailures.put(file.getName(), failure);
                                maskReady.notifyAll();
                            }
                        }
                    });
        } catch (java.io.IOException error) {
            if (temporary != null) temporary.delete();
            throw new java.io.FileNotFoundException(error.getMessage());
        }
    }

    private void awaitMasks(Bundle data) {
        for (String field : DepthMetadata.BLOBS) {
            String key = data.getString(field + "Ref");
            if (key == null) continue;
            DepthStorage.uri(key);
            java.io.File file =
                    new java.io.File(
                            new java.io.File(getContext().getFilesDir(), "depth-masks"), key);
            long deadline = android.os.SystemClock.uptimeMillis() + 3000;
            synchronized (maskReady) {
                while (!file.isFile()) {
                    Exception rejected = maskFailures.get(key);
                    if (rejected != null)
                        throw new IllegalStateException("Subject mask upload was rejected: " + rejected.getMessage(), rejected);
                    long remaining = deadline - android.os.SystemClock.uptimeMillis();
                    if (remaining <= 0)
                        throw new IllegalStateException("Subject mask upload did not complete");
                    try {
                        maskReady.wait(remaining);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(error);
                    }
                }
            }
        }
    }

    @Override
    public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri u) {
        return "vnd.android.cursor.item/vnd.dressroomadvanced.settings";
    }

    @Override
    public Uri insert(Uri u, ContentValues v) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri u, String s, String[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri u, ContentValues v, String s, String[] a) {
        throw new UnsupportedOperationException();
    }
}
