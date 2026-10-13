package com.eric.himawidgetbridge;

final class BluetoothPolicy {
    static final int OFF = 10, TURNING_ON = 11, ON = 12, TURNING_OFF = 13;
    enum Action { NONE, PERMISSION, AUTO_ENABLE, ENABLE_DIALOG, READY, FAIL }
    private enum Stage { NEW, PERMISSION, AUTO, DIALOG, WAIT, DONE }
    private Stage stage = Stage.NEW;
    private boolean compatible, attempted;
    private long deadline;
    private String reason = "";
    Action start(boolean present, boolean permitted, int radio, boolean compatible, long now) {
        if (stage != Stage.NEW) return Action.NONE;
        this.compatible = compatible;
        if (!present) return fail("此手机没有可用的蓝牙，本次触发已取消");
        if (!permitted) { stage = Stage.PERMISSION; return Action.PERMISSION; }
        return choose(radio, now);
    }
    Action permissionResult(boolean granted, int radio, long now) {
        if (stage != Stage.PERMISSION) return Action.NONE;
        return granted ? choose(radio, now) : fail("未授权附近设备，本次触发已取消");
    }
    Action enableResult(boolean accepted, int radio, long now) {
        if (stage != Stage.AUTO) return Action.NONE;
        if (radio == ON) return ready();
        if (accepted || radio == TURNING_ON) return waitForRadio(now);
        stage = Stage.DIALOG; return Action.ENABLE_DIALOG;
    }
    Action dialogResult(boolean accepted, int radio, long now) {
        if (stage != Stage.DIALOG) return Action.NONE;
        if (!accepted) return fail("未开启蓝牙，本次触发已取消");
        return radio == ON ? ready() : waitForRadio(now);
    }
    Action poll(int radio, long now) {
        if (stage != Stage.WAIT) return Action.NONE;
        if (radio == ON) return ready();
        if (now >= deadline) return fail("蓝牙开启超时，本次触发已取消");
        if (radio == OFF && !attempted) return choose(radio, now);
        return Action.NONE;
    }
    private Action choose(int radio, long now) {
        if (radio == ON) return ready();
        if (radio == OFF) {
            attempted = true;
            stage = compatible ? Stage.AUTO : Stage.DIALOG;
            return compatible ? Action.AUTO_ENABLE : Action.ENABLE_DIALOG;
        }
        if (radio == TURNING_ON || radio == TURNING_OFF) return waitForRadio(now);
        return fail("无法读取蓝牙状态，本次触发已取消");
    }
    private Action waitForRadio(long now) { stage = Stage.WAIT; deadline = now + 12000; return Action.NONE; }
    private Action ready() { stage = Stage.DONE; return Action.READY; }
    private Action fail(String text) { reason = text; stage = Stage.DONE; return Action.FAIL; }
    boolean waiting() { return stage == Stage.WAIT; }
    String reason() { return reason; }
    Action taskVisibility(boolean visible) {
        if (!visible && stage != Stage.NEW && stage != Stage.DONE)
            return fail("已离开蓝牙准备界面，本次触发已取消");
        return Action.NONE;
    }
    void cancel() { stage = Stage.DONE; }
}
