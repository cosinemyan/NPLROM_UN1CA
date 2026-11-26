// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.os.Bundle;
import java.util.HashMap;
import java.util.Map;

/** Latest shared policy for each screen; orientation drafts can be older than the policy. */
final class WidgetDepthPolicies {
    private record Policy(int layouts, int depth) {}
    private final Map<Integer, Policy> screens = new HashMap<>();
    void clear() { screens.clear(); }
    boolean contains(int screen) { return screens.containsKey(screen); }
    void adopt(Bundle profile) {
        screens.put(ScreenIdentity.value(profile), new Policy(profile.getInt("depthLayouts"), profile.getInt("depth")));
    }
    void overlay(Bundle profile) {
        Policy policy = screens.get(ScreenIdentity.value(profile));
        if (policy == null) return;
        profile.putInt("depthLayouts", policy.layouts);
        if (policy.layouts != 0) profile.putInt("depth", policy.depth);
    }
    void clearDepth(int screen) {
        Policy policy = screens.get(screen);
        if (policy != null && policy.depth == 1) screens.put(screen, new Policy(policy.layouts, 2));
    }
}
