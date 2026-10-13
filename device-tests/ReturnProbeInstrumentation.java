package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

/** Observe task visibility while opening only ordinary MAIN; no widget or vehicle action. */
public final class ReturnProbeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); Activity page = null;
        try {
            page = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            final Activity source = page; final int taskId = page.getTaskId();
            result.putInt("helper_task_id", taskId);
            runOnMainSync(() -> {
                Intent launch = source.getPackageManager().getLaunchIntentForPackage(WidgetAdapter.PACKAGE);
                if (launch == null) throw new AssertionError("No original launcher");
                launch.setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION).replaceExtras((Bundle) null);
                source.startActivityForResult(launch, 6101);
            });
            long start = SystemClock.elapsedRealtime(); boolean originalInOwnTask = false, visibleSeen = false;
            while (SystemClock.elapsedRealtime() - start < 3000) {
                for (ActivityManager.AppTask task : source.getSystemService(ActivityManager.class).getAppTasks()) {
                    ActivityManager.RecentTaskInfo info = task.getTaskInfo();
                    if (info.taskId != taskId) continue;
                    boolean originalTop = info.topActivity != null && WidgetAdapter.PACKAGE.equals(info.topActivity.getPackageName());
                    originalInOwnTask |= originalTop; visibleSeen |= info.isVisible();
                    Bundle state = new Bundle(); state.putLong("elapsed_ms", SystemClock.elapsedRealtime() - start);
                    state.putBoolean("own_task_visible", info.isVisible()); state.putBoolean("original_top_in_own_task", originalTop);
                    sendStatus(1, state);
                }
                SystemClock.sleep(500);
            }
            result.putBoolean("original_top_in_own_task_seen", originalInOwnTask);
            result.putBoolean("own_task_visible_seen", visibleSeen);
            final boolean[] moved = {false};
            runOnMainSync(() -> {
                for (ActivityManager.AppTask task : source.getSystemService(ActivityManager.class).getAppTasks()) {
                    if (task.getTaskInfo().taskId == taskId) { task.moveToFront(); moved[0] = true; break; }
                }
            });
            SystemClock.sleep(1000); result.putBoolean("move_requested", moved[0]);
            result.putBoolean("helper_focus_after_move", source.hasWindowFocus());
            result.putBoolean("vehicle_action", false); result.putBoolean("toggle_activity_started", false);
            runOnMainSync(source::finish); finish(-1, result);
        } catch (Throwable error) {
            result.putString("failure", error.getClass().getSimpleName() + ": " + error.getMessage());
            result.putBoolean("vehicle_action", false);
            if (page != null) { final Activity close = page; runOnMainSync(close::finish); }
            finish(0, result);
        }
    }
}
