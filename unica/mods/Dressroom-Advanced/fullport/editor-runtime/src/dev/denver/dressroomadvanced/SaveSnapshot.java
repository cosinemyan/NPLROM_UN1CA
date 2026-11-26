package dev.denver.dressroomadvanced;

final class SaveSnapshot<T> {
    private T frozen;

    synchronized void begin(T immutableSnapshot) {
        frozen = immutableSnapshot;
    }

    synchronized T complete() {
        T value = frozen;
        frozen = null;
        return value;
    }

    synchronized void cancel() {
        frozen = null;
    }
}
