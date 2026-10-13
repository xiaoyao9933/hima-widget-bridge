package com.eric.himawidgetbridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.NdefMessage;

/** Last enrollment stage only: never logs UID, tag payload, tokens, or exception messages. */
final class NfcIoDiagnostic {
    static final String PREFS = "nfc_io_diagnostic";
    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    static void stage(Context context, String action, String stage, boolean writeReturned) {
        SharedPreferences.Editor edit = prefs(context).edit();
        if ("waiting_tag".equals(stage)) edit.clear();
        edit.putString("action", action).putString("stage", stage).putBoolean("write_returned", writeReturned)
                .remove("exception").commit();
    }
    static void info(Context context, String action, int capacity, boolean writable, boolean formatable) {
        stage(context, action, "confirm_write", false);
        prefs(context).edit().putInt("capacity", capacity).putBoolean("writable", writable)
                .putBoolean("formatable", formatable).commit();
    }
    static void readback(Context context, NdefMessage message, String action) {
        byte[] marker = NfcStore.marker(message, action);
        prefs(context).edit().putBoolean("readback_null", message == null)
                .putInt("readback_records", message == null ? 0 : message.getRecords().length)
                .putBoolean("readback_marker_valid", marker != null).commit();
    }
    static void failure(Context context, Exception error) {
        prefs(context).edit().putString("exception", error.getClass().getSimpleName()).commit();
    }
    static String summary(Context context) {
        SharedPreferences data = prefs(context);
        if (!data.contains("stage")) return "NFC：尚无录入诊断";
        return "最近 NFC 录入：" + data.getString("action", "") + " / " + data.getString("stage", "")
                + "\n写入已返回：" + data.getBoolean("write_returned", false)
                + (data.contains("exception") ? "\n异常：" + data.getString("exception", "") : "");
    }
}
