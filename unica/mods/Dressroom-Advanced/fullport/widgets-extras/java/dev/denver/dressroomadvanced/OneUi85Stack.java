// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

/** Clock Style on the P613. The 8.5 AOD provider needs its own checked mapping. */
final class OneUi85Stack {
    private OneUi85Stack() {}

    static NowBarStackBinding.Names names() {
        return new NowBarStackBinding.Names(
                "aod.Io0",
                "aod.Ak",
                "aod.LT0",
                "aod.No0",
                "aod.o71",
                "B",
                "b",
                "I",
                "F",
                "H",
                "b0",
                "e0",
                "h0",
                "N",
                "O",
                "b",
                "d",
                false,
                "P",
                "o",
                null,
                "com.samsung.android.app.clockpack");
    }
}
