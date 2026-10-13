package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

/** Observe listener-only then helper MAIN/widget loading. No refresh or vehicle button is clicked. */
public final class WidgetInitSequenceInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void report(String stage, Context context, long start) {
        Bundle value = new Bundle(); value.putString("stage", stage);
        value.putLong("elapsed_ms", SystemClock.elapsedRealtime() - start);
        value.putInt("key_state", KeyNotificationListener.current(context).state);
        value.putBoolean("listener_ready", KeyNotificationListener.ready(context)); sendStatus(1, value);
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); Activity home = null;
        try {
            Context target = getTargetContext(); long start = SystemClock.elapsedRealtime();
            if (!KeyNotificationListener.granted(target)) throw new AssertionError("Notification grant required");
            runOnMainSync(() -> KeyNotificationListener.reconnectIfNeeded(target));
            long deadline = SystemClock.elapsedRealtime() + 8000;
            while (!KeyNotificationListener.ready(target) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100);
            if (!KeyNotificationListener.ready(target)) throw new AssertionError("Listener recovery failed");
            report("listener_only_before_widget", target, start);
            home = startActivitySync(new Intent(target, MainActivity.class).setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync(); report("main_widget_created", target, start);
            int previous = KeyNotificationListener.current(target).state;
            deadline = SystemClock.elapsedRealtime() + 10000;
            while (SystemClock.elapsedRealtime() < deadline) {
                int current = KeyNotificationListener.current(target).state;
                if (current != previous) { report("key_state_changed_after_widget", target, start); previous = current; }
                SystemClock.sleep(100);
            }
            report("observation_end", target, start);
            result.putBoolean("original_activity_started", false); result.putBoolean("vehicle_action", false);
            final Activity page = home; runOnMainSync(page::finish); finish(-1, result);
        } catch (Throwable e) {
            result.putString("failure", e.getClass().getSimpleName() + ": " + e.getMessage());
            if (home != null) { final Activity page = home; runOnMainSync(page::finish); }
            result.putBoolean("vehicle_action", false); finish(0, result);
        }
    }
}
