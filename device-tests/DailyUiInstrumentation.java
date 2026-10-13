package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.io.File;
import java.io.FileOutputStream;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Opens ordinary home only. No vehicle controls are clicked. */
public final class DailyUiInstrumentation extends Instrumentation {
    private int checks;
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    static View text(View root, String value) {
        if (root instanceof WidgetPreview) return null; // Original read-only content is not helper UI.
        if (root instanceof TextView && value.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i=0; i<group.getChildCount(); i++) { View found = text(group.getChildAt(i), value); if (found != null) return found; }
        }
        return null;
    }
    private void capture(Activity page, String name) throws Exception {
        final Bitmap[] rendered = {null};
        runOnMainSync(() -> {
            View content = page.findViewById(android.R.id.content);
            rendered[0] = Bitmap.createBitmap(content.getWidth(), content.getHeight(), Bitmap.Config.ARGB_8888);
            content.draw(new Canvas(rendered[0]));
        });
        Bitmap shot = rendered[0];
        try (FileOutputStream stream = new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null), name))) {
            shot.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } finally { shot.recycle(); }
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); Activity home = null, settings = null, tag = null, diagnostics = null;
        try {
            home = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            View root = home.getWindow().getDecorView();
            check(text(root, "仅测试开启蓝牙") == null, "daily home must not contain Bluetooth debug button");
            check(text(root, "仅诊断钥匙连接") == null, "daily home must not contain key diagnostic button");
            check(text(root, "仅测试预热并自动返回") == null, "daily home must not contain prewarm diagnostic button");
            check(text(root, "车辆小组件预览") == null, "daily home must not show the raw widget preview");
            check(text(root, "切换车锁") != null && text(root, "后备箱") != null, "daily home has both explicit controls");
            check(text(root, "车锁标签") != null && text(root, "后备箱标签") != null, "daily home has two independent tag summaries");
            SystemClock.sleep(700); capture(home, "home-0.6.0.png");
            settings = startActivitySync(new Intent(getTargetContext(), SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync(); View settingsRoot = settings.getWindow().getDecorView();
            check(text(settingsRoot, "启用车锁快捷项") != null && text(settingsRoot, "启用后备箱快捷项") != null, "settings has independent enables");
            check(text(settingsRoot, "仅测试开启蓝牙") == null && text(settingsRoot, "诊断与测试") != null, "debug tools are behind diagnostic navigation");
            check(text(settingsRoot, "管理车锁标签") != null && text(settingsRoot, "管理后备箱标签") != null, "settings separates tag management");
            capture(settings, "settings-0.6.0.png");
            tag = startActivitySync(new Intent(getTargetContext(), NfcSettingsActivity.class).putExtra(ControlAction.EXTRA, ControlAction.TRUNK).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync(); check(text(tag.getWindow().getDecorView(), "后备箱标签") != null, "trunk management is action scoped");
            capture(tag, "trunk-tag-0.6.0.png"); final Activity tagPage=tag; runOnMainSync(tagPage::finish); tag=null;
            diagnostics=startActivitySync(new Intent(getTargetContext(), DiagnosticsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();View debugRoot=diagnostics.getWindow().getDecorView();
            check(text(debugRoot,"仅测试开启蓝牙")!=null && text(debugRoot,"仅测试预热并自动返回")!=null,"diagnostics retains no-car tests");
            check(text(debugRoot,"切换车锁") == null && text(debugRoot,"后备箱") == null,"diagnostics has no vehicle action buttons");
            final Activity debugPage=diagnostics;runOnMainSync(debugPage::finish);diagnostics=null;
            final Activity config=settings;runOnMainSync(config::finish);settings=null;
            result.putInt("checks", checks); result.putBoolean("vehicle_action", false);
            final Activity close=home; runOnMainSync(close::finish); finish(-1, result);
        } catch (Throwable error) {
            result.putInt("checks", checks); result.putString("failure", error.getClass().getSimpleName()+": "+error.getMessage());
            result.putBoolean("vehicle_action", false);
            if (diagnostics != null) { final Activity close=diagnostics;runOnMainSync(close::finish); }
            if (tag != null) { final Activity close=tag;runOnMainSync(close::finish); }
            if (settings != null) { final Activity close=settings;runOnMainSync(close::finish); }
            if (home != null) { final Activity close=home;runOnMainSync(close::finish); }
            finish(0, result);
        }
    }
}
