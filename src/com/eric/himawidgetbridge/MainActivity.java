package com.eric.himawidgetbridge;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The ordinary launcher is a calm dashboard. Only explicitly selected actions can dispatch. */
public final class MainActivity extends WidgetPage {
    private TextView keyStatus, setup, lockStatus, trunkStatus, lockTag, trunkTag;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.hima_companion_logo);
        icon.setBackground(ui.round(Color.WHITE, 12, 0)); icon.setClipToOutline(true); icon.setContentDescription("智行快捷");
        brand.addView(icon, new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));
        LinearLayout names = ui.column(this); names.setPadding(ui.dp(12), 0, 0, 0);
        names.addView(ui.label("智行快捷", 22, ui.text, true)); ui.space(names, 4);
        names.addView(ui.label("让出发更从容", 12, ui.muted, false));
        brand.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        TextView settings = ui.label("设置", 14, ui.text, true); settings.setGravity(Gravity.CENTER);
        brand.addView(settings, new LinearLayout.LayoutParams(ui.dp(56), ui.dp(48)));
        settings.setOnClickListener(v -> openSettings()); body.addView(brand); ui.space(body, 26);
        LinearLayout hero = ui.card(); hero.setBackground(ui.round(ui.hero, 24, 0));
        hero.setPadding(ui.dp(22), ui.dp(24), ui.dp(22), ui.dp(24));
        hero.addView(ui.label("随手一按，即刻出发。", 24, Ui.color("#F6F5F1"), true)); ui.space(hero, 14);
        keyStatus = ui.label("读取数字钥匙状态…", 13, Ui.color("#B8C8DC"), false); hero.addView(keyStatus); body.addView(hero);
        setup = ui.label("", 13, ui.blue, true); setup.setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(16));
        setup.setOnClickListener(v -> openSettings()); body.addView(setup);
        LinearLayout actions = new LinearLayout(this);
        lockStatus = actionCard(actions, ControlAction.LOCK, R.drawable.action_lock);
        trunkStatus = actionCard(actions, ControlAction.TRUNK, R.drawable.action_trunk);
        body.addView(actions); ui.space(body, 20);
        TextView hint = ui.label("也可长按图标，或将快捷项绑定到 AI 键。", 12, ui.muted, false);
        hint.setGravity(Gravity.CENTER); body.addView(hint); ui.space(body, 30);
        body.addView(ui.label("NFC 碰一碰", 18, ui.text, true)); ui.space(body, 12);
        LinearLayout tags = ui.card();
        lockTag = tagRow(tags, ControlAction.LOCK, "车锁标签"); ui.space(tags, 20);
        trunkTag = tagRow(tags, ControlAction.TRUNK, "后备箱标签"); body.addView(tags);
        body.addView(ui.label("亮屏解锁后碰触，各用一枚标签。", 12, ui.muted, false));
        loadWidget();
    }
    private TextView actionCard(LinearLayout parent, String action, int iconId) {
        LinearLayout card = ui.card(); card.setPadding(ui.dp(18), ui.dp(20), ui.dp(18), ui.dp(20));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (ControlAction.LOCK.equals(action)) params.rightMargin = ui.dp(12);
        card.setLayoutParams(params);
        ImageView icon = new ImageView(this); icon.setImageResource(iconId); icon.setColorFilter(ui.text);
        card.addView(icon, new LinearLayout.LayoutParams(ui.dp(30), ui.dp(30))); ui.space(card, 18);
        card.addView(ui.label(ControlAction.label(action), 19, ui.text, true)); ui.space(card, 8);
        TextView detail = ui.label("", 12, ui.muted, false); card.addView(detail);
        card.setContentDescription(ControlAction.label(action)); card.setFocusable(true);
        card.setOnClickListener(v -> {
            if (!session.enabled(action) || !ready(action)) { openSettings(); return; }
            startActivity(ShortcutActions.trigger(this, action));
        }); parent.addView(card); return detail;
    }
    private TextView tagRow(LinearLayout parent, String action, String label) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = ui.column(this); names.addView(ui.label(label, 15, ui.text, true)); ui.space(names, 5);
        TextView detail = ui.label("", 12, ui.muted, false); names.addView(detail);
        row.addView(names, new LinearLayout.LayoutParams(0, -2, 1)); row.addView(ui.label("›", 26, ui.muted, false));
        row.setPadding(0, ui.dp(6), 0, ui.dp(6)); row.setFocusable(true);
        row.setOnClickListener(v -> startActivity(new Intent(this, NfcSettingsActivity.class).putExtra(ControlAction.EXTRA, action)));
        parent.addView(row); return detail;
    }
    private void openSettings() { startActivity(new Intent(this, SettingsActivity.class)); }
    @Override protected void update() {
        if (keyStatus == null) return;
        KeyNotificationListener.Snapshot key = KeyNotificationListener.current(this);
        keyStatus.setText(key.state == KeyReadinessPolicy.CONNECTED ? "数字钥匙已连接" : key.state == KeyReadinessPolicy.DISCONNECTED
                ? "数字钥匙未连接 · 触发时会等待连接" : "数字钥匙将在触发时准备");
        String notice = !WidgetAdapter.installed(this) ? "先安装鸿蒙智行 · 查看设置  ›"
                : !session.bound() ? "先完成车辆小组件绑定  ›"
                : !KeyNotificationListener.granted(this) ? "开启通知使用权，读取钥匙连接状态  ›"
                : !ForegroundUsage.granted(this) ? "开启自动回切，完成首次连接准备  ›" : "";
        setup.setText(notice); setup.setVisibility(notice.isEmpty() ? View.GONE : View.VISIBLE);
        int remaining = WidgetSession.GATE.remainingSeconds(SystemClock.elapsedRealtime());
        lockStatus.setText(actionState(ControlAction.LOCK, remaining)); trunkStatus.setText(actionState(ControlAction.TRUNK, remaining));
        lockTag.setText(tagState(ControlAction.LOCK)); trunkTag.setText(tagState(ControlAction.TRUNK));
    }
    private String actionState(String action, int remaining) {
        return !session.enabled(action) ? "前往设置启用  ›" : !ready(action) ? "等待原版按钮就绪"
                : remaining > 0 ? "防重复保护 · " + remaining + " 秒" : "点击发起原版操作";
    }
    private String tagState(String action) {
        NfcStore store = new NfcStore(this, action);
        return !store.registered() ? "未录入 · 点击设置" : !store.enabled() ? "已录入 · 触发关闭"
                : !session.enabled(action) ? "已录入 · 快捷项关闭" : "碰触触发已开启";
    }
}
