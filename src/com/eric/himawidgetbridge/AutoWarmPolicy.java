package com.eric.himawidgetbridge;

/** One explicit request may return once; it never dispatches a vehicle command. */
public final class AutoWarmPolicy {
    public static final int HELPER = 0, ENTRY = 1, MAIN = 2, OTHER = 3, UNKNOWN = 4;
    public static final int NONE = 0, WAIT = 1, RETURN = 2, READY = 3, FAIL = 4;
    private boolean started, finished, returning, hadKey, departed;
    private long deadline, mainAt = -1, returnDeadline, focusedAt = -1;
    public int start(long now) {
        if (started || finished) return NONE;
        started = true; deadline = now + 12000; return WAIT;
    }
    public int poll(int front, boolean safe, int key, boolean prepared, long observedMainAt, boolean focused, long now) {
        if (!started || finished) return NONE;
        if (!safe || front == OTHER || front == UNKNOWN || key == KeyReadinessPolicy.UNKNOWN) return fail();
        if (front == ENTRY || front == MAIN) departed = true;
        if (mainAt < 0 && observedMainAt >= 0) mainAt = observedMainAt;
        if (key != KeyReadinessPolicy.ABSENT && !prepared) return fail();
        if (prepared && key != KeyReadinessPolicy.ABSENT) hadKey = true;
        if (hadKey && key == KeyReadinessPolicy.ABSENT) return fail();
        if (returning) {
            if (now >= returnDeadline) return fail();
            if (front == HELPER && focused) {
                if (focusedAt < 0) focusedAt = now;
                if (now - focusedAt >= 300) { finished = true; return READY; }
            } else focusedAt = -1;
            return WAIT;
        }
        if (now >= deadline || (departed || mainAt >= 0) && front == HELPER
                || mainAt >= 0 && front == ENTRY) return fail();
        if (front == MAIN && mainAt >= 0 && now - mainAt >= 2000 && prepared && key != KeyReadinessPolicy.ABSENT) {
            returning = true; returnDeadline = now + 1500; return RETURN;
        }
        return WAIT;
    }
    private int fail() { finished = true; return FAIL; }
    public void cancel() { finished = true; }
}
