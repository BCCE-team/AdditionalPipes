package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.filter.SideFilterData;
import buildcraft.additionalpipes.platform.APContentRegistry;
import buildcraft.additionalpipes.platform.APDimensionIds;
import buildcraft.additionalpipes.platform.APPlayerLevel;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Six selectable 3x9 real filter inventories, with synchronized per-side settings. */
public final class JeweledMenu extends AbstractContainerMenu {
    private static final int FILTER_SLOTS = JeweledPipeComponent.SLOTS_PER_SIDE;
    private final JeweledPipeComponent component;
    private final PipeLocation position;
    private final Container inventory;
    private final DataSlot selectedSide = DataSlot.standalone();
    private final DataSlot[] optionData = new DataSlot[JeweledPipeComponent.SIDE_COUNT];

    public JeweledMenu(int id, Inventory playerInventory) { this(id, playerInventory, null, null); }

    public JeweledMenu(int id, Inventory playerInventory, JeweledPipeComponent component, PipeLocation position) {
        super(APContentRegistry.JEWELED_MENU.get(), id);
        this.component = component;
        this.position = position;
        inventory = component == null ? new SimpleContainer(FILTER_SLOTS)
            : new FilterInventory(component, selectedSide::get);
        addDataSlot(selectedSide);
        for (int i = 0; i < optionData.length; i++) {
            final int side = i;
            optionData[i] = component == null ? DataSlot.standalone() : new DataSlot() {
                @Override public int get() { return flags(component.options(side)); }
                @Override public void set(int value) { /* Only the server component may mutate options. */ }
            };
            addDataSlot(optionData[i]);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, row * 9 + col, 21 + col * 18, 33 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 21 + col * 18, 128 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 21 + col * 18, 186));
        }
        inventory.startOpen(playerInventory.player);
    }

    private static int flags(SideFilterData.Options options) {
        return (options.matchMetadata() ? 1 : 0) | (options.matchNBT() ? 2 : 0)
            | (options.acceptUnsortedItems() ? 4 : 0);
    }

    public int currentSide() { return selectedSide.get(); }
    public boolean matchMetadata() { return (optionData[currentSide()].get() & 1) != 0; }
    public boolean matchNBT() { return (optionData[currentSide()].get() & 2) != 0; }
    public boolean acceptsUnsorted() { return (optionData[currentSide()].get() & 4) != 0; }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (component == null || !stillValid(player)) return false;
        if (button >= 0 && button < JeweledPipeComponent.SIDE_COUNT) {
            selectedSide.set(button);
            broadcastChanges();
            return true;
        }
        int side = currentSide();
        SideFilterData.Options before = component.options(side);
        SideFilterData.Options after = switch (button) {
            case 6 -> new SideFilterData.Options(before.matchMetadata(), before.matchNBT(), !before.acceptUnsortedItems());
            case 7 -> new SideFilterData.Options(before.matchMetadata(), !before.matchNBT(), before.acceptUnsortedItems());
            case 8 -> new SideFilterData.Options(!before.matchMetadata(), before.matchNBT(), before.acceptUnsortedItems());
            default -> null;
        };
        if (after == null) return false;
        component.setOptions(side, after);
        broadcastChanges();
        return true;
    }

    @Override public boolean stillValid(Player player) {
        if (component == null) return true;
        if (!(player instanceof ServerPlayer serverPlayer) || position == null) return false;
        ServerLevel level = APPlayerLevel.of(serverPlayer);
        if (!APDimensionIds.of(level).equals(position.dimension())) return false;
        BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
        if (!level.hasChunkAt(pos) || player.distanceToSqr(pos.getX() + 0.5,
            pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return false;
        return BuildCraftApi.service(BuildCraftServices.PIPES).pipe(level, pos)
            .flatMap(pipe -> pipe.component(APJeweledPipes.COMPONENT))
            .filter(found -> found == component).isPresent();
    }

    /** Original ContainerJeweledPipe deliberately disabled shift-click transfers. */
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }

    @Override public void removed(Player player) {
        super.removed(player);
        inventory.stopOpen(player);
    }

    private static final class FilterInventory implements Container {
        private final JeweledPipeComponent component;
        private final IntSupplier side;
        private FilterInventory(JeweledPipeComponent component, IntSupplier side) {
            this.component = component;
            this.side = side;
        }
        @Override public int getContainerSize() { return FILTER_SLOTS; }
        @Override public boolean isEmpty() { return component.isEmpty(side.getAsInt()); }
        @Override public ItemStack getItem(int slot) { return component.getItem(side.getAsInt(), slot); }
        @Override public ItemStack removeItem(int slot, int count) { return component.takeItem(side.getAsInt(), slot, count); }
        @Override public ItemStack removeItemNoUpdate(int slot) { return component.removeItem(side.getAsInt(), slot); }
        @Override public void setItem(int slot, ItemStack stack) { component.setItem(side.getAsInt(), slot, stack); }
        @Override public int getMaxStackSize() { return 64; }
        @Override public void setChanged() { /* setItem/takeItem already persist mutations. */ }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() {
            for (int slot = 0; slot < FILTER_SLOTS; slot++) {
                component.setItem(side.getAsInt(), slot, ItemStack.EMPTY);
            }
        }
    }
}
