package com.eric.himawidgetbridge;

/** A one-shot readiness decision; connection is a hint, not a car-control result. */
public final class KeyReadinessPolicy {
    public static final int UNKNOWN = 0, ABSENT = 1, DISCONNECTED = 2, CONNECTED = 3;
    public static final int NONE = 0, WAIT = 1, PROCEED = 2, PREWARM = 3, FAIL = 4;
    public static final long WAIT_MILLIS = 10000;
    private boolean started, finished;
    private long deadline;

    public int start(int state, long now) {
        return start(state, false, now);
    }
    public int start(int state, boolean prepared, long now) {
        if (started || finished) return NONE;
        started = true; deadline = now + WAIT_MILLIS;
        return decide(state, prepared, now);
    }
    public int poll(int state, long now) {
        return poll(state, false, now);
    }
    public int poll(int state, boolean prepared, long now) {
        return !started || finished ? NONE : decide(state, prepared, now);
    }
    private int decide(int state, boolean prepared, long now) {
        int action = state == UNKNOWN ? FAIL : state == ABSENT ? PREWARM
                : (state == CONNECTED || state == DISCONNECTED) && !prepared ? PREWARM
                : state == CONNECTED ? PROCEED
                : state == DISCONNECTED ? (now >= deadline ? PROCEED : WAIT) : FAIL;
        if (action != WAIT) finished = true;
        return action;
    }
    public void cancel() { finished = true; }
    public static int classify(String title, String body) {
        if (title == null || !title.contains("数字车钥匙")) return ABSENT;
        if (body == null) return UNKNOWN;
        boolean connected = body.contains("已连接"), disconnected = body.contains("未连接");
        if (connected == disconnected) return UNKNOWN;
        return connected ? CONNECTED : DISCONNECTED;
    }
}
