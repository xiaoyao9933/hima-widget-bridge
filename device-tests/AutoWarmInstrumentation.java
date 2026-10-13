package com.eric.himawidgetbridge;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.reflect.Field;

/** Private prewarm diagnostic only; never launches ToggleActivity or clicks vehicle controls. */
public final class AutoWarmInstrumentation extends Instrumentation {
    private String mode = "normal";
    private int checks;
    private boolean restoreUsage;
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private void shell(String command) throws Exception {
        try (android.os.ParcelFileDescriptor.AutoCloseInputStream input =
                new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))) {
            byte[] buffer = new byte[256]; while (input.read(buffer) >= 0) { }
        }
    }
    private boolean cancellationMode() {
        return "home".equals(mode) || "manual_return".equals(mode) || "destroy".equals(mode)
                || "lock".equals(mode) || "revoke".equals(mode);
    }
    private void restoreUsage() throws Exception {
        if (!restoreUsage) return;
        shell("cmd appops set com.eric.himawidgetbridge GET_USAGE_STATS allow");
        if (!ForegroundUsage.granted(getTargetContext())) throw new AssertionError("usage permission was not restored");
        restoreUsage = false;
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); if (args != null && args.getString("mode") != null) mode = args.getString("mode"); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); PrewarmTestActivity page = null;
        try {
            boolean granted = ForegroundUsage.granted(getTargetContext());
            check("missing".equals(mode) ? !granted : granted, "Expected usage access precondition");
            page = (PrewarmTestActivity) startActivitySync(new Intent(getTargetContext(), PrewarmTestActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitForIdleSync();
            final PrewarmTestActivity source = page;
            long deadline = SystemClock.elapsedRealtime() + 15000;
            boolean injected = false; final boolean[] done = {false}, passed = {false};
            Field controllerField = PrewarmTestActivity.class.getDeclaredField("warm"); controllerField.setAccessible(true);
            Field observerField = PrewarmController.class.getDeclaredField("observer"); observerField.setAccessible(true);
            while (SystemClock.elapsedRealtime() < deadline) {
                runOnMainSync(() -> { done[0] = source.testFinished; passed[0] = source.testPassed; });
                if (done[0]) break;
                final PrewarmController[] current = {null};
                final ForegroundUsage.Snapshot[] observed = {null};
                runOnMainSync(() -> {
                    try {
                        current[0] = (PrewarmController) controllerField.get(source);
                        ForegroundUsage.Observer observer = current[0] == null ? null
                                : (ForegroundUsage.Observer) observerField.get(current[0]);
                        observed[0] = observer == null ? null : observer.read();
                    } catch (IllegalAccessException error) { throw new AssertionError(error); }
                });
                PrewarmController controller = current[0];
                ForegroundUsage.Snapshot foreground = observed[0];
                if (!injected && foreground != null && foreground.front == AutoWarmPolicy.MAIN && cancellationMode()) {
                    injected = true;
                    if ("home".equals(mode)) shell("input keyevent 3");
                    else if ("lock".equals(mode)) shell("input keyevent 223");
                    else if ("revoke".equals(mode)) {
                        restoreUsage = true;
                        shell("cmd appops set com.eric.himawidgetbridge GET_USAGE_STATS ignore");
                        check(!ForegroundUsage.granted(getTargetContext()), "usage access actually revoked during preparation");
                    }
                    else if ("manual_return".equals(mode)) runOnMainSync(() -> {
                        for (ActivityManager.AppTask task : source.getSystemService(ActivityManager.class).getAppTasks())
                            if (task.getTaskInfo().taskId == source.getTaskId()) { task.moveToFront(); break; }
                    });
                    else {
                        runOnMainSync(source::finish); SystemClock.sleep(2000);
                        check(source.isDestroyed() && !controller.busy(), "destroyed host leaves no active controller");
                        done[0] = true; passed[0] = false; break;
                    }
                }
                SystemClock.sleep(50);
            }
            check(done[0], "diagnostic reaches a bounded terminal state");
            boolean normal = "normal".equals(mode);
            check(passed[0] == normal, "only uninterrupted normal flow may return successfully");
            if (normal) {
                check(source.hasWindowFocus(), "successful return has actual window focus");
                final boolean[] retained = {false};
                runOnMainSync(() -> retained[0] = !KeyNotificationListener.current(source).warmRequired);
                check(retained[0], "successful return preserves preparation for the same key session");
            }
            if (cancellationMode()) {
                check(injected, "cancellation was injected while original MAIN was active");
                SystemClock.sleep(1800);
                if ("home".equals(mode)) check(!source.hasWindowFocus(), "Home does not get pulled back by a late return");
                final boolean[] canceled = {false}, needsWarmup = {false};
                runOnMainSync(() -> { canceled[0] = !source.testPassed;
                    needsWarmup[0] = KeyNotificationListener.current(source).warmRequired; });
                check(canceled[0], "canceled diagnostic cannot revive success");
                check(needsWarmup[0], "failed automatic preparation requires warmup on the next explicit request");
                if ("lock".equals(mode)) check(!source.getSystemService(android.os.PowerManager.class).isInteractive(), "lock test remains noninteractive after cancellation");
            }
            result.putInt("checks", checks); result.putString("mode", mode);
            result.putBoolean("returned", passed[0]); result.putBoolean("usage_access", granted);
            result.putBoolean("vehicle_action", false); result.putBoolean("toggle_activity_started", false);
            WidgetSession existing = new WidgetSession(source, () -> {});
            result.putBoolean("widget_bound", existing.bound()); result.putBoolean("shortcut_enabled", existing.enabled());
            NfcStore nfc = new NfcStore(getTargetContext());
            result.putBoolean("nfc_registered", nfc.registered()); result.putBoolean("nfc_enabled", nfc.enabled());
            result.putString("message", source.testMessage);
            runOnMainSync(source::finish); restoreUsage(); finish(-1, result);
        } catch (Throwable error) {
            result.putInt("checks", checks); result.putString("mode", mode);
            result.putString("failure", error.getClass().getSimpleName() + ": " + error.getMessage());
            result.putBoolean("vehicle_action", false);
            if (page != null) { final PrewarmTestActivity close = page; runOnMainSync(close::finish); }
            try { restoreUsage(); } catch (Exception restoreError) { result.putString("permission_restore_failure", restoreError.getClass().getSimpleName()); }
            finish(0, result);
        } finally {
            if (restoreUsage) {
                try { restoreUsage(); }
                catch (Exception error) { android.util.Log.e("HimaWidgetBridgeTest", "usage permission restoration needs verification"); }
            }
        }
    }
}
