import buildcraft.additionalpipes.pipes.DistributionCycle;
import java.util.Arrays;

/** Executable behavioural regression for the classic Distribution Pipe quota cursor. */
public final class DistributionCycleRegression {
    private static int assertions;
    private static void yes(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        assertions++;
    }
    private static void rejected(Runnable action, String label) {
        try { action.run(); throw new AssertionError(label); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static int mask(int... sides) {
        int mask = 0;
        for (int side : sides) mask |= 1 << side;
        return mask;
    }
    public static void main(String[] ignored) {
        DistributionCycle cycle = new DistributionCycle();
        yes(Arrays.equals(cycle.quotas(), new int[]{1,1,1,1,1,1}), "default quotas");
        yes(cycle.select(1, mask(1, 2, 3)) == 1, "first output starts at UP");
        yes(cycle.select(1, mask(1, 2, 3)) == 2, "one unit moves to NORTH");
        yes(cycle.select(1, mask(1, 2, 3)) == 3, "then SOUTH");
        yes(cycle.select(1, mask(1, 2, 3)) == 1, "wrap to first eligible side");
        cycle.setQuota(1, 5);
        cycle.setQuota(2, 0);
        cycle.setQuota(3, 2);
        int[] snapshot = cycle.quotas();
        snapshot[1] = 99;
        yes(cycle.quotas()[1] == 5, "quota snapshot defensive copy");
        yes(cycle.select(3, mask(1, 3)) == 1, "route to enabled side after current quota exhausted");
        yes(cycle.select(1, mask(1, 3)) == 1, "remain until five items passed");
        yes(cycle.select(64, mask(1, 3)) == 3, "next route receives a full stack without splitting");
        yes(cycle.sent() == 64, "full stack counted by items");
        DistributionCycle huge = new DistributionCycle();
        huge.select(Integer.MAX_VALUE, mask(1));
        yes(huge.sent() == DistributionCycle.NOT_STARTED, "oversized count saturates without overflow");
        int previous = cycle.selected();
        int previousCount = cycle.sent();
        yes(cycle.select(5, 0) == -1, "no connected outputs");
        yes(cycle.selected() == previous && cycle.sent() == previousCount,
            "unavailable outputs do not consume quota");
        yes(cycle.select(1, mask(2)) == -1, "disabled only output rejected");
        yes(cycle.setQuota(1, 0), "disable UP");
        yes(!cycle.setQuota(1, 0), "same quota unchanged");
        yes(cycle.select(1, mask(1, 3)) == 3, "skip disabled UP");
        for (int side : new int[]{0, 2, 4, 5}) cycle.setQuota(side, 0);
        yes(!cycle.setQuota(3, 0), "must retain one enabled output");
        DistributionCycle saved = new DistributionCycle(cycle.quotas(), cycle.selected(), cycle.sent());
        yes(saved.select(1, mask(3)) == cycle.select(1, mask(3)), "restored cursor stable");
        rejected(() -> cycle.setQuota(-1, 1), "invalid negative side");
        rejected(() -> cycle.setQuota(5, 4097), "quota bound");
        rejected(() -> new DistributionCycle(new int[6], 0, 0), "all-zero restored state");
        rejected(() -> new DistributionCycle(new int[]{1,1,1,1,1,1}, 6, 0), "bad cursor");
        System.out.println("PASS DistributionCycleRegression " + assertions + " assertions");
    }
}
