package dev.thor.fnaf2;

import java.util.Arrays;

/** Per-condition elapsed-time timers. Inactive conditions retain their remainder. */
final class EventTimers {
    private final double[] remaining = new double[800];
    private final boolean[] initialized = new boolean[800];
    void reset() { Arrays.fill(initialized, false); }
    boolean every(int event, double interval, double delta) {
        if (!initialized[event]) {
            initialized[event] = true;
            remaining[event] = interval;
            return false;
        }
        remaining[event] -= delta;
        if (remaining[event] > 1e-9) return false;
        remaining[event] += interval;
        return true;
    }
}
