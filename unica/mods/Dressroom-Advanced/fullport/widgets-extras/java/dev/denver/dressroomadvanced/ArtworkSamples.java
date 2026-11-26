// Native adaptation of BlueOutside / Dressroom-Advanced, revision 5bb6652.
// SPDX-License-Identifier: LicenseRef-Dressroom-Advanced-Source-Available-1.0
package dev.denver.dressroomadvanced;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;

final class ArtworkSamples {
    private static final double[] linear = new double[256];

    static {
        for (int i = 0; i < 256; i++) {
            double v = i / 255d;
            linear[i] = v <= .04045 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4);
        }
    }

    private final int width, height;
    private final int[] pixels;

    private ArtworkSamples(Bitmap image) {
        width = image.getWidth();
        height = image.getHeight();
        pixels = new int[width * height];
        image.getPixels(pixels, 0, width, 0, 0, width, height);
    }

    static ArtworkSamples read(Bitmap image) {
        return new ArtworkSamples(image);
    }

    double sample(RectF cover, Rect bounds) {
        double sum = 0;
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 8; x++) {
                int px =
                        Math.max(
                                0,
                                Math.min(
                                        width - 1,
                                        (int)
                                                ((bounds.left
                                                                + bounds.width() * (x + .5f) / 8
                                                                - cover.left)
                                                        / cover.width()
                                                        * width)));
                int py =
                        Math.max(
                                0,
                                Math.min(
                                        height - 1,
                                        (int)
                                                ((bounds.top
                                                                + bounds.height() * (y + .5f) / 4
                                                                - cover.top)
                                                        / cover.height()
                                                        * height)));
                int color = pixels[py * width + px];
                sum =
                        .2126 * linear[(color >> 16) & 255]
                                + .7152 * linear[(color >> 8) & 255]
                                + .0722 * linear[color & 255]
                                + sum;
            }
        return sum / 32;
    }
}
