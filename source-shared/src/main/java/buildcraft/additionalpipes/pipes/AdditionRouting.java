package buildcraft.additionalpipes.pipes;

/**
 * The two-stage routing rule used by the original Addition Transport Pipe.
 * Bit positions are the six Direction ordinals. No per-pipe state is required.
 */
public final class AdditionRouting {
    private AdditionRouting() { }

    /**
     * Prefer any candidate inventory already containing the item and accepting an insertion.
     * Otherwise continue through ordinary pipes, never into an inventory with no matching item.
     * Full inventories are excluded as well: they cannot accept the travelling stack.
     */
    public static int select(int candidates, int inventoryDestinations, int matchingInsertableDestinations) {
        int preferred = candidates & inventoryDestinations & matchingInsertableDestinations;
        return preferred != 0 ? preferred : candidates & ~inventoryDestinations;
    }
}
