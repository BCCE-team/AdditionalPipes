package buildcraft.additionalpipes.client;

import buildcraft.additionalpipes.pipes.AdvancedWoodMenu;
import buildcraft.lib.gui.GuiIcon;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class AdvancedWoodScreen extends AbstractContainerScreen<AdvancedWoodMenu> {
    private static final GuiIcon BACKGROUND = new GuiIcon(ResourceLocation.fromNamespaceAndPath("additionalpipes", "textures/gui/advanced_wood_gui.png"), 0, 0, 175, 156);
    private Button exclusionButton;

    public AdvancedWoodScreen(AdvancedWoodMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 175;
        imageHeight = 156;
    }

    @Override
    protected void init() {
        super.init();
        exclusionButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            if (minecraft != null && minecraft.gameMode != null)
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 8, topPos + 40, 140, 20).build());
        updateButton();
    }

    private void updateButton() {
        if (exclusionButton != null)
            exclusionButton.setMessage(Component.literal(menu.excluded()
                ? "These items are excluded" : "These items are required"));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButton();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        BACKGROUND.drawAt(graphics, leftPos, topPos);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 5, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, 8, 63, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
