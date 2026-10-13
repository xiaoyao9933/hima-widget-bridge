package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.function.BooleanSupplier;

/** Opens ordinary MAIN, observes cancellation, returns to this instance, and completes once. */
final class PrewarmController {
    interface Completion { void done(boolean returned, String message); }
    interface Progress { void show(String message); }
    private static final OperationLease OWNERS = new OperationLease();
    private final Activity activity;
    private final BooleanSupplier safe;
    private final Completion completion;
    private final Progress progress;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AutoWarmPolicy policy;
    private ForegroundUsage.Observer observer;
    private boolean active, returned;
    private long launchToken;
    PrewarmController(Activity activity, BooleanSupplier safe, Progress progress, Completion completion) {
        this.activity = activity; this.safe = safe; this.progress = progress; this.completion = completion;
    }
    boolean busy() { return active; }
    boolean returned() { return returned; }
    boolean continuationAllowed() {
        if (!returned || observer == null || !safe.getAsBoolean() || !activity.hasWindowFocus()) return false;
        ForegroundUsage.Snapshot front = observer.read(); return front.valid && !front.interrupted;
    }
    void start() {
        if (active) return;
        if (!ForegroundUsage.granted(activity) || !safe.getAsBoolean() || !activity.hasWindowFocus()) {
            completion.done(false, "自动预热需要使用情况访问权限、手机解锁与蓝牙开启"); return;
        }
        if (!OWNERS.acquire(this)) { completion.done(false, "另一次预热正在进行"); return; }
        active = true; returned = false;
        observer = new ForegroundUsage.Observer(activity); policy = new AutoWarmPolicy();
        policy.start(SystemClock.elapsedRealtime());
        KeyNotificationListener.Snapshot key = KeyNotificationListener.current(activity);
        if (key.state != KeyReadinessPolicy.UNKNOWN) launchToken = OriginalPreparation.launchRecorded(activity, key.generation);
        if (launchToken == 0) {
            complete(false, "无法启动原版准备，本次触发已取消"); return;
        }
        progress.show("正在打开原版准备钥匙，稍后自动返回…"); handler.postDelayed(this::poll, 100);
    }
    private void poll() {
        if (!active) return;
        ForegroundUsage.Snapshot front = observer.read();
        KeyNotificationListener.Snapshot key = KeyNotificationListener.current(activity);
        int visible = activity.hasWindowFocus() && !front.interrupted ? AutoWarmPolicy.HELPER : front.front;
        int action = policy.poll(visible, safe.getAsBoolean() && front.valid && !front.interrupted,
                key.state, !key.warmRequired, front.mainAt, activity.hasWindowFocus(), SystemClock.elapsedRealtime());
        if (action == AutoWarmPolicy.RETURN) {
            observer.allowReturn();
            boolean found = false;
            try {
                for (ActivityManager.AppTask task : activity.getSystemService(ActivityManager.class).getAppTasks()) {
                    if (task.getTaskInfo().taskId == activity.getTaskId()) { task.moveToFront(); found = true; break; }
                }
            } catch (RuntimeException ignored) { }
            if (!found) { complete(false, "无法回到快捷页，本次触发已取消"); return; }
            progress.show("正在回到快捷页…");
        } else if (action == AutoWarmPolicy.READY) { complete(true, "已回到快捷页，重新核对操作条件"); return; }
        else if (action == AutoWarmPolicy.FAIL) { complete(false, "预热已取消或未安全返回，请重新触发"); return; }
        handler.postDelayed(this::poll, 100);
    }
    void cancel() { if (active) complete(false, "已取消预热和本次车锁请求"); }
    void close() {
        if (active && launchToken > 0) KeyNotificationListener.cancelWarmup(launchToken);
        launchToken = 0;
        if (policy != null) policy.cancel();
        active = false; handler.removeCallbacksAndMessages(null); OWNERS.release(this);
    }
    private void complete(boolean success, String message) {
        if (success) launchToken = 0; // Only a fully observed return retains preparation for a later request.
        close(); returned = success;
        android.util.Log.i("HimaWidgetBridge", "automatic_prewarm_finished returned=" + success);
        completion.done(success, message);
    }
}
