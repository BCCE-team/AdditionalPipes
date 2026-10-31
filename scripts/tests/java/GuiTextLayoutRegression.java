import buildcraft.additionalpipes.client.GuiTextLayout;
import java.util.function.ToIntFunction;

public final class GuiTextLayoutRegression {
    private static final ToIntFunction<String> CHAR_WIDTH = value -> value.codePointCount(0, value.length()) * 6;
    private static int checked;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checked++;
    }

    private static void fits(String text, int maxPixels) {
        String result = GuiTextLayout.fit(text, maxPixels, CHAR_WIDTH);
        require(CHAR_WIDTH.applyAsInt(result) <= Math.max(0, maxPixels), "Text overflow: " + result);
        if (text.codePointCount(0, text.length()) * 6 > maxPixels && maxPixels >= 18) {
            require(result.endsWith("..."), "Missing ellipsis: " + result);
        }
    }

    public static void main(String[] args) {
        require("Priority Insertion Pipe".equals(GuiTextLayout.fit("Priority Insertion Pipe", 200, CHAR_WIDTH)), "short title changed");
        require("".equals(GuiTextLayout.fit("Priority Insertion Pipe", 0, CHAR_WIDTH)), "zero width");
        require("".equals(GuiTextLayout.fit("Priority Insertion Pipe", -1, CHAR_WIDTH)), "negative width");
        require(".".equals(GuiTextLayout.fit("Priority Insertion Pipe", 6, CHAR_WIDTH)), "small label");
        require("...".equals(GuiTextLayout.fit("Priority Insertion Pipe", 18, CHAR_WIDTH)), "ellipsis-only label");
        require("".equals(GuiTextLayout.fit("Priority Insertion Pipe", 5, CHAR_WIDTH)), "unrenderable suffix");
        String supplementary = "\uD83D\uDE80\uD83D\uDE80\uD83D\uDE80\uD83D\uDE80\uD83D\uDE80\uD83D\uDE80";
        require("\uD83D\uDE80...".equals(GuiTextLayout.fit(supplementary, 24, CHAR_WIDTH)), "surrogate pair split");
        for (int width = 0; width <= 240; width++) {
            fits("Priority Insertion Pipe", width);
            fits("Really Really Really Long Translated Name", width);
            fits("\u041E\u0447\u0435\u043D\u044C \u0434\u043B\u0438\u043D\u043D\u0430\u044F \u043D\u0430\u0434\u043F\u0438\u0441\u044C", width);
            fits(supplementary, width);
        }
        System.out.println("PASS GuiTextLayoutRegression " + checked + " assertions");
    }
}
