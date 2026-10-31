package buildcraft.additionalpipes.pipes;

import buildcraft.lib.internal.inventory.IItemTransactor;
import buildcraft.lib.inventory.ItemTransactorHelper;
import buildcraft.lib.inventory.NoSpaceTransactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class APItemPorts {
    private APItemPorts() { }

    private static IItemTransactor transactor(Level level, BlockPos pos, Direction side) {
        if (!level.hasChunkAt(pos)) return NoSpaceTransactor.INSTANCE;
        return ItemTransactorHelper.getTransactor(level, pos, side, level.getBlockEntity(pos));
    }

    public static boolean isInventory(Level level, BlockPos pos, Direction side) {
        return transactor(level, pos, side) != NoSpaceTransactor.INSTANCE;
    }

    public static boolean canInsert(Level level, BlockPos pos, Direction side, ItemStack stack) {
        if (stack.isEmpty()) return false;
        IItemTransactor items = transactor(level, pos, side);
        if (items == NoSpaceTransactor.INSTANCE) return false;
        ItemStack remainder = items.insert(stack.copy(), false, true);
        return remainder.getCount() < stack.getCount();
    }
}
