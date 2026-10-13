package com.eric.himawidgetbridge;

public final class BluetoothPolicyTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message); checks++;
    }
    public static void main(String[] args) {
        BluetoothPolicy on = new BluetoothPolicy();
        check(on.start(true, true, BluetoothPolicy.ON, true, 1000) == BluetoothPolicy.Action.READY, "already on proceeds");
        check(on.poll(BluetoothPolicy.ON, 1001) == BluetoothPolicy.Action.NONE, "success cannot dispatch twice");
        BluetoothPolicy off = new BluetoothPolicy();
        check(off.start(true, true, BluetoothPolicy.OFF, true, 1000) == BluetoothPolicy.Action.AUTO_ENABLE, "off requests automatic enable");
        check(off.enableResult(true, BluetoothPolicy.TURNING_ON, 1100) == BluetoothPolicy.Action.NONE, "accepted enable is not yet on");
        check(off.poll(BluetoothPolicy.ON, 2000) == BluetoothPolicy.Action.READY, "observed on proceeds");
        BluetoothPolicy denied = new BluetoothPolicy();
        check(denied.start(true, false, -1, true, 1000) == BluetoothPolicy.Action.PERMISSION, "missing permission requests authorization");
        check(denied.permissionResult(false, -1, 1100) == BluetoothPolicy.Action.FAIL, "permission denial ends flow");
        check(denied.poll(BluetoothPolicy.ON, 2000) == BluetoothPolicy.Action.NONE, "late state cannot revive denied request");
        BluetoothPolicy grant = new BluetoothPolicy();
        grant.start(true, false, -1, true, 1000);
        check(grant.permissionResult(true, BluetoothPolicy.OFF, 1100) == BluetoothPolicy.Action.AUTO_ENABLE, "grant resumes off request");
        check(grant.enableResult(false, BluetoothPolicy.OFF, 1200) == BluetoothPolicy.Action.ENABLE_DIALOG, "automatic refusal falls back to confirmation");
        check(grant.dialogResult(true, BluetoothPolicy.ON, 2000) == BluetoothPolicy.Action.READY, "confirmed and on proceeds");
        check(grant.dialogResult(true, BluetoothPolicy.ON, 2100) == BluetoothPolicy.Action.NONE, "duplicate confirmation cannot repeat dispatch");
        BluetoothPolicy reject = new BluetoothPolicy();
        check(reject.start(true, true, BluetoothPolicy.OFF, false, 1000) == BluetoothPolicy.Action.ENABLE_DIALOG, "modern mode uses system confirmation");
        check(reject.dialogResult(false, BluetoothPolicy.ON, 2000) == BluetoothPolicy.Action.FAIL, "cancel ends even if another app enabled radio");
        BluetoothPolicy timeout = new BluetoothPolicy();
        timeout.start(true, true, BluetoothPolicy.OFF, true, 1000);
        timeout.enableResult(true, BluetoothPolicy.OFF, 1100);
        check(timeout.poll(BluetoothPolicy.OFF, 13099) == BluetoothPolicy.Action.NONE, "pending enable stays pending before deadline");
        check(timeout.poll(BluetoothPolicy.OFF, 13100) == BluetoothPolicy.Action.FAIL, "pending enable times out");
        BluetoothPolicy transition = new BluetoothPolicy();
        check(transition.start(true, true, BluetoothPolicy.TURNING_OFF, true, 1000) == BluetoothPolicy.Action.NONE, "wait for shutdown transition");
        check(transition.poll(BluetoothPolicy.OFF, 2000) == BluetoothPolicy.Action.AUTO_ENABLE, "enable once shutdown completes");
        transition.cancel();
        check(transition.enableResult(true, BluetoothPolicy.ON, 2100) == BluetoothPolicy.Action.NONE, "cancel ignores delayed enable result");
        check(new BluetoothPolicy().start(false, true, -1, true, 1000) == BluetoothPolicy.Action.FAIL, "no adapter ends request");
        BluetoothPolicy away = new BluetoothPolicy();
        away.start(true, true, BluetoothPolicy.OFF, false, 1000);
        check(away.taskVisibility(false) == BluetoothPolicy.Action.FAIL, "leaving system prompt task cancels old trigger");
        check(away.dialogResult(true, BluetoothPolicy.ON, 2000) == BluetoothPolicy.Action.NONE, "returning after Home cannot revive old trigger");
        BluetoothPolicy visible = new BluetoothPolicy();
        visible.start(true, false, -1, true, 1000);
        check(visible.taskVisibility(true) == BluetoothPolicy.Action.NONE, "visible permission task remains valid");
        check(visible.taskVisibility(false) == BluetoothPolicy.Action.FAIL, "leaving permission task cancels authorization continuation");
        check(visible.permissionResult(true, BluetoothPolicy.ON, 2000) == BluetoothPolicy.Action.NONE, "late permission grant cannot continue canceled trigger");
        System.out.println("BluetoothPolicyTest: " + checks + " checks passed");
    }
}
