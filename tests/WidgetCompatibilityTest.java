package com.eric.himawidgetbridge;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.ViewGroup;
import android.widget.TextView;

/** Runs the real adapter against host API fixtures. Never binds or clicks a real widget. */
public final class WidgetCompatibilityTest {
    private static int checks;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label); checks++;
    }
    private static Context app(String version, boolean installed) {
        return new Context() {
            public PackageManager getPackageManager() {
                return new PackageManager() {
                    public PackageInfo getPackageInfo(String name, int flags) throws NameNotFoundException {
                        if (!installed || !"app.huawei.motor".equals(name)) throw new NameNotFoundException();
                        PackageInfo info = new PackageInfo(); info.versionName = version; return info;
                    }
                };
            }
        };
    }
    private static TextView button(int id, String label) {
        TextView view = new TextView(); view.setId(id); view.setText(label); return view;
    }
    private static ViewGroup root(TextView... buttons) {
        ViewGroup root = new ViewGroup(); for (TextView button : buttons) root.addView(button); return root;
    }
    public static void main(String[] args) {
        check(WidgetAdapter.installed(app("3.0.2.320", true)), "tested version must remain available");
        check(WidgetAdapter.installed(app("3.0.2.319", true)), "installed older version must not be blocked by version number");
        check(WidgetAdapter.installed(app("4.0.0.999", true)), "installed newer version must not be blocked by version number");
        check(WidgetAdapter.installed(app(null, true)), "installed app without version metadata remains eligible for widget checks");
        check(!WidgetAdapter.installed(app("3.0.2.320", false)), "missing original app must reject");
        check("4.0.0.999".equals(WidgetAdapter.version(app("4.0.0.999", true))), "actual version is still reported for diagnostics");
        check("未知".equals(WidgetAdapter.version(app(null, true))), "missing version metadata is diagnostic only");
        TextView lock = button(2131430983, "车锁"), trunk = button(2131430990, "后备箱");
        WidgetAdapter.Snapshot ready = WidgetAdapter.inspect(root(lock, trunk));
        check(ready.control(ControlAction.LOCK) == lock, "unique visible actionable lock remains selectable");
        check(ready.control(ControlAction.TRUNK) == trunk, "unique visible actionable trunk remains selectable");
        check(WidgetAdapter.inspect(root(button(2131430983, "儿童锁"))).lock == null, "similar non-target label rejects");
        check(WidgetAdapter.inspect(root(button(999, "车锁"))).lock == null, "unrecognized resource ID cannot guess an action");
        check(WidgetAdapter.inspect(root(button(2131430983, "车锁"), button(2131430990, "车锁"))).lock == null, "ambiguous actionable lock controls reject");
        lock.shown = false; check(WidgetAdapter.inspect(root(lock)).lock == null, "hidden control rejects"); lock.shown = true;
        lock.enabled = false; check(WidgetAdapter.inspect(root(lock)).lock == null, "disabled control rejects"); lock.enabled = true;
        lock.clickable = false; check(WidgetAdapter.inspect(root(lock)).lock == null, "non-clickable control rejects"); lock.clickable = true;
        lock.listener = false; check(WidgetAdapter.inspect(root(lock)).lock == null, "control without original click listener rejects"); lock.listener = true;
        check(WidgetAdapter.inspect(root()).lock == null, "missing control rejects");
        TriggerGate gate = new TriggerGate();
        check(!gate.tryStart(true, false, true, true, 1000), "removing version restriction does not bypass binding");
        check(!gate.tryStart(true, true, true, false, 1000), "removing version restriction does not bypass button readiness");
        check(gate.tryStart(true, true, WidgetAdapter.installed(app("4.0.0.999", true)), ready.lock != null, 1000), "a bound ready control may start for a newer original version");
        System.out.println("WidgetCompatibilityTest: " + checks + " checks passed (host fixtures; no physical widget or vehicle action)");
    }
}
