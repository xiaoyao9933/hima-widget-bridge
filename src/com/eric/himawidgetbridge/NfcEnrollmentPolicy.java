package com.eric.himawidgetbridge;

import java.util.Arrays;

/** Only a matching read, following a completed write, permits registration. */
final class NfcEnrollmentPolicy {
    static final int RETAP = 1, VERIFIED = 2, FAILED = 3, WRONG_TAG = 4, IGNORED = 5;
    private static final int WRITING = 0, VERIFYING = 1, FINISHED = 2;
    private final byte[] uid, marker;
    private int state = WRITING;
    private boolean leftForeground;

    NfcEnrollmentPolicy(byte[] uid, byte[] marker) {
        this.uid = uid.clone(); this.marker = marker.clone();
    }
    int writeFinished(boolean writeReturned, boolean immediateMatches) {
        if (state != WRITING) return IGNORED;
        if (!writeReturned) { state = FINISHED; return FAILED; }
        if (immediateMatches) { state = FINISHED; return VERIFIED; }
        if (leftForeground) { state = FINISHED; return FAILED; }
        state = VERIFYING; return RETAP;
    }
    int verify(byte[] foundUid, byte[] foundMarker) {
        if (state != VERIFYING) return IGNORED;
        if (!Arrays.equals(uid, foundUid)) return WRONG_TAG;
        state = FINISHED;
        return Arrays.equals(marker, foundMarker) ? VERIFIED : FAILED;
    }
    void cancel() { state = FINISHED; }
    void pauseDuringWrite() { if (state == WRITING) leftForeground = true; }
}
