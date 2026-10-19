package buildcraft.additionalpipes.pipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public final class APItemPorts {
    private APItemPorts() { }

    private static IItemHandler handler(Level level, BlockPos pos, Direction side) {
        if (!level.hasChunkAt(pos)) return null;
        BlockEntity block = level.getBlockEntity(pos);
        return block == null ? null : block.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
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
