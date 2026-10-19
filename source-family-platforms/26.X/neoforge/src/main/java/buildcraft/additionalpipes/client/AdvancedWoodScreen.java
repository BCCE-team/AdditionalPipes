package buildcraft.additionalpipes.client;

import buildcraft.additionalpipes.pipes.AdvancedWoodMenu;
import buildcraft.lib.gui.GuiIcon;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class AdvancedWoodScreen extends AbstractContainerScreen<AdvancedWoodMenu> {
    private static final GuiIcon BACKGROUND = new GuiIcon(Identifier.fromNamespaceAndPath("additionalpipes", "textures/gui/advanced_wood_gui.png"), 0, 0, 175, 156);
    private Button exclusionButton;

    public AdvancedWoodScreen(AdvancedWoodMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 175, 156);
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        BACKGROUND.drawAt(graphics, leftPos, topPos);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, (imageWidth - font.width(title)) / 2, 5, 0xFF404040, false);
        graphics.text(font, playerInventoryTitle, 8, 63, 0xFF404040, false);
    }
}
