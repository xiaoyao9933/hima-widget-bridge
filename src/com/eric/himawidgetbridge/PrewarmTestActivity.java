package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.KeyguardManager;
import android.bluetooth.BluetoothAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Private diagnostic. No ToggleActivity, RemoteViews controls, or vehicle action. */
public final class PrewarmTestActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private NotificationPreparation notification;
    private PrewarmController warm;
    private TextView status;
    boolean testFinished, testPassed;
    String testMessage = "";
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui ui = new Ui(this); LinearLayout body = ui.column(this);
        body.setPadding(ui.dp(24), ui.dp(30), ui.dp(24), ui.dp(24)); body.setBackgroundColor(ui.background);
        body.addView(ui.label("预热与自动返回测试", 22, ui.text, true)); ui.space(body, 20);
        status = ui.label("准备测试…", 17, ui.blue, true); body.addView(status); ui.space(body, 20);
        body.addView(ui.label("此测试只打开鸿蒙智行普通页面并返回。\n不刷新车辆、不操作车锁。\n测试期间按 Home、锁屏或切换应用会取消。", 14, ui.muted, false));
        body.addView(ui.button("取消 / 返回主页", false, () -> { if (warm != null && warm.busy()) warm.cancel(); finish(); }));
        setContentView(body);
        warm = new PrewarmController(this, this::safe, text -> status.setText(text), this::finishTest);
        notification = new NotificationPreparation(this, () -> safe() && hasWindowFocus(),
                text -> status.setText(text), (ready, message) -> { if (ready) warm.start(); else finishTest(false, message); });
        if (saved != null) { finishTest(false, "测试被重建，旧请求已取消"); return; }
        if (!ForegroundUsage.granted(this)) { finishTest(false, "请先在主页开启使用情况访问权限"); return; }
        if (!safe()) { finishTest(false, "请先解锁手机、开启蓝牙并确认原版版本支持"); return; }
        handler.postDelayed(notification::start, 350);
    }
    private boolean safe() {
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        return !isFinishing() && (keyguard == null || !keyguard.isDeviceLocked())
                && BluetoothPreparation.state(this) == BluetoothAdapter.STATE_ON
                && WidgetAdapter.supported(this) && ForegroundUsage.granted(this);
    }
    private void finishTest(boolean passed, String message) {
        if (testFinished) return;
        testFinished = true; testPassed = passed; testMessage = message;
        handler.removeCallbacksAndMessages(null);
        if (notification != null) notification.close();
        if (warm != null) warm.close();
        status.setText(passed ? "已自动返回 · 本次未操作车锁" : "测试已结束 · " + message);
        getSharedPreferences("prewarm_test", MODE_PRIVATE).edit().putBoolean("done", true).putBoolean("passed", passed).apply();
        android.util.Log.i("HimaWidgetBridge", "prewarm_test_finished passed=" + passed);
    }
    @Override protected void onPause() {
        handler.removeCallbacksAndMessages(null);
        if (notification != null) notification.close();
        if (!testFinished && (warm == null || !warm.busy())) { finishTest(false, "已离开测试页面"); finish(); }
        super.onPause();
    }
    @Override protected void onUserLeaveHint() {
        if (warm != null && warm.busy()) warm.cancel();
        super.onUserLeaveHint();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (notification != null) notification.close();
        if (warm != null) warm.close();
        super.onDestroy();
    }
}
