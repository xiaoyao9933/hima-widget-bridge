package com.eric.himawidgetbridge;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

public final class SettingsActivity extends WidgetPage {
    private TextView binding, permissions;
    private Button bind;
    private Switch lock, trunk;
    private boolean updating;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); header("设置", "把常用入口准备好，日常使用更省心。");
        LinearLayout controls = ui.card(); controls.addView(ui.label("快捷操作", 18, ui.text, true)); ui.space(controls, 10);
        lock = toggle(controls, "启用车锁快捷项", "由原版根据车锁状态切换", ControlAction.LOCK);
        ui.space(controls, 16); trunk = toggle(controls, "启用后备箱快捷项", "由原版根据后备箱状态处理", ControlAction.TRUNK);
        controls.addView(ui.button("添加「切换车锁」到桌面", false, () -> ShortcutActions.pin(this, ControlAction.LOCK)));
        controls.addView(ui.button("添加「后备箱」到桌面", false, () -> ShortcutActions.pin(this, ControlAction.TRUNK)));
        body.addView(controls);
        LinearLayout nfc = ui.card(); nfc.addView(ui.label("NFC 标签", 18, ui.text, true)); ui.space(nfc, 6);
        nfc.addView(ui.label("车锁与后备箱分别使用一枚可写标签。", 13, ui.muted, false));
        nfc.addView(ui.button("管理车锁标签", false, () -> tag(ControlAction.LOCK)));
        nfc.addView(ui.button("管理后备箱标签", false, () -> tag(ControlAction.TRUNK))); body.addView(nfc);
        LinearLayout access = ui.card(); access.addView(ui.label("连接与自动回切", 18, ui.text, true)); ui.space(access, 10);
        permissions = ui.label("", 13, ui.muted, false); access.addView(permissions);
        access.addView(ui.button("管理通知使用权", false, () -> {
            try { startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
            catch (RuntimeException e) { toast("请在系统设置中开启通知使用权"); }
        }));
        access.addView(ui.button("管理使用情况访问权限", false, () -> {
            try { ForegroundUsage.openSettings(this); }
            catch (RuntimeException e) { toast("请在系统设置中开启使用情况访问权限"); }
        }));
        ui.space(access, 12); access.addView(ui.label("首次钥匙准备会短暂打开鸿蒙智行，再自动返回继续。蓝牙关闭时会尝试开启；系统要求时需确认。", 12, ui.muted, false)); body.addView(access);
        LinearLayout widget = ui.card(); widget.addView(ui.label("车辆小组件", 18, ui.text, true)); ui.space(widget, 10);
        binding = ui.label("", 13, ui.muted, false); widget.addView(binding);
        bind = ui.button("绑定车辆小组件", false, this::requestBinding); widget.addView(bind);
        widget.addView(ui.button("移除小组件绑定", false, () -> new AlertDialog.Builder(this).setTitle("移除小组件？")
                .setMessage("移除后将关闭车锁和后备箱快捷项，需要重新绑定才能使用。")
                .setNegativeButton("保留", null).setPositiveButton("移除", (d,w) -> { session.delete(); loadWidget(); }).show())); body.addView(widget);
        body.addView(ui.button("诊断与测试", false, () -> startActivity(new Intent(this, DiagnosticsActivity.class))));
        ui.space(body, 16); TextView footer = ui.label("智行快捷 0.6.1\n控制与鉴权由鸿蒙智行处理", 11, ui.muted, false);
        footer.setGravity(Gravity.CENTER); body.addView(footer); loadWidget();
    }
    private Switch toggle(LinearLayout parent, String title, String subtitle, String action) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = ui.column(this); words.addView(ui.label(title, 15, ui.text, true)); ui.space(words, 5);
        words.addView(ui.label(subtitle, 12, ui.muted, false)); row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        Switch control = ui.switchControl(title); row.addView(control, new LinearLayout.LayoutParams(-2, ui.dp(48)));
        control.setOnCheckedChangeListener((button, checked) -> {
            if (updating) return;
            if (checked && !ready(action)) { toast("请先绑定并确认原版有可用的" + ControlAction.label(action) + "按钮"); session.setEnabled(action, false); }
            else session.setEnabled(action, checked);
            update();
        }); parent.addView(row); return control;
    }
    private void tag(String action) { startActivity(new Intent(this, NfcSettingsActivity.class).putExtra(ControlAction.EXTRA, action)); }
    private void requestBinding() {
        Runnable work = () -> { try { if (session.requestBinding()) loadWidget(); } catch (RuntimeException e) { toast("暂时无法绑定小组件"); } };
        if (!session.bound()) { work.run(); return; }
        new AlertDialog.Builder(this).setTitle("重新绑定小组件？")
                .setMessage("重新绑定后会关闭两个快捷项，请确认原版按钮可用后再开启。")
                .setNegativeButton("取消", null).setPositiveButton("重新绑定", (d,w) -> work.run()).show();
    }
    @Override protected void update() {
        if (binding == null) return;
        binding.setText(!WidgetAdapter.supported(this) ? "原版版本需要适配，请到诊断页查看" : !session.bound() ? "尚未绑定" : "已绑定鸿蒙智行小组件");
        bind.setText(session.bound() ? "重新绑定小组件" : "绑定车辆小组件");
        permissions.setText("通知使用权：" + (KeyNotificationListener.granted(this) ? "已开启" : "待授权")
                + "\n自动回切权限：" + (ForegroundUsage.granted(this) ? "已开启" : "待授权"));
        updating = true; lock.setChecked(session.enabled(ControlAction.LOCK)); trunk.setChecked(session.enabled(ControlAction.TRUNK)); updating = false;
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == WidgetSession.BIND_REQUEST) { toast(session.completeBinding(result == RESULT_OK) ? "小组件已绑定" : "绑定未完成"); loadWidget(); }
    }
}
