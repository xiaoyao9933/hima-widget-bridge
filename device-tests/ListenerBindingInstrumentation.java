package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/** Readiness recovery only. Never starts a vehicle trigger or clicks a widget. */
public final class ListenerBindingInstrumentation extends Instrumentation {
    private boolean reset;
    private boolean preparation;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        reset = args != null && "true".equals(args.getString("resetBinding"));
        preparation = args != null && "true".equals(args.getString("exercisePreparation")); start();
    }
    private Button diagnostic(View view) {
        if (view instanceof Button && "仅诊断钥匙连接".contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { Button found = diagnostic(group.getChildAt(i)); if (found != null) return found; }
        }
        return null;
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); Activity home = null;
        try {
            Context target = getTargetContext();
            if (!KeyNotificationListener.granted(target)) throw new AssertionError("Existing notification grant required");
            result.putInt("initial_state", KeyNotificationListener.current(target).state);
            home = startActivitySync(new Intent(target, MainActivity.class).setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            if (preparation) {
                long readyDeadline = SystemClock.elapsedRealtime() + 8000;
                while (!KeyNotificationListener.ready(target) && SystemClock.elapsedRealtime() < readyDeadline) SystemClock.sleep(100);
                if (!KeyNotificationListener.ready(target)) throw new AssertionError("Initial production listener not ready");
                runOnMainSync(() -> NotificationListenerService.requestUnbind(new ComponentName(target, KeyNotificationListener.class)));
                long unbindDeadline = SystemClock.elapsedRealtime() + 2000;
                while (KeyNotificationListener.ready(target) && SystemClock.elapsedRealtime() < unbindDeadline) SystemClock.sleep(50);
                if (KeyNotificationListener.ready(target)) throw new AssertionError("Listener disconnect not observed; no diagnostic clicked");
                final Activity page = home;
                runOnMainSync(() -> {
                    if (!page.hasWindowFocus()) throw new AssertionError("MAIN lacks focus; no button clicked");
                    Button button = diagnostic(page.getWindow().getDecorView());
                    if (button == null) throw new AssertionError("Diagnostic-only button absent");
                    button.performClick();
                });
                result.putBoolean("disconnected_precondition", true);
            }
            if (reset) runOnMainSync(() -> {
                if (Build.VERSION.SDK_INT < 34) throw new AssertionError("Static unbind requires API34");
                ComponentName own = new ComponentName(target, KeyNotificationListener.class);
                NotificationListenerService.requestUnbind(own);
                NotificationListenerService.requestRebind(own);
            });
            long start = SystemClock.elapsedRealtime(), deadline = start + 8000;
            KeyNotificationListener.Snapshot value;
            do {
                value = KeyNotificationListener.current(target);
                if (value.state != KeyReadinessPolicy.UNKNOWN) break;
                SystemClock.sleep(100);
            } while (SystemClock.elapsedRealtime() < deadline);
            result.putInt("final_state", value.state);
            result.putString("readiness_message", value.message);
            result.putLong("elapsed_ms", SystemClock.elapsedRealtime() - start);
            result.putBoolean("reset_experiment", reset); result.putBoolean("vehicle_action", false);
            result.putBoolean("production_preparation_exercised", preparation);
            if (value.state == KeyReadinessPolicy.UNKNOWN) throw new AssertionError("Granted listener did not recover within 8 seconds");
            final Activity page = home; runOnMainSync(page::finish); finish(-1, result);
        } catch (Throwable e) {
            result.putString("failure", e.getClass().getSimpleName() + ": " + e.getMessage());
            if (home != null) { final Activity page = home; runOnMainSync(page::finish); }
            result.putBoolean("vehicle_action", false); finish(0, result);
        }
    }
}
