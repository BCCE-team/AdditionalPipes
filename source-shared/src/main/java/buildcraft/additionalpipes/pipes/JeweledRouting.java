package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.filter.SideFilterData;

/** Pure 4.7.7 routing rule, independent of Minecraft's network or rendering APIs. */
public final class JeweledRouting {
    private JeweledRouting() { }

    public static <T> int select(int candidates, T stack, SideFilterData<T>[] sides,
                                 SideFilterData.ItemMatcher<? super T> matcher) {
        if (sides.length != 6) throw new IllegalArgumentException("Jeweled pipe requires exactly six sides");
        int matching = 0, unsorted = 0;
        for (int side = 0; side < 6; side++) {
            int bit = 1 << side;
            if ((candidates & bit) == 0) continue;
            SideFilterData<T> filter = sides[side];
            if (filter.matchesStack(stack, matcher)) matching |= bit;
            else if (filter.acceptsUnsortedItems()) unsorted |= bit;
        }
        return matching != 0 ? matching : unsorted;
    }
}
