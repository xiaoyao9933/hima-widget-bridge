package com.eric.himawidgetbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.SystemClock;
import android.provider.Settings;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** A local trigger registration; no vehicle credentials or account data. */
final class NfcStore {
    private static final Object BIND_LOCK = new Object();
    private final String action;
    private final Context context;
    private final SharedPreferences prefs;
    NfcStore(Context context) { this(context, ControlAction.LOCK); }
    NfcStore(Context context, String action) {
        if (ControlAction.resolve(action, true) == null) throw new IllegalArgumentException("action");
        this.action = action;
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(ControlAction.TRUNK.equals(action) ? "nfc_trunk" : "nfc", Context.MODE_PRIVATE);
    }
    String action() { return action; }
    boolean conflicts(byte[] uid) {
        if (uid == null || uid.length == 0) return false;
        NfcStore other = new NfcStore(context, ControlAction.LOCK.equals(action) ? ControlAction.TRUNK : ControlAction.LOCK);
        return other.registered() && NfcPolicy.hex(uid).equals(other.prefs.getString("uid", ""));
    }
    boolean registered() { return !prefs.getString("uid", "").isEmpty() && prefs.getString("token", "").length() == 32; }
    boolean enabled() { return registered() && prefs.getBoolean("enabled", false); }
    void setEnabled(boolean value) { prefs.edit().putBoolean("enabled", value && registered()).apply(); }
    boolean bind(byte[] uid, byte[] token) {
        synchronized (BIND_LOCK) {
            if (uid == null || uid.length == 0 || uid.length > 32 || token == null || token.length != 16 || conflicts(uid)) return false;
            return prefs.edit().putString("uid", NfcPolicy.hex(uid)).putString("token", NfcPolicy.hex(token))
                    .putBoolean("enabled", false).remove("last_scan").remove("last_boot").commit();
        }
    }
    void clear() { prefs.edit().clear().apply(); }
    boolean matches(byte[] uid, byte[] token) {
        return NfcPolicy.matches(uid, token, prefs.getString("uid", ""), prefs.getString("token", ""));
    }
    int remainingSeconds() {
        return NfcPolicy.remainingSeconds(SystemClock.elapsedRealtime(), prefs.getLong("last_scan", -1),
                boot(), prefs.getInt("last_boot", -1));
    }
    boolean recordScan() {
        return prefs.edit().putLong("last_scan", SystemClock.elapsedRealtime()).putInt("last_boot", boot()).commit();
    }
    private int boot() { return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1); }
    static NdefMessage message(byte[] token, String packageName) { return message(ControlAction.LOCK, token, packageName); }
    static NdefMessage message(String action, byte[] token, String packageName) {
        if (ControlAction.mime(action) == null || token == null || token.length != 16) throw new IllegalArgumentException("marker");
        return new NdefMessage(new NdefRecord[] {
                NdefRecord.createMime(ControlAction.mime(action), token), NdefRecord.createApplicationRecord(packageName)});
    }
    static byte[] marker(NdefMessage message) { return marker(message, ControlAction.LOCK); }
    static byte[] marker(NdefMessage message, String action) {
        if (ControlAction.mime(action) == null) return null;
        if (message == null || message.getRecords().length == 0) return null;
        NdefRecord first = message.getRecords()[0];
        if (first.getTnf() != NdefRecord.TNF_MIME_MEDIA
                || !Arrays.equals(first.getType(), ControlAction.mime(action).getBytes(StandardCharsets.US_ASCII))) return null;
        byte[] payload = first.getPayload();
        return payload.length == 16 ? payload : null;
    }
}
