package com.eric.himawidgetbridge;

public final class TriggerGateTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        TriggerGate gate = new TriggerGate();
        check(!gate.tryStart(false, true, true, true, 1000), "disabled must reject");
        check(!gate.tryStart(true, false, true, true, 1000), "unbound must reject");
        check(!gate.tryStart(true, true, false, true, 1000), "unknown adapter must reject");
        check(!gate.tryStart(true, true, true, false, 1000), "missing lock must reject");
        check(gate.tryStart(true, true, true, true, 1000), "ready trigger must be accepted");
        check(!gate.tryStart(true, true, true, true, 1001), "duplicate must reject");
        check(!gate.tryStart(true, true, true, true, 10999), "lease must remain active");
        check(gate.tryStart(true, true, true, true, 11000), "expired lease must release");
        gate.cancel();
        check(!gate.tryStart(true, true, true, true, 11001), "cancellation retains cooldown");
        check(gate.tryStart(true, true, true, true, 14000), "cooldown must expire");
        check(!gate.tryStart(true, true, true, true, 100), "backward clock must reject");
        System.out.println("TriggerGateTest: 11 checks passed");
        check(gate.remainingSeconds(14000) == 10, "new protection must show ten seconds");
        check(gate.remainingSeconds(23001) == 1, "remaining fraction must round up");
        check(gate.remainingSeconds(23999) == 1, "last millisecond must remain protected");
        check(gate.remainingSeconds(24000) == 0, "expired protection must show zero");
        check(new TriggerGate().remainingSeconds(1000) == 0, "fresh process has no protection");
        TriggerGate cancelled = new TriggerGate();
        cancelled.tryStart(true, true, true, true, 1000);
        cancelled.cancel();
        check(cancelled.remainingSeconds(1001) == 3, "cancelled request retains minimum interval");
        check(cancelled.remainingSeconds(4000) == 0, "cancelled interval must expire");
        System.out.println("ProtectionCountdownTest: 7 checks passed");
        check(SlotPolicy.findLock(new String[]{"空调", "车锁", "后备箱"},
                new boolean[]{true, true, true}) == 1, "exact lock label must select slot");
        check(SlotPolicy.findLock(new String[]{"空调", "后备箱"},
                new boolean[]{true, true}) == -1, "missing lock must reject");
        check(SlotPolicy.findLock(new String[]{"车锁", "车锁"},
                new boolean[]{true, true}) == -1, "ambiguous slots must reject");
        check(SlotPolicy.findLock(new String[]{"车锁"}, new boolean[]{false}) == -1,
                "disabled lock must reject");
        check(SlotPolicy.findLock(new String[]{"儿童锁"}, new boolean[]{true}) == -1,
                "other lock label must reject");
        check(SlotPolicy.findLock(null, new boolean[]{true}) == -1,
                "null labels must reject");
        check(SlotPolicy.findLock(new String[]{"车锁"}, new boolean[]{}) == -1,
                "mismatched snapshot must reject");
        check(SlotPolicy.findLock(new String[]{" 车锁 "}, new boolean[]{true}) == 0,
                "whitespace must not prevent selection");
        System.out.println("SlotPolicyTest: 8 checks passed");
    }
}
