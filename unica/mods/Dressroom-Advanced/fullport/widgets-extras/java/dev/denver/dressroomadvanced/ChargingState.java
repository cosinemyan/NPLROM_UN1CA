// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

final class ChargingState {
    static int percent(int level, int scale) {
        if (level < 0 || scale <= 0) return -1;
        return (int) Math.min(100, Math.round(level * 100.0 / scale));
    }

    static float translation(float width, int direction, int percent) {
        float remaining = width * (1 - Math.max(0, Math.min(100, percent)) / 100f);
        return direction == 0 ? -remaining : remaining;
    }

    static void pause(Object animation) throws ReflectiveOperationException {
        Object nativeListeners = Reflect.field(animation, "mListeners");
        java.util.ArrayList<?> listeners =
                nativeListeners instanceof java.util.List<?> list
                        ? new java.util.ArrayList<>(list)
                        : new java.util.ArrayList<>();
        Reflect.call(animation, "removeAllListeners");
        try {
            Reflect.call(animation, "cancel");
        } finally {
            for (Object listener : listeners) Reflect.call(animation, "addListener", listener);
        }
    }
}
