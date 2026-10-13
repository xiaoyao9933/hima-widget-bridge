package com.eric.himawidgetbridge;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Ordinary original-app launch only. No vehicle command and no automatic return. */
final class OriginalPreparation {
    static boolean launch(Activity activity, long generation) {
        return launchRecorded(activity, generation) > 0;
    }
    static long launchRecorded(Activity activity, long generation) {
        KeyNotificationListener.Snapshot current = KeyNotificationListener.current(activity);
        if (current.state == KeyReadinessPolicy.UNKNOWN || current.generation != generation) return 0;
        Intent launch = activity.getPackageManager().getLaunchIntentForPackage(WidgetAdapter.PACKAGE);
        if (launch == null) return 0;
        launch.setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        launch.replaceExtras((Bundle) null);
        try {
            activity.startActivity(launch);
            return KeyNotificationListener.recordWarmupLaunched(generation);
        } catch (RuntimeException ignored) { return 0; }
    }
}
