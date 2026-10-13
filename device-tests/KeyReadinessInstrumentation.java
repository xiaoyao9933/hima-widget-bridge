package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

/** Notification parsing and MAIN/diagnostic only. Never starts ToggleActivity or touches widget controls. */
public final class KeyReadinessInstrumentation extends Instrumentation {
    private int checks;
    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private Bundle extras(String title, String body) {
        Bundle extras = new Bundle();
        extras.putCharSequence(Notification.EXTRA_TITLE, title);
        extras.putCharSequence(Notification.EXTRA_TEXT, body);
        return extras;
    }
    private View text(View view, String wanted) {
        if (view instanceof TextView && wanted.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = text(group.getChildAt(i), wanted); if (found != null) return found;
            }
        }
        return null;
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); Activity home = null;
        try {
            Bundle yes = extras("数字车钥匙服务", "已连接"), no = extras("数字车钥匙服务", "未连接");
            check(KeyNotificationListener.classify(null) == KeyReadinessPolicy.UNKNOWN, "null read is unknown");
            check(KeyNotificationListener.classify(new StatusBarNotification[0]) == KeyReadinessPolicy.ABSENT, "healthy empty read");
            check(KeyNotificationListener.classify(WidgetAdapter.PACKAGE, yes) == KeyReadinessPolicy.CONNECTED, "framework connected body");
            check(KeyNotificationListener.classify(WidgetAdapter.PACKAGE, no) == KeyReadinessPolicy.DISCONNECTED, "framework disconnected body");
            check(KeyNotificationListener.classify("test.unrelated", yes) == KeyReadinessPolicy.ABSENT, "other package excluded");
            check(KeyNotificationListener.classify(WidgetAdapter.PACKAGE, extras("其他车辆消息", "已连接")) == KeyReadinessPolicy.ABSENT, "other title excluded");
            check(KeyNotificationListener.classify(WidgetAdapter.PACKAGE, extras("数字车钥匙服务", null)) == KeyReadinessPolicy.UNKNOWN, "missing key body");
            check(KeyNotificationListener.classify(WidgetAdapter.PACKAGE, extras("数字车钥匙服务", "已连接 / 未连接")) == KeyReadinessPolicy.UNKNOWN, "ambiguous key body");
            check(KeyNotificationListener.classify(WidgetAdapter.PACKAGE, extras(null, "已连接")) == KeyReadinessPolicy.ABSENT, "title required");
            Context context = getTargetContext();
            result.putBoolean("notification_access", KeyNotificationListener.granted(context));
            home = startActivitySync(new Intent(context, DiagnosticsActivity.class).setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            final Activity page = home;
            long deadline = SystemClock.elapsedRealtime() + 8000;
            KeyNotificationListener.Snapshot current;
            do {
                current = KeyNotificationListener.current(context);
                if (!KeyNotificationListener.granted(context) || current.state != KeyReadinessPolicy.UNKNOWN) break;
                SystemClock.sleep(200);
            } while (SystemClock.elapsedRealtime() < deadline);
            result.putInt("current_key_state", current.state);
            if (!KeyNotificationListener.granted(context)) check(current.state == KeyReadinessPolicy.UNKNOWN, "permission absence never implies cold");
            runOnMainSync(() -> {
                check(text(page.getWindow().getDecorView(), "数字车钥匙连接") != null, "connection card exists");
                View button = text(page.getWindow().getDecorView(), "仅诊断钥匙连接");
                check(button instanceof Button, "diagnostic-only button exists");
                check(page.hasWindowFocus(), "MAIN has focus before diagnostic");
                button.performClick();
            });
            result.putInt("checks", checks);
            result.putBoolean("vehicle_action", false);
            result.putString("scope", "synthetic notification parsing, MAIN and diagnostic-only button");
            runOnMainSync(page::finish); finish(-1, result);
        } catch (Throwable failure) {
            result.putString("failure", failure.getClass().getSimpleName() + ": " + failure.getMessage());
            if (home != null) { final Activity page = home; runOnMainSync(page::finish); }
            finish(0, result);
        }
    }
}
