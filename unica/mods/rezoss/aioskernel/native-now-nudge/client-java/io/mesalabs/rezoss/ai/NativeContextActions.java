package io.mesalabs.rezoss.ai;

import android.content.Context;
import android.provider.Settings;
import java.util.List;

/** Keeps opt-in recall eligibility; action extraction always uses Samsung's SCS path. */
public final class NativeContextActions {
    private NativeContextActions() {}
    private static boolean enabled(Context context) throws Exception {
        return context != null && Settings.Global.getInt(context.getContentResolver(),
            "rezoss_now_nudge_context_actions", 0) == 1 &&
            (Boolean) Class.forName("io.mesalabs.rezoss.ai.NativeNowNudgeRoute")
                .getMethod("enabled", Context.class).invoke(null, context);
    }
    /** Explicit POC exception to the recall-action package blocklist, not the safety gate. */
    public static boolean allowRecall(Context context, String packageName) {
        if (!"com.whatsapp".equals(packageName) && !"com.whatsapp.w4b".equals(packageName)) return false;
        try { return enabled(context); }
        catch (Exception | LinkageError e) { return false; }
    }
    public static List<?> extract(String input, boolean chat, String user) {
        // Always continue through Samsung SCS/SSCO preprocessing and validation.
        // Compatible inference is supplied inside SSCO; never infer actions here.
        return null;
    }
}
