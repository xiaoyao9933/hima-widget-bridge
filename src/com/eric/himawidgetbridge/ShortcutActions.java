package com.eric.himawidgetbridge;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.widget.Toast;
final class ShortcutActions {
    private ShortcutActions() {}
    static Intent trigger(Activity activity, String action) {
        if (ControlAction.resolve(action, true) == null) throw new IllegalArgumentException("action");
        return new Intent(activity, ToggleActivity.class).setAction(Intent.ACTION_VIEW).putExtra(ControlAction.EXTRA, action);
    }
    static void pin(Activity activity, String action) {
        ShortcutManager manager = activity.getSystemService(ShortcutManager.class);
        String label = ControlAction.label(action);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            Toast.makeText(activity, "请长按应用图标，将「" + label + "」拖到桌面", Toast.LENGTH_LONG).show(); return;
        }
        ShortcutInfo shortcut = new ShortcutInfo.Builder(activity, ControlAction.TRUNK.equals(action) ? "trunk" : "toggle_lock")
                .setShortLabel(label).setIcon(Icon.createWithResource(activity, R.mipmap.ic_launcher))
                .setActivity(new ComponentName(activity, MainActivity.class)).setIntent(trigger(activity, action)).build();
        try { if (!manager.requestPinShortcut(shortcut, null)) throw new IllegalStateException(); }
        catch (RuntimeException e) { Toast.makeText(activity, "桌面暂不支持添加，请使用长按快捷项", Toast.LENGTH_LONG).show(); }
    }
}
