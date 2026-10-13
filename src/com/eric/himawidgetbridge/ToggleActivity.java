package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.KeyguardManager;
import android.appwidget.AppWidgetHostView;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class ToggleActivity extends Activity {
    private static final OperationLease REQUESTS = new OperationLease();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WidgetSession session;
    private String action;
    private BluetoothPreparation bluetooth;
    private NotificationPreparation notification;
    private PrewarmController warm;
    private TextView status;
    private boolean pending, sawLoading;
    private boolean flowStarted, keyPreparing;
    private boolean warmAttempted;
    private KeyReadinessPolicy keyPolicy;
    private Runnable afterKey;
    private long firstDisconnectedAt = -1;
    private long refreshDeadline;

    @SuppressWarnings("deprecation") // BaseBundle.get validates legacy extras without coercion.
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        boolean present = getIntent().hasExtra(ControlAction.EXTRA);
        Object raw = getIntent().getExtras() == null ? null : getIntent().getExtras().get(ControlAction.EXTRA);
        action = ControlAction.resolve(raw instanceof String ? (String) raw : null, present);
        if (action == null) { stop("未知快捷操作，已取消"); return; }
        session = new WidgetSession(this, () -> {});
        if (saved != null || !session.enabled(action) || !session.bound()
                || !WidgetAdapter.supported(this) || Intent.ACTION_MAIN.equals(getIntent().getAction())
                || getIntent().getBooleanExtra("configure", false)) {
            startActivity(new Intent(this, MainActivity.class)); finish(); return;
        }
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard != null && keyguard.isDeviceLocked()) { stop("请解锁手机后再触发"); return; }
        if (!REQUESTS.acquire(this)) { stop("另一次车辆请求仍在准备，请先完成或取消"); return; }
        Ui ui = new Ui(this);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        scroll.setBackgroundColor(ui.background);
        LinearLayout body = ui.column(this);
        body.setPadding(ui.dp(20), ui.dp(22), ui.dp(20), ui.dp(24)); scroll.addView(body); setContentView(scroll);
        body.addView(ui.label(ControlAction.label(action), 24, ui.text, true)); ui.space(body, 8);
        body.addView(ui.label("操作由鸿蒙智行处理", 13, ui.muted, false)); ui.space(body, 24);
        LinearLayout progress = ui.card(); progress.setBackground(ui.round(ui.hero, 22, 0));
        status = ui.label("准备原版操作…", 19, Ui.color("#F6F5F1"), true); progress.addView(status);
        ui.space(progress, 10);
        progress.addView(ui.label("请以原版提示和实际车辆状态为准。", 12, Ui.color("#ADB7C6"), false));
        body.addView(progress);
        AppWidgetHostView view;
        try { view = session.createView(); }
        catch (Exception e) { stop("无法显示原版小组件"); return; }
        if (view == null) { stop("小组件绑定已失效，请重新绑定"); return; }
        LinearLayout widget = ui.card(); widget.setBackground(ui.round(android.graphics.Color.WHITE, 22, 0));
        widget.addView(new WidgetPreview(this, view), new LinearLayout.LayoutParams(-1, ui.dp(300))); body.addView(widget);
        view.post(() -> session.resize(view.getWidth(), 300));
        body.addView(ui.button("取消本次触发", false,
                () -> {
                    if (pending) WidgetSession.GATE.cancel();
                    if (bluetooth != null && bluetooth.busy()) bluetooth.cancel(); else stop("已取消");
                }));
        bluetooth = new BluetoothPreparation(this, text -> status.setText(text), (ready, message) -> {
            if (isFinishing()) return;
            if (!ready) { stop(message); return; }
            status.setText("蓝牙已开启，检查数字车钥匙…");
            handler.postDelayed(() -> { flowStarted = true; notification.start(); }, 300);
        });
        notification = new NotificationPreparation(this, this::available, text -> status.setText(text), (ready, message) -> {
            if (isFinishing()) return;
            if (!ready) { stop(message); return; }
            checkKey(this::prepare);
        });
        handler.postDelayed(() -> {
            int remaining = WidgetSession.GATE.remainingSeconds(SystemClock.elapsedRealtime());
            if (remaining > 0) stop("防重复保护，请在 " + remaining + " 秒后重试"); else bluetooth.start();
        }, 300);
    }
    private boolean available() {
        return coreSafe() && hasWindowFocus() && (warm == null || !warm.returned() || warm.continuationAllowed());
    }
    private boolean coreSafe() {
        return !isFinishing() && unlocked() && bluetooth != null && bluetooth.readyNow()
                && session.enabled(action) && session.bound() && WidgetAdapter.supported(this);
    }
    private void checkKey(Runnable continuation) {
        if (!available()) { stop("请保持快捷页在前台、手机解锁和蓝牙开启后重试"); return; }
        KeyNotificationListener.Snapshot state = KeyNotificationListener.current(this);
        trace("key_check", state);
        if (stopForWarmRetry(state)) return;
        long now = SystemClock.elapsedRealtime();
        if (state.state == KeyReadinessPolicy.DISCONNECTED && firstDisconnectedAt < 0) firstDisconnectedAt = now;
        keyPreparing = true; afterKey = continuation; keyPolicy = new KeyReadinessPolicy();
        handleKey(keyPolicy.start(state.state, !state.warmRequired,
                firstDisconnectedAt < 0 ? now : firstDisconnectedAt), state);
    }
    private void pollKey() {
        if (!keyPreparing || isFinishing()) return;
        if (!available()) { stop("连接准备已取消，请保持手机解锁和蓝牙开启"); return; }
        KeyNotificationListener.Snapshot state = KeyNotificationListener.current(this);
        if (stopForWarmRetry(state)) return;
        handleKey(keyPolicy.poll(state.state, !state.warmRequired, SystemClock.elapsedRealtime()), state);
    }
    private void handleKey(int action, KeyNotificationListener.Snapshot state) {
        if (action == KeyReadinessPolicy.WAIT) {
            int remaining = (int) Math.max(0, (firstDisconnectedAt + KeyReadinessPolicy.WAIT_MILLIS
                    - SystemClock.elapsedRealtime() + 999) / 1000);
            status.setText("等待数字车钥匙连接 · " + remaining + " 秒");
            handler.postDelayed(this::pollKey, 250);
        } else if (action == KeyReadinessPolicy.PROCEED) {
            keyPreparing = false;
            Runnable next = afterKey; afterKey = null;
            if (next != null) next.run();
        } else if (action == KeyReadinessPolicy.PREWARM) initializeOriginal();
        else if (action == KeyReadinessPolicy.FAIL) stop(state.message + "，本次触发已取消");
    }
    private void initializeOriginal() {
        KeyNotificationListener.Snapshot key = KeyNotificationListener.current(this);
        if (key.state == KeyReadinessPolicy.UNKNOWN) { stop(key.message + "，本次触发已取消"); return; }
        if (stopForWarmRetry(key)) return;
        trace("prewarm_main", key);
        if (!ForegroundUsage.granted(this)) {
            stop("未开启自动回切权限，先打开原版准备；完成后请再次触发");
            if (!OriginalPreparation.launch(this, key.generation))
                Toast.makeText(this, "请手动打开鸿蒙智行准备钥匙", Toast.LENGTH_LONG).show();
            return;
        }
        if (warmAttempted) { stop("钥匙会话再次变化，本次触发已取消"); return; }
        warmAttempted = true; keyPreparing = false; afterKey = null;
        if (keyPolicy != null) keyPolicy.cancel();
        handler.removeCallbacksAndMessages(null);
        if (pending) { pending = false; WidgetSession.GATE.cancel(); }
        warm = new PrewarmController(this, this::coreSafe, text -> status.setText(text), (returned, message) -> {
            if (!returned) stop(message); else resumeAfterWarm();
        });
        warm.start();
    }
    private void resumeAfterWarm() {
        if (!available()) { stop("自动返回后的操作条件已失效，已取消"); return; }
        int remaining = WidgetSession.GATE.remainingSeconds(SystemClock.elapsedRealtime());
        if (remaining > 0) {
            status.setText("防重复保护 · " + remaining + " 秒");
            handler.postDelayed(this::resumeAfterWarm, 200); return;
        }
        checkKey(this::prepare);
    }
    private void prepare() {
        if (isFinishing() || session.view() == null) return;
        if (!available()) { stop("请保持快捷页在前台、手机解锁和蓝牙开启后重试"); return; }
        KeyNotificationListener.Snapshot key = KeyNotificationListener.current(this);
        if (key.state == KeyReadinessPolicy.UNKNOWN) { stop(key.message + "，本次触发已取消"); return; }
        if (stopForWarmRetry(key)) return;
        if (key.warmRequired) { initializeOriginal(); return; }
        WidgetAdapter.Snapshot state = WidgetAdapter.inspect(session.view());
        if (!WidgetSession.GATE.tryStart(session.enabled(action), session.bound(),
                WidgetAdapter.supported(this), state.control(action) != null, SystemClock.elapsedRealtime())) {
            stop(WidgetSession.GATE.getReason()); return;
        }
        pending = true;
        refreshAndDispatch();
    }
    private void refreshAndDispatch() {
        if (!available() || session.view() == null || !pending) { stop("本次触发条件已失效"); return; }
        WidgetAdapter.Snapshot state = WidgetAdapter.inspect(session.view());
        sawLoading = false;
        if (state.refresh != null) {
            status.setText("正在刷新原版车辆状态…");
            refreshDeadline = SystemClock.elapsedRealtime() + 6000;
            if (!state.refresh.performClick()) { abort("无法触发原版刷新"); return; }
            handler.postDelayed(this::awaitRefresh, 100);
        } else dispatch(state);
    }
    private void awaitRefresh() {
        if (isFinishing() || !pending) return;
        if (!available()) { abort("已离开快捷页、锁屏或关闭蓝牙，本次触发已取消"); return; }
        KeyNotificationListener.Snapshot key = KeyNotificationListener.current(this);
        if (key.state == KeyReadinessPolicy.UNKNOWN) { abort(key.message + "，本次触发已取消"); return; }
        if (stopForWarmRetry(key)) return;
        if (key.state == KeyReadinessPolicy.ABSENT || key.warmRequired) { initializeOriginal(); return; }
        WidgetAdapter.Snapshot state = WidgetAdapter.inspect(session.view());
        if (state.refreshing) sawLoading = true;
        if (sawLoading && !state.refreshing && !state.updated.isEmpty()
                && !state.updated.contains("失败")) { dispatch(state); return; }
        if (SystemClock.elapsedRealtime() >= refreshDeadline) { abort("原版刷新未完成，已取消本次触发"); return; }
        handler.postDelayed(this::awaitRefresh, 100);
    }
    private void dispatch(WidgetAdapter.Snapshot state) {
        if (!available() || !pending) { abort("触发条件已失效，本次触发已取消"); return; }
        KeyNotificationListener.Snapshot key = KeyNotificationListener.current(this);
        trace("before_dispatch", key);
        if (key.state == KeyReadinessPolicy.UNKNOWN) { abort(key.message + "，本次触发已取消"); return; }
        if (stopForWarmRetry(key)) return;
        if (key.state == KeyReadinessPolicy.ABSENT || key.warmRequired) { initializeOriginal(); return; }
        if (key.state == KeyReadinessPolicy.DISCONNECTED
                && (firstDisconnectedAt < 0 || SystemClock.elapsedRealtime()
                    < firstDisconnectedAt + KeyReadinessPolicy.WAIT_MILLIS)) {
            checkKey(this::refreshAndDispatch); return;
        }
        if (!session.enabled(action) || !session.bound() || state.control(action) == null) {
            abort("原版" + ControlAction.label(action) + "按钮已不可用，已取消"); return;
        }
        pending = false;
        try {
            if (!state.control(action).performClick()) { WidgetSession.GATE.cancel(); stop("原版操作未执行"); return; }
            android.util.Log.i("HimaWidgetBridge", "original_widget_" + action + "_click_dispatched");
            stop("已触发原版" + ControlAction.label(action) + "，请以原版提示为准");
        } catch (Exception e) { WidgetSession.GATE.cancel(); stop("原版操作触发失败"); }
    }
    private void abort(String text) { pending = false; WidgetSession.GATE.cancel(); stop(text); }
    private boolean stopForWarmRetry(KeyNotificationListener.Snapshot key) {
        if (key.retryRemainingMillis <= 0) return false;
        stop("原版准备中，请在 " + ((key.retryRemainingMillis + 999) / 1000) + " 秒后再次触发");
        return true;
    }
    private void trace(String stage, KeyNotificationListener.Snapshot key) {
        android.util.Log.i("HimaWidgetBridge", "stage=" + stage + " key_state=" + key.state
                + " session=" + key.generation + " warm_required=" + key.warmRequired);
    }
    private boolean unlocked() {
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        return keyguard == null || !keyguard.isDeviceLocked();
    }
    private void stop(String text) {
        handler.removeCallbacksAndMessages(null);
        if (notification != null) notification.close();
        if (keyPolicy != null) keyPolicy.cancel();
        keyPreparing = false; afterKey = null;
        if (pending) { pending = false; WidgetSession.GATE.cancel(); }
        if (bluetooth != null) bluetooth.close();
        if (warm != null) warm.close();
        REQUESTS.release(this);
        Toast.makeText(this, text, Toast.LENGTH_LONG).show(); finish();
    }
    @Override protected void onResume() {
        super.onResume();
        if (bluetooth != null && !isFinishing()) bluetooth.onResume();
        if (session != null && !isFinishing()) {
            try { session.start(); } catch (Exception e) { stop("小组件更新不可用，已取消"); }
        }
    }
    @Override protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        if (notification != null) notification.close();
        if (bluetooth != null) bluetooth.onPause();
        if (pending) { pending = false; WidgetSession.GATE.cancel(); }
        if (keyPolicy != null) keyPolicy.cancel();
        keyPreparing = false; afterKey = null;
        if (session != null) session.stop();
        super.onPause();
        if (flowStarted && !isFinishing() && (warm == null || !warm.busy())) stop("已离开快捷页，本次触发已取消");
    }
    @Override protected void onUserLeaveHint() {
        if (warm != null && warm.busy()) warm.cancel();
        super.onUserLeaveHint();
    }
    @Override protected void onDestroy() {
        if (notification != null) notification.close();
        if (bluetooth != null) bluetooth.close();
        if (warm != null) warm.close(); REQUESTS.release(this); super.onDestroy();
    }
    @Override protected void onStop() { if (bluetooth != null) bluetooth.onStop(); super.onStop(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (bluetooth != null) bluetooth.activityResult(request, result);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (bluetooth != null) bluetooth.permissionResult(request);
    }
}
