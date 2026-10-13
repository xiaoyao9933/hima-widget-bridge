package com.eric.himawidgetbridge;

import android.app.Instrumentation;
import android.app.ActivityManager;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Framework format checks in isolated preferences. Does not start any Activity or scan a tag. */
public final class NfcFrameworkInstrumentation extends Instrumentation {
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        checks++;
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Context target = getTargetContext();
        Context isolated = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("nfc_framework_validation_" + name, mode);
            }
        };
        NfcStore testStore = new NfcStore(isolated);
        NfcStore trunkStore = new NfcStore(isolated, ControlAction.TRUNK);
        try {
            testStore.clear(); trunkStore.clear();
            byte[] token = new byte[16]; token[0] = 42;
            byte[] uid = {4, 10, 11, 12};
            NdefMessage message = NfcStore.message(token, target.getPackageName());
            NdefRecord[] records = message.getRecords();
            check(records.length == 2, "two routing records");
            check(records[0].getTnf() == NdefRecord.TNF_MIME_MEDIA, "first record is MIME");
            check(Arrays.equals(records[0].getType(), NfcPolicy.MIME.getBytes(StandardCharsets.US_ASCII)), "dedicated MIME");
            check(Arrays.equals(records[0].getPayload(), token), "marker payload");
            check(records[1].getTnf() == NdefRecord.TNF_EXTERNAL_TYPE, "AAR external record");
            check(new String(records[1].getType(), StandardCharsets.US_ASCII).equals("android.com:pkg"), "AAR type");
            check(new String(records[1].getPayload(), StandardCharsets.UTF_8).equals(target.getPackageName()), "AAR package");
            check(Arrays.equals(NfcStore.marker(new NdefMessage(message.toByteArray())), token), "serialized NDEF roundtrip");
            check(NfcStore.marker(null) == null, "null message rejects");
            check(NfcStore.marker(new NdefMessage(new NdefRecord[] {NdefRecord.createUri("https://example.invalid")})) == null, "web tag rejects");
            check(NfcStore.marker(new NdefMessage(new NdefRecord[] {NdefRecord.createMime(NfcPolicy.MIME, new byte[15])})) == null, "short marker rejects");
            check(NfcStore.marker(new NdefMessage(new NdefRecord[] {NdefRecord.createMime("application/octet-stream", token)})) == null, "foreign MIME rejects");
            check(!testStore.registered() && !testStore.enabled(), "fresh disabled");
            check(testStore.bind(uid, token) && testStore.registered() && !testStore.enabled(), "registration defaults disabled");
            testStore.setEnabled(true); check(testStore.enabled(), "explicit enable");
            check(testStore.matches(uid, token), "persisted identity");
            check(testStore.recordScan() && testStore.remainingSeconds() > 0, "persisted repeat suppression");
            check(new NfcStore(isolated).remainingSeconds() > 0, "new store retains repeat suppression");
            check(testStore.bind(uid, token) && !testStore.enabled() && testStore.remainingSeconds() == 0, "reenroll disables and resets");
            byte[] trunkUid = {4, 20, 21, 22}; byte[] trunkToken = token.clone(); trunkToken[1] = 53;
            check(trunkStore.bind(trunkUid, trunkToken) && !trunkStore.enabled(), "trunk registration defaults disabled");
            trunkStore.setEnabled(true); check(trunkStore.enabled(), "trunk enabled independently");
            check(!testStore.enabled() && testStore.matches(uid, token), "trunk does not change old lock state");
            check(!trunkStore.bind(uid, trunkToken) && trunkStore.matches(trunkUid, trunkToken), "lock UID cannot overwrite trunk binding");
            check(!testStore.bind(trunkUid, token) && testStore.matches(uid, token), "trunk UID cannot overwrite lock binding");
            check(!trunkStore.bind(null, trunkToken) && !trunkStore.bind(trunkUid, new byte[15]), "invalid registration rejects");
            NdefMessage trunkMessage = NfcStore.message(ControlAction.TRUNK, trunkToken, target.getPackageName());
            check(Arrays.equals(NfcStore.marker(new NdefMessage(trunkMessage.toByteArray()), ControlAction.TRUNK), trunkToken), "trunk serialized roundtrip");
            check(NfcStore.marker(trunkMessage) == null, "trunk tag rejected by legacy lock route");
            check(NfcStore.marker(message, ControlAction.TRUNK) == null, "lock tag rejected by trunk route");
            check(!trunkStore.matches(uid, token), "trunk cannot accept lock identity");
            check(trunkStore.recordScan() && trunkStore.remainingSeconds() > 0 && testStore.remainingSeconds() == 0, "scan protection namespaces separate");
            testStore.clear(); check(!testStore.registered() && !testStore.enabled(), "remove registration");
            check(trunkStore.enabled() && trunkStore.matches(trunkUid, trunkToken), "clearing lock leaves trunk intact");
            trunkStore.clear(); check(!trunkStore.registered() && !trunkStore.enabled(), "remove trunk registration");
            result.putInt("checks_passed", checks);
            result.putInt("ndef_bytes", message.getByteArrayLength());
            SharedPreferences widget = target.getSharedPreferences("widget", Context.MODE_PRIVATE);
            int id = widget.getInt("id", -1);
            result.putBoolean("existing_widget_bound", id >= 0 && AppWidgetManager.getInstance(target).getAppWidgetInfo(id) != null);
            result.putBoolean("existing_shortcuts_enabled", widget.getBoolean("enabled", false));
            NfcStore actual = new NfcStore(target);
            result.putBoolean("actual_nfc_registered", actual.registered());
            result.putBoolean("actual_nfc_enabled", actual.enabled());
            result.putInt("helper_target_sdk", target.getApplicationInfo().targetSdkVersion);
            result.putBoolean("bluetooth_permission_granted", BluetoothPreparation.permitted(target));
            result.putInt("bluetooth_state", BluetoothPreparation.state(target));
            result.putString("last_bluetooth_preparation", target.getSharedPreferences("bluetooth", Context.MODE_PRIVATE).getAll().toString());
            ActivityManager processes = target.getSystemService(ActivityManager.class);
            java.util.List<ActivityManager.RunningAppProcessInfo> running = processes.getRunningAppProcesses();
            boolean originalVisible = false;
            if (running != null) for (ActivityManager.RunningAppProcessInfo process : running) {
                if (WidgetAdapter.PACKAGE.equals(process.processName)) originalVisible = true;
            }
            result.putInt("public_api_process_count", running == null ? 0 : running.size());
            result.putBoolean("public_api_original_process_visible", originalVisible);
            Drawable icon = target.getPackageManager().getApplicationIcon(target.getPackageName());
            Bitmap bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
            icon.setBounds(0, 0, 256, 256); icon.draw(new Canvas(bitmap));
            File preview = new File(target.getExternalFilesDir(null), "hima-icon-v03.png");
            try (FileOutputStream stream = new FileOutputStream(preview)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
            bitmap.recycle(); result.putString("icon_preview", preview.getAbsolutePath());
            result.putString("scope", "NDEF framework and isolated preferences; no hardware tag or vehicle action");
            finish(-1, result);
        } catch (Throwable e) {
            result.putString("failure", e.getClass().getSimpleName() + ": " + e.getMessage()); finish(0, result);
        } finally { testStore.clear(); trunkStore.clear(); }
    }
}
