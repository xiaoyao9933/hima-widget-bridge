package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Enrollment is scoped to one action and never dispatches a vehicle operation. */
public final class NfcSettingsActivity extends Activity {
    private Ui ui;
    private NfcStore store;
    private NfcEnrollment enrollment;
    private WidgetSession session;
    private TextView status;
    private Switch enabled;
    private Button enroll, remove;
    private boolean updating;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        String action = getIntent().getStringExtra(ControlAction.EXTRA);
        if (ControlAction.resolve(action, true) == null) { finish(); return; }
        ui = new Ui(this); store = new NfcStore(this, action); session = new WidgetSession(this, () -> {});
        enrollment = new NfcEnrollment(this, store, this::update);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(ui.background);
        LinearLayout body = ui.column(this); body.setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(28)); scroll.addView(body); setContentView(scroll);
        TextView back = ui.label("‹  返回", 16, ui.text, true); back.setPadding(0, 0, 0, ui.dp(22)); back.setOnClickListener(v -> finish()); body.addView(back);
        body.addView(ui.label(ControlAction.TRUNK.equals(action) ? "后备箱标签" : "车锁标签", 26, ui.text, true)); ui.space(body, 10);
        body.addView(ui.label("碰触标签，发起「" + ControlAction.label(action) + "」。", 14, ui.muted, false)); ui.space(body, 24);
        LinearLayout card = ui.card(); status = ui.label("", 16, ui.text, true); card.addView(status); ui.space(card, 18);
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(ui.label("启用碰触触发", 15, ui.text, true), new LinearLayout.LayoutParams(0, -2, 1));
        enabled = ui.switchControl("启用碰触触发"); row.addView(enabled); card.addView(row);
        enabled.setOnCheckedChangeListener((view, value) -> {
            if (updating) return;
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);
            if (value && (enrollment.busy() || adapter == null || !adapter.isEnabled() || !store.registered() || !session.enabled(store.action()))) {
                toast("请先开启手机 NFC、录入标签，并启用对应快捷项"); store.setEnabled(false);
            } else store.setEnabled(value); update();
        });
        enroll = ui.button("录入可写 NFC 标签", true, enrollment::start); card.addView(enroll);
        remove = ui.button("移除本机标签绑定", false, () -> {
            if (enrollment.busy()) { toast("请先完成或取消录入"); return; }
            new AlertDialog.Builder(this).setTitle("移除标签绑定？").setMessage("将关闭这枚标签的触发并删除本机登记，标签里的内容不会改变。")
                    .setNegativeButton("保留", null).setPositiveButton("移除", (d,w) -> { store.clear(); update(); }).show();
        }); card.addView(remove); body.addView(card);
        LinearLayout info = ui.card(); info.addView(ui.label("使用方式", 17, ui.text, true)); ui.space(info, 12);
        info.addView(ui.label("录入时会请你确认写入，完成后再手动开启触发。\n\n车锁和后备箱各用一枚标签。手机须亮屏解锁；蓝牙与连接准备沿用快捷项流程。\n\n贴在车身金属处，请使用抗金属标签。", 13, ui.muted, false)); body.addView(info); update();
    }
    private void update() {
        if (status == null) return;
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this); boolean on = adapter != null && adapter.isEnabled();
        status.setText(adapter == null ? "此手机没有 NFC 功能" : !on ? "手机 NFC 未开启" : !store.registered() ? "尚未录入标签"
                : !store.enabled() ? "标签已录入 · 触发关闭" : !session.enabled(store.action()) ? "对应快捷项未开启" : "碰触触发已开启");
        updating = true; enabled.setChecked(store.enabled()); updating = false;
        enabled.setEnabled(on && store.registered() && !enrollment.busy()); enroll.setEnabled(on && !enrollment.busy());
        enroll.setText(store.registered() ? "重新录入／更换标签" : "录入可写 NFC 标签"); remove.setVisibility(store.registered() ? android.view.View.VISIBLE : android.view.View.GONE);
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onResume() { super.onResume(); if (enrollment != null) { enrollment.onResume(); update(); } }
    @Override protected void onPause() { if (enrollment != null) enrollment.onPause(); super.onPause(); }
    @Override protected void onDestroy() { if (enrollment != null) enrollment.onDestroy(); super.onDestroy(); }
}
