package io.mesalabs.rezoss.ai;

import android.content.*;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.*;
import android.view.inputmethod.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Main-thread bridge into Samsung's existing suggested-reply renderer. */
public final class ShadeReplyKeyboard {
    private static final ShadeReplyContract.State state = new ShadeReplyContract.State();
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static WeakReference<InputMethodService> service = new WeakReference<>(null);
    private static WeakReference<Object> receiver = new WeakReference<>(null);
    private static final List<Ticket> tickets = new ArrayList<>();
    private static String token, key, pkg, fp;
    private static boolean pinned, rendering, clearing;
    private static final Runnable expiry = ShadeReplyKeyboard::refresh;
    private static long epoch;
    private static List<String> shown = Collections.emptyList();
    private static final class Ticket {
        final WeakReference<Object> item;
        final long epoch;
        Ticket(Object item) { this.item = new WeakReference<>(item); this.epoch = ShadeReplyKeyboard.epoch; }
    }
    private static long now() { return SystemClock.elapsedRealtimeNanos(); }
    private static String value(EditorInfo info, String name) {
        return info == null || info.extras == null ? null : info.extras.getString(name);
    }
    public static void start(InputMethodService ime, EditorInfo info) {
        String next = value(info, ShadeReplyContract.TOKEN);
        if (info == null || !"com.android.systemui".equals(info.packageName)
                || next == null || !ShadeReplyContract.valid(value(info, ShadeReplyContract.KEY),
                value(info, ShadeReplyContract.PACKAGE), value(info, ShadeReplyContract.FINGERPRINT))) {
            if (token != null) finish();
            return;
        }
        if (!Objects.equals(token, next)) { clear(); epoch++; }
        service = new WeakReference<>(ime);
        token = next; key = value(info, ShadeReplyContract.KEY);
        pkg = value(info, ShadeReplyContract.PACKAGE); fp = value(info, ShadeReplyContract.FINGERPRINT);
        state.open(key, pkg, fp, token, now());
        if (info.extras.getBoolean(ShadeReplyContract.BLOCKED)) state.typed(now());
        handler.post(ShadeReplyKeyboard::refresh);
        handler.postDelayed(ShadeReplyKeyboard::refresh, 150);
    }
    public static void viewStarted() { handler.post(ShadeReplyKeyboard::refresh); }
    public static boolean active() { return token != null; }
    public static void finish() {
        if (token == null) return;
        state.close(now()); clear(); epoch++; token = null; service.clear();
    }
    public static void command(String action, Bundle data) {
        if (!"rezoss.shade.invalidate".equals(action) || data == null || token == null
                || !token.equals(data.getString(ShadeReplyContract.TOKEN))) return;
        if (data.getBoolean(ShadeReplyContract.BLOCKED)) { state.typed(now()); clear(); epoch++; }
        else finish();
    }
    private static boolean matches(InputMethodService ime) {
        EditorInfo info = ime.getCurrentInputEditorInfo();
        return info != null && "com.android.systemui".equals(info.packageName)
                && Objects.equals(token, value(info, ShadeReplyContract.TOKEN))
                && Objects.equals(key, value(info, ShadeReplyContract.KEY))
                && Objects.equals(pkg, value(info, ShadeReplyContract.PACKAGE))
                && Objects.equals(fp, value(info, ShadeReplyContract.FINGERPRINT))
                && !info.extras.getBoolean(ShadeReplyContract.BLOCKED);
    }
    private static int text(InputMethodService ime) {
        InputConnection c = ime.getCurrentInputConnection();
        if (c == null) return -1;
        CharSequence a = c.getTextBeforeCursor(1, 0), b = c.getTextAfterCursor(1, 0), s = c.getSelectedText(0);
        if (a == null || b == null) return -1;
        return a.length() + b.length() > 0 || s != null && s.length() > 0 ? 1 : 0;
    }
    public static void selection() {
        InputMethodService ime = service.get();
        if (token != null && ime != null && text(ime) == 1) { state.typed(now()); clear(); epoch++; }
    }
    public static boolean keep() { if (rendering) return true; if (clearing) return false; refresh(); return pinned; }
    private static void clear() {
        boolean previous = pinned; pinned = false; shown = Collections.emptyList();
        Object target = receiver.get();
        handler.removeCallbacks(expiry);
        if (previous && target != null) {
            clearing = true;
            try { invoke(target, "q", new Intent()); } finally { clearing = false; }
        }
    }
    private static boolean invoke(Object target, String method, Intent intent) {
        try { target.getClass().getMethod(method, Intent.class).invoke(target, intent); return true; }
        catch (Exception failure) { android.util.Log.w("RezossShadeReply", "render status=" + failure.getClass().getSimpleName()); return false; }
    }
    public static boolean consume(Object target, Intent intent, Context context) {
        if (!intent.getBooleanExtra(ShadeReplyContract.BRIDGE, false)) {
            // Ordinary display-nudge broadcasts cannot supply a shade conversation identity.
            return active() && !"com.samsung.android.intent.action.INPUT_SUGGESTED_REPLIES".equals(intent.getAction());
        }
        receiver = new WeakReference<>(target);
        try {
            if (!(target instanceof BroadcastReceiver)) return true;
            InputMethodService ime = service.get();
            if (context == null) return true;
            PackageManager pm = context.getPackageManager();
            int uid = pm.getApplicationInfo("com.samsung.android.smartsuggestions", 0).uid;
            if (((BroadcastReceiver) target).getSentFromUid() != uid
                    || pm.checkSignatures(uid, android.os.Process.myUid()) != PackageManager.SIGNATURE_MATCH) return true;
            List<String> replies = intent.getStringArrayListExtra("SUGGESTIONS");
            if (state.receive(intent.getStringExtra(ShadeReplyContract.KEY), intent.getStringExtra(ShadeReplyContract.PACKAGE),
                    intent.getStringExtra(ShadeReplyContract.FINGERPRINT), intent.getLongExtra(ShadeReplyContract.STARTED, 0), replies, now())) refresh();
        } catch (Exception ignored) { /* Malformed or untrusted delivery fails closed. */ }
        return true;
    }
    public static void refresh() {
        if (rendering || clearing) return;
        handler.removeCallbacks(expiry);
        InputMethodService ime = service.get();
        if (token == null || ime == null) return;
        if (!matches(ime)) { clear(); return; }
        selection();
        List<String> replies = state.current(now());
        if (text(ime) != 0 || !ime.isInputViewShown() || replies.isEmpty()) { clear(); return; }
        if (pinned && replies.equals(shown)) { handler.postDelayed(expiry, 1000); return; }
        Object target = receiver.get();
        if (target == null) return;
        epoch++; rendering = true;
        Intent intent = new Intent("com.samsung.android.intent.action.SUGGESTED_REPLIES");
        intent.putExtra("PACKAGE_NAME", "com.android.systemui");
        intent.putStringArrayListExtra("SUGGESTIONS", new ArrayList<>(replies));
        try { pinned = invoke(target, "t", intent); shown = replies; }
        finally { rendering = false; }
        if (pinned) {
            android.util.Log.i("RezossShadeReply", "render count=" + replies.size());
            handler.postDelayed(expiry, 1000);
        }
    }
    public static void tagItem(Object item) {
        if (!rendering) return;
        tickets.removeIf(t -> t.item.get() == null);
        tickets.add(new Ticket(item));
    }
    public static boolean isBridgeItem(Object item) {
        for (Ticket ticket : tickets) if (ticket.item.get() == item) return true;
        return false;
    }
    public static boolean allowClick(Object item) {
        for (Ticket ticket : tickets) if (ticket.item.get() == item) {
            InputMethodService ime = service.get();
            return ticket.epoch == epoch && token != null && pinned && ime != null
                    && matches(ime) && text(ime) == 0 && !state.current(now()).isEmpty();
        }
        return true;
    }
}
