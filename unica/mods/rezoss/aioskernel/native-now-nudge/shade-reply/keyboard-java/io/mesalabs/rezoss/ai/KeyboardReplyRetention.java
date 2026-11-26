package io.mesalabs.rezoss.ai;

import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Main-thread state scoped to one keyboard editor; never stores message text. */
public final class KeyboardReplyRetention {
    private static WeakReference<InputMethodService> service = new WeakReference<>(null);
    private static WeakReference<Object> receiver = new WeakReference<>(null);
    private static String packageName;
    private static int fieldId;
    private static boolean pinned;
    private static boolean waiting;
    private static boolean typed;

    public static void start(InputMethodService ime, EditorInfo info, boolean restarting) {
        if (info == null) { finish(); return; }
        boolean samePackage = info.packageName != null && info.packageName.equals(packageName);
        boolean sameEditor = samePackage && info.fieldId == fieldId;
        if (!sameEditor && (!pinned || typed || !samePackage)) {
            clear();
            typed = false;
        } else if (!sameEditor) {
            Log.i("RezossKeyboard", "retention status=editor_refresh_preserved");
        }
        service = new WeakReference<>(ime);
        packageName = info.packageName;
        fieldId = info.fieldId;
        ShadeReplyKeyboard.start(ime, info);
    }

    public static void finish() {
        ShadeReplyKeyboard.finish();
        if (pinned && !typed) {
            service.clear();
            Log.i("RezossKeyboard", "retention status=finish_retained");
            return;
        }
        clear();
        packageName = null;
        typed = false;
        service.clear();
    }

    private static boolean hasText(InputMethodService ime) {
        InputConnection connection = ime.getCurrentInputConnection();
        if (connection == null) return true;
        CharSequence before = connection.getTextBeforeCursor(1, 0);
        CharSequence after = connection.getTextAfterCursor(1, 0);
        CharSequence selected = connection.getSelectedText(0);
        return before == null || after == null || before.length() > 0 || after.length() > 0
                || (selected != null && selected.length() > 0);
    }

    public static void selection(InputMethodService ime) {
        ShadeReplyKeyboard.selection();
        if (packageName == null || typed) return;
        if (hasText(ime)) {
            typed = true;
            clear();
            Log.i("RezossKeyboard", "retention status=manual_input");
        }
    }

    public static boolean keep() {
        if (ShadeReplyKeyboard.active()) return ShadeReplyKeyboard.keep();
        InputMethodService ime = service.get();
        if (ime != null) selection(ime);
        return pinned && !typed;
    }

    private static void clear() {
        boolean wasPinned = pinned;
        pinned = false;
        waiting = false;
        Object target = receiver.get();
        receiver.clear();
        if (wasPinned && target != null) {
            try {
                target.getClass().getMethod("q", Intent.class).invoke(target, new Intent());
            } catch (Exception failure) {
                Log.w("RezossKeyboard", "clear status=" + failure.getClass().getSimpleName());
            }
        }
    }

    /** True consumes only conflicting nudge events; normal keyboard behavior is untouched. */
    public static boolean consume(Object target, Intent intent, android.content.Context context) {
        if (ShadeReplyKeyboard.consume(target, intent, context)) return true;
        InputMethodService ime = service.get();
        if (ime == null || packageName == null) return false;
        String action = intent.getAction();
        if (action == null) return false;
        if (intent.getBooleanExtra("REZOSS_QWEN", false)) {
            if (!packageName.equals(intent.getStringExtra("PACKAGE_NAME"))) return true;
            selection(ime);
            if (typed) return true;
            ArrayList<String> items = intent.getStringArrayListExtra("SUGGESTIONS");
            if (action.endsWith(".NOW_NUDGE") && items != null && !items.isEmpty()) {
                pinned = true;
                waiting = intent.getBooleanExtra("REZOSS_WAITING", false);
                receiver = new WeakReference<>(target);
                Log.i("RezossKeyboard", "retention status=pinned count=" + items.size() + " waiting=" + waiting);
                return false;
            }
            if (waiting && (action.endsWith(".EXIT") || action.endsWith(".FAILED_SUGGESTED_REPLIES"))) {
                clear();
                Log.i("RezossKeyboard", "retention status=waiting_cleared");
                return false;
            }
            return pinned;
        }
        if (!pinned) return false;
        if (action.endsWith(".USER_SENT")) { typed = true; clear(); return false; }
        if (action.endsWith(".EXIT") || action.endsWith(".NOW_NUDGE")
                || action.endsWith(".SUGGESTED_REPLIES") || action.endsWith(".PREPARE_SUGGESTED_REPLIES")
                || action.endsWith(".FAILED_SUGGESTED_REPLIES")) {
            Log.i("RezossKeyboard", "retention status=preserved");
            return true;
        }
        return false;
    }
}
