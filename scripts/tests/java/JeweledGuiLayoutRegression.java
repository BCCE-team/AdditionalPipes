import buildcraft.additionalpipes.client.JeweledGuiLayout;
import java.util.Arrays;

public final class JeweledGuiLayoutRegression {
    private static void check(boolean ok, String description) {
        if (!ok) throw new AssertionError(description);
    }
    public static void main(String[] args) {
        String[] original = {"Down", "Up", "North", "South", "West", "East"};
        int[] ends = JeweledGuiLayout.tabEnds(original, name -> name.length() * 6);
        check(ends[0] == 15, "First tab begins at original x=15");
        check(ends[1] == 15 + 4 * 6 - 1 + 7, "Original tab width formula");
        check(ends[6] <= JeweledGuiLayout.WIDTH - 5, "Tabs fit the panel");
        for (int side = 0; side < 6; side++) {
            check(JeweledGuiLayout.tabAt((ends[side] + ends[side + 1]) / 2.0, 19, 0, ends) == side,
                "Tab " + side + " must be clickable and aligned with its text");
        }
        check(JeweledGuiLayout.tabAt(ends[0] - 1, 19, 0, ends) == -1, "Tabs reject outside clicks");
        check(JeweledGuiLayout.tabAt(ends[1], 12, 0, ends) == -1, "Unselected tab isn't raised");
        check(JeweledGuiLayout.tabAt(ends[0] + 4, 12, 0, ends) == 0, "Selected tab is raised");
        check(JeweledGuiLayout.toggleAt(9, 98) == 6, "Accept Unsorted hit box");
        check(JeweledGuiLayout.toggleAt(136, 98) == 7, "NBT hit box");
        check(JeweledGuiLayout.toggleAt(166, 98) == 8, "Metadata hit box");
        check(JeweledGuiLayout.toggleAt(103, 98) == -1, "Gap for Match label remains clear");
        check(JeweledGuiLayout.toggleAt(105, 94) == -1, "Match label isn't a button");
        check(JeweledGuiLayout.toggleAt(21, 70) == -1, "Filter slots aren't buttons");
        check(JeweledGuiLayout.toggleAt(21, 132) == -1, "Player inventory isn't a button");
        check(JeweledGuiLayout.toggleAt(8, 108) == -1, "Button bottom is exclusive");
        String[] longLabels = {"Очень длинный низ", "Верхнее направление", "Северное направление", "Южное направление", "Западное направление", "Восточное направление"};
        int[] fitted = JeweledGuiLayout.tabEnds(longLabels, name -> name.length() * 7);
        check(fitted[6] == 197, "Oversized localized tabs are constrained to the panel");
        for (int i = 1; i < fitted.length; i++) {
            check(fitted[i] - fitted[i - 1] >= 12, "Translated tabs must retain usable hit boxes " + Arrays.toString(fitted));
        }
        System.out.println("PASS JeweledGuiLayoutRegression");
    }
}
