package com.eric.himawidgetbridge;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/** Prepare the phone radio only. Original app continues to own the vehicle connection. */
final class BluetoothPreparation {
    static final int PERMISSION_REQUEST = 1204, ENABLE_REQUEST = 1205;
    interface Completion { void done(boolean ready, String message); }
    interface Progress { void show(String message); }
    private static BluetoothPreparation owner;
    private final Activity activity;
    private final Completion completion;
    private final Progress progress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothPolicy policy;
    private boolean active, resumed, externalUi;
    private BluetoothPolicy.Action deferred;
    private final Runnable visibilityGuard = this::checkTaskVisibility;
    private final Runnable expiry = () -> {
        if (active) { policy.cancel(); finish(false, "蓝牙准备等待超时，请重新触发"); }
    };
    private final Runnable poll = this::pollRadio;
    private void pollRadio() {
        if (!active || !resumed || !policy.waiting()) return;
        if (!permitted(activity)) { finish(false, "附近设备权限已失效，本次触发已取消"); return; }
        handle(policy.poll(state(activity), SystemClock.elapsedRealtime()));
    }
    private void checkTaskVisibility() {
        if (!active) return;
        if (!resumed && Build.VERSION.SDK_INT >= 32 && !taskVisible()) {
            BluetoothPolicy.Action action = policy.taskVisibility(false);
            if (action == BluetoothPolicy.Action.FAIL) handle(action);
            else cancel(); // Includes a READY result deferred while the host is in the background.
            return;
        }
        handler.postDelayed(visibilityGuard, 300);
    }
    private boolean taskVisible() {
        if (Build.VERSION.SDK_INT < 32) return true;
        try {
            ActivityManager manager = activity.getSystemService(ActivityManager.class);
            if (manager != null) for (ActivityManager.AppTask task : manager.getAppTasks()) {
                ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                if (info.taskId == activity.getTaskId()) return info.isVisible();
            }
        } catch (RuntimeException ignored) {}
        return false;
    }
    BluetoothPreparation(Activity activity, Progress progress, Completion completion) {
        this.activity = activity; this.progress = progress; this.completion = completion;
    }
    static BluetoothAdapter adapter(Context context) {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        return manager == null ? null : manager.getAdapter();
    }
    static boolean permitted(Context context) {
        return Build.VERSION.SDK_INT < 31
                || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }
    static int state(Context context) {
        if (!permitted(context)) return -1;
        try { BluetoothAdapter adapter = adapter(context); return adapter == null ? -1 : adapter.getState(); }
        catch (RuntimeException e) { return -1; }
    }
    boolean readyNow() { return state(activity) == BluetoothAdapter.STATE_ON; }
    boolean busy() { return active; }
    static String lastSummary(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("bluetooth", Context.MODE_PRIVATE);
        String method = prefs.getString("method", "");
        if (method.isEmpty()) return "";
        String label = "auto".equals(method) ? "自动开启" : "system".equals(method) ? "系统确认"
                : "already_on".equals(method) ? "蓝牙原已开启" : "权限授权";
        return "上次蓝牙准备：" + label + " · " + (prefs.getBoolean("ready", false) ? "已就绪" : prefs.getString("message", "等待中"));
    }
    private void method(String value) {
        activity.getSharedPreferences("bluetooth", Context.MODE_PRIVATE).edit().putString("method", value).apply();
    }
    void start() {
        if (active) return;
        if (owner != null && owner != this) { completion.done(false, "另一次蓝牙准备仍在进行，请先完成或取消"); return; }
        owner = this; active = true; policy = new BluetoothPolicy();
        int initialState = state(activity);
        activity.getSharedPreferences("bluetooth", Context.MODE_PRIVATE).edit().clear()
                .putString("method", initialState == BluetoothAdapter.STATE_ON ? "already_on" : "pending")
                .putInt("initial_state", initialState).putBoolean("ready", false).putString("message", "等待中").apply();
        handler.postDelayed(expiry, 60000);
        handler.postDelayed(visibilityGuard, 300);
        boolean compatible = Build.VERSION.SDK_INT < 33 || activity.getApplicationInfo().targetSdkVersion < 33;
        handle(policy.start(adapter(activity) != null, permitted(activity), state(activity),
                compatible, SystemClock.elapsedRealtime()));
    }
    @SuppressWarnings("deprecation") // Public compatibility API for the user-approved target32 build.
    private void handle(BluetoothPolicy.Action action) {
        if (!active) return;
        if (!resumed && action != BluetoothPolicy.Action.FAIL) { deferred = action; return; }
        switch (action) {
            case PERMISSION:
                method("permission");
                progress.show("需要附近设备权限以准备蓝牙…"); externalUi = true;
                try { activity.requestPermissions(new String[] {Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST); }
                catch (RuntimeException e) { externalUi = false; finish(false, "无法请求蓝牙权限，本次触发已取消"); }
                break;
            case AUTO_ENABLE:
                method("auto");
                progress.show("正在开启手机蓝牙…");
                activity.getSharedPreferences("bluetooth", Context.MODE_PRIVATE).edit()
                        .putInt("auto_start_state", state(activity)).apply();
                boolean accepted;
                try { BluetoothAdapter adapter = adapter(activity); accepted = adapter != null && adapter.enable(); }
                catch (RuntimeException e) { accepted = false; }
                Log.i("HimaBluetoothPreparation", "auto_enable_request accepted=" + accepted);
                activity.getSharedPreferences("bluetooth", Context.MODE_PRIVATE).edit().putBoolean("auto_accepted", accepted).apply();
                handle(policy.enableResult(accepted, state(activity), SystemClock.elapsedRealtime()));
                break;
            case ENABLE_DIALOG:
                method("system");
                progress.show("请在系统提示中开启蓝牙…"); externalUi = true;
                Log.i("HimaBluetoothPreparation", "system_enable_confirmation_requested");
                try { activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION), ENABLE_REQUEST); }
                catch (RuntimeException e) { externalUi = false; finish(false, "无法打开蓝牙确认，请手动开启后重试"); }
                break;
            case READY: finish(true, "蓝牙已开启"); break;
            case FAIL: finish(false, policy.reason()); break;
            case NONE:
                if (policy.waiting()) {
                    progress.show("等待手机蓝牙开启…"); handler.removeCallbacks(poll); handler.postDelayed(poll, 200);
                }
                break;
        }
    }
    boolean permissionResult(int request) {
        if (request != PERMISSION_REQUEST) return false;
        if (active && externalUi) {
            externalUi = false;
            handle(policy.permissionResult(permitted(activity), state(activity), SystemClock.elapsedRealtime()));
        }
        return true;
    }
    boolean activityResult(int request, int result) {
        if (request != ENABLE_REQUEST) return false;
        if (active && externalUi) {
            externalUi = false;
            handle(policy.dialogResult(result == Activity.RESULT_OK, state(activity), SystemClock.elapsedRealtime()));
        }
        return true;
    }
    void onResume() {
        resumed = true;
        if (active) {
            BluetoothPolicy.Action action = deferred; deferred = null;
            if (action != null) handle(action);
            else if (!externalUi && policy.waiting()) handle(BluetoothPolicy.Action.NONE);
        }
    }
    void onPause() {
        resumed = false; handler.removeCallbacks(poll);
        if (active && !externalUi) cancel();
    }
    void onStop() {
        if (!active) return;
        if (Build.VERSION.SDK_INT < 32 || !taskVisible()) cancel();
    }
    void cancel() { if (active) { policy.cancel(); finish(false, "已取消蓝牙准备"); } }
    void close() {
        if (policy != null) policy.cancel();
        active = false; externalUi = false; deferred = null; handler.removeCallbacksAndMessages(null);
        if (owner == this) owner = null;
    }
    private void finish(boolean ready, String message) {
        activity.getSharedPreferences("bluetooth", Context.MODE_PRIVATE).edit()
                .putBoolean("ready", ready).putInt("final_state", state(activity)).putString("message", message).apply();
        close(); Log.i("HimaBluetoothPreparation", "preparation_finished ready=" + ready);
        completion.done(ready, message);
    }
}
