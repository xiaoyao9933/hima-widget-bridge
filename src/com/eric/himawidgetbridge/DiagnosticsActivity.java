package com.eric.himawidgetbridge;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Explicit diagnostic tools; there are no vehicle control buttons here. */
public final class DiagnosticsActivity extends WidgetPage {
    private BluetoothPreparation bluetooth;
    private NotificationPreparation notification;
    private TextView radio, key, details;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); header("诊断与测试", "连接排查工具，测试按钮不会操作车锁或后备箱。");
        bluetooth = new BluetoothPreparation(this, text -> radio.setText(text), (ok,text) -> { toast(text); update(); });
        notification = new NotificationPreparation(this, () -> hasWindowFocus()
                && !getSystemService(android.app.KeyguardManager.class).isDeviceLocked(), text -> key.setText(text),
                (ok,text) -> { toast(text); update(); });
        LinearLayout connection = ui.card(); connection.addView(ui.label("连接检查", 18, ui.text, true)); ui.space(connection, 10);
        radio = ui.label("", 13, ui.muted, false); connection.addView(radio);
        connection.addView(ui.button("仅测试开启蓝牙", false, bluetooth::start)); ui.space(connection, 16);
        key = ui.label("", 13, ui.muted, false); connection.addView(key);
        connection.addView(ui.button("仅诊断钥匙连接", false, notification::start));
        connection.addView(ui.button("仅测试预热并自动返回", false, () -> startActivity(new Intent(this, PrewarmTestActivity.class)))); body.addView(connection);
        LinearLayout widget = ui.card(); widget.addView(ui.label("车辆小组件预览", 18, ui.text, true)); ui.space(widget, 8);
        widget.addView(ui.label("原版内容，只读展示。", 12, ui.muted, false)); ui.space(widget, 12);
        ((android.view.ViewGroup) widgetFrame.getParent()).removeView(widgetFrame); widgetFrame.setAlpha(1);
        widgetFrame.setBackground(ui.round(android.graphics.Color.WHITE, 14, 0)); widget.addView(widgetFrame, new LinearLayout.LayoutParams(-1, ui.dp(300)));
        widget.addView(ui.button("刷新车辆小组件", false, () -> {
            WidgetAdapter.Snapshot state = snapshot();
            if (state == null || state.refresh == null || !state.refresh.performClick()) toast("原版刷新按钮不可用");
        })); body.addView(widget);
        LinearLayout adapter = ui.card(); adapter.addView(ui.label("适配信息", 18, ui.text, true)); ui.space(adapter, 10);
        details = ui.label("", 12, ui.muted, false); details.setTextIsSelectable(true); adapter.addView(details); body.addView(adapter); loadWidget();
    }
    @Override protected void update() {
        if (details == null) return;
        if (!bluetooth.busy()) radio.setText(BluetoothPreparation.state(this) == android.bluetooth.BluetoothAdapter.STATE_ON ? "手机蓝牙已开启" : "手机蓝牙未开启或权限待授权");
        if (!notification.busy()) key.setText(KeyNotificationListener.current(this).message);
        WidgetAdapter.Snapshot state = snapshot();
        details.setText("助手版本：0.6.1\n原版版本：" + WidgetAdapter.version(this) + "\n小组件：" + (session.bound() ? "已绑定" : "未绑定")
                + (state == null ? "" : "\n" + state.describe()) + "\n" + BluetoothPreparation.lastSummary(this) + "\n" + NfcIoDiagnostic.summary(this));
    }
    @Override protected void onResume() { super.onResume(); if (bluetooth != null) bluetooth.onResume(); }
    @Override protected void onPause() { if (notification != null) notification.close(); if (bluetooth != null) bluetooth.onPause(); super.onPause(); }
    @Override protected void onStop() { if (bluetooth != null) bluetooth.onStop(); super.onStop(); }
    @Override protected void onDestroy() { if (notification != null) notification.close(); if (bluetooth != null) bluetooth.close(); super.onDestroy(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if (bluetooth != null) bluetooth.activityResult(request, result);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results); if (bluetooth != null) bluetooth.permissionResult(request);
    }
}
