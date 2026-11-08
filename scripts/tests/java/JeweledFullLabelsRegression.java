import buildcraft.additionalpipes.client.GuiTextLayout;
import buildcraft.additionalpipes.client.JeweledGuiLayout;
import java.util.function.ToIntFunction;

/** All GUI labels remain intact even when the active Minecraft font is wider. */
public final class JeweledFullLabelsRegression {
    private static int checks;

    private static void require(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }

    private static void fits(String label, int pixels, ToIntFunction<String> fontWidth) {
        float scale = GuiTextLayout.horizontalScale(label, pixels, fontWidth);
        int fullWidth = fontWidth.applyAsInt(label);
        require(scale > 0.0F && scale <= 1.0F, "Invalid scale for " + label + ": " + scale);
        require(fullWidth * (double) scale <= pixels + 0.001,
            "Label clips: " + label + " requires " + fullWidth * (double) scale + "px of " + pixels);
        if (fullWidth <= pixels) require(scale == 1.0F, "Unnecessary shrink for " + label);
    }

    public static void main(String[] args) {
        String[][] languages = {
            {"Down", "Up", "North", "South", "West", "East"},
            {"Вниз", "Вверх", "Север", "Юг", "Запад", "Восток"},
            {"Abwärts", "Aufwärts", "Norden", "Süden", "Westen", "Osten"},
            {"Вниз и левее", "Вверх и правее", "Севернее", "Южнее", "На запад", "На восток"}
        };
        for (String[] labels : languages) {
            for (int glyphWidth : new int[]{4, 6, 9, 13, 20}) {
                ToIntFunction<String> activeFont = s -> s.codePointCount(0, s.length()) * glyphWidth;
                int[] ends = JeweledGuiLayout.tabEnds(labels, activeFont);
                require(ends[0] == 15 && ends[6] <= 197, "Tabs overflow panel");
                for (int side = 0; side < 6; side++) {
                    int region = ends[side + 1] - ends[side] - 7;
                    require(region > 0, "No tab text space on side " + side);
                    fits(labels[side], region, activeFont);
                    double mid = (ends[side] + ends[side + 1]) / 2.0;
                    require(JeweledGuiLayout.tabAt(mid, 19, -1, ends) == side, "Wrong tab hitbox " + side);
                }
                fits("Accept Unsorted", JeweledGuiLayout.ACCEPT_WIDTH - 6, activeFont);
                fits("Принимать несортированные предметы", JeweledGuiLayout.ACCEPT_WIDTH - 6, activeFont);
                fits("Metadata", JeweledGuiLayout.META_WIDTH - 6, activeFont);
                fits("Совпадение:", JeweledGuiLayout.TAG_X - 107, activeFont);
                fits("Jeweled Pipe", JeweledGuiLayout.WIDTH - 16, activeFont);
            }
        }
        require(GuiTextLayout.horizontalScale("", 5, String::length) == 1.0F,
            "Empty text needs no scaling");
        require(GuiTextLayout.horizontalScale("No space", 0, String::length) == 0.0F,
            "Zero-width region cannot draw a label");
        System.out.println("PASS JeweledFullLabelsRegression " + checks + " checks");
    }
}
