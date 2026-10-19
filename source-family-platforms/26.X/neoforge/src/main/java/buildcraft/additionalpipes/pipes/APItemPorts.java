package buildcraft.additionalpipes.pipes;

import buildcraft.lib.misc.CapUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class APItemPorts {
    private APItemPorts() { }

    public static boolean isInventory(Level level, BlockPos pos, Direction side) {
        return level.hasChunkAt(pos) && CapUtil.getItemHandler(level, pos, side) != null;
    }

    public static boolean canInsert(Level level, BlockPos pos, Direction side, ItemStack stack) {
        if (stack.isEmpty() || !level.hasChunkAt(pos)) return false;
        var items = CapUtil.getItemHandler(level, pos, side);
        if (items == null) return false;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            if (!items.isItemValid(slot, stack)) continue;
            ItemStack remaining = items.insertItem(slot, stack.copy(), true);
            if (remaining.getCount() < stack.getCount()) return true;
        }
        return false;
    }
}
