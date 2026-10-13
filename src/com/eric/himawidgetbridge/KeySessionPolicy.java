package com.eric.himawidgetbridge;

/** Volatile preparation history for a notification session, never a car-control readiness proof. */
public final class KeySessionPolicy {
    public static final long MIN_RETRY_MILLIS = 2000, FIRST_NOTIFICATION_MILLIS = 30000;
    private String identity;
    private long generation;
    private long launchedAt = -1;
    private long launchSequence, activeLaunch;
    private boolean awaitFirst;

    public synchronized long generation() { return generation; }
    public synchronized void resetHistory() {
        generation++; identity = null; launchedAt = -1; activeLaunch = 0; awaitFirst = false;
    }
    public synchronized void observe(int state, String key, long now) {
        if (state == KeyReadinessPolicy.UNKNOWN) { resetHistory(); return; }
        if (state == KeyReadinessPolicy.ABSENT) {
            if (identity != null) resetHistory();
            return;
        }
        if (key == null) { resetHistory(); return; }
        posted(key, now);
    }
    public synchronized void posted(String key, long now) {
        if (key == null || key.equals(identity)) return;
        boolean carryLaunch = identity == null && awaitFirst && launchedAt >= 0
                && now >= launchedAt && now - launchedAt <= FIRST_NOTIFICATION_MILLIS;
        generation++; identity = key;
        if (!carryLaunch) { launchedAt = -1; activeLaunch = 0; }
        awaitFirst = false;
    }
    public synchronized boolean removed(String key) {
        if (key == null || !key.equals(identity)) return false;
        resetHistory(); return true;
    }
    /** Called only after a normal MAIN start succeeds. No action is scheduled by this policy. */
    public synchronized boolean warmupLaunched(long expectedGeneration, long now) {
        return recordWarmupLaunched(expectedGeneration, now) > 0;
    }
    public synchronized long recordWarmupLaunched(long expectedGeneration, long now) {
        if (generation != expectedGeneration) return 0;
        launchedAt = now; awaitFirst = identity == null; activeLaunch = ++launchSequence;
        return activeLaunch;
    }
    /** A failed automatic preparation revokes only its own launch, even across first-key arrival. */
    public synchronized void cancelWarmup(long token) {
        if (token <= 0 || activeLaunch != token) return;
        launchedAt = -1; activeLaunch = 0; awaitFirst = false;
    }
    public synchronized boolean requiresWarmup(long now) {
        return identity == null || launchedAt < 0 || now < launchedAt;
    }
    public synchronized long retryRemainingMillis(long now) {
        return launchedAt < 0 || now < launchedAt ? 0 : Math.max(0, MIN_RETRY_MILLIS - (now - launchedAt));
    }
}
