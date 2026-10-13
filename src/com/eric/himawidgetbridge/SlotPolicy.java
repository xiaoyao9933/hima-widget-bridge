package com.eric.himawidgetbridge;

public final class SlotPolicy {
    private SlotPolicy() {}
    public static int findLock(String[] labels, boolean[] available) {
        return find("车锁", labels, available);
    }
    public static int findTrunk(String[] labels, boolean[] available) {
        return find("后备箱", labels, available);
    }
    private static int find(String target, String[] labels, boolean[] available) {
        if (labels == null || available == null || labels.length != available.length) return -1;
        int found = -1;
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] != null && target.equals(labels[i].trim()) && available[i]) {
                if (found >= 0) return -1;
                found = i;
            }
        }
        return found;
    }
}
