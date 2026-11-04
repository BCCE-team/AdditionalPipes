package buildcraft.additionalpipes.pipes;

import buildcraft.api.v2.fluid.FluidVolume;
import buildcraft.lib.fluid.FuelApiBridge;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

/** Forge item-fluid capability bridge, including vanilla buckets and modded containers. */
public final class APFluidContainers {
    public record Drained(FluidVolume fluid, ItemStack container) { }
    private APFluidContainers() { }

    public static Drained drainOne(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ItemStack unit = stack.copy();
        unit.setCount(1);
        IFluidHandlerItem handler = FluidUtil.getFluidHandler(unit).orElse(null);
        if (handler == null) return null;
        FluidStack sample = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (sample == null || sample.isEmpty() || sample.getAmount() <= 0) return null;
        FluidStack drained = handler.drain(sample.copy(), IFluidHandler.FluidAction.EXECUTE);
        if (drained == null || drained.isEmpty() || drained.getAmount() != sample.getAmount()
            || !drained.isFluidEqual(sample)) return null;
        FluidVolume volume = FuelApiBridge.volumeOf(drained);
        return volume.isEmpty() ? null : new Drained(volume, handler.getContainer().copy());
    }
}
