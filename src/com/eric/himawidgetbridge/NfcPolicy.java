package com.eric.himawidgetbridge;

final class NfcPolicy {
    static final String MIME = "application/vnd.eric.hima.toggle";
    static boolean matches(byte[] uid, byte[] payload, String savedUid, String savedToken) {
        return uid != null && uid.length > 0 && uid.length <= 32
                && payload != null && payload.length == 16
                && hex(uid).equals(savedUid) && hex(payload).equals(savedToken);
    }
    static String hex(byte[] value) {
        char[] digits = "0123456789ABCDEF".toCharArray();
        char[] result = new char[value.length * 2];
        for (int i = 0; i < value.length; i++) {
            result[i * 2] = digits[(value[i] & 255) >>> 4];
            result[i * 2 + 1] = digits[value[i] & 15];
        }
        return new String(result);
    }
    static int remainingSeconds(long now, long previous, int boot, int previousBoot) {
        if (previous < 0 || boot != previousBoot || now < previous) return 0;
        long elapsed = now - previous;
        return elapsed >= 10000 ? 0 : (int) ((10000 - elapsed + 999) / 1000);
    }
}
