package com.eric.himawidgetbridge;

public final class TriggerGate {
    private long lastStart = -1;
    private long busyUntil;
    private String reason = "";
    public synchronized boolean tryStart(boolean enabled, boolean bound,
            boolean adapterKnown, boolean controlReady, long now) {
        if (!enabled) return deny("请先在配置页启用快捷触发");
        if (!bound) return deny("请先绑定鸿蒙智行小组件");
        if (!adapterKnown) return deny("当前鸿蒙智行版本尚未适配");
        if (!controlReady) return deny("原版小组件中没有可用的操作按钮");
        if (lastStart >= 0 && now < lastStart) return deny("设备计时发生变化，请稍后重试");
        int remaining = remainingSeconds(now);
        if (remaining > 0) return deny("防重复保护，请在 " + remaining + " 秒后重试");
        lastStart = now;
        busyUntil = now + 10000;
        reason = "";
        return true;
    }
    private boolean deny(String text) { reason = text; return false; }
    public synchronized String getReason() { return reason; }
    public synchronized int remainingSeconds(long now) {
        if (lastStart < 0) return 0;
        long until = Math.max(busyUntil, lastStart + 3000);
        return (int) Math.min(10, (Math.max(0, until - now) + 999) / 1000);
    }
    public synchronized void cancel() { busyUntil = 0; }
}
