package buildcraft.additionalpipes.pipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public final class APItemPorts {
    private APItemPorts() { }

    private static IItemHandler handler(Level level, BlockPos pos, Direction side) {
        return level.hasChunkAt(pos) ? level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) : null;
    }

    public static boolean isInventory(Level level, BlockPos pos, Direction side) {
        return handler(level, pos, side) != null;
    }

    public static boolean canInsert(Level level, BlockPos pos, Direction side, ItemStack stack) {
        if (stack.isEmpty()) return false;
        IItemHandler items = handler(level, pos, side);
        if (items == null) return false;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            if (!items.isItemValid(slot, stack)) continue;
            ItemStack remaining = items.insertItem(slot, stack.copy(), true);
            if (remaining.getCount() < stack.getCount()) return true;
        }
        return false;
    }
}
