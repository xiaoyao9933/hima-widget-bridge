package com.eric.himawidgetbridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.widget.Toast;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit write confirmation and verified registration. Never dispatches a vehicle action. */
final class NfcEnrollment implements NfcAdapter.ReaderCallback {
    private enum Step { IDLE, READ, READING, CONFIRM, WRITE, WRITING, VERIFY, VERIFYING }
    private static final OperationLease ENROLLMENTS = new OperationLease();
    private final Activity activity;
    private final NfcAdapter adapter;
    private final NfcStore store;
    private final Runnable changed;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Step step = Step.IDLE;
    private AlertDialog dialog;
    private boolean resumed;
    private int generation;
    private byte[] expectedUid, token;
    private NfcEnrollmentPolicy policy;
    private String pendingResult;

    NfcEnrollment(Activity activity, NfcStore store, Runnable changed) {
        this.activity = activity; this.store = store; this.changed = changed;
        adapter = NfcAdapter.getDefaultAdapter(activity);
    }
    boolean busy() { return step != Step.IDLE; }
    void onResume() {
        resumed = true;
        if (pendingResult != null) { toast(pendingResult); pendingResult = null; }
    }
    void onPause() {
        resumed = false;
        disableReader(); dismiss();
        // A confirmed physical write is allowed to finish and save its disabled registration.
        if (step == Step.WRITING && policy != null) policy.pauseDuringWrite();
        else { generation++; clearPending(); step = Step.IDLE; ENROLLMENTS.release(this); }
    }
    void onDestroy() { worker.shutdown(); if (step != Step.WRITING) { clearPending(); ENROLLMENTS.release(this); } }
    void start() {
        if (busy()) { toast("请先完成或取消当前标签录入"); return; }
        if (adapter == null || !adapter.isEnabled()) { toast("请先开启手机 NFC"); return; }
        if (!ENROLLMENTS.acquire(this)) { toast("另一枚标签正在录入，请先完成或取消"); return; }
        NfcIoDiagnostic.stage(activity, store.action(), "waiting_tag", false);
        generation++; step = Step.READ;
        dialog = new AlertDialog.Builder(activity).setTitle("读取 NFC 标签")
            .setMessage("将可写标签贴近手机 NFC 感应区。\n\n本次只读取标签信息，不写入，也不操作车辆。")
            .setNegativeButton("取消", (d, w) -> cancel()).create();
        dialog.setOnCancelListener(d -> cancel()); dialog.show();
        try {
            adapter.enableReaderMode(activity, this, NfcAdapter.FLAG_READER_NFC_A
                    | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_F
                    | NfcAdapter.FLAG_READER_NFC_V | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null);
        } catch (RuntimeException e) { fail("无法开启标签读取，请检查手机 NFC 设置"); }
    }
    @Override public void onTagDiscovered(Tag tag) { activity.runOnUiThread(() -> receive(tag)); }
    private void receive(Tag tag) {
        if (!resumed || activity.isFinishing()
                || (step != Step.READ && step != Step.WRITE && step != Step.VERIFY)) return;
        byte[] uid = tag.getId();
        if (store.conflicts(uid)) { fail("此标签已用于另一项操作，请使用另一枚标签"); return; }
        if (uid == null || uid.length == 0 || uid.length > 32) { fail("此标签无法提供稳定身份，请换用普通可写 NFC 标签"); return; }
        if ((step == Step.WRITE || step == Step.VERIFY) && !Arrays.equals(expectedUid, uid)) {
            toast("请碰触刚才确认的同一枚标签，未操作此标签"); return;
        }
        final int attempt = generation;
        if (step == Step.VERIFY) {
            verify(tag, attempt);
            return;
        }
        if (step == Step.READ) {
            step = Step.READING;
            worker.execute(() -> {
                try {
                    NfcIoDiagnostic.stage(activity, store.action(), "inspect_connect", false);
                    Info info = inspect(tag);
                    NfcIoDiagnostic.info(activity, store.action(), info.max, info.writable, info.formatable);
                    activity.runOnUiThread(() -> { if (attempt == generation && resumed) confirm(info); });
                } catch (Exception e) { NfcIoDiagnostic.failure(activity, e); activity.runOnUiThread(() -> {
                    if (attempt == generation && resumed) fail("读取失败，请保持标签贴近手机后重新录入");
                }); }
            });
        } else {
            step = Step.WRITING; dismiss();
            dialog = new AlertDialog.Builder(activity).setTitle("正在写入标签")
                .setMessage("请保持标签贴近手机，等待写入完成。\n录入过程不会操作车辆。")
                .setCancelable(false).create(); dialog.show();
            final byte[] writeUid = expectedUid.clone(), writeToken = token.clone();
            final NfcEnrollmentPolicy writePolicy = policy;
            worker.execute(() -> {
                try {
                    final boolean immediateMatches = write(tag, writeToken);
                    activity.runOnUiThread(() -> {
                        if (attempt != generation || step != Step.WRITING) return;
                        int outcome = writePolicy.writeFinished(true, immediateMatches);
                        if (outcome == NfcEnrollmentPolicy.VERIFIED) saveBinding(writeUid, writeToken);
                        else if (outcome == NfcEnrollmentPolicy.RETAP) awaitVerification();
                        else finish("写入接口已返回，但录入页面曾离开，尚未验证。请重新录入。");
                    });
                } catch (Exception e) {
                    NfcIoDiagnostic.failure(activity, e);
                    activity.runOnUiThread(() -> {
                        if (attempt != generation || step != Step.WRITING) return;
                        writePolicy.writeFinished(false, false);
                        finish("写入未完成，请保持标签贴近手机后重新录入。");
                    });
                }
            });
        }
    }
    private Info inspect(Tag tag) throws IOException, android.nfc.FormatException {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) return new Info(tag.getId(), -1, NdefFormatable.get(tag) != null, false, true);
        try {
            ndef.connect();
            NdefMessage existing = ndef.getNdefMessage();
            return new Info(tag.getId(), ndef.getMaxSize(), false,
                    existing != null && existing.getByteArrayLength() > 3, ndef.isWritable());
        } finally { try { ndef.close(); } catch (IOException ignored) {} }
    }
    private void confirm(Info info) {
        int required = NfcStore.message(store.action(), new byte[16], activity.getPackageName()).getByteArrayLength();
        if (!info.writable) { fail("此标签为只读，无法录入。请使用可写标签。"); return; }
        if (info.max < 0 && !info.formatable) { fail("此标签不支持 NDEF 写入，请换用普通可写 NFC 标签。"); return; }
        if (info.max >= 0 && info.max < required) { fail("标签容量不足，需要至少 " + required + " 字节。"); return; }
        step = Step.CONFIRM; dismiss();
        String detail = info.formatable ? "检测到可格式化的空白标签。" : "检测到可写标签，容量 " + info.max + " 字节。";
        dialog = new AlertDialog.Builder(activity).setTitle("确认写入此标签？")
            .setMessage("用途：" + ControlAction.label(store.action()) + "\n" + detail + (info.hasContent ? "\n标签已有内容。" : "")
                + "\n\n写入会覆盖标签原有 NDEF 内容，并绑定到本机。标签仍可重新写入。"
                + "\n写入内容仅包含快捷触发标识和应用信息。录入不操作车辆，完成后 NFC 触发默认关闭。")
            .setNegativeButton("取消", (d, w) -> cancel())
            .setPositiveButton("确认并准备写入", (d, w) -> {
                expectedUid = info.uid.clone(); token = new byte[16]; new SecureRandom().nextBytes(token);
                policy = new NfcEnrollmentPolicy(expectedUid, token);
                step = Step.WRITE;
                dialog = new AlertDialog.Builder(activity).setTitle("再次碰触同一标签")
                    .setMessage("先移开标签，再将同一枚标签贴近手机。\n请保持贴近，等待写入完成。")
                    .setNegativeButton("取消", (ignored, which) -> cancel()).create();
                dialog.setOnCancelListener(ignored -> cancel()); dialog.show();
            }).create();
        dialog.setOnCancelListener(d -> cancel()); dialog.show();
    }
    private boolean write(Tag tag, byte[] marker) throws IOException, android.nfc.FormatException {
        NfcIoDiagnostic.stage(activity, store.action(), "write_connect", false);
        if (store.conflicts(tag.getId())) throw new IOException("tag_already_bound");
        NdefMessage message = NfcStore.message(store.action(), marker, activity.getPackageName());
        Ndef ndef = Ndef.get(tag);
        if (ndef != null) {
            try {
                ndef.connect();
                if (!ndef.isWritable() || ndef.getMaxSize() < message.getByteArrayLength()) throw new IOException("unwritable");
                NfcIoDiagnostic.stage(activity, store.action(), "write_message", false);
                ndef.writeNdefMessage(message);
                NfcIoDiagnostic.stage(activity, store.action(), "readback", true);
                // Some phones return empty data immediately after a successful write. A fresh
                // discovery can verify it; write success alone never creates a registration.
                try {
                    NdefMessage read = ndef.getNdefMessage();
                    NfcIoDiagnostic.stage(activity, store.action(), "compare_marker", true);
                    NfcIoDiagnostic.readback(activity, read, store.action());
                    return Arrays.equals(marker, NfcStore.marker(read, store.action()));
                } catch (IOException | android.nfc.FormatException | SecurityException e) {
                    NfcIoDiagnostic.failure(activity, e);
                    return false;
                }
            } finally { try { ndef.close(); } catch (IOException ignored) {} }
        } else {
            NdefFormatable formatable = NdefFormatable.get(tag);
            if (formatable == null) throw new IOException("unsupported");
            try {
                formatable.connect(); NfcIoDiagnostic.stage(activity, store.action(), "format_message", false);
                formatable.format(message); NfcIoDiagnostic.stage(activity, store.action(), "format_returned", true);
            }
            finally { try { formatable.close(); } catch (IOException ignored) {} }
            // Formatting changes the technology list. Verify Ndef on a new discovery before binding.
            return false;
        }
    }
    private void awaitVerification() {
        if (!resumed || activity.isFinishing()) {
            finish("写入接口已返回，但尚未验证。请返回录入页面重新录入。");
            return;
        }
        step = Step.VERIFY; dismiss();
        NfcIoDiagnostic.stage(activity, store.action(), "waiting_verify_retap", true);
        dialog = new AlertDialog.Builder(activity).setTitle("再次碰触，验证写入")
            .setMessage("写入接口已返回，需要重新读取确认。\n\n先完全移开标签，再碰触同一枚标签并保持贴近。验证通过才保存绑定；本次不会操作车辆。")
            .setNegativeButton("取消", (d, w) -> cancel()).create();
        dialog.setOnCancelListener(d -> cancel()); dialog.show(); changed.run();
    }
    private void verify(Tag tag, int attempt) {
        step = Step.VERIFYING; dismiss();
        dialog = new AlertDialog.Builder(activity).setTitle("正在验证标签")
            .setMessage("请保持标签贴近手机。\n本次只读取，不写入，也不操作车辆。")
            .setNegativeButton("取消", (d, w) -> cancel()).create();
        dialog.setOnCancelListener(d -> cancel()); dialog.show();
        worker.execute(() -> {
            try {
                byte[] marker = readMarker(tag);
                activity.runOnUiThread(() -> {
                    if (attempt != generation || !resumed || step != Step.VERIFYING) return;
                    int outcome = policy.verify(tag.getId(), marker);
                    if (outcome == NfcEnrollmentPolicy.VERIFIED) saveBinding(expectedUid, token);
                    else finish("重新读取的内容未匹配，未保存绑定。请重新录入或换一枚标签。");
                });
            } catch (Exception e) {
                NfcIoDiagnostic.failure(activity, e);
                activity.runOnUiThread(() -> {
                    if (attempt == generation && resumed && step == Step.VERIFYING)
                        finish("重新读取验证失败，未保存绑定。请保持标签贴近手机后重新录入。");
                });
            }
        });
    }
    private byte[] readMarker(Tag tag) throws IOException, android.nfc.FormatException {
        NfcIoDiagnostic.stage(activity, store.action(), "verify_connect", true);
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) throw new IOException("verify_unsupported");
        try {
            ndef.connect();
            NfcIoDiagnostic.stage(activity, store.action(), "verify_read", true);
            NdefMessage read = ndef.getNdefMessage();
            NfcIoDiagnostic.stage(activity, store.action(), "verify_compare", true);
            NfcIoDiagnostic.readback(activity, read, store.action());
            return NfcStore.marker(read, store.action());
        } finally { try { ndef.close(); } catch (IOException ignored) {} }
    }
    private void saveBinding(byte[] uid, byte[] marker) {
        NfcIoDiagnostic.stage(activity, store.action(), "save_binding", true);
        boolean saved = store.bind(uid, marker);
        NfcIoDiagnostic.stage(activity, store.action(), saved ? "verified_saved" : "save_failed", true);
        finish(saved ? "标签已录入。确认对应快捷项已开启后，再开启 NFC 触发。"
                : "标签已验证，但本机保存失败，请重新录入。");
    }
    private void finish(String message) {
        clearPending(); disableReader(); dismiss(); step = Step.IDLE; ENROLLMENTS.release(this); changed.run();
        if (resumed && !activity.isFinishing()) toast(message); else pendingResult = message;
    }
    private void clearPending() {
        if (policy != null) policy.cancel();
        policy = null; expectedUid = null; token = null;
    }
    private void cancel() { generation++; clearPending(); step = Step.IDLE; ENROLLMENTS.release(this); disableReader(); dismiss(); changed.run(); }
    private void fail(String message) { cancel(); toast(message); }
    private void disableReader() { if (adapter != null) try { adapter.disableReaderMode(activity); } catch (RuntimeException ignored) {} }
    private void dismiss() { if (dialog != null) { dialog.dismiss(); dialog = null; } }
    private void toast(String message) { Toast.makeText(activity, message, Toast.LENGTH_LONG).show(); }
    private static final class Info {
        final byte[] uid; final int max; final boolean formatable, hasContent, writable;
        Info(byte[] uid, int max, boolean formatable, boolean hasContent, boolean writable) {
            this.uid = uid.clone(); this.max = max; this.formatable = formatable;
            this.hasContent = hasContent; this.writable = writable;
        }
    }
}
