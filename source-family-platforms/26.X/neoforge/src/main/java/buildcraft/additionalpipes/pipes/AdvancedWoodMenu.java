package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.platform.APDimensionIds;
import buildcraft.additionalpipes.platform.APPlayerLevel;
import buildcraft.additionalpipes.platform.APContentRegistry;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.lib.gui.BCMenuUtil;
import buildcraft.lib.gui.slot.IPhantomSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class AdvancedWoodMenu extends AbstractContainerMenu {
    private static final int FILTER_COUNT = 9;
    private static final int PLAYER_FIRST = FILTER_COUNT;
    private static final int PLAYER_LAST = PLAYER_FIRST + 36;
    private final AdvancedWoodComponent component;
    private final PipeLocation position;
    private final Container filterInventory;
    private final DataSlot exclusion;

    public AdvancedWoodMenu(int id, Inventory playerInventory) {
        this(id, playerInventory, new SimpleContainer(FILTER_COUNT), null, null);
    }

    public AdvancedWoodMenu(int id, Inventory playerInventory, AdvancedWoodComponent component, PipeLocation position) {
        this(id, playerInventory, new FilterInventory(component), component, position);
    }

    private AdvancedWoodMenu(int id, Inventory playerInventory, Container filterInventory,
                             AdvancedWoodComponent component, PipeLocation position) {
        super(APContentRegistry.ADVANCED_WOOD_MENU.get(), id);
        this.component = component;
        this.position = position;
        this.filterInventory = filterInventory;
        this.exclusion = component == null ? DataSlot.standalone() : new DataSlot() {
            @Override public int get() { return component.excluded() ? 1 : 0; }
            @Override public void set(int value) { }
        };
        addDataSlot(exclusion);
        for (int slot = 0; slot < FILTER_COUNT; slot++) {
            addSlot(new FilterSlot(filterInventory, slot, 8 + 18 * slot, 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 76 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 134));
        }
        filterInventory.startOpen(playerInventory.player);
    }

    public boolean excluded() { return exclusion.get() != 0; }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (button != 0 || component == null || !stillValid(player)) return false;
        component.setExcluded(!component.excluded());
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (component == null) return true;
        if (!(player instanceof ServerPlayer serverPlayer) || position == null) return false;
        ServerLevel level = APPlayerLevel.of(serverPlayer);
        if (!APDimensionIds.of(level).equals(position.dimension())) return false;
        BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
        if (!level.hasChunkAt(pos) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return false;
        return BuildCraftApi.service(BuildCraftServices.PIPES).pipe(level, pos)
            .flatMap(pipe -> pipe.component(APInsertionPipes.ADVANCED_COMPONENT))
            .filter(found -> found == component).isPresent();
    }

    @Override
    public void clicked(int slotId, int dragType, ContainerInput clickType, Player player) {
        if (BCMenuUtil.handleFakeSlotClick(this, slotId, dragType, clickType, player)) return;
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        if (index < FILTER_COUNT) return ItemStack.EMPTY;
        if (index < PLAYER_FIRST + 27) {
            if (!moveItemStackTo(original, PLAYER_FIRST + 27, PLAYER_LAST, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(original, PLAYER_FIRST, PLAYER_FIRST + 27, false)) {
            return ItemStack.EMPTY;
        }
        if (original.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, original);
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        filterInventory.stopOpen(player);
    }

    private static final class FilterSlot extends Slot implements IPhantomSlot {
        private FilterSlot(Container inventory, int slot, int x, int y) {
            super(inventory, slot, x, y);
        }

        @Override public boolean canAdjustCount() { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public int getMaxStackSize() { return 1; }
    }

    private static final class FilterInventory implements Container {
        private final AdvancedWoodComponent component;

        private FilterInventory(AdvancedWoodComponent component) { this.component = component; }
        @Override public int getContainerSize() { return FILTER_COUNT; }
        @Override public boolean isEmpty() {
            for (int i = 0; i < FILTER_COUNT; i++) if (!component.getFilterStack(i).isEmpty()) return false;
            return true;
        }
        @Override public ItemStack getItem(int slot) { return component.getFilterStack(slot); }
        @Override public ItemStack removeItem(int slot, int amount) { return ItemStack.EMPTY; }
        @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }
        @Override public int getMaxStackSize() { return 1; }
        @Override public void setItem(int slot, ItemStack stack) { component.setFilterStack(slot, stack); }
        @Override public void setChanged() { component.markFiltersChanged(); }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() {
            for (int i = 0; i < FILTER_COUNT; i++) component.setFilterStack(i, ItemStack.EMPTY);
        }
    }
}
