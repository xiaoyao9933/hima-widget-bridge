package com.eric.himawidgetbridge;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RemoteViews;

final class WidgetSession {
    static final int BIND_REQUEST = 1044;
    static final TriggerGate GATE = new TriggerGate();
    private final Activity activity;
    private final AppWidgetManager manager;
    private final AppWidgetHost host;
    private final SharedPreferences prefs;
    private final Runnable onUpdate;
    private AppWidgetHostView view;
    private boolean listening;

    WidgetSession(Activity activity, Runnable onUpdate) {
        this.activity = activity;
        this.onUpdate = onUpdate;
        manager = AppWidgetManager.getInstance(activity);
        prefs = activity.getSharedPreferences("widget", Context.MODE_PRIVATE);
        host = new AppWidgetHost(activity, 4044) {
            @Override protected AppWidgetHostView onCreateView(Context context, int id,
                    AppWidgetProviderInfo info) {
                return new AppWidgetHostView(context) {
                    @Override public void updateAppWidget(RemoteViews views) {
                        super.updateAppWidget(views);
                        post(WidgetSession.this.onUpdate);
                    }
                };
            }
        };
    }

    boolean enabled() { return enabled(ControlAction.LOCK); }
    void setEnabled(boolean enabled) { setEnabled(ControlAction.LOCK, enabled); }
    boolean enabled(String action) {
        if (ControlAction.LOCK.equals(action)) return prefs.getBoolean("enabled", false);
        return ControlAction.TRUNK.equals(action) && prefs.getBoolean("trunk_enabled", false);
    }
    void setEnabled(String action, boolean enabled) {
        if (ControlAction.resolve(action, true) == null) return;
        prefs.edit().putBoolean(ControlAction.TRUNK.equals(action) ? "trunk_enabled" : "enabled", enabled).apply();
    }
    int id() { return prefs.getInt("id", AppWidgetManager.INVALID_APPWIDGET_ID); }
    boolean bound() {
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id());
        return info != null && provider().equals(info.provider);
    }
    private ComponentName provider() {
        return new ComponentName(WidgetAdapter.PACKAGE, WidgetAdapter.PROVIDER);
    }
    AppWidgetHostView createView() {
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id());
        if (info == null || !provider().equals(info.provider)) return null;
        view = host.createView(activity, id(), info);
        int widthDp = (int) (activity.getResources().getDisplayMetrics().widthPixels
                / activity.getResources().getDisplayMetrics().density) - 32;
        resize(Math.round(widthDp * activity.getResources().getDisplayMetrics().density), 300);
        return view;
    }
    @SuppressWarnings("deprecation")
    void resize(int widthPixels, int heightDp) {
        if (view == null || widthPixels <= 0) return;
        int widthDp = Math.round(widthPixels / activity.getResources().getDisplayMetrics().density);
        view.updateAppWidgetSize(new Bundle(), widthDp, heightDp, widthDp, heightDp);
    }
    AppWidgetHostView view() { return view; }
    void start() {
        if (!listening) { host.startListening(); listening = true; }
    }
    void stop() {
        if (listening) { host.stopListening(); listening = false; }
    }
    boolean requestBinding() {
        int previousPending = prefs.getInt("pending", -1);
        if (previousPending >= 0) host.deleteAppWidgetId(previousPending);
        int freshId = host.allocateAppWidgetId();
        prefs.edit().putInt("pending", freshId).commit();
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
        if (manager.bindAppWidgetIdIfAllowed(freshId, provider(), options)) {
            completeBinding(true);
            return true;
        }
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, freshId);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider());
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
        activity.startActivityForResult(intent, BIND_REQUEST);
        return false;
    }
    boolean completeBinding(boolean approved) {
        int pending = prefs.getInt("pending", -1);
        AppWidgetProviderInfo info = pending < 0 ? null : manager.getAppWidgetInfo(pending);
        if (approved && info != null && provider().equals(info.provider)) {
            int old = id();
            prefs.edit().putInt("id", pending).remove("pending").putBoolean("enabled", false)
                    .putBoolean("trunk_enabled", false).commit();
            if (old >= 0 && old != pending) host.deleteAppWidgetId(old);
            return true;
        }
        if (pending >= 0) host.deleteAppWidgetId(pending);
        prefs.edit().remove("pending").commit();
        return false;
    }
    void delete() {
        int old = id();
        if (old >= 0) host.deleteAppWidgetId(old);
        prefs.edit().remove("id").putBoolean("enabled", false).putBoolean("trunk_enabled", false).apply();
        view = null;
    }
}
