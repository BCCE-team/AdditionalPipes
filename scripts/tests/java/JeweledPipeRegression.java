import buildcraft.additionalpipes.filter.SideFilterData;
import buildcraft.additionalpipes.pipes.JeweledRouting;

public final class JeweledPipeRegression {
    private record Stack(String item, int damage, String nbt) { }
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static final SideFilterData.ItemMatcher<Stack> MATCHER = new SideFilterData.ItemMatcher<>() {
        @Override public boolean sameItem(Stack a, Stack b) { return a.item().equals(b.item()); }
        @Override public boolean sameMetadata(Stack a, Stack b) { return a.damage() == b.damage(); }
        @Override public boolean sameComponents(Stack a, Stack b) { return a.nbt().equals(b.nbt()); }
    };

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        SideFilterData<Stack>[] filters = (SideFilterData<Stack>[]) new SideFilterData<?>[6];
        for (int i=0; i<6; i++) {
            filters[i] = new SideFilterData<>();
            check(filters[i].snapshot().size() == 27, "27 slots per direction");
        }
        Stack gold = new Stack("gold", 0, "tag:A");
        Stack goldDifferent = new Stack("gold", 2, "tag:B");
        Stack copper = new Stack("copper", 0, "tag:A");
        int all = 0b111111;
        check(JeweledRouting.select(all, gold, filters, MATCHER) == 0, "No unsorted exits by default");
        filters[0].set(26, gold);
        filters[1].set(0, gold);
        filters[2].setAcceptUnsortedItems(true);
        check(JeweledRouting.select(all, gold, filters, MATCHER) == 0b11,
            "Matching filters on two sides override unsorted exit");
        check(JeweledRouting.select(all, copper, filters, MATCHER) == 0b100,
            "Unmatched item is routed only through accept-unsorted side");
        check(JeweledRouting.select(0b110100, gold, filters, MATCHER) == 0b100,
            "Never select sides missing from the carrier's candidate set");
        filters[0].setMatchMetadata(true);
        filters[0].setMatchNBT(true);
        filters[1].setMatchMetadata(true);
        filters[1].setMatchNBT(true);
        check(JeweledRouting.select(all, goldDifferent, filters, MATCHER) == 0b100,
            "Strict metadata and NBT rejects mismatches");
        filters[0].setMatchMetadata(false);
        filters[0].setMatchNBT(false);
        check(JeweledRouting.select(all, goldDifferent, filters, MATCHER) == 1,
            "Relaxed side matches item despite different metadata and NBT");
        filters[0].setMatchNBT(true);
        check(JeweledRouting.select(all, goldDifferent, filters, MATCHER) == 0b100,
            "NBT comparison remains independent of metadata");
        filters[0].setMatchNBT(false);
        filters[0].setMatchMetadata(true);
        check(JeweledRouting.select(all, goldDifferent, filters, MATCHER) == 0b100,
            "Metadata comparison remains independent of NBT");
        filters[0].setMatchMetadata(false);
        filters[0].clear();
        check(JeweledRouting.select(all, gold, filters, MATCHER) == 0b10,
            "Clearing one tab does not clear another tab's filters");
        filters[1].clear();
        filters[2].setAcceptUnsortedItems(false);
        check(JeweledRouting.select(all, gold, filters, MATCHER) == 0,
            "Do not silently route items when all fallbacks are disabled");
        try { filters[0].set(27, gold); throw new AssertionError("Slot 27 accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        for (int i=0; i<27; i++) filters[5].set(i, gold);
        check(filters[5].snapshot().stream().filter(gold::equals).count() == 27,
            "Entire 27-slot last side is addressable");
        System.out.println("PASS JeweledPipeRegression " + assertions + " assertions");
    }
}
