package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.APConfiguration;
import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.fluid.FluidVariant;
import buildcraft.api.v2.fluid.FluidVolume;
import buildcraft.api.v2.pipe.PipeConnectionComponent;
import buildcraft.api.v2.pipe.PipeConnectionContext;
import buildcraft.api.v2.pipe.PipeConnectionDecision;
import buildcraft.api.v2.pipe.PipeExecutionContext;
import buildcraft.api.v2.pipe.PipeMutationContext;
import buildcraft.api.v2.pipe.PipeTickComponent;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Classic Water Pump Pipe: an infinite, rate-limited source while a still water
 * block sits immediately below. It neither consumes world water nor uses MJ.
 */
public final class WaterPumpComponent implements PipeTickComponent, PipeConnectionComponent {
    private static final FluidVariant WATER = FluidVariant.of(Identifier.parse("minecraft:water"));

    @Override public Identifier typeId() { return APUtilityPipes.WATER_PUMP_COMPONENT; }

    /** The underside is an intake, not a usable connection to another pipe/tank. */
    @Override public PipeConnectionDecision connection(PipeConnectionContext context) {
        return context.side() == Direction.DOWN ? PipeConnectionDecision.DENY : PipeConnectionDecision.PASS;
    }

    @Override public void tick(PipeMutationContext context) {
        if (!(context instanceof PipeExecutionContext world) || !world.isServerSide()
            || !world.isActive() || !world.isNeighbourLoaded(Direction.DOWN)) return;
        int rate = APConfiguration.get().waterPumpWaterPerTick;
        if (rate <= 0) return;
        BlockState below = world.neighbourState(Direction.DOWN).orElse(null);
        if (below == null || !isValidSource(below.is(Blocks.WATER), below.getFluidState().isSource())) return;
        context.fluidPort(Direction.DOWN).ifPresent(port -> {
            long accepted = port.insert(FluidVolume.of(WATER, rate), OperationMode.EXECUTE)
                .transferredAmount().milliBuckets();
            if (accepted > 0) context.markChanged();
        });
    }

    static boolean isValidSource(boolean vanillaWaterBlock, boolean source) {
        return vanillaWaterBlock && source;
    }
}
