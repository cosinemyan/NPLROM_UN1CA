package dev.denver.dressroomadvanced;

import android.graphics.Matrix;
import android.view.View;

import java.util.Arrays;

final class ViewGeometry {
    private final Matrix matrix = new Matrix();
    private final float[] previous = new float[9], current = new float[9];
    private int width = -1, height = -1;
    private Object subject, cover;

    boolean update(View view, Object nextSubject, Object nextCover) {
        matrix.reset();
        view.transformMatrixToGlobal(matrix);
        matrix.getValues(current);
        if (width == view.getWidth()
                && height == view.getHeight()
                && subject == nextSubject
                && cover == nextCover
                && Arrays.equals(previous, current)) return false;
        width = view.getWidth();
        height = view.getHeight();
        subject = nextSubject;
        cover = nextCover;
        System.arraycopy(current, 0, previous, 0, 9);
        return true;
    }
}
