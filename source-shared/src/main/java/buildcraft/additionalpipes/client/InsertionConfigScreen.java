package buildcraft.additionalpipes.client;

import buildcraft.additionalpipes.network.APPacket;
import buildcraft.additionalpipes.network.APMessageType;
import buildcraft.additionalpipes.platform.network.PlatformNetwork;
import buildcraft.lib.gui.GuiIcon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class InsertionConfigScreen extends Screen {
    private static final int WOOD_WIDTH = AdvancedWoodGuiLayout.WIDTH;
    private static final int WOOD_HEIGHT = AdvancedWoodGuiLayout.HEIGHT;
    private static final int PRIORITY_WIDTH = 132;
    private static final int PRIORITY_HEIGHT = 130;
    private static final int PRIORITY_X = 36;
    private static final int PRIORITY_Y = 14;
    private static final int PRIORITY_STEP = 18;
    private static final int PRIORITY_TITLE_Y = 4;
    private static final int WOOD_TITLE_Y = 5;
    private static final int WOOD_INVENTORY_X = 8;
    private static final int WOOD_INVENTORY_Y = 63;
    private static final int TITLE_COLOR = 0x404040;
    private static final int TEXT_COLOR = 0x404040;
    private static final GuiIcon WOOD_BACKGROUND = new GuiIcon(ResourceLocation.fromNamespaceAndPath("additionalpipes", "textures/gui/advanced_wood_gui.png"), 0, 0, WOOD_WIDTH, WOOD_HEIGHT);
    private static final GuiIcon PRIORITY_BACKGROUND = new GuiIcon(ResourceLocation.fromNamespaceAndPath("additionalpipes", "textures/gui/priority_gui.png"), 0, 0, PRIORITY_WIDTH, PRIORITY_HEIGHT);
    private APPacket.InsertionView state;
    private Button modeButton;
    private final Button[] valueButtons = new Button[6];
    private int ticks;
    private int selectedInventorySlot = -1;
    private final ItemStack[] filterIcons = new ItemStack[9];

    public InsertionConfigScreen(APPacket.InsertionView state) {
        super(Component.translatable(state.priority() ? "gui.additionalpipes.priority" : "gui.additionalpipes.advanced_wood"));
        this.state = state;
        updateFilterIcons();
    }

    @Override
    protected void init() {
        int left = (width - panelWidth()) / 2;
        int top = (height - panelHeight()) / 2;
        if (state.priority()) {
            for (int i = 0; i < 6; i++) {
                final int face = i;
                int y = top + PRIORITY_Y + i * PRIORITY_STEP;
                addButton(left + PRIORITY_X, y, 20, 17, "-", b -> changePriority(face, -1));
                valueButtons[i] = addButton(left + PRIORITY_X + 22, y, 30, 17, "0", b -> { });
                valueButtons[i].active = false;
                addButton(left + PRIORITY_X + 54, y, 20, 17, "+", b -> changePriority(face, 1));
            }
        } else {
            modeButton = addButton(left + 8, top + 40, 140, 20, "", b ->
                PlatformNetwork.sendToServer(new APPacket.AdvancedWoodUpdate(state.position())));
        }
        updateLabels();
    }

    private Button addButton(int x, int y, int w, int h, String label, Button.OnPress press) {
        return addRenderableWidget(Button.builder(Component.literal(label), press).bounds(x, y, w, h).build());
    }

    private int panelWidth() { return state.priority() ? PRIORITY_WIDTH : WOOD_WIDTH; }
    private int panelHeight() { return state.priority() ? PRIORITY_HEIGHT : WOOD_HEIGHT; }

    private void changePriority(int face, int step) {
        int now = state.priorities().get(face);
        int value = now + step;
        if (value < 0 || value > 6) return;
        PlatformNetwork.sendToServer(new APPacket.ValueUpdate(
            APMessageType.PRIORITY_UPDATE, state.position(), (byte) face, value));
    }

    private void clickFilter(int slot, boolean clear) {
        ItemStack stack = ItemStack.EMPTY;
        if (!clear && minecraft != null && minecraft.player != null) {
            stack = selectedInventorySlot >= 0
                ? minecraft.player.getInventory().getItem(selectedInventorySlot)
                : minecraft.player.getMainHandItem();
        }
        if (!clear && stack.isEmpty()) return;
        String name = stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int damage = stack.isEmpty() ? 0 : stack.getDamageValue();
        PlatformNetwork.sendToServer(new APPacket.AdvancedWoodFilterUpdate(state.position(), slot, name, damage));
    }

    private int hoveredFilterSlot(double mouseX, double mouseY) {
        if (state.priority()) return -1;
        int left = (width - panelWidth()) / 2;
        int top = (height - panelHeight()) / 2;
        return AdvancedWoodGuiLayout.filterSlotAt(mouseX, mouseY, left, top);
    }

    private int hoveredInventorySlot(double mouseX, double mouseY) {
        if (state.priority()) return -1;
        int left = (width - panelWidth()) / 2;
        int top = (height - panelHeight()) / 2;
        return AdvancedWoodGuiLayout.inventorySlotAt(mouseX, mouseY, left, top);
    }

    private ItemStack filterStack(int slot) {
        String itemName = state.filters().get(slot);
        if (itemName.isEmpty()) return ItemStack.EMPTY;
        for (var item : BuiltInRegistries.ITEM) {
            if (!BuiltInRegistries.ITEM.getKey(item).toString().equals(itemName)) continue;
            ItemStack stack = new ItemStack(item);
            if (stack.isDamageableItem()) stack.setDamageValue(state.damages().get(slot));
            return stack;
        }
        return ItemStack.EMPTY;
    }

    private void updateFilterIcons() {
        for (int slot = 0; slot < filterIcons.length; slot++) filterIcons[slot] = filterStack(slot);
    }

    private void updateLabels() {
        if (state.priority()) {
            for (int i = 0; i < 6; i++) {
                if (valueButtons[i] != null) valueButtons[i].setMessage(Component.literal(Integer.toString(state.priorities().get(i))));
            }
        } else if (modeButton != null) {
            modeButton.setMessage(Component.literal(
                state.excluded() ? "These items are excluded" : "These items are required"));
        }
    }

    public void refresh(APPacket.InsertionView next) {
        if (!next.position().equals(state.position()) || next.priority() != state.priority()) return;
        state = next;
        updateFilterIcons();
        updateLabels();
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks >= 20) {
            ticks = 0;
            PlatformNetwork.sendToServer(new APPacket.InsertionViewRequest(state.position()));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int slot = hoveredFilterSlot(mouseX, mouseY);
        if (slot >= 0 && (button == 0 || button == 1)) {
            clickFilter(slot, button == 1);
            return true;
        }
        if (!state.priority() && button == 0) {
            int inventorySlot = hoveredInventorySlot(mouseX, mouseY);
            if (inventorySlot >= 0) {
                selectedInventorySlot = minecraft == null || minecraft.player == null
                    || minecraft.player.getInventory().getItem(inventorySlot).isEmpty()
                    || selectedInventorySlot == inventorySlot ? -1 : inventorySlot;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - panelWidth()) / 2;
        int top = (height - panelHeight()) / 2;
        (state.priority() ? PRIORITY_BACKGROUND : WOOD_BACKGROUND).drawAt(graphics, left, top);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (state.priority()) {
            graphics.drawString(font, title(), left + (PRIORITY_WIDTH - font.width(title())) / 2, top + PRIORITY_TITLE_Y, TITLE_COLOR, false);
        } else {
            drawSlotHighlight(graphics, left, top, mouseX, mouseY);
            drawFilterItems(graphics, left, top);
            drawPlayerInventory(graphics, left, top);
            graphics.drawString(font, title(), left + (WOOD_WIDTH - font.width(title())) / 2, top + WOOD_TITLE_Y, TITLE_COLOR, false);
            graphics.drawString(font, Component.translatable("container.inventory"), left + WOOD_INVENTORY_X, top + WOOD_INVENTORY_Y, TEXT_COLOR, false);
        }
    }

    private void drawFilterItems(GuiGraphics graphics, int left, int top) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = filterIcons[i];
            if (stack.isEmpty()) continue;
            int x = left + AdvancedWoodGuiLayout.FILTER_X + i * AdvancedWoodGuiLayout.SLOT_STEP;
            int y = top + AdvancedWoodGuiLayout.FILTER_Y;
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y);
        }
    }

    private void drawPlayerInventory(GuiGraphics graphics, int left, int top) {
        if (minecraft == null || minecraft.player == null) return;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = minecraft.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            int x = AdvancedWoodGuiLayout.inventorySlotX(slot, left);
            int y = AdvancedWoodGuiLayout.inventorySlotY(slot, top);
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y);
        }
    }

    private void drawSlotHighlight(GuiGraphics graphics, int left, int top,
                                   int mouseX, int mouseY) {
        if (selectedInventorySlot >= 0) {
            int x = AdvancedWoodGuiLayout.inventorySlotX(selectedInventorySlot, left);
            int y = AdvancedWoodGuiLayout.inventorySlotY(selectedInventorySlot, top);
            graphics.fill(x, y, x + 16, y + 16, 0x66F9CE60);
        }
        int hovered = hoveredInventorySlot(mouseX, mouseY);
        if (hovered >= 0 && hovered != selectedInventorySlot) {
            int x = AdvancedWoodGuiLayout.inventorySlotX(hovered, left);
            int y = AdvancedWoodGuiLayout.inventorySlotY(hovered, top);
            graphics.fill(x, y, x + 16, y + 16, 0x60FFFFFF);
        }
        int filter = hoveredFilterSlot(mouseX, mouseY);
        if (filter >= 0) {
            int x = left + AdvancedWoodGuiLayout.FILTER_X + filter * AdvancedWoodGuiLayout.SLOT_STEP;
            int y = top + AdvancedWoodGuiLayout.FILTER_Y;
            graphics.fill(x, y, x + 16, y + 16, 0x60FFFFFF);
        }
    }

    private String title() {
        return Component.translatable(state.priority() ? "gui.additionalpipes.priority" : "gui.additionalpipes.advanced_wood").getString();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
