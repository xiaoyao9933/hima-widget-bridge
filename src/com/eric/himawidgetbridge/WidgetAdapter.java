package com.eric.himawidgetbridge;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

final class WidgetAdapter {
    static final String PACKAGE = "app.huawei.motor";
    static final String PROVIDER = "app.huawei.auto.vehicle.appwidget.VehAppWidget44";
    // Resource IDs recovered from the original 3.0.2.320 R$id class.
    private static final int[] SLOTS = {2131430983, 2131430990, 2131430988, 2131430981};
    private static final int REFRESH = 2131430986;
    private static final int LOADING = 2131434571;
    private static final int UPDATED = 2131434299;

    static String version(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return info.versionName == null ? "未知" : info.versionName;
        } catch (Exception e) { return "未安装"; }
    }
    static boolean installed(Context context) {
        try { return context.getPackageManager().getPackageInfo(PACKAGE, 0) != null; }
        catch (Exception e) { return false; }
    }

    static Snapshot inspect(View root) {
        String[] labels = new String[SLOTS.length];
        boolean[] available = new boolean[SLOTS.length];
        View[] slots = new View[SLOTS.length];
        for (int i = 0; i < SLOTS.length; i++) {
            View slot = root.findViewById(SLOTS[i]);
            slots[i] = slot;
            List<String> texts = new ArrayList<>();
            collectText(slot, texts);
            labels[i] = String.join(" / ", texts);
            available[i] = slot != null && slot.isShown() && slot.isEnabled()
                    && slot.isClickable() && slot.hasOnClickListeners();
        }
        int selected = SlotPolicy.findLock(labels, available);
        int trunk = SlotPolicy.findTrunk(labels, available);
        View refresh = root.findViewById(REFRESH);
        if (refresh == null || !refresh.isShown() || !refresh.isEnabled()
                || !refresh.hasOnClickListeners()) refresh = null;
        View loading = root.findViewById(LOADING);
        View updated = root.findViewById(UPDATED);
        String time = updated instanceof TextView ? ((TextView) updated).getText().toString() : "";
        return new Snapshot(selected < 0 ? null : slots[selected], trunk < 0 ? null : slots[trunk], refresh,
                loading != null && loading.isShown(), time, labels);
    }

    private static void collectText(View view, List<String> out) {
        if (view instanceof TextView) {
            String text = ((TextView) view).getText().toString().trim();
            if (!text.isEmpty()) out.add(text);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectText(group.getChildAt(i), out);
        }
    }

    static final class Snapshot {
        final View lock, trunk, refresh;
        final boolean refreshing;
        final String updated;
        final String[] labels;
        Snapshot(View lock, View trunk, View refresh, boolean refreshing, String updated, String[] labels) {
            this.lock = lock; this.trunk = trunk; this.refresh = refresh; this.refreshing = refreshing;
            this.updated = updated; this.labels = labels;
        }
        View control(String action) {
            if (ControlAction.LOCK.equals(action)) return lock;
            return ControlAction.TRUNK.equals(action) ? trunk : null;
        }
        String describe() {
            return "原版按钮：" + String.join("、", labels)
                    + "\n车锁按钮：" + (lock == null ? "不可用" : "已识别")
                    + "\n后备箱按钮：" + (trunk == null ? "不可用" : "已识别")
                    + "\n更新时间：" + (updated.isEmpty() ? "未显示" : updated)
                    + "\n刷新状态：" + (refreshing ? "正在刷新" : "空闲");
        }
    }
}
