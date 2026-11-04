package buildcraft.additionalpipes.pipes;

import buildcraft.api.v2.fluid.FluidVolume;
import buildcraft.lib.fluid.FuelApiBridge;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/** NeoForge item-fluid bridge. The 26.3 source overlay uses BCCE's transaction shim. */
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
            || !FluidStack.isSameFluidSameComponents(drained, sample)) return null;
        FluidVolume volume = FuelApiBridge.volumeOf(drained);
        return volume.isEmpty() ? null : new Drained(volume, handler.getContainer().copy());
    }
}
