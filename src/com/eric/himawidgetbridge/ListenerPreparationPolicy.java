package com.eric.himawidgetbridge;

/** Bounded, cancellable preparation of the listener transport, separate from car readiness. */
public final class ListenerPreparationPolicy {
    public static final int NONE = 0, REBIND = 1, WAIT = 2, READY = 3, FAIL = 4;
    public static final long WAIT_MILLIS = 8000;
    private boolean started, finished;
    private long deadline;
    private String reason = "";
    public int start(boolean granted, boolean ready, long now) {
        if (started || finished) return NONE;
        started = true; deadline = now + WAIT_MILLIS;
        if (!granted) return fail("请在主页开启通知使用权");
        if (ready) { finished = true; return READY; }
        return REBIND;
    }
    public int poll(boolean granted, boolean ready, boolean foreground, long now) {
        if (!started || finished) return NONE;
        if (!granted) return fail("通知使用权已失效，本次准备已取消");
        if (!foreground) return fail("已离开准备界面，本次触发已取消");
        if (now >= deadline) return fail("通知监听未恢复，请关闭再开启通知使用权后重试");
        if (ready) { finished = true; return READY; }
        return WAIT;
    }
    private int fail(String value) { finished = true; reason = value; return FAIL; }
    public String reason() { return reason; }
    public void cancel() { finished = true; }
}
