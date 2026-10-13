package com.eric.himawidgetbridge;

/** One immutable action per request. Only missing legacy extras default to lock. */
public final class ControlAction {
    public static final String LOCK = "lock", TRUNK = "trunk", EXTRA = "control_action";
    private ControlAction() {}
    public static String resolve(String raw, boolean present) {
        if (!present) return LOCK;
        return LOCK.equals(raw) || TRUNK.equals(raw) ? raw : null;
    }
    public static String label(String action) { return TRUNK.equals(action) ? "后备箱" : "切换车锁"; }
    public static String mime(String action) {
        if (LOCK.equals(action)) return NfcPolicy.MIME;
        return TRUNK.equals(action) ? "application/vnd.eric.hima.trunk" : null;
    }
    public static String fromMime(String mime) {
        if (NfcPolicy.MIME.equals(mime)) return LOCK;
        return mime(TRUNK).equals(mime) ? TRUNK : null;
    }
}
