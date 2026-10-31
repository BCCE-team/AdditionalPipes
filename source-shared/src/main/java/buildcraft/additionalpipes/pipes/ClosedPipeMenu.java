package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.platform.APContentRegistry;
import buildcraft.additionalpipes.platform.APDimensionIds;
import buildcraft.additionalpipes.platform.APPlayerLevel;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ClosedPipeMenu extends AbstractContainerMenu {
    private static final int PIPE_SLOTS = ClosedPipeComponent.GUI_SLOT_COUNT;
    private static final int PLAYER_FIRST = PIPE_SLOTS;
    private static final int PLAYER_END = PLAYER_FIRST + 36;
    private final ClosedPipeComponent component;
    private final PipeLocation position;
    private final Container storage;

    public ClosedPipeMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(PIPE_SLOTS), null, null);
    }

    public ClosedPipeMenu(int id, Inventory inventory, ClosedPipeComponent component, PipeLocation position) {
        this(id, inventory, new PipeInventory(component), component, position);
    }

    private ClosedPipeMenu(int id, Inventory inventory, Container storage, ClosedPipeComponent component, PipeLocation position) {
        super(APContentRegistry.CLOSED_PIPE_MENU.get(), id);
        this.component = component;
        this.position = position;
        this.storage = storage;
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            addSlot(new Slot(storage, col + row * 3, 62 + col * 18, 17 + row * 18));
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        storage.startOpen(inventory.player);
    }

    @Override public boolean stillValid(Player player) {
        if (component == null) return true;
        if (!(player instanceof ServerPlayer serverPlayer) || position == null) return false;
        ServerLevel level = APPlayerLevel.of(serverPlayer);
        if (!APDimensionIds.of(level).equals(position.dimension())) return false;
        BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
        if (!level.hasChunkAt(pos) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return false;
        return BuildCraftApi.service(BuildCraftServices.PIPES).pipe(level, pos)
            .flatMap(pipe -> pipe.component(APStoragePipes.CLOSED_COMPONENT))
            .filter(found -> found == component).isPresent();
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < PIPE_SLOTS) {
            if (!moveItemStackTo(stack, PLAYER_FIRST, PLAYER_END, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, PIPE_SLOTS, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        storage.stopOpen(player);
    }

    private static final class PipeInventory implements Container {
        private final ClosedPipeComponent storage;
        private PipeInventory(ClosedPipeComponent storage) { this.storage = storage; }
        @Override public int getContainerSize() { return PIPE_SLOTS; }
        @Override public boolean isEmpty() { return storage.isEmpty(); }
        @Override public ItemStack getItem(int slot) { return storage.getItem(slot); }
        @Override public ItemStack removeItem(int slot, int amount) { return storage.takeItem(slot, amount); }
        @Override public ItemStack removeItemNoUpdate(int slot) { return storage.removeItem(slot); }
        @Override public void setItem(int slot, ItemStack stack) { storage.setItem(slot, stack); }
        @Override public void setChanged() { storage.markInventoryChanged(); }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() {
            for (int i = 0; i < PIPE_SLOTS; i++) storage.setItem(i, ItemStack.EMPTY);
        }
    }
}
