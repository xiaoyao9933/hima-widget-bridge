package com.eric.himawidgetbridge;

public final class NfcPolicyTest {
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        byte[] uid = {4, 10, 11, 12};
        byte[] token = new byte[16]; token[0] = 42;
        String expected = "2A000000000000000000000000000000";
        check(NfcPolicy.matches(uid, token, "040A0B0C", expected), "registered tag and token must match");
        check(!NfcPolicy.matches(new byte[]{4, 10, 11, 13}, token, "040A0B0C", expected), "different tag must reject");
        byte[] other = token.clone(); other[1] = 1;
        check(!NfcPolicy.matches(uid, other, "040A0B0C", expected), "wrong token must reject");
        check(!NfcPolicy.matches(null, token, "040A0B0C", expected), "missing tag must reject");
        check(!NfcPolicy.matches(uid, new byte[15], "040A0B0C", expected), "malformed token must reject");
        check(!NfcPolicy.matches(uid, token, "", expected), "unregistered tag must reject");
        check(NfcPolicy.remainingSeconds(1000, -1, 3, 3) == 0, "first scan must be allowed");
        check(NfcPolicy.remainingSeconds(1001, 1000, 3, 3) == 10, "repeated scan must be protected");
        check(NfcPolicy.remainingSeconds(10999, 1000, 3, 3) == 1, "protection rounds up");
        check(NfcPolicy.remainingSeconds(11000, 1000, 3, 3) == 0, "expired scan protection must release");
        check(NfcPolicy.remainingSeconds(1001, 1000, 4, 3) == 0, "new boot must not keep old protection");
        check(NfcPolicy.remainingSeconds(100, 1000, 3, 3) == 0, "monotonic clock reset must not block indefinitely");
        System.out.println("NfcPolicyTest: 12 checks passed");
    }
}
