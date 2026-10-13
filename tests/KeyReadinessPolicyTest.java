package com.eric.himawidgetbridge;

public final class KeyReadinessPolicyTest {
    private static int checks;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message); checks++;
    }
    public static void main(String[] args) {
        KeyReadinessPolicy connected = new KeyReadinessPolicy();
        check(connected.start(KeyReadinessPolicy.CONNECTED, true, 1000) == KeyReadinessPolicy.PROCEED, "connected skips warmup");
        check(connected.poll(KeyReadinessPolicy.CONNECTED, true, 1001) == KeyReadinessPolicy.NONE, "one readiness result only");
        KeyReadinessPolicy waiting = new KeyReadinessPolicy();
        check(waiting.start(KeyReadinessPolicy.DISCONNECTED, true, 1000) == KeyReadinessPolicy.WAIT, "initialized but disconnected waits");
        check(waiting.poll(KeyReadinessPolicy.DISCONNECTED, true, 10999) == KeyReadinessPolicy.WAIT, "wait lasts ten seconds");
        check(waiting.poll(KeyReadinessPolicy.DISCONNECTED, true, 11000) == KeyReadinessPolicy.PROCEED, "timeout leaves transport choice to original");
        check(waiting.poll(KeyReadinessPolicy.CONNECTED, true, 11001) == KeyReadinessPolicy.NONE, "timeout cannot double-dispatch");
        KeyReadinessPolicy reconnected = new KeyReadinessPolicy();
        reconnected.start(KeyReadinessPolicy.DISCONNECTED, true, 1000);
        check(reconnected.poll(KeyReadinessPolicy.CONNECTED, true, 2000) == KeyReadinessPolicy.PROCEED, "background reconnect proceeds early");
        KeyReadinessPolicy absent = new KeyReadinessPolicy();
        check(absent.start(KeyReadinessPolicy.ABSENT, true, 1000) == KeyReadinessPolicy.PREWARM, "healthy listener with no key notification initializes original");
        check(absent.poll(KeyReadinessPolicy.CONNECTED, true, 3000) == KeyReadinessPolicy.NONE, "old prewarm request never dispatches automatically");
        check(new KeyReadinessPolicy().start(KeyReadinessPolicy.UNKNOWN, true, 1000) == KeyReadinessPolicy.FAIL, "listener failure is not cold startup");
        KeyReadinessPolicy lost = new KeyReadinessPolicy();
        lost.start(KeyReadinessPolicy.DISCONNECTED, true, 1000);
        check(lost.poll(KeyReadinessPolicy.UNKNOWN, true, 2000) == KeyReadinessPolicy.FAIL, "listener loss cancels waiting");
        KeyReadinessPolicy removed = new KeyReadinessPolicy();
        removed.start(KeyReadinessPolicy.DISCONNECTED, true, 1000);
        check(removed.poll(KeyReadinessPolicy.ABSENT, true, 2000) == KeyReadinessPolicy.PREWARM, "notification disappearance cannot silently use cloud");
        KeyReadinessPolicy canceled = new KeyReadinessPolicy();
        canceled.start(KeyReadinessPolicy.DISCONNECTED, true, 1000); canceled.cancel();
        check(canceled.poll(KeyReadinessPolicy.CONNECTED, true, 2000) == KeyReadinessPolicy.NONE, "Home/cancel cannot revive old action");
        check(KeyReadinessPolicy.classify("数字车钥匙服务", "已连接") == KeyReadinessPolicy.CONNECTED, "exact connection keyword accepted");
        check(KeyReadinessPolicy.classify("数字车钥匙服务", "未连接") == KeyReadinessPolicy.DISCONNECTED, "disconnected is distinct");
        check(KeyReadinessPolicy.classify("数字车钥匙服务", "已连接 / 未连接") == KeyReadinessPolicy.UNKNOWN, "ambiguous text is unknown");
        check(KeyReadinessPolicy.classify("数字车钥匙服务", "") == KeyReadinessPolicy.UNKNOWN, "missing body never implies disconnected");
        check(KeyReadinessPolicy.classify("普通车辆消息", "已连接") == KeyReadinessPolicy.ABSENT, "unrelated title is ignored");
        System.out.println("KeyReadinessPolicyTest: " + checks + " checks passed");
    }
}
