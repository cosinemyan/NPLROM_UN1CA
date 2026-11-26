// Native adaptation of BlueOutside / Dressroom-Advanced.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

/** Grid-cell geometry shared by preview gestures and native ModuleInfo persistence. */
final class WidgetCellBounds {
    static int[] fit(int x,int y,int width,int height,int columns,int rows) {
        int w=Math.max(1,Math.min(columns,width)),h=Math.max(1,Math.min(rows,height));
        return new int[]{Math.max(0,Math.min(columns-w,x)),Math.max(0,Math.min(rows-h,y)),w,h};
    }
    static boolean overlaps(int[] a,int[] b) {
        return a[0]<b[0]+b[2]&&a[0]+a[2]>b[0]&&a[1]<b[1]+b[3]&&a[1]+a[3]>b[1];
    }
}
