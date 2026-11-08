package buildcraft.additionalpipes.client;

import java.util.Objects;
import java.util.function.ToIntFunction;

/** Pixel-constrained, one-line GUI labels shared by all supported Minecraft versions. */
public final class GuiTextLayout {
    private GuiTextLayout() { }

    /**
     * Uniform horizontal text scale (<= 1), measured with the actual Minecraft font.
     * Unlike {@link #fit}, this retains the entire label for narrow tabs, buttons
     * and translated strings while keeping the original text height.
     */
    public static float horizontalScale(String text, int maxPixels, ToIntFunction<String> width) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(width, "width");
        if (maxPixels <= 0) return 0.0F;
        int natural = Math.max(0, width.applyAsInt(text));
        return natural <= maxPixels ? 1.0F : (float) maxPixels / natural;
    }

    /**
     * Truncates translated labels at a font-measured pixel boundary. An ellipsis
     * signals that text was clipped, and surrogate pairs are never split.
     */
    public static String fit(String text, int maxPixels, ToIntFunction<String> width) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(width, "width");
        if (maxPixels <= 0) return "";
        if (width.applyAsInt(text) <= maxPixels) return text;

        String suffix = "...";
        if (width.applyAsInt(suffix) > maxPixels) {
            suffix = ".";
            if (width.applyAsInt(suffix) > maxPixels) return "";
        }
        int end = text.length();
        while (end > 0) {
            end -= Character.charCount(Character.codePointBefore(text, end));
            String candidate = text.substring(0, end) + suffix;
            if (width.applyAsInt(candidate) <= maxPixels) return candidate;
        }
        return suffix;
    }
}
