package com.eric.himawidgetbridge;

public final class KeySessionPolicyTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message); checks++;
    }
    public static void main(String[] args) {
        KeySessionPolicy p = new KeySessionPolicy();
        p.observe(KeyReadinessPolicy.CONNECTED, "key-a", 1000);
        check(p.requiresWarmup(1000), "connected notification with unknown history needs warmup");
        long first = p.generation();
        check(!p.warmupLaunched(first - 1, 1000), "stale warmup token rejected");
        check(p.requiresWarmup(5000), "failed launch does not prepare session");
        check(p.warmupLaunched(first, 5000), "successful ordinary launch registers current session");
        check(!p.requiresWarmup(6999) && p.retryRemainingMillis(6999) == 1, "fast repeat waits without launching MAIN again");
        check(!p.requiresWarmup(7000) && p.retryRemainingMillis(7000) == 0, "new user request may use the launched session after minimum interval");
        p.observe(KeyReadinessPolicy.DISCONNECTED, "key-a", 8000);
        check(p.generation() == first && !p.requiresWarmup(8000), "leaving car preserves same initialized key session");
        p.posted("key-a", 9000); p.observe(KeyReadinessPolicy.CONNECTED, "key-a", 9000);
        check(p.generation() == first && !p.requiresWarmup(9000), "notification update and reconnect preserve session");
        p.removed("unrelated");
        check(!p.requiresWarmup(10000), "unrelated notification removal ignored");
        p.removed("key-a"); p.posted("key-a", 11000);
        p.observe(KeyReadinessPolicy.CONNECTED, "key-a", 11000);
        check(p.generation() != first && p.requiresWarmup(11000), "same key removed and recreated requires warmup");
        check(!p.warmupLaunched(first, 11000), "old session cannot authorize rebuilt notification");
        long rebuilt = p.generation(); p.warmupLaunched(rebuilt, 12000);
        p.observe(KeyReadinessPolicy.CONNECTED, "key-b", 15000);
        check(p.requiresWarmup(15000), "replacement key identity invalidates launch record");
        p.warmupLaunched(p.generation(), 16000); p.resetHistory();
        p.observe(KeyReadinessPolicy.CONNECTED, "key-b", 20000);
        check(p.requiresWarmup(20000), "listener history loss requires warmup even if connected");
        p.warmupLaunched(p.generation(), 20000);
        p.observe(KeyReadinessPolicy.UNKNOWN, null, 23000);
        p.observe(KeyReadinessPolicy.CONNECTED, "key-b", 24000);
        check(p.requiresWarmup(24000), "ambiguous or failed read invalidates history");
        KeySessionPolicy absent = new KeySessionPolicy();
        absent.observe(KeyReadinessPolicy.ABSENT, null, 1000);
        check(absent.warmupLaunched(absent.generation(), 1000), "ordinary launch may initialize absent session");
        check(absent.requiresWarmup(1000) && absent.retryRemainingMillis(1000) == 2000,
                "absent notification after a successful MAIN launch also prevents immediate repeated warmup");
        absent.posted("created-by-main", 1500);
        absent.observe(KeyReadinessPolicy.CONNECTED, "created-by-main", 1500);
        check(!absent.requiresWarmup(2999) && absent.retryRemainingMillis(2999) == 1
                && absent.retryRemainingMillis(3000) == 0, "first notification after MAIN uses bounded pending launch");
        absent.removed("created-by-main"); absent.posted("created-by-main", 4000);
        check(absent.requiresWarmup(4000), "later restart cannot reuse absent-session launch");
        KeySessionPolicy late = new KeySessionPolicy(); late.warmupLaunched(late.generation(), 1000);
        late.posted("late", 31001);
        check(late.requiresWarmup(34000), "first notification after launch window is not trusted");
        KeySessionPolicy lost = new KeySessionPolicy(); lost.warmupLaunched(lost.generation(), 1000);
        lost.resetHistory(); lost.posted("new", 1500);
        check(lost.requiresWarmup(4000), "listener reset cancels pending absent-session launch");
        KeySessionPolicy vanished = new KeySessionPolicy(); vanished.posted("key", 1000);
        vanished.warmupLaunched(vanished.generation(), 1000);
        vanished.observe(KeyReadinessPolicy.ABSENT, null, 4000); vanished.posted("key", 5000);
        check(vanished.requiresWarmup(5000), "fresh empty snapshot invalidates old session");
        check(new KeyReadinessPolicy().start(KeyReadinessPolicy.DISCONNECTED, false, 1000)
                == KeyReadinessPolicy.PREWARM, "unprepared disconnected session initializes before waiting");
        KeyReadinessPolicy waiting = new KeyReadinessPolicy();
        check(waiting.start(KeyReadinessPolicy.DISCONNECTED, true, 1000) == KeyReadinessPolicy.WAIT, "prepared disconnected session waits");
        check(waiting.poll(KeyReadinessPolicy.CONNECTED, false, 2000) == KeyReadinessPolicy.PREWARM, "session replacement while waiting cannot dispatch");
        check(waiting.poll(KeyReadinessPolicy.CONNECTED, true, 5000) == KeyReadinessPolicy.NONE, "prewarm result never revives old request");
        System.out.println("KeySessionPolicyTest: " + checks + " checks passed");
    }
}
