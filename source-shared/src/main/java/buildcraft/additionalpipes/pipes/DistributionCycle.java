package buildcraft.additionalpipes.pipes;

import java.util.Arrays;

/**
 * The classic Additional Pipes distribution counter, kept independent from Minecraft.
 * Side order is DOWN, UP, NORTH, SOUTH, WEST, EAST; a zero quota disables a side.
 * The quota counts items in each travelling stack rather than payloads.
 */
public final class DistributionCycle {
    public static final int SIDES = 6;
    public static final int MAX_QUOTA = 4096;
    public static final int NOT_STARTED = MAX_QUOTA + 1;
    private final int[] quotas = {1, 1, 1, 1, 1, 1};
    private int selected = 0;
    private int sent = NOT_STARTED;

    public DistributionCycle() { }

    public DistributionCycle(int[] quotas, int selected, int sent) {
        if (quotas == null || quotas.length != SIDES || selected < 0 || selected >= SIDES
            || sent < 0 || sent > NOT_STARTED) throw new IllegalArgumentException("Invalid distribution state");
        for (int quota : quotas) if (quota < 0 || quota > MAX_QUOTA)
            throw new IllegalArgumentException("Invalid side quota");
        if (Arrays.stream(quotas).allMatch(quota -> quota == 0))
            throw new IllegalArgumentException("All distribution sides disabled");
        System.arraycopy(quotas, 0, this.quotas, 0, SIDES);
        this.selected = selected;
        this.sent = sent;
    }

    public int[] quotas() { return quotas.clone(); }
    public int selected() { return selected; }
    public int sent() { return sent; }

    /** Returns whether the value changed; at least one nonzero quota must remain. */
    public boolean setQuota(int side, int quota) {
        if (side < 0 || side >= SIDES || quota < 0 || quota > MAX_QUOTA)
            throw new IllegalArgumentException("Invalid distribution quota");
        if (quotas[side] == quota) return false;
        if (quota == 0) {
            boolean otherEnabled = false;
            for (int i = 0; i < SIDES; i++) if (i != side && quotas[i] > 0) otherEnabled = true;
            if (!otherEnabled) return false;
        }
        quotas[side] = quota;
        return true;
    }

    /**
     * Chooses exactly one currently connected output, without choosing the item input.
     * A full/disabled/unavailable side advances the round-robin cursor. Returns -1
     * if no configured output is eligible; in that case the cursor stays unchanged.
     */
    public int select(int stackCount, int candidateMask) {
        if (stackCount <= 0) return -1;
        candidateMask &= 0x3f;
        if (candidateMask == 0) return -1;
        int next = selected;
        if (sent >= quotas[next] || quotas[next] == 0 || (candidateMask & (1 << next)) == 0) {
            next = -1;
            for (int offset = 1; offset <= SIDES; offset++) {
                int side = (selected + offset) % SIDES;
                if (quotas[side] > 0 && (candidateMask & (1 << side)) != 0) {
                    next = side;
                    break;
                }
            }
            if (next < 0) return -1;
            selected = next;
            sent = 0;
        }
        sent = (int) Math.min(NOT_STARTED, (long) sent + stackCount);
        return selected;
    }
}
