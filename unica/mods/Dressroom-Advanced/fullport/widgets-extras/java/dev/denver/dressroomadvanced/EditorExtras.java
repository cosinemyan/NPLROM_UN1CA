// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.content.Context;
import android.os.Bundle;

final class EditorExtras {
    private Context context;
    private Bundle draft;
    private boolean dirty;
    private final java.util.Map<Integer, Bundle> drafts = new java.util.HashMap<>();
    private final java.util.Set<Integer> changed = new java.util.HashSet<>();

    Bundle settings(Context context) {
        this.context = context.getApplicationContext();
        int screen = ScreenIdentity.current();
        if (draft == null || ScreenIdentity.value(draft) != screen) {
            draft = drafts.get(screen);
            if (draft == null) draft = ExtrasPreferences.read(context);
            dirty = changed.contains(screen);
        }
        return new Bundle(draft);
    }

    void update(Context context, Bundle b) {
        settings(context);
        if (b.containsKey(ScreenIdentity.FIELD) && ScreenIdentity.value(b) != ScreenIdentity.current())
            throw new IllegalStateException("Reopen Extras after switching screens.");
        draft = ExtrasPreferences.clean(b);
        ScreenIdentity.stamp(draft);
        dirty = true;
        drafts.put(ScreenIdentity.value(draft), new Bundle(draft));
        changed.add(ScreenIdentity.value(draft));
    }

    Bundle freeze() {
        if (changed.isEmpty()) return null;
        java.util.ArrayList<Bundle> screens = new java.util.ArrayList<>();
        for (int screen : changed) screens.add(new Bundle(drafts.get(screen)));
        Bundle batch = new Bundle();
        batch.putParcelableArrayList("screenProfiles", screens);
        return batch;
    }

    void commit(Bundle b) {
        if (context != null) {
            ExtrasPreferences.commit(context, b);
            committed(b);
        }
    }

    void committed(Bundle saved) {
        if (saved == null) return;
        java.util.ArrayList<Bundle> screens = saved.getParcelableArrayList("screenProfiles", Bundle.class);
        if (screens != null) { for (Bundle screen : screens) committed(screen); return; }
        Bundle pending = drafts.get(ScreenIdentity.value(saved));
        if (same(pending, saved)) changed.remove(ScreenIdentity.value(saved));
        if (draft == null || saved == null) return;
        if (ScreenIdentity.value(draft) != ScreenIdentity.value(saved)) return;
        for (String key : ExtrasPreferences.FLAGS)
            if (draft.getBoolean(key) != saved.getBoolean(key)) return;
        if (draft.getInt("stackCards", 3) != saved.getInt("stackCards", 3)) return;
        if (!draft.getString("title", "").equals(saved.getString("title", ""))
                || !draft.getString("message", "").equals(saved.getString("message", ""))) return;
        dirty = false;
        changed.remove(ScreenIdentity.value(saved));
    }

    void reset() {
        context = null;
        draft = null;
        dirty = false;
        drafts.clear();
        changed.clear();
    }

    private static boolean same(Bundle a, Bundle b) {
        if (a == null || b == null) return false;
        for (String key : ExtrasPreferences.FLAGS)
            if (a.getBoolean(key) != b.getBoolean(key)) return false;
        return a.getInt("stackCards", 3) == b.getInt("stackCards", 3)
                && a.getString("title", "").equals(b.getString("title", ""))
                && a.getString("message", "").equals(b.getString("message", ""));
    }
}
