package buildcraft.additionalpipes.client;

public final class AdvancedWoodGuiLayout {
    public static final int WIDTH = 175;
    public static final int HEIGHT = 156;
    public static final int FILTER_X = 8;
    public static final int FILTER_Y = 18;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 76;
    public static final int HOTBAR_Y = 134;
    public static final int SLOT_STEP = 18;
    public static final int SLOT_SIZE = 16;

    private AdvancedWoodGuiLayout() { }

    public static int filterSlotAt(double mouseX, double mouseY, int left, int top) {
        return rowSlotAt(mouseX, mouseY, left + FILTER_X, top + FILTER_Y);
    }

    public static int inventorySlotAt(double mouseX, double mouseY, int left, int top) {
        for (int row = 0; row < 3; row++) {
            int col = rowSlotAt(mouseX, mouseY, left + INVENTORY_X,
                top + INVENTORY_Y + row * SLOT_STEP);
            if (col >= 0) return row * 9 + col + 9;
        }
        return rowSlotAt(mouseX, mouseY, left + INVENTORY_X, top + HOTBAR_Y);
    }

    public static int inventorySlotX(int slot, int left) {
        if (slot < 0 || slot >= 36) throw new IllegalArgumentException("Invalid inventory slot");
        return left + INVENTORY_X + slot % 9 * SLOT_STEP;
    }

    public static int inventorySlotY(int slot, int top) {
        if (slot < 0 || slot >= 36) throw new IllegalArgumentException("Invalid inventory slot");
        return slot < 9 ? top + HOTBAR_Y : top + INVENTORY_Y + (slot - 9) / 9 * SLOT_STEP;
    }

    private static int rowSlotAt(double mouseX, double mouseY, int left, int top) {
        if (mouseX < left || mouseY < top || mouseY >= top + SLOT_SIZE
            || mouseX >= left + 9 * SLOT_STEP) return -1;
        if ((mouseX - left) % SLOT_STEP >= SLOT_SIZE) return -1;
        return (int) ((mouseX - left) / SLOT_STEP);
    }
}
