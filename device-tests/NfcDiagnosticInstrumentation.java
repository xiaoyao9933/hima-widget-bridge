package com.eric.himawidgetbridge;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

/** Read only local enrollment diagnostics, after the user has finished physical I/O. */
public final class NfcDiagnosticInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); Context context = getTargetContext();
        SharedPreferences data = context.getSharedPreferences(NfcIoDiagnostic.PREFS, Context.MODE_PRIVATE);
        for (String key : new String[] {"action", "stage", "exception"}) result.putString(key, data.getString(key, ""));
        for (String key : new String[] {"write_returned", "writable", "formatable", "readback_null", "readback_marker_valid"})
            result.putBoolean(key, data.getBoolean(key, false));
        for (String key : new String[] {"capacity", "readback_records"}) result.putInt(key, data.getInt(key, -1));
        result.putBoolean("lock_registered", new NfcStore(context).registered());
        result.putBoolean("lock_nfc_enabled", new NfcStore(context).enabled());
        result.putBoolean("vehicle_action", false); result.putString("scope", "local enrollment diagnostics only");
        finish(-1, result);
    }
}
