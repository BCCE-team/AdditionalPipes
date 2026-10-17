package buildcraft.additionalpipes.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The 27-slot Jeweled Pipe filter's behaviour, separate from ItemStack/NBT/menu APIs.
 * Slot contents are stored by the eventual pipe component; this class never inventories
 * real items or creates a vanilla container. Client writes must be authorized on server.
 */
public final class SideFilterData<T> {
    public static final int INVENTORY_SIZE = 27;

    /** Implement against ItemStack on each platform; avoid Minecraft-version NBT assumptions. */
    public interface ItemMatcher<T> {
        boolean sameItem(T expected, T tested);
        boolean sameMetadata(T expected, T tested);
        boolean sameComponents(T expected, T tested);
    }

    private final Object[] entries = new Object[INVENTORY_SIZE];
    private boolean matchMetadata;
    private boolean matchNBT;
    private boolean acceptUnsortedItems;

    public synchronized void set(int slot, T value) { checkSlot(slot); entries[slot] = value; }
    @SuppressWarnings("unchecked")
    public synchronized T get(int slot) { checkSlot(slot); return (T) entries[slot]; }
    public synchronized void clear() { Arrays.fill(entries, null); }
    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= INVENTORY_SIZE) throw new IllegalArgumentException("Invalid filter slot: " + slot);
    }

    public synchronized boolean matchMetadata() { return matchMetadata; }
    public synchronized void setMatchMetadata(boolean enabled) { matchMetadata = enabled; }
    public synchronized boolean matchNBT() { return matchNBT; }
    public synchronized void setMatchNBT(boolean enabled) { matchNBT = enabled; }
    public synchronized boolean acceptsUnsortedItems() { return acceptUnsortedItems; }
    public synchronized void setAcceptUnsortedItems(boolean enabled) { acceptUnsortedItems = enabled; }

    public synchronized boolean matchesStack(T stack, ItemMatcher<? super T> comparer) {
        if (stack == null) return false;
        Objects.requireNonNull(comparer, "comparer");
        for (Object entry : entries) {
            if (entry == null) continue;
            @SuppressWarnings("unchecked") T expected = (T) entry;
            if (!comparer.sameItem(expected, stack)) continue;
            if (matchMetadata && !comparer.sameMetadata(expected, stack)) continue;
            if (matchNBT && !comparer.sameComponents(expected, stack)) continue;
            return true;
        }
        return false;
    }

    public synchronized List<T> snapshot() {
        List<T> copy = new ArrayList<>(INVENTORY_SIZE);
        for (Object entry : entries) {
            @SuppressWarnings("unchecked") T value = (T) entry;
            copy.add(value);
        }
        return java.util.Collections.unmodifiableList(copy);
    }

    public synchronized Options options() { return new Options(matchMetadata, matchNBT, acceptUnsortedItems); }
    public synchronized void setOptions(Options options) {
        Objects.requireNonNull(options);
        matchMetadata = options.matchMetadata();
        matchNBT = options.matchNBT();
        acceptUnsortedItems = options.acceptUnsortedItems();
    }

    /** Options are trivially version-independent; slot item codecs are version-specific. */
    public record Options(boolean matchMetadata, boolean matchNBT, boolean acceptUnsortedItems) { }
}
