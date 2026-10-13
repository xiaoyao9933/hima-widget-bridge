package com.eric.himawidgetbridge;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Reads only current original-app key notifications; never persists notification text. */
public final class KeyNotificationListener extends NotificationListenerService {
    private static volatile KeyNotificationListener connected;
    private static final KeySessionPolicy sessions = new KeySessionPolicy();

    public static final class Snapshot {
        public final int state;
        public final String message;
        public final long generation;
        public final boolean warmRequired;
        public final long retryRemainingMillis;
        private Snapshot(int state, String message) {
            this.state = state;
            synchronized (sessions) {
                generation = sessions.generation();
                warmRequired = sessions.requiresWarmup(SystemClock.elapsedRealtime());
                retryRemainingMillis = sessions.retryRemainingMillis(SystemClock.elapsedRealtime());
            }
            this.message = message + (state == KeyReadinessPolicy.CONNECTED || state == KeyReadinessPolicy.DISCONNECTED
                    ? warmRequired ? "\n新钥匙会话 · 待预热"
                    : retryRemainingMillis > 0 ? "\n原版准备中 · 请稍后再次触发" : "\n本会话已打开原版准备" : "");
        }
    }
    public static boolean granted(Context context) {
        try {
            String value = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
            if (value == null) return false;
            ComponentName own = new ComponentName(context, KeyNotificationListener.class);
            for (String name : value.split(":"))
                if (own.equals(ComponentName.unflattenFromString(name))) return true;
        } catch (RuntimeException ignored) { }
        return false;
    }
    public static void reconnectIfNeeded(Context context) {
        if (connected == null && granted(context)) {
            ComponentName own = new ComponentName(context, KeyNotificationListener.class);
            // Rebind alone can be a no-op after a force-stop while the component is still enabled.
            // API34 exposes an own-component unbind that repairs this without changing the user grant.
            if (Build.VERSION.SDK_INT >= 34) {
                try { requestUnbind(own); } catch (RuntimeException ignored) { }
            }
            try { requestRebind(own); }
            catch (RuntimeException ignored) { }
        }
    }
    public static boolean ready(Context context) { return granted(context) && connected != null; }
    public static Snapshot current(Context context) {
        if (!granted(context)) return unknown("通知使用权待开启");
        KeyNotificationListener listener = connected;
        if (listener == null) return unknown("通知监听尚未就绪，请返回主页稍后重试");
        try {
            StatusBarNotification[] notifications = listener.getActiveNotifications();
            if (notifications == null || connected != listener) return unknown("通知读取暂不可用");
            int state = classify(notifications);
            if (connected != listener || !granted(context)) return unknown("通知监听已断开");
            if (state == KeyReadinessPolicy.UNKNOWN) return unknown("钥匙通知状态无法确定");
            String identity = null;
            for (StatusBarNotification item : notifications) {
                if (!isKey(item)) continue;
                if (identity != null && !identity.equals(item.getKey())) return unknown("存在多个钥匙通知，会话无法确定");
                identity = item.getKey();
            }
            sessions.observe(state, identity, SystemClock.elapsedRealtime());
            return new Snapshot(state, state == KeyReadinessPolicy.CONNECTED ? "数字车钥匙 · 已连接"
                    : state == KeyReadinessPolicy.DISCONNECTED ? "数字车钥匙 · 未连接" : "当前没有数字车钥匙通知");
        } catch (RuntimeException ignored) { return unknown("通知读取失败，请稍后重试"); }
    }
    public static boolean warmupLaunched(long generation) {
        return recordWarmupLaunched(generation) > 0;
    }
    static long recordWarmupLaunched(long generation) {
        return connected == null ? 0 : sessions.recordWarmupLaunched(generation, SystemClock.elapsedRealtime());
    }
    static void cancelWarmup(long token) { sessions.cancelWarmup(token); }
    static int classify(StatusBarNotification[] notifications) {
        if (notifications == null) return KeyReadinessPolicy.UNKNOWN;
        int state = KeyReadinessPolicy.ABSENT;
        for (StatusBarNotification item : notifications) {
            if (item == null || !WidgetAdapter.PACKAGE.equals(item.getPackageName())) continue;
            int found = classify(item.getPackageName(), item.getNotification().extras);
            if (found == KeyReadinessPolicy.ABSENT) continue;
            if (found == KeyReadinessPolicy.UNKNOWN || state != KeyReadinessPolicy.ABSENT && state != found)
                return KeyReadinessPolicy.UNKNOWN;
            state = found;
        }
        return state;
    }
    static int classify(String pkg, Bundle extras) {
        if (!WidgetAdapter.PACKAGE.equals(pkg) || extras == null) return KeyReadinessPolicy.ABSENT;
        CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (title == null || !title.toString().contains("数字车钥匙")) return KeyReadinessPolicy.ABSENT;
        CharSequence body = extras.getCharSequence(Notification.EXTRA_TEXT);
        return KeyReadinessPolicy.classify(title.toString(), body == null ? null : body.toString());
    }
    private static boolean isKey(StatusBarNotification item) {
        return item != null && WidgetAdapter.PACKAGE.equals(item.getPackageName())
                && item.getNotification() != null
                && classify(item.getPackageName(), item.getNotification().extras) != KeyReadinessPolicy.ABSENT;
    }
    private static Snapshot unknown(String message) {
        sessions.resetHistory(); return new Snapshot(KeyReadinessPolicy.UNKNOWN, message);
    }
    @Override public void onListenerConnected() { sessions.resetHistory(); connected = this; }
    @Override public void onNotificationPosted(StatusBarNotification item) {
        if (connected == this && isKey(item)) sessions.posted(item.getKey(), SystemClock.elapsedRealtime());
    }
    @Override public void onNotificationRemoved(StatusBarNotification item) {
        // Match a previously scoped key; the removed item's title/body need not remain parseable.
        if (connected == this && item != null && WidgetAdapter.PACKAGE.equals(item.getPackageName())
                && sessions.removed(item.getKey()))
            android.util.Log.i("HimaWidgetBridge", "key_session_invalidated source=notification_removed");
    }
    @Override public void onListenerDisconnected() {
        if (connected == this) { connected = null; sessions.resetHistory(); }
    }
    @Override public void onDestroy() {
        if (connected == this) { connected = null; sessions.resetHistory(); }
        super.onDestroy();
    }
}
