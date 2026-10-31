package buildcraft.additionalpipes.client;

import buildcraft.additionalpipes.pipes.ClosedPipeMenu;
import buildcraft.lib.gui.GuiIcon;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;

public final class ClosedPipeScreen extends AbstractContainerScreen<ClosedPipeMenu> {
    private static final GuiIcon BACKGROUND = new GuiIcon(new ResourceLocation("minecraft", "textures/gui/container/dispenser.png"), 0, 0, 176, 166);

    public ClosedPipeScreen(ClosedPipeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
    }

    @Override protected void renderBg(PoseStack graphics, float partialTick, int mouseX, int mouseY) {
        BACKGROUND.drawAt(graphics, leftPos, topPos);
    }

    @Override protected void renderLabels(PoseStack graphics, int mouseX, int mouseY) {
        String heading = GuiTextLayout.fit(title.getString(), imageWidth - 16, font::width);
        font.draw(graphics, heading, (imageWidth - font.width(heading)) / 2, 6, 0x404040);
        String inventoryLabel = GuiTextLayout.fit(playerInventoryTitle.getString(), imageWidth - 16, font::width);
        font.draw(graphics, inventoryLabel, 8, imageHeight - 96 + 2, 0x404040);
    }

    @Override public void render(PoseStack graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
