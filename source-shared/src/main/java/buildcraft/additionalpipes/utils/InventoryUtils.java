package buildcraft.additionalpipes.utils;

import java.util.Objects;

/**
 * Inventory simulation for the Advanced/Jeweled/Insertion pipes, without the removed
 * IInventory API. Later adapters supply vanilla Container or capability-backed slots.
 */
public final class InventoryUtils {
    private InventoryUtils() { }

    public interface Slots<T> {
        int size();
        T stackInSlot(int slot);
        int count(T stack);
        int slotLimit(int slot, T stack);
        int maximumStackSize(T stack);
        boolean canInsert(int slot, T stack);
        boolean canMerge(T left, T right);
    }

    /** Returns the first empty AND insertable slot. */
    public static <T> int getFirstFreeSlot(Slots<T> inventory, T stack) {
        Objects.requireNonNull(inventory);
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.stackInSlot(i) == null && inventory.canInsert(i, stack)) return i;
        }
        return -1;
    }

    /**
     * Can the WHOLE stack fit? Simulates per-slot limits, partial merges and empty slots
     * without mutating an inventory or assuming that every stack has size 64.
     */
    public static <T> boolean canItemFit(Slots<T> inventory, T stack) {
        Objects.requireNonNull(inventory);
        if (stack == null) return false;
        int remaining = inventory.count(stack);
        if (remaining <= 0) return false;
        for (int i = 0; i < inventory.size(); i++) {
            if (!inventory.canInsert(i, stack)) continue;
            T existing = inventory.stackInSlot(i);
            if (existing != null && !inventory.canMerge(existing, stack)) continue;
            int slotMax = Math.max(0, Math.min(inventory.slotLimit(i, stack), inventory.maximumStackSize(stack)));
            if (existing == null) remaining -= slotMax;
            else remaining -= Math.max(0, slotMax - inventory.count(existing));
            if (remaining <= 0) return true;
        }
        return false;
    }

    /** Original matchMeta/matchNBT filtering via a platform-owned comparer. */
    public static <T> boolean containsItem(Slots<T> inventory, T test,
        boolean matchMeta, boolean matchNBT,
        buildcraft.additionalpipes.filter.SideFilterData.ItemMatcher<T> matcher) {
        if (test == null) return false;
        for (int i = 0; i < inventory.size(); i++) {
            T existing = inventory.stackInSlot(i);
            if (existing == null || !matcher.sameItem(test, existing)) continue;
            if (matchMeta && !matcher.sameMetadata(test, existing)) continue;
            if (matchNBT && !matcher.sameComponents(test, existing)) continue;
            return true;
        }
        return false;
    }
}
