package com.eric.himawidgetbridge;

/** A process-local explicit request owner; canceled requests cannot release newer requests. */
public final class OperationLease {
    private Object owner;
    public synchronized boolean acquire(Object candidate) {
        if (candidate == null || owner != null && owner != candidate) return false;
        owner = candidate; return true;
    }
    public synchronized void release(Object candidate) { if (owner == candidate) owner = null; }
}
