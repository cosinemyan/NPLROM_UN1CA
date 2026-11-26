package dev.denver.dressroomadvanced;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Hosts the system document picker without adding a launcher entry. */
public final class FontPickerActivity extends Activity {
    private static final java.util.concurrent.ExecutorService importer =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final java.util.Set<String> active =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private String token;
    private boolean importing;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        token = getIntent().getStringExtra("token");
        if (!FontFiles.valid(this, token)) {
            finish();
            return;
        }
        if (state != null) {
            if (state.getBoolean("importing") && !active.contains(token))
                finishWithError("Font import was interrupted. Please choose the file again.");
            return;
        }
        Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("*/*")
                        .addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(intent, 1);
        } catch (RuntimeException error) {
            finishWithError("No file picker is available");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 1) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            FontFiles.complete(this, token, new Bundle());
            finish();
            return;
        }
        android.net.Uri selected = data.getData();
        importing = true;
        active.add(token);
        setContentView(new android.widget.ProgressBar(this));
        importer.execute(
                () -> {
                    Bundle imported;
                    try {
                        imported = FontFiles.importFont(getApplicationContext(), selected);
                    } catch (Exception error) {
                        imported = new Bundle();
                        imported.putString(
                                "error",
                                error.getMessage() == null
                                        ? "Could not load this font"
                                        : error.getMessage());
                    }
                    FontFiles.complete(getApplicationContext(), token, imported);
                    active.remove(token);
                    runOnUiThread(this::finish);
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("importing", importing);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (token != null && FontFiles.result(this, token).getBoolean("done")) finish();
    }

    private void finishWithError(String message) {
        Bundle result = new Bundle();
        result.putString("error", message);
        FontFiles.complete(this, token, result);
        finish();
    }
}
