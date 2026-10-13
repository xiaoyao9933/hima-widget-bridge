package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.lang.reflect.Field;

/** Session preparation and ordinary MAIN only. Never opens ToggleActivity or clicks widget controls. */
public final class KeySessionInstrumentation extends Instrumentation {
    private int checks;
    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message); checks++;
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private boolean containsPreview(View view) {
        if (view instanceof WidgetPreview) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (containsPreview(group.getChildAt(i))) return true;
        }
        return false;
    }
    private KeyNotificationListener.Snapshot readable(Context context) {
        long deadline = SystemClock.elapsedRealtime() + 8000;
        KeyNotificationListener.Snapshot state;
        do {
            state = KeyNotificationListener.current(context);
            if (state.state != KeyReadinessPolicy.UNKNOWN) return state;
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Listener did not become readable");
    }
    private Activity home(Context context) {
        Activity page = startActivitySync(new Intent(context, MainActivity.class).setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync(); return page;
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); Activity page = null;
        try {
            Context context = getTargetContext(); check(KeyNotificationListener.granted(context), "Existing notification grant required");
            runOnMainSync(() -> {
                final int[] clicks = {0};
                Button child = new Button(context) {
                    @Override public boolean onTouchEvent(MotionEvent event) {
                        if (event.getAction() == MotionEvent.ACTION_UP) performClick();
                        return true;
                    }
                };
                child.setOnClickListener(v -> clicks[0]++);
                MotionEvent down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 30, 30, 0);
                MotionEvent up = MotionEvent.obtain(0, 1, MotionEvent.ACTION_UP, 30, 30, 0);
                child.dispatchTouchEvent(down); child.dispatchTouchEvent(up);
                check(clicks[0] == 1, "unguarded dummy control responds to touch");
                WidgetPreview preview = new WidgetPreview(context, child);
                preview.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY));
                preview.layout(0, 0, 300, 300);
                preview.dispatchTouchEvent(down); preview.dispatchTouchEvent(up);
                check(clicks[0] == 1, "production preview prevents touch from reaching child control");
                child.performClick();
                check(clicks[0] == 2, "preview keeps explicit internal click capability");
                check(preview.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                        "preview excludes original interactive accessibility descendants");
                check(preview.getDescendantFocusability() == WidgetPreview.FOCUS_BLOCK_DESCENDANTS,
                        "preview blocks DPAD and keyboard focus into original controls");
                down.recycle(); up.recycle();
            });
            page = home(context);
            final Activity initialPage = page;
            runOnMainSync(() -> check(containsPreview(initialPage.getWindow().getDecorView()),
                    "production MAIN wraps the bound widget in the guarded preview"));
            KeyNotificationListener.Snapshot first = readable(context);
            check(first.state == KeyReadinessPolicy.CONNECTED || first.state == KeyReadinessPolicy.DISCONNECTED,
                    "Baseline requires an existing key notification");
            check(first.warmRequired, "fresh helper history must require preparation even with a key notification");
            check(new KeyReadinessPolicy().start(first.state, !first.warmRequired, SystemClock.elapsedRealtime())
                    == KeyReadinessPolicy.PREWARM, "real fresh session selects PREWARM");
            final Activity source = page; final boolean[] launched = {false};
            runOnMainSync(() -> { source.finish(); launched[0] = OriginalPreparation.launch(source, first.generation); });
            check(launched[0], "production ordinary MAIN preparation starts successfully");
            SystemClock.sleep(2500);
            page = home(context);
            KeyNotificationListener.Snapshot prepared = readable(context);
            check(prepared.generation == first.generation && !prepared.warmRequired,
                    "same key session retains the ordinary MAIN launch record on manual return");
            check(new KeyReadinessPolicy().start(KeyReadinessPolicy.CONNECTED, !prepared.warmRequired,
                    SystemClock.elapsedRealtime()) == KeyReadinessPolicy.PROCEED, "initialized connected session skips prewarm");
            // Feed the actual active key item into the production removal callback without altering the system notification.
            Field field = KeyNotificationListener.class.getDeclaredField("connected"); field.setAccessible(true);
            KeyNotificationListener listener = (KeyNotificationListener) field.get(null);
            StatusBarNotification key = null;
            for (StatusBarNotification item : listener.getActiveNotifications()) {
                if (WidgetAdapter.PACKAGE.equals(item.getPackageName())
                        && KeyNotificationListener.classify(item.getPackageName(), item.getNotification().extras)
                            != KeyReadinessPolicy.ABSENT) { key = item; break; }
            }
            check(key != null, "strictly scoped live key item exists");
            final StatusBarNotification removed = key; runOnMainSync(() -> listener.onNotificationRemoved(removed));
            KeyNotificationListener.Snapshot rebuilt = readable(context);
            check(rebuilt.generation != prepared.generation && rebuilt.warmRequired,
                    "production removal callback invalidates a same-key rebuilt connected notification");
            check(new KeyReadinessPolicy().start(rebuilt.state, !rebuilt.warmRequired, SystemClock.elapsedRealtime())
                    == KeyReadinessPolicy.PREWARM, "rebuilt real key state cannot skip warmup");
            check(!KeyNotificationListener.warmupLaunched(prepared.generation), "old launch token cannot prepare rebuilt session");
            check(KeyNotificationListener.warmupLaunched(rebuilt.generation), "register current test session before listener-loss check");
            SystemClock.sleep(2100); check(!readable(context).warmRequired, "current generation is prepared");
            ComponentName own = new ComponentName(context, KeyNotificationListener.class);
            runOnMainSync(() -> NotificationListenerService.requestUnbind(own));
            long deadline = SystemClock.elapsedRealtime() + 2000;
            while (KeyNotificationListener.ready(context) && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
            check(!KeyNotificationListener.ready(context), "system listener disconnect observed");
            runOnMainSync(() -> KeyNotificationListener.reconnectIfNeeded(context));
            KeyNotificationListener.Snapshot recovered = readable(context);
            check(recovered.warmRequired, "real system listener recovery loses preparation history");
            result.putInt("checks", checks); result.putInt("key_state", recovered.state);
            result.putBoolean("original_main_only", true); result.putBoolean("synthetic_removal_callback", true);
            result.putBoolean("finished_source_before_main", true);
            result.putBoolean("vehicle_action", false); result.putBoolean("toggle_activity_started", false);
            WidgetSession existing = new WidgetSession(page, () -> {});
            result.putBoolean("widget_bound", existing.bound()); result.putBoolean("shortcut_enabled", existing.enabled());
            NfcStore nfc = new NfcStore(context);
            result.putBoolean("nfc_registered", nfc.registered()); result.putBoolean("nfc_enabled", nfc.enabled());
            final Activity close = page; runOnMainSync(close::finish); finish(-1, result);
        } catch (Throwable failure) {
            result.putString("failure", failure.getClass().getSimpleName() + ": " + failure.getMessage());
            result.putInt("checks", checks); result.putBoolean("vehicle_action", false);
            if (page != null) { final Activity close = page; runOnMainSync(close::finish); }
            finish(0, result);
        }
    }
}
