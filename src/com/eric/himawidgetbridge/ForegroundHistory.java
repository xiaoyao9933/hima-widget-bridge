package com.eric.himawidgetbridge;

/** Replayed request-local resume events; a later return cannot erase an earlier interruption. */
final class ForegroundHistory {
    private final long returnAt;
    private boolean originalSeen, mainSeen, interrupted;
    private int front = AutoWarmPolicy.HELPER;
    ForegroundHistory(long returnAt) { this.returnAt = returnAt; }
    void resume(int value, long at) {
        if (value == AutoWarmPolicy.OTHER || value == AutoWarmPolicy.UNKNOWN
                || value == AutoWarmPolicy.HELPER && originalSeen && at < returnAt
                || value == AutoWarmPolicy.ENTRY && mainSeen) interrupted = true;
        if (value == AutoWarmPolicy.ENTRY || value == AutoWarmPolicy.MAIN) originalSeen = true;
        if (value == AutoWarmPolicy.MAIN) mainSeen = true;
        front = value;
    }
    boolean interrupted() { return interrupted; }
    int front() { return interrupted ? AutoWarmPolicy.OTHER : front; }
}
