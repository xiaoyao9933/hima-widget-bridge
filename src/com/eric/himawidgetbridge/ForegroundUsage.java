package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;

/** Per-request foreground metadata only. Nothing is persisted or uploaded. */
final class ForegroundUsage {
    @SuppressWarnings("deprecation") // API26-28 need the public legacy AppOps query.
    static boolean granted(Context context) {
        try {
            AppOpsManager ops = context.getSystemService(AppOpsManager.class);
            int value = Build.VERSION.SDK_INT >= 29
                    ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName())
                    : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
            return value == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException ignored) { return false; }
    }
    static void openSettings(Activity activity) {
        activity.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .setData(Uri.parse("package:" + activity.getPackageName())));
    }
    static final class Snapshot {
        final boolean valid, interrupted;
        final int front;
        final long mainAt;
        Snapshot(boolean valid, boolean interrupted, int front, long mainAt) {
            this.valid = valid; this.interrupted = interrupted; this.front = front; this.mainAt = mainAt;
        }
    }
    static final class Observer {
        private final Activity activity;
        private final long startWall = System.currentTimeMillis(), startElapsed = SystemClock.elapsedRealtime();
        private boolean interrupted;
        private int front = AutoWarmPolicy.HELPER;
        private long mainAt = -1;
        private long returnWall = Long.MAX_VALUE;
        Observer(Activity activity) { this.activity = activity; }
        void allowReturn() { returnWall = System.currentTimeMillis(); }
        Snapshot read() {
            if (!granted(activity)) return new Snapshot(false, true, AutoWarmPolicy.UNKNOWN, mainAt);
            long nowWall = System.currentTimeMillis(), nowElapsed = SystemClock.elapsedRealtime();
            if (Math.abs((nowWall - startWall) - (nowElapsed - startElapsed)) > 1000)
                return new Snapshot(false, true, AutoWarmPolicy.UNKNOWN, mainAt);
            try {
                UsageEvents events = activity.getSystemService(UsageStatsManager.class).queryEvents(startWall, nowWall);
                if (events == null) return new Snapshot(false, true, AutoWarmPolicy.UNKNOWN, mainAt);
                ForegroundHistory history = new ForegroundHistory(returnWall);
                UsageEvents.Event event = new UsageEvents.Event();
                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    if (event.getTimeStamp() < startWall) continue;
                    if (event.getEventType() == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                            || event.getEventType() == UsageEvents.Event.KEYGUARD_SHOWN) interrupted = true;
                    if (event.getEventType() != UsageEvents.Event.ACTIVITY_RESUMED) continue;
                    String pkg = event.getPackageName(), cls = event.getClassName();
                    int resumed;
                    if (activity.getPackageName().equals(pkg) && activity.getClass().getName().equals(cls)) resumed = AutoWarmPolicy.HELPER;
                    else if (WidgetAdapter.PACKAGE.equals(pkg) && "app.huawei.auto.MainActivity".equals(cls)) {
                        resumed = AutoWarmPolicy.MAIN;
                        if (mainAt < 0) mainAt = startElapsed + Math.max(0, event.getTimeStamp() - startWall);
                    } else if (WidgetAdapter.PACKAGE.equals(pkg)
                            && "app.huawei.auto.privacyagreement.PrivacyAgreementActivity".equals(cls)) resumed = AutoWarmPolicy.ENTRY;
                    else resumed = AutoWarmPolicy.OTHER;
                    history.resume(resumed, event.getTimeStamp());
                }
                interrupted |= history.interrupted(); front = history.front();
                return new Snapshot(true, interrupted, interrupted ? AutoWarmPolicy.OTHER : front, mainAt);
            } catch (RuntimeException ignored) { return new Snapshot(false, true, AutoWarmPolicy.UNKNOWN, mainAt); }
        }
    }
}
