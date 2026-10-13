package com.eric.himawidgetbridge;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.function.BooleanSupplier;

/** Prepares only the helper's notification listener; contains no original-app or vehicle action. */
final class NotificationPreparation {
    interface Progress { void show(String message); }
    interface Completion { void done(boolean ready, String message); }
    private final Activity activity;
    private final BooleanSupplier foreground;
    private final Progress progress;
    private final Completion completion;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ListenerPreparationPolicy policy;
    private boolean active;
    NotificationPreparation(Activity activity, BooleanSupplier foreground, Progress progress, Completion completion) {
        this.activity = activity; this.foreground = foreground; this.progress = progress; this.completion = completion;
    }
    boolean busy() { return active; }
    void start() {
        if (active) return;
        if (!foreground.getAsBoolean()) { completion.done(false, "请保持准备界面在前台并解锁手机"); return; }
        active = true; policy = new ListenerPreparationPolicy();
        handle(policy.start(KeyNotificationListener.granted(activity), KeyNotificationListener.ready(activity),
                SystemClock.elapsedRealtime()));
    }
    private void poll() {
        if (!active) return;
        handle(policy.poll(KeyNotificationListener.granted(activity), KeyNotificationListener.ready(activity),
                foreground.getAsBoolean(), SystemClock.elapsedRealtime()));
    }
    private void handle(int action) {
        if (!active) return;
        if (action == ListenerPreparationPolicy.REBIND) {
            KeyNotificationListener.reconnectIfNeeded(activity);
            progress.show("正在恢复通知监听…"); handler.postDelayed(this::poll, 100);
        } else if (action == ListenerPreparationPolicy.WAIT) handler.postDelayed(this::poll, 100);
        else if (action == ListenerPreparationPolicy.READY) complete(true, "通知监听已就绪");
        else if (action == ListenerPreparationPolicy.FAIL) complete(false, policy.reason());
    }
    private void complete(boolean ready, String message) { close(); completion.done(ready, message); }
    void close() {
        if (policy != null) policy.cancel();
        active = false; handler.removeCallbacksAndMessages(null);
    }
}
