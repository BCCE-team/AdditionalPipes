package buildcraft.additionalpipes.client;

import buildcraft.additionalpipes.pipes.JeweledMenu;
import buildcraft.lib.gui.GuiIcon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Faithful layout of AdditionalPipesBC 4.7.7's six-colour Jeweled Pipe GUI. */
public final class JeweledScreen extends AbstractContainerScreen<JeweledMenu> {
    private static final GuiIcon BACKGROUND = icon("jeweled_gui", 202, 211);
    private static final GuiIcon[] OUTLINES = {
        icon("jeweled_outline_1", 192, 86), icon("jeweled_outline_2", 192, 86),
        icon("jeweled_outline_3", 192, 86), icon("jeweled_outline_4", 192, 86),
        icon("jeweled_outline_5", 192, 86), icon("jeweled_outline_6", 192, 86)
    };
    private final String[] tabNames = new String[6];
    private int[] tabEnds;

    private static GuiIcon icon(String name, int width, int height) {
        return new GuiIcon(ResourceLocation.fromNamespaceAndPath("additionalpipes", "textures/gui/" + name + ".png"), 0, 0, width, height);
    }

    public JeweledScreen(JeweledMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 202;
        imageHeight = 211;
    }

    @Override protected void init() {
        super.init();
        for (int i = 0; i < tabNames.length; i++) {
            tabNames[i] = Component.translatable("gui.additionalpipes.jeweled.side."
                + JeweledGuiLayout.DIRECTIONS[i]).getString();
        }
        tabEnds = JeweledGuiLayout.tabEnds(tabNames, font::width);
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private boolean clickOriginalPanel(double x, double y, int button) {
        if (button != 0 || tabEnds == null) return false;
        double localX = x - leftPos;
        double localY = y - topPos;
        int tab = JeweledGuiLayout.tabAt(localX, localY, menu.currentSide(), tabEnds);
        if (tab >= 0) {
            sendButton(tab);
            return true;
        }
        int toggle = JeweledGuiLayout.toggleAt(localX, localY);
        if (toggle >= 0) {
            sendButton(toggle);
            return true;
        }
        return false;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clickOriginalPanel(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        BACKGROUND.drawAt(graphics, leftPos, topPos);
        OUTLINES[Math.max(0, Math.min(5, menu.currentSide()))].drawAt(graphics, leftPos + 5, topPos + 25);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String heading = Component.translatable("gui.additionalpipes.jeweled").getString();
        drawUnclippedText(graphics, heading, 8, 6, imageWidth - 16, 0xFF404040, true);
        drawUnclippedText(graphics, playerInventoryTitle.getString(), 8, imageHeight - 93, 170, 0xFF404040, false);
        String match = Component.translatable("gui.additionalpipes.jeweled.match").getString();
        drawUnclippedText(graphics, match, 105, 94, JeweledGuiLayout.TAG_X - 107, 0xFF404040, false);
        drawOriginalTabs(graphics);
        boolean unsorted = menu.acceptsUnsorted();
        boolean nbt = menu.matchNBT();
        boolean metadata = menu.matchMetadata();
        double localX = mouseX - leftPos;
        double localY = mouseY - topPos;
        int hovered = JeweledGuiLayout.toggleAt(localX, localY);
        drawOriginalToggle(graphics, JeweledGuiLayout.ACCEPT_X, JeweledGuiLayout.ACCEPT_WIDTH,
            Component.translatable("gui.additionalpipes.jeweled.unsorted").getString(), unsorted, hovered == 6);
        drawOriginalToggle(graphics, JeweledGuiLayout.TAG_X, JeweledGuiLayout.TAG_WIDTH,
            Component.translatable("gui.additionalpipes.jeweled.nbt").getString(), nbt, hovered == 7);
        drawOriginalToggle(graphics, JeweledGuiLayout.META_X, JeweledGuiLayout.META_WIDTH,
            Component.translatable("gui.additionalpipes.jeweled.metadata").getString(), metadata, hovered == 8);
    }

    /** Preserve full translated labels by narrowing the glyphs, never substituting ellipses. */
    private void drawUnclippedText(GuiGraphics graphics, String text, float x, float y,
                                   int maxWidth, int colour, boolean centered) {
        float factor = GuiTextLayout.horizontalScale(text, maxWidth, font::width);
        if (factor <= 0.0F) return;
        float drawX = centered ? x + (maxWidth - font.width(text) * factor) / 2.0F : x;
        graphics.pose().pushPose();
        graphics.pose().translate(drawX, y, 0);
        graphics.pose().scale(factor, 1.0F, 1.0F);
        graphics.drawString(font, text, 0, 0, colour, false);
        graphics.pose().popPose();
    }

    private void drawOriginalTabs(GuiGraphics graphics) {
        if (tabEnds == null) return;
        for (int side = 0; side < 6; side++) {
            int x0 = tabEnds[side];
            int x1 = tabEnds[side + 1] - 1;
            int top = side == menu.currentSide() ? JeweledGuiLayout.TAB_Y - 3 : JeweledGuiLayout.TAB_Y;
            graphics.fill(x0, top, x1, JeweledGuiLayout.TAB_Y + JeweledGuiLayout.TAB_HEIGHT, JeweledGuiLayout.TAB_BORDER[side]);
            graphics.fill(x0 + 2, top + 2, x1 - 2, JeweledGuiLayout.TAB_Y + JeweledGuiLayout.TAB_HEIGHT, JeweledGuiLayout.TAB_MAIN[side]);
            drawUnclippedText(graphics, tabNames[side], x0 + 3, JeweledGuiLayout.TAB_Y + 3,
                Math.max(1, x1 - x0 - 6), 0xFF404040, false);
        }
    }

    private void drawOriginalToggle(GuiGraphics graphics, int x, int width, String text, boolean pressed,
                                    boolean hovered) {
        int y = JeweledGuiLayout.BUTTON_Y;
        int height = JeweledGuiLayout.BUTTON_HEIGHT;
        // 4.7.7 GuiButtonOnOff uses the vanilla hovered look whenever pressed.
        boolean highlighted = pressed || hovered;
        graphics.fill(x, y, x + width, y + height, 0xFF161616);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, highlighted ? 0xFFB7B7B7 : 0xFF858585);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, highlighted ? 0xFFE5E5E5 : 0xFFB8B8B8);
        graphics.fill(x + 1, y + 1, x + 2, y + height - 1, highlighted ? 0xFFE5E5E5 : 0xFFB8B8B8);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xFF555555);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + height - 1, 0xFF555555);
        drawUnclippedText(graphics, text, x + 3, y + 6, width - 6,
            highlighted ? 0xFFFFFFA0 : 0xFFFFFFFF, true);
    }



    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
