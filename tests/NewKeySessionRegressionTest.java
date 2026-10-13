package com.eric.himawidgetbridge;

/** A restored connected notification without session history must not skip initialization. */
public final class NewKeySessionRegressionTest {
    public static void main(String[] args) {
        int result = new KeyReadinessPolicy().start(KeyReadinessPolicy.CONNECTED, 1000);
        if (result != KeyReadinessPolicy.PREWARM)
            throw new AssertionError("restored connected key with unknown history skipped warmup: " + result);
        System.out.println("NewKeySessionRegressionTest: 1 check passed");
    }
}
