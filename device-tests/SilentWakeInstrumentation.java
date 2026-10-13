package com.eric.himawidgetbridge;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

/** Binding probe only. Never starts an Activity or invokes any original business method. */
public final class SilentWakeInstrumentation extends Instrumentation {
    private volatile boolean connected, binderAlive;
    private volatile String callback = "none";
    private long holdMillis;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        try { holdMillis = Math.max(0, Math.min(10000, Long.parseLong(args.getString("holdMillis", "0")))); }
        catch (RuntimeException ignored) { holdMillis = 0; }
        start();
    }
    @Override public void onStart() {
        Context context = getTargetContext().getApplicationContext();
        Bundle result = new Bundle();
        ComponentName component = new ComponentName("app.huawei.motor", "app.huawei.motor.base.bleopen.BleICCEService");
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                connected = component.equals(name);
                binderAlive = connected && service != null && service.isBinderAlive();
                callback = "connected";
            }
            @Override public void onServiceDisconnected(ComponentName name) { binderAlive = false; callback = "disconnected"; }
            @Override public void onNullBinding(ComponentName name) { callback = "null_binding"; }
            @Override public void onBindingDied(ComponentName name) { binderAlive = false; callback = "binding_died"; }
        };
        boolean bound = false;
        long start = SystemClock.elapsedRealtime();
        try {
            ServiceInfo info = context.getPackageManager().getServiceInfo(component, 0);
            result.putBoolean("service_exported", info.exported);
            result.putBoolean("service_enabled", info.enabled);
            result.putBoolean("service_requires_permission", info.permission != null);
            bound = context.bindService(new Intent().setComponent(component), connection, Context.BIND_AUTO_CREATE);
            result.putBoolean("bind_returned", bound);
            while (bound && "none".equals(callback) && SystemClock.elapsedRealtime() - start < 5000) SystemClock.sleep(50);
            result.putBoolean("service_connected", connected);
            result.putBoolean("binder_alive", binderAlive);
            result.putString("callback", callback);
            result.putLong("callback_wait_ms", SystemClock.elapsedRealtime() - start);
            result.putString("caller_package", context.getPackageName());
            if (connected && binderAlive && holdMillis > 0) {
                Bundle status = new Bundle(); status.putString("stage", "bound_without_activity");
                status.putLong("elapsed_ms", SystemClock.elapsedRealtime() - start); sendStatus(1, status);
                long holdStart = SystemClock.elapsedRealtime();
                while (binderAlive && SystemClock.elapsedRealtime() - holdStart < holdMillis) SystemClock.sleep(50);
                result.putLong("held_ms", SystemClock.elapsedRealtime() - holdStart);
                result.putBoolean("binder_alive_after_hold", binderAlive);
            }
        } catch (Throwable e) { result.putString("failure_class", e.getClass().getSimpleName()); }
        finally {
            if (bound) { try { context.unbindService(connection); result.putBoolean("unbound", true); }
                catch (RuntimeException e) { result.putBoolean("unbound", false); } }
        }
        result.putBoolean("activity_started_by_test", false);
        result.putBoolean("original_business_method_called", false);
        result.putBoolean("vehicle_action", false);
        result.putBoolean("cold_start_precondition_checked_by_instrumentation", false);
        result.putString("scope", "bind/unbind exported service only; proves entry access, not digital key connection readiness");
        finish(connected && binderAlive ? -1 : 0, result);
    }
}
