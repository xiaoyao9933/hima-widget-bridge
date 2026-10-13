package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.appwidget.AppWidgetHostView;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

/** Opens helper home and clicks only the original widget refresh control, once. */
public final class WidgetRefreshInstrumentation extends Instrumentation {
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
    private AppWidgetHostView findHost(View view) {
        if (view instanceof AppWidgetHostView) return (AppWidgetHostView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                AppWidgetHostView host = findHost(group.getChildAt(i));
                if (host != null) return host;
            }
        }
        return null;
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        result.putBoolean("vehicle_action", false);
        result.putBoolean("original_activity_started_by_test", false);
        result.putBoolean("widget_refresh_clicked", false);
        Activity home = null;
        long started = SystemClock.elapsedRealtime();
        try {
            Context context = getTargetContext();
            KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguard == null || keyguard.isDeviceLocked()) throw new IllegalStateException("Phone locked");
            if (!WidgetAdapter.supported(context)) throw new IllegalStateException("Original version not supported");
            if (!BluetoothPreparation.permitted(context)
                    || BluetoothPreparation.state(context) != BluetoothAdapter.STATE_ON)
                throw new IllegalStateException("Bluetooth not ready");
            home = startActivitySync(new Intent(context, DiagnosticsActivity.class).setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            final Activity activity = home;
            waitForIdleSync();
            long readyDeadline = SystemClock.elapsedRealtime() + 5000;
            final boolean[] clicked = {false}, loadingSeen = {false};
            while (!clicked[0] && SystemClock.elapsedRealtime() < readyDeadline) {
                runOnMainSync(() -> {
                    if (!homeResumed || !activity.hasWindowFocus() || keyguard.isDeviceLocked()) return;
                    AppWidgetHostView host = findHost(activity.getWindow().getDecorView());
                    if (host == null || host.getAppWidgetInfo() == null
                            || !WidgetAdapter.PACKAGE.equals(host.getAppWidgetInfo().provider.getPackageName())
                            || !WidgetAdapter.PROVIDER.equals(host.getAppWidgetInfo().provider.getClassName())) return;
                    WidgetAdapter.Snapshot state = WidgetAdapter.inspect(host);
                    if (state.refresh != null) clicked[0] = state.refresh.performClick();
                });
                if (!clicked[0]) SystemClock.sleep(100);
            }
            result.putBoolean("widget_refresh_clicked", clicked[0]);
            if (!clicked[0]) throw new IllegalStateException("No visible authorized refresh control; no click");
            result.putLong("refresh_click_elapsed_ms", SystemClock.elapsedRealtime() - started);
            Bundle status = new Bundle(); status.putString("stage", "widget_refresh_clicked_once");
            status.putLong("elapsed_ms", SystemClock.elapsedRealtime() - started); sendStatus(1, status);
            long observationStarted = SystemClock.elapsedRealtime();
            while (homeResumed && SystemClock.elapsedRealtime() - observationStarted < 10000) {
                runOnMainSync(() -> {
                    AppWidgetHostView host = findHost(activity.getWindow().getDecorView());
                    if (host != null && WidgetAdapter.inspect(host).refreshing) loadingSeen[0] = true;
                });
                SystemClock.sleep(100);
            }
            result.putBoolean("refresh_loading_seen", loadingSeen[0]);
            result.putLong("observation_ms", SystemClock.elapsedRealtime() - observationStarted);
            result.putString("scope", "helper MAIN home plus one original widget refresh; no vehicle control or original Activity called");
        } catch (Throwable error) {
            result.putString("failure", error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (home != null) { final Activity activity = home; runOnMainSync(activity::finish); }
        }
        finish(result.containsKey("failure") ? 0 : -1, result);
    }
}
