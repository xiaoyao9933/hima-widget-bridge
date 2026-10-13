package com.eric.himawidgetbridge;

public final class AutoWarmRecordTest {
    private static int checks;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    public static void main(String[] args) {
        KeySessionPolicy p = new KeySessionPolicy(); p.posted("same", 1000);
        long token = p.recordWarmupLaunched(p.generation(), 1000);
        check(token > 0 && !p.requiresWarmup(4000), "launch owns a preparation record");
        p.cancelWarmup(token);
        check(p.requiresWarmup(4000) && p.retryRemainingMillis(4000) == 0, "canceled launch cannot let next request skip preparation");
        long next = p.recordWarmupLaunched(p.generation(), 5000);
        p.cancelWarmup(token);
        check(next != token && !p.requiresWarmup(7000), "stale cancellation cannot erase a later launch");
        KeySessionPolicy absent = new KeySessionPolicy();
        long before = absent.generation(), absentToken = absent.recordWarmupLaunched(before, 1000);
        absent.posted("first", 1500);
        check(absent.generation() != before && !absent.requiresWarmup(4000), "first key retains launch across generation change");
        absent.cancelWarmup(absentToken);
        check(absent.requiresWarmup(4000), "failed launch after first key arrives is still revoked by token");
        long accepted = absent.recordWarmupLaunched(absent.generation(), 5000);
        check(accepted > 0 && !absent.requiresWarmup(7000), "successful flow keeps its record when no cancellation occurs");
        absent.resetHistory(); absent.posted("new", 8000);
        long newer = absent.recordWarmupLaunched(absent.generation(), 8000);
        absent.cancelWarmup(accepted);
        check(newer != accepted && !absent.requiresWarmup(10000), "listener reset does not reuse launch tokens");
        check(absent.recordWarmupLaunched(absent.generation() - 1, 10000) == 0, "stale session cannot create a launch token");
        System.out.println("AutoWarmRecordTest: " + checks + " checks passed");
    }
}
