package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PowerManager;
import android.widget.Toast;

/** Dedicated MIME route. Enrollment never enters this activity. */
public final class NfcTriggerActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) { finish(); return; }
        try { accept(getIntent()); }
        catch (Exception e) { reject("NFC 标签数据不可用，请重新录入"); }
    }
    @SuppressWarnings("deprecation") // Typed Parcelable overload is unavailable on API 26.
    private void accept(Intent intent) {
        String action = ControlAction.fromMime(intent.getType());
        if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()) || action == null) { reject("此入口仅用于已录入的 NFC 标签"); return; }
        Parcelable tagValue = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        Parcelable[] messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (!(tagValue instanceof Tag) || messages == null || messages.length == 0
                || !(messages[0] instanceof NdefMessage)) { reject("未读取到 NFC 标签，请重新碰触"); return; }
        NfcStore store = new NfcStore(this, action);
        if (!store.matches(((Tag) tagValue).getId(), NfcStore.marker((NdefMessage) messages[0], action))) {
            reject("此 NFC 标签尚未在本机录入"); return;
        }
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        PowerManager power = getSystemService(PowerManager.class);
        if ((keyguard != null && keyguard.isDeviceLocked()) || (power != null && !power.isInteractive())) {
            reject("请亮屏解锁手机后再碰触标签"); return;
        }
        if (!store.enabled() || !new WidgetSession(this, () -> {}).enabled(action)) {
            reject("请先开启对应快捷项和 NFC 触发"); return;
        }
        int remaining = store.remainingSeconds();
        if (remaining > 0) { reject("NFC 防重复保护，请在 " + remaining + " 秒后重试"); return; }
        if (!store.recordScan()) { reject("无法保存 NFC 触发记录，已取消"); return; }
        // Existing ToggleActivity owns widget readiness, refresh, and the shared dispatch gate.
        startActivity(new Intent(this, ToggleActivity.class).setAction(Intent.ACTION_VIEW).putExtra(ControlAction.EXTRA, action)); finish();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); finish(); }
    private void reject(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); finish(); }
}
