package com.eric.himawidgetbridge;

public final class ListenerPreparationPolicyTest {
    private static int checks;
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    public static void main(String[] args) {
        check(new ListenerPreparationPolicy().start(false, false, 1000) == ListenerPreparationPolicy.FAIL, "no permission cannot reset binding");
        ListenerPreparationPolicy ready = new ListenerPreparationPolicy();
        check(ready.start(true, true, 1000) == ListenerPreparationPolicy.READY, "healthy listener not reset");
        check(ready.poll(true, true, true, 1001) == ListenerPreparationPolicy.NONE, "only one completion");
        ListenerPreparationPolicy cold = new ListenerPreparationPolicy();
        check(cold.start(true, false, 1000) == ListenerPreparationPolicy.REBIND, "dead service requests recovery");
        check(cold.poll(true, false, true, 1001) == ListenerPreparationPolicy.WAIT, "initial binding is asynchronous");
        check(cold.poll(true, true, true, 1500) == ListenerPreparationPolicy.READY, "late connected callback resumes current request");
        check(cold.poll(true, true, true, 1501) == ListenerPreparationPolicy.NONE, "connected cannot repeat completion");
        ListenerPreparationPolicy timeout = new ListenerPreparationPolicy(); timeout.start(true, false, 1000);
        check(timeout.poll(true, false, true, 8999) == ListenerPreparationPolicy.WAIT, "bounded recovery waits up to eight seconds");
        check(timeout.poll(true, false, true, 9000) == ListenerPreparationPolicy.FAIL, "dead listener times out");
        check(timeout.poll(true, true, true, 9001) == ListenerPreparationPolicy.NONE, "late result cannot revive timed-out request");
        ListenerPreparationPolicy revoked = new ListenerPreparationPolicy(); revoked.start(true, false, 1000);
        check(revoked.poll(false, true, true, 1100) == ListenerPreparationPolicy.FAIL, "permission loss overrides connected callback");
        ListenerPreparationPolicy home = new ListenerPreparationPolicy(); home.start(true, false, 1000);
        check(home.poll(true, false, false, 1100) == ListenerPreparationPolicy.FAIL, "Home stops pending recovery");
        check(home.poll(true, true, true, 1200) == ListenerPreparationPolicy.NONE, "returning cannot revive old request");
        ListenerPreparationPolicy cancel = new ListenerPreparationPolicy(); cancel.start(true, false, 1000); cancel.cancel();
        check(cancel.poll(true, true, true, 1200) == ListenerPreparationPolicy.NONE, "cancel drops late listener callback");
        System.out.println("ListenerPreparationPolicyTest: " + checks + " checks passed");
    }
}
