package buildcraft.additionalpipes.pipes;

import buildcraft.lib.internal.inventory.IItemTransactor;
import buildcraft.lib.inventory.ItemTransactorHelper;
import buildcraft.lib.inventory.NoSpaceTransactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.Container;
import buildcraft.lib.misc.BlockUtil;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class APItemPorts {
    private APItemPorts() { }

    private static IItemTransactor transactor(Level level, BlockPos pos, Direction side) {
        if (!level.hasChunkAt(pos)) return NoSpaceTransactor.INSTANCE;
        BlockEntity block = level.getBlockEntity(pos);
        return ItemTransactorHelper.getTransactor(block, side);
    }

    public static boolean isInventory(Level level, BlockPos pos, Direction side) {
        return transactor(level, pos, side) != NoSpaceTransactor.INSTANCE;
    }

    /** Original Addition Pipe: match item identity and damage/metadata, but ignore NBT. */
    private static boolean matches(ItemStack found, ItemStack desired) {
        return !found.isEmpty() && found.getItem() == desired.getItem()
            && found.getDamageValue() == desired.getDamageValue();
    }

    private static boolean contains(Container container, ItemStack stack) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (matches(container.getItem(slot), stack)) return true;
        }
        return false;
    }

    private static boolean contains(IItemHandler handler, ItemStack stack) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (matches(handler.getStackInSlot(slot), stack)) return true;
        }
        return false;
    }

    /** Inspects both vanilla inventories and capability-backed modded storage. Read-only. */
    public static boolean containsMatchingItem(Level level, BlockPos pos, Direction side, ItemStack stack) {
        if (stack.isEmpty() || !level.hasChunkAt(pos)) return false;
        BlockEntity block = level.getBlockEntity(pos);
        if (block != null) {
            // A double chest exposes one logical inventory, not two independent halves.
            Container combined = BlockUtil.getCombinedDoubleChestContainer(block);
            if (combined != null && contains(combined, stack)) return true;
            if (block instanceof Container container && contains(container, stack)) return true;
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        if (handler != null && contains(handler, stack)) return true;
        // Some BCCE/addon inventories expose only a transactor. Simulation cannot
        // consume items and lets these inventories participate where extraction is supported.
        IItemTransactor items = transactor(level, pos, side);
        return items != NoSpaceTransactor.INSTANCE
            && !items.extract(found -> matches(found, stack), 1, 1, true).isEmpty();
    }

    public static boolean canInsert(Level level, BlockPos pos, Direction side, ItemStack stack) {
        if (stack.isEmpty()) return false;
        IItemTransactor items = transactor(level, pos, side);
        if (items == NoSpaceTransactor.INSTANCE) return false;
        ItemStack remainder = items.insert(stack.copy(), false, true);
        return remainder.getCount() < stack.getCount();
    }
}
