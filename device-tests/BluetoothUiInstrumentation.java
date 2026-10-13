package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/** Explicit Bluetooth-only integration check. Never starts ToggleActivity or clicks widget controls. */
public final class BluetoothUiInstrumentation extends Instrumentation {
    private volatile boolean homeResumed;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        if (activity instanceof DiagnosticsActivity) homeResumed = true;
    }
    @Override public void callActivityOnPause(Activity activity) {
        if (activity instanceof DiagnosticsActivity) homeResumed = false;
        super.callActivityOnPause(activity);
    }
    private Button find(View view) {
        if (view instanceof Button && "仅测试开启蓝牙".contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { Button match = find(group.getChildAt(i)); if (match != null) return match; }
        }
        return null;
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            if (!BluetoothPreparation.permitted(context)) throw new IllegalStateException("Nearby permission not granted; no UI operation");
            result.putInt("initial_radio_state", BluetoothPreparation.state(context));
            Activity home = startActivitySync(new Intent(context, DiagnosticsActivity.class)
                    .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            long foregroundDeadline = SystemClock.elapsedRealtime() + 5000;
            while (!homeResumed && SystemClock.elapsedRealtime() < foregroundDeadline) SystemClock.sleep(100);
            if (!homeResumed) throw new IllegalStateException("Helper home never resumed; no button clicked");
            runOnMainSync(() -> {
                if (!home.hasWindowFocus()) throw new IllegalStateException("Helper home has no window focus; no button clicked");
                Button button = find(home.getWindow().getDecorView());
                if (button == null) throw new IllegalStateException("Bluetooth-only button missing");
                button.performClick();
            });
            SharedPreferences prefs = context.getSharedPreferences("bluetooth", Context.MODE_PRIVATE);
            long deadline = SystemClock.elapsedRealtime() + 30000;
            Bundle start = new Bundle(); start.putString("stage", "bluetooth_only_button_clicked"); sendStatus(1, start);
            String previous = "";
            while (SystemClock.elapsedRealtime() < deadline) {
                String data = prefs.getAll().toString();
                if (!data.equals(previous)) { Bundle status = new Bundle(); status.putString("diagnostic", data); sendStatus(1, status); previous = data; }
                if (!"等待中".equals(prefs.getString("message", "等待中"))) break;
                SystemClock.sleep(200);
            }
            result.putString("diagnostic", prefs.getAll().toString());
            result.putInt("final_radio_state", BluetoothPreparation.state(context));
            result.putBoolean("radio_ready", prefs.getBoolean("ready", false));
            result.putBoolean("vehicle_action", false);
            result.putString("scope", "clicked exact Bluetooth-only helper button; no vehicle entry called");
            finish(-1, result);
        } catch (Throwable e) { result.putString("failure", e.getClass().getSimpleName() + ": " + e.getMessage()); finish(0, result); }
    }
}
