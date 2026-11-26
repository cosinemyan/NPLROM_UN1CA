/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.rezoss.memory;

import android.app.Activity;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.os.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import com.samsung.android.libaihub.aicore.prompt.*;

/** Exact Memory 1.0.00.48 bridge; raw results go through its existing parser. */
public final class MemoryBackend {
    public static final String MODEL = "UN1CA_AI_BACKEND";
    public static final int MODEL_ID = 0x00ee0001;
    private static final String PREFS = "com.samsung.android.app.develop_preferences";
    private static final Object UNIT = n20.b0.a;
    private static final Object SUSPENDED = s20.a.COROUTINE_SUSPENDED;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private MemoryBackend() {}

    public static String model(Context context) {
        if (context == null) return MODEL;
        SharedPreferences p = context.getSharedPreferences(PREFS, 0);
        synchronized (MemoryBackend.class) {
            if (!p.getBoolean("un1ca_memory_default_v1", false)) {
                // One migration chooses the requested new default; later explicit selections persist.
                p.edit().putString("preferences_analysis_model", MODEL)
                    .putBoolean("preferences_analysis_model_scs_default_migrated", true)
                    .putBoolean("un1ca_memory_default_v1", true).apply();
            }
        }
        return p.getString("preferences_analysis_model", MODEL);
    }

    public static int[] appendId(int[] source) {
        int[] out = Arrays.copyOf(source, source.length + 1);
        out[source.length] = MODEL_ID;
        return out;
    }

    public static void addModel(Activity activity) {
        RadioGroup group = activity.findViewById(0x7f0a0090);
        if (group == null || group.findViewById(MODEL_ID) != null) return;
        // Reuse Samsung's inflated RadioButton style, padding, typeface and sizing.
        RadioButton template = null;
        for (int i = 0; i < group.getChildCount(); i++) {
            if (group.getChildAt(i) instanceof RadioButton) {
                template = (RadioButton) group.getChildAt(i); break;
            }
        }
        RadioButton button = new RadioButton(group.getContext());
        button.setId(MODEL_ID);
        button.setText("UN1CA Ai Backend");
        if (template != null) {
            button.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, template.getTextSize());
            button.setTypeface(template.getTypeface());
            button.setTextColor(template.getTextColors());
            button.setPadding(template.getPaddingLeft(), template.getPaddingTop(),
                    template.getPaddingRight(), template.getPaddingBottom());
            button.setMinHeight(template.getMinHeight());
            button.setButtonTintList(template.getButtonTintList());
        }
        group.addView(button, new RadioGroup.LayoutParams(-1, -2));
    }

    static boolean absentSpac(Context context, String model) {
        if (!model.startsWith("SCS_SPAC_")) return false;
        Intent query = new Intent("android.intellivoiceservice.SpacClientService").setPackage("com.samsung.android.intellivoiceservice");
        List<ResolveInfo> services = context.getPackageManager().queryIntentServices(query, 0);
        return services == null || services.isEmpty();
    }

    public static Object request(li.w original, Context context, di.c request,
            ci.a callback, PromptType type, t20.c continuation) {
        PromptType selected = type == null ? li.w.k(context, request) : type;
        if (!supported(selected.name())) {
            // Insight/query tasks have different response contracts and keep their existing path.
            return original.memoryCloud(context, request, callback, type, continuation);
        }
        String selection = model(context);
        if (MODEL.equals(selection) || absentSpac(context, selection)) {
            return local(context, request, callback, type, continuation);
        }
        return original.memoryCloud(context, request,
                new Fallback(context, request, callback, type), type, continuation);
    }

    static boolean supported(String type) {
        return "EXTRACT_MEMORY_SUMMARY_COLLECTION_PROMPT".equals(type) ||
                "EXTRACT_MEMORY_FULL_PROMPT".equals(type) ||
                "EXTRACT_MEMORY_SUMMARY_PROMPT".equals(type) ||
                "EXTRACT_MEMORY_COLLECTION_PROMPT".equals(type);
    }

    static Object local(Context context, di.c request, ci.a callback,
            PromptType type, r20.c completion) {
        State state = new State(context.getApplicationContext(), request, callback, type, completion);
        return state.invokeSuspend(UNIT);
    }

    static final class Fallback implements ci.a {
        final Context context; final di.c request; final ci.a target; final PromptType type;
        final AtomicBoolean attempted = new AtomicBoolean();
        final AtomicBoolean terminal = new AtomicBoolean();
        volatile String lastContent;
        Fallback(Context c, di.c r, ci.a t, PromptType p) { context=c; request=r; target=t; type=p; }
        public Object a(t20.i continuation) {
            if (attempted.get() || !terminal.compareAndSet(false, true)) return UNIT;
            // Commit the final cloud result only after success. A failed partial stream
            // must not persist fields before the configured backend retries the item.
            if (lastContent == null) return target.a(continuation);
            State delivery = new State(context, request, target, type, continuation);
            delivery.step = 1;
            return delivery.invokeSuspend(lastContent);
        }
        public Object b(String content, r20.c continuation) {
            if (!attempted.get() && !terminal.get()) lastContent = content;
            return UNIT;
        }
        public Object c(String content, li.h continuation) {
            return attempted.get() || terminal.get() ? UNIT : target.c(content, continuation);
        }
        public Object e(String content, t20.i continuation) {
            return attempted.get() || terminal.get() ? UNIT : target.e(content, continuation);
        }
        public Object d(Throwable error, t20.i continuation) {
            if (terminal.get() || attempted.get()) return UNIT;
            // A scoped request timeout can retry only while its caller is active.
            w50.e0.l(continuation.getContext());
            if (MemoryFailurePolicy.eligible(error)) {
                if (!attempted.compareAndSet(false, true) || terminal.get()) return UNIT;
                lastContent = null;
                android.util.Log.i("Un1caMemory", "Cloud unavailable; using configured Memory backend once");
                return local(context, request, target, type, continuation);
            }
            return terminal.compareAndSet(false, true) ? target.d(error, continuation) : UNIT;
        }
    }

    /** A real suspend continuation: callback parsing/completion may themselves suspend. */
    static final class State extends t20.i implements ServiceConnection {
        final Context context; final di.c request; final ci.a target; final PromptType type;
        final AtomicBoolean terminal = new AtomicBoolean();
        Messenger remote, reply;
        boolean bound;
        int step;
        long deadline;
        String envelope;
        final Runnable poll = new Runnable() { public void run() {
            if (terminal.get()) return;
            try { w50.e0.l(getContext()); }
            catch (CancellationException error) { finish(null, error); return; }
            if (SystemClock.elapsedRealtime() >= deadline) {
                finish(null, new java.util.concurrent.TimeoutException("Memory backend timed out"));
            } else MAIN.postDelayed(this, 250);
        }};
        State(Context c, di.c r, ci.a t, PromptType p, r20.c completion) {
            super(1, completion); context=c; request=r; target=t; type=p;
        }
        public Object invokeSuspend(Object result) {
            if (step == 0) {
                step = 1;
                // Always schedule to avoid resuming the continuation before suspension returns.
                MAIN.post(new Runnable() { public void run() { start(); }});
                return SUSPENDED;
            }
            if (step == 1) {
                try {
                    dt.a.K(result);
                    w50.e0.l(getContext());
                } catch (CancellationException error) { throw error; }
                catch (Throwable error) {
                    step = 4;
                    Object value = target.d(error, this);
                    return value == SUSPENDED ? value : UNIT;
                }
                step = 2;
                Object value = target.b((String) result, this);
                if (value == SUSPENDED) return value;
                result = UNIT;
            }
            if (step == 2) {
                dt.a.K(result);
                w50.e0.l(getContext());
                step = 3;
                Object value = target.a(this);
                if (value == SUSPENDED) return value;
                result = UNIT;
            }
            dt.a.K(result);
            return UNIT;
        }
        void start() {
            try {
                w50.e0.l(getContext());
                PromptType selected = type == null ? li.w.k(context, request) : type;
                // Samsung splits this combined task into summary then collection. Our single
                // response uses its equivalent full prompt so category is actually requested.
                IPrompt prompt = "EXTRACT_MEMORY_SUMMARY_COLLECTION_PROMPT".equals(selected.name())
                        ? new ExtractMemoryFullPrompt() : selected.createPrompt();
                prompt.setInputText(request.c());
                prompt.setComponentCatalog(request.e());
                // Text-only bridge: never tell the model it saw the screenshot/video.
                prompt.setHasImage(false);
                prompt.setImageCollage(false);
                String language = qi.c.b(context).getDisplayName(Locale.ENGLISH);
                prompt.setTargetLanguageOverride(language);
                prompt.init(context);
                if (request.c().trim().isEmpty())
                    throw new IllegalArgumentException("Memory local analysis needs OCR, transcription or memo text; image-only analysis is unsupported");
                JSONObject data = new JSONObject();
                data.put("system_instruction", prompt.getSystemInstruction());
                data.put("prompt", prompt.getPrompt());
                data.put("text", request.c());
                data.put("language", language);
                data.put("prompt_type", selected.name());
                envelope = data.toString();
                if (envelope.length() > 100000) throw new IllegalArgumentException("Memory request exceeds 100000 characters");
                deadline = SystemClock.elapsedRealtime() + 120000L;
                reply = new Messenger(new Handler(Looper.getMainLooper()) {
                    public void handleMessage(Message message) {
                        Bundle data = message.getData();
                        if (message.what == 1 && "generated".equals(data.getString("status"))) {
                            String content = data.getString("content");
                            if (content == null || content.trim().isEmpty()) finish(null, new IllegalStateException("Memory backend returned no JSON"));
                            else finish(content, null);
                        } else finish(null, new IllegalStateException("Memory backend failed: " + data.getString("status", "unavailable")));
                    }
                });
                Intent intent = new Intent().setClassName("com.samsung.android.aioskernelservice",
                        "io.mesalabs.rezoss.nudge.LocalInferenceService");
                bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE);
                if (!bound) throw new IllegalStateException("UN1CA Memory backend service unavailable");
                MAIN.post(poll);
                android.util.Log.i("Un1caMemory", "UN1CA Ai Backend text request; prompt=" + selected.name());
            } catch (Throwable error) { finish(null, error); }
        }
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (terminal.get()) return;
            remote = new Messenger(binder);
            try {
                Message message = Message.obtain(null, 1);
                Bundle data = new Bundle();
                data.putString("task", "memory"); data.putString("input", envelope);
                data.putLong("deadline", deadline); message.setData(data); message.replyTo = reply;
                remote.send(message);
            } catch (Throwable error) { finish(null, error); }
        }
        public void onServiceDisconnected(ComponentName name) { finish(null, new IllegalStateException("Memory backend disconnected")); }
        public void onNullBinding(ComponentName name) { finish(null, new IllegalStateException("Memory backend null binding")); }
        public void onBindingDied(ComponentName name) { finish(null, new IllegalStateException("Memory backend binding died")); }
        void finish(String content, Throwable error) {
            if (!terminal.compareAndSet(false, true)) return;
            MAIN.removeCallbacks(poll);
            if (error != null && remote != null) {
                try { Message cancel = Message.obtain(null, 2); cancel.replyTo = reply; remote.send(cancel); }
                catch (RemoteException ignored) { }
            }
            if (bound) { bound = false; try { context.unbindService(this); } catch (IllegalArgumentException ignored) {} }
            resumeWith(error == null ? content : dt.a.g(error));
        }
    }
}
