package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.APConfiguration;
import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.item.ItemPort;
import buildcraft.api.v2.pipe.ItemInjectionRequest;
import buildcraft.api.v2.pipe.ItemPipePort;
import buildcraft.api.v2.pipe.ItemTransitData;
import buildcraft.api.v2.pipe.PipeMutationContext;
import buildcraft.api.v2.pipe.PipeTickComponent;
import buildcraft.api.v2.pipe.PipeView;
import buildcraft.api.v2.world.WorldAccessContext;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class GravityFeedComponent implements PipeTickComponent {
    private int ticksSincePull;

    public GravityFeedComponent() { }
    public GravityFeedComponent(PipeView view) { }

    @Override public ResourceLocation typeId() { return APStoragePipes.GRAVITY_COMPONENT; }

    @Override public void tick(PipeMutationContext context) {
        if (context.isClientSide()) return;
        if (++ticksSincePull < APConfiguration.get().gravityFeedPipeTicksPerPull) return;
        ticksSincePull = 0;
        Optional<WorldAccessContext> access = context.worldAccess();
        if (access.isEmpty() || !context.connectedSides().contains(Direction.UP)) return;
        BlockPos above = context.position().above();
        WorldAccessContext world = access.get();
        if (!world.isLoaded(above) || world.pipeTypeId(above).isPresent()) return;
        Optional<ItemPort> source = world.itemPort(above, Direction.DOWN);
        Optional<ItemPipePort> destination = context.itemPipePort(Direction.UP);
        if (source.isEmpty() || destination.isEmpty()) return;

        ItemPort inventory = source.get();
        ItemPipePort pipe = destination.get();
        ItemStack offered = inventory.extract(stack -> !stack.isEmpty(), 1, OperationMode.SIMULATE).transferred();
        if (offered.isEmpty()) return;
        ItemInjectionRequest request = new ItemInjectionRequest(offered, Direction.UP, ItemTransitData.DEFAULT);
        if (pipe.inject(request, OperationMode.SIMULATE).transferredCount() < 1) return;
        ItemStack taken = inventory.extract(stack -> !stack.isEmpty(), 1, OperationMode.EXECUTE).transferred();
        if (taken.isEmpty()) return;
        int accepted = pipe.inject(new ItemInjectionRequest(taken, Direction.UP, ItemTransitData.DEFAULT),
            OperationMode.EXECUTE).transferredCount();
        if (accepted < taken.getCount()) {
            ItemStack remainder = taken.copy();
            remainder.shrink(Math.max(0, accepted));
            if (!remainder.isEmpty()) inventory.insert(remainder, OperationMode.EXECUTE);
        }
    }
}
