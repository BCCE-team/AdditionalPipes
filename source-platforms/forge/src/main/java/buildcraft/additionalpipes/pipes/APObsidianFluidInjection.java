package buildcraft.additionalpipes.pipes;

import buildcraft.api.v2.fluid.FluidVolume;
import buildcraft.api.v2.pipe.PipeMutationContext;
import buildcraft.lib.fluid.FuelApiBridge;
import buildcraft.transport.internal.pipe.IFlowFluid;
import buildcraft.transport.internal.pipe.IPipe;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/** Internal, neutral-face injection into BCCE's native fluid carrier. */
public final class APObsidianFluidInjection {
    private APObsidianFluidInjection() { }

    public static long insert(PipeMutationContext context, FluidVolume offered) {
        if (offered == null || offered.isEmpty() || !(context instanceof IPipe pipe)
            || !(pipe.getFlow() instanceof IFlowFluid flow)) return 0;
        var stack = FuelApiBridge.stackOf(offered);
        if (stack.isEmpty()) return 0;
        // null is deliberate: no physical face is an input, so connected outputs
        // must not be switched into input-only cooldown by the fluid flow.
        int accepted = flow.insertFluidsForce(stack, null, FluidAction.EXECUTE);
        return Math.max(0L, Math.min(offered.amount().milliBuckets(), (long) accepted));
    }
}
