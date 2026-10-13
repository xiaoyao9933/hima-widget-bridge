package com.eric.himawidgetbridge;

import android.app.Activity;
import android.appwidget.AppWidgetHostView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** A read-only host kept measured behind the page; no refresh or control on lifecycle events. */
abstract class WidgetPage extends Activity {
    protected Ui ui;
    protected WidgetSession session;
    protected LinearLayout body;
    protected FrameLayout widgetFrame;
    private final Handler updates = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() { update(); updates.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); ui = new Ui(this); session = new WidgetSession(this, this::update);
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(ui.background);
        widgetFrame = new FrameLayout(this); widgetFrame.setAlpha(0);
        root.addView(widgetFrame, new FrameLayout.LayoutParams(-1, ui.dp(300)));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(ui.background);
        body = ui.column(this); body.setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(28));
        scroll.addView(body); root.addView(scroll, new FrameLayout.LayoutParams(-1, -1)); setContentView(root);
    }
    protected void header(String title, String subtitle) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = ui.label("‹", 32, ui.text, false); back.setContentDescription("返回");
        back.setGravity(Gravity.CENTER); row.addView(back, new LinearLayout.LayoutParams(ui.dp(42), ui.dp(48)));
        back.setOnClickListener(v -> finish()); row.addView(ui.label(title, 24, ui.text, true));
        body.addView(row); ui.space(body, 6); body.addView(ui.label(subtitle, 13, ui.muted, false)); ui.space(body, 22);
    }
    protected void loadWidget() {
        widgetFrame.removeAllViews();
        try {
            AppWidgetHostView view = session.createView();
            if (view != null) {
                widgetFrame.addView(new WidgetPreview(this, view), new FrameLayout.LayoutParams(-1, -1));
                view.post(() -> { session.resize(view.getWidth(), 300); update(); });
            }
        } catch (RuntimeException e) { toast("暂时无法读取小组件，请在设置中检查绑定"); }
        update();
    }
    protected WidgetAdapter.Snapshot snapshot() { return session.view() == null ? null : WidgetAdapter.inspect(session.view()); }
    protected boolean ready(String action) {
        WidgetAdapter.Snapshot state = snapshot();
        return session.bound() && WidgetAdapter.installed(this) && state != null && state.control(action) != null;
    }
    protected void update() {}
    protected void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    @Override protected void onResume() {
        super.onResume(); try { session.start(); } catch (RuntimeException e) { toast("小组件更新不可用"); }
        KeyNotificationListener.reconnectIfNeeded(this); updates.removeCallbacks(poll); updates.post(poll);
    }
    @Override protected void onPause() { updates.removeCallbacks(poll); session.stop(); super.onPause(); }
    @Override protected void onDestroy() { updates.removeCallbacksAndMessages(null); session.stop(); super.onDestroy(); }
}
