package com.eric.himawidgetbridge;

public final class AutoWarmPolicyTest {
    private static int checks;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static AutoWarmPolicy started() { AutoWarmPolicy p = new AutoWarmPolicy(); check(p.start(1000) == AutoWarmPolicy.WAIT, "start waits"); return p; }
    public static void main(String[] args) {
        AutoWarmPolicy p = started();
        check(p.poll(AutoWarmPolicy.ENTRY, true, KeyReadinessPolicy.ABSENT, false, -1, false, 1100) == AutoWarmPolicy.WAIT, "entry does not imply page ready");
        check(p.poll(AutoWarmPolicy.MAIN, true, KeyReadinessPolicy.CONNECTED, true, 1500, false, 3499) == AutoWarmPolicy.WAIT, "MAIN observed for less than two seconds waits");
        check(p.poll(AutoWarmPolicy.MAIN, true, KeyReadinessPolicy.CONNECTED, true, 1500, false, 3500) == AutoWarmPolicy.RETURN, "initialized session requests return once");
        check(p.poll(AutoWarmPolicy.MAIN, true, KeyReadinessPolicy.CONNECTED, true, 1500, false, 3501) == AutoWarmPolicy.WAIT, "background waiting cannot execute vehicle");
        check(p.poll(AutoWarmPolicy.HELPER, true, KeyReadinessPolicy.CONNECTED, true, 1500, true, 3600) == AutoWarmPolicy.WAIT, "return needs stable actual focus");
        check(p.poll(AutoWarmPolicy.HELPER, true, KeyReadinessPolicy.CONNECTED, true, 1500, true, 3900) == AutoWarmPolicy.READY, "one completion after focused return");
        check(p.poll(AutoWarmPolicy.HELPER, true, KeyReadinessPolicy.CONNECTED, true, 1500, true, 4000) == AutoWarmPolicy.NONE, "late poll cannot duplicate completion");
        AutoWarmPolicy home = started();
        check(home.poll(AutoWarmPolicy.OTHER, true, KeyReadinessPolicy.CONNECTED, true, 1500, false, 2000) == AutoWarmPolicy.FAIL, "Home or other foreground app cancels");
        check(home.poll(AutoWarmPolicy.HELPER, true, KeyReadinessPolicy.CONNECTED, true, 1500, true, 4000) == AutoWarmPolicy.NONE, "return after Home cannot revive request");
        AutoWarmPolicy back = started();
        check(back.poll(AutoWarmPolicy.HELPER, true, KeyReadinessPolicy.CONNECTED, true, 1500, true, 2000) == AutoWarmPolicy.FAIL, "manual back before automatic return cancels");
        AutoWarmPolicy locked = started();
        check(locked.poll(AutoWarmPolicy.MAIN, false, KeyReadinessPolicy.CONNECTED, true, 1500, false, 2000) == AutoWarmPolicy.FAIL, "locked/radio off/permission lost invalidates operation");
        AutoWarmPolicy lost = started();
        check(lost.poll(AutoWarmPolicy.MAIN, true, KeyReadinessPolicy.UNKNOWN, false, 1500, false, 2000) == AutoWarmPolicy.FAIL, "listener failure cancels");
        AutoWarmPolicy newKey = started();
        check(newKey.poll(AutoWarmPolicy.MAIN, true, KeyReadinessPolicy.CONNECTED, false, 1500, false, 3500) == AutoWarmPolicy.FAIL, "new unprepared key generation cannot continue");
        AutoWarmPolicy missing = started();
        check(missing.poll(AutoWarmPolicy.MAIN, true, KeyReadinessPolicy.ABSENT, false, 1500, false, 3500) == AutoWarmPolicy.WAIT, "initially absent key waits for initialization");
        check(missing.poll(AutoWarmPolicy.MAIN, true, KeyReadinessPolicy.ABSENT, false, 1500, false, 13000) == AutoWarmPolicy.FAIL, "initialization bounded to twelve seconds");
        AutoWarmPolicy blocked = started();
        blocked.poll(AutoWarmPolicy.MAIN, true, KeyReadinessPolicy.DISCONNECTED, true, 1500, false, 3500);
        check(blocked.poll(AutoWarmPolicy.MAIN, true, KeyReadinessPolicy.DISCONNECTED, true, 1500, false, 5000) == AutoWarmPolicy.FAIL, "blocked return times out without execution");
        AutoWarmPolicy cancel = started(); cancel.cancel();
        check(cancel.poll(AutoWarmPolicy.HELPER, true, KeyReadinessPolicy.CONNECTED, true, 1500, true, 4000) == AutoWarmPolicy.NONE, "cancel permanently invalidates old request");
        AutoWarmPolicy transientOwn = started();
        check(transientOwn.poll(AutoWarmPolicy.HELPER, true, KeyReadinessPolicy.ABSENT, false, -1, true, 1300) == AutoWarmPolicy.WAIT, "source focus before departure may settle");
        AutoWarmPolicy earlyBack = started();
        earlyBack.poll(AutoWarmPolicy.ENTRY, true, KeyReadinessPolicy.ABSENT, false, -1, false, 1100);
        check(earlyBack.poll(AutoWarmPolicy.HELPER, true, KeyReadinessPolicy.ABSENT, false, -1, true, 1200) == AutoWarmPolicy.FAIL, "manual return before MAIN cancels too");
        check(earlyBack.poll(AutoWarmPolicy.MAIN, true, KeyReadinessPolicy.CONNECTED, true, 1500, false, 4000) == AutoWarmPolicy.NONE, "late MAIN cannot revive early cancellation");
        ForegroundHistory preMain = new ForegroundHistory(3500);
        preMain.resume(AutoWarmPolicy.ENTRY, 1100); preMain.resume(AutoWarmPolicy.HELPER, 1200);
        preMain.resume(AutoWarmPolicy.MAIN, 1500); preMain.resume(AutoWarmPolicy.HELPER, 3600);
        check(preMain.interrupted(), "automatic return cannot erase earlier user return between polls");
        ForegroundHistory normalEvents = new ForegroundHistory(3500);
        normalEvents.resume(AutoWarmPolicy.HELPER, 1000); normalEvents.resume(AutoWarmPolicy.ENTRY, 1100);
        normalEvents.resume(AutoWarmPolicy.MAIN, 1500); normalEvents.resume(AutoWarmPolicy.HELPER, 3600);
        check(!normalEvents.interrupted() && normalEvents.front() == AutoWarmPolicy.HELPER, "authorized return permits its new source resume");
        ForegroundHistory homeEvents = new ForegroundHistory(3500);
        homeEvents.resume(AutoWarmPolicy.MAIN, 1500); homeEvents.resume(AutoWarmPolicy.OTHER, 3400);
        homeEvents.resume(AutoWarmPolicy.HELPER, 3600);
        check(homeEvents.interrupted(), "Home between final poll and return remains canceled");
        OperationLease lease = new OperationLease(); Object a = new Object(), b = new Object();
        check(lease.acquire(a), "first explicit request owns operation");
        check(!lease.acquire(b), "second request cannot run beside prewarm");
        lease.release(b); check(!lease.acquire(b), "rejected request cannot release active owner");
        lease.release(a); check(lease.acquire(b), "cancellation releases operation for new explicit request");
        System.out.println("AutoWarmPolicyTest: " + checks + " checks passed");
    }
}
