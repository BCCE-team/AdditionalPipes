package buildcraft.additionalpipes.client;

import java.util.Objects;
import java.util.function.ToIntFunction;

/** The tab hit boxes, palette and toggle placement from AdditionalPipesBC 4.7.7. */
public final class JeweledGuiLayout {
    public static final int WIDTH = 202;
    public static final int HEIGHT = 211;
    public static final int TAB_START = 15;
    public static final int TAB_Y = 14;
    public static final int TAB_HEIGHT = 11;
    public static final int TAB_OUTLINE = 2;
    public static final int[] TAB_MAIN = {
        0xFFD0D0D0, 0xFF3FC2FF, 0xFF3E3EE0, 0xFF44BE4E, 0xFFF0DB00, 0xFFD20000
    };
    public static final int[] TAB_BORDER = {
        0xFFFFFFFF, 0xFF20A2D0, 0xFF1111C0, 0xFF259F2F, 0xFFE0CB00, 0xFFC20000
    };
    public static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};
    public static final int ACCEPT_X = 8, ACCEPT_WIDTH = 95;
    public static final int TAG_X = 135, TAG_WIDTH = 30;
    public static final int META_X = 165, META_WIDTH = 30;
    public static final int BUTTON_Y = 88, BUTTON_HEIGHT = 20;
    private JeweledGuiLayout() { }

    /** Match 4.7.7's proportional tab widths. Scale only when a translation would overflow the panel. */
    public static int[] tabEnds(String[] names, ToIntFunction<String> width) {
        Objects.requireNonNull(names);
        Objects.requireNonNull(width);
        if (names.length != 6) throw new IllegalArgumentException("Six directions required");
        int[] result = new int[7];
        result[0] = TAB_START;
        for (int i = 0; i < 6; i++) {
            result[i + 1] = result[i] + Math.max(12, width.applyAsInt(names[i]) - 1 + 7);
        }
        if (result[6] <= WIDTH - 5) return result;
        int[] original = result.clone();
        int total = original[6] - TAB_START;
        int budget = WIDTH - 5 - TAB_START;
        for (int i = 1; i <= 6; i++) {
            result[i] = TAB_START + (int) Math.round((original[i] - TAB_START) * (double) budget / total);
        }
        result[6] = WIDTH - 5;
        return result;
    }

    /** Returns the original 0..5 side or -1. The selected tab extends three pixels higher. */
    public static int tabAt(double x, double y, int selected, int[] ends) {
        if (x < ends[0] || x >= ends[6] || y >= TAB_Y + TAB_HEIGHT) return -1;
        for (int i = 0; i < 6; i++) {
            if (x >= ends[i] && x < ends[i + 1]) {
                return y >= (i == selected ? TAB_Y - 3 : TAB_Y) ? i : -1;
            }
        }
        return -1;
    }

    /** Menu IDs 6/7/8 correspond to Accept Unsorted, NBT, Metadata. */
    public static int toggleAt(double x, double y) {
        if (y < BUTTON_Y || y >= BUTTON_Y + BUTTON_HEIGHT) return -1;
        if (x >= ACCEPT_X && x < ACCEPT_X + ACCEPT_WIDTH) return 6;
        if (x >= TAG_X && x < TAG_X + TAG_WIDTH) return 7;
        if (x >= META_X && x < META_X + META_WIDTH) return 8;
        return -1;
    }
}
