package buildcraft.additionalpipes.client;

import buildcraft.additionalpipes.pipes.ClosedPipeMenu;
import buildcraft.lib.gui.GuiIcon;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class ClosedPipeScreen extends AbstractContainerScreen<ClosedPipeMenu> {
    private static final GuiIcon BACKGROUND = new GuiIcon(Identifier.fromNamespaceAndPath("minecraft", "textures/gui/container/dispenser.png"), 0, 0, 176, 166);

    public ClosedPipeScreen(ClosedPipeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        BACKGROUND.drawAt(graphics, leftPos, topPos);
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String heading = GuiTextLayout.fit(title.getString(), imageWidth - 16, font::width);
        graphics.text(font, heading, (imageWidth - font.width(heading)) / 2, 6, 0xFF404040, false);
        String inventoryLabel = GuiTextLayout.fit(playerInventoryTitle.getString(), imageWidth - 16, font::width);
        graphics.text(font, inventoryLabel, 8, imageHeight - 96 + 2, 0xFF404040, false);
    }
}
