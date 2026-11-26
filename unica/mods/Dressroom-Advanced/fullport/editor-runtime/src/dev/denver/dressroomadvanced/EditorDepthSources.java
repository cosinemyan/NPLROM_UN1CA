// Native adaptation of BlueOutside / Dressroom Advanced. See LICENSE.upstream.
package dev.denver.dressroomadvanced;

import android.os.Bundle;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Source identity and invalidation belong to a screen, not the editor's current posture. */
final class EditorDepthSources {
    static final class State {
        final DepthSourcePolicy source = new DepthSourcePolicy();
        boolean known, image, reset;
        long revision;
        void invalidate() { revision++; reset = true; }
        String imageKey() {
            String key = source.key();
            return key == null ? "" : DepthStorage.hash(key.getBytes(StandardCharsets.UTF_8));
        }
    }
    private final Map<Integer, State> screens = new HashMap<>();
    State screen(int screen) { return screens.computeIfAbsent(screen, ignored -> new State()); }
    void clear() { screens.clear(); }
    static void forScreen(Iterable<Bundle> profiles, int screen, Consumer<Bundle> action) {
        for (Bundle profile : profiles)
            if (profile != null && ScreenIdentity.value(profile) == screen) action.accept(profile);
    }
    void stamp(Bundle snapshot) {
        var layouts = snapshot.getParcelableArrayList("layoutProfiles", Bundle.class);
        if (layouts != null) {
            for (Bundle layout : layouts) stamp(layout);
            return;
        }
        State state = screens.get(ScreenIdentity.value(snapshot));
        if (state != null && state.known) snapshot.putString("depthImageKey", state.imageKey());
    }
}
