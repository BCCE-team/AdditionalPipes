package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.service.APServices;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.pipe.ItemRouteComponent;
import buildcraft.api.v2.pipe.ItemRouteContext;
import buildcraft.api.v2.pipe.PipeComponentLifecycle;
import buildcraft.api.v2.pipe.PipeEndpointKind;
import buildcraft.api.v2.pipe.PipeView;
import buildcraft.api.v2.pipe.RouteDecision;
import buildcraft.transport.internal.pipe.IPipe;
import java.util.EnumMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** Routes to existing stacks first, using the 4.7.7 Addition Pipe selection rules. */
public final class AdditionPipeComponent implements ItemRouteComponent, PipeComponentLifecycle {
    private PipeView attached;
    private ServerLevel cachedWorld;

    public AdditionPipeComponent() { }
    public AdditionPipeComponent(PipeView pipe) { attached(pipe); }

    @Override public ResourceLocation typeId() { return APAdditionPipes.COMPONENT; }
    @Override public void attached(PipeView pipe) { attached = pipe; }
    @Override public void suspended() { cachedWorld = null; }
    @Override public void invalidated() { cachedWorld = null; attached = null; }

    private boolean stillAttached(ServerLevel level) {
        return level != null && attached != null && level.hasChunkAt(attached.position())
            && BuildCraftApi.service(BuildCraftServices.PIPES).pipe(level, attached.position())
                .flatMap(view -> view.component(typeId())).orElse(null) == this;
    }

    private ServerLevel world(PipeView pipe) {
        if (pipe instanceof IPipe runtime
            && runtime.getHolder().getPipeWorld() instanceof ServerLevel level) return level;
        MinecraftServer server = APServices.server();
        if (server == null || !server.isSameThread()) return null;
        if (stillAttached(cachedWorld)) return cachedWorld;
        cachedWorld = null;
        for (ServerLevel level : server.getAllLevels()) {
            if (stillAttached(level)) return cachedWorld = level;
        }
        return null;
    }

    @Override public RouteDecision route(ItemRouteContext context) {
        EnumMap<Direction, Integer> weights = new EnumMap<>(Direction.class);
        int candidates = 0;
        for (Direction side : context.candidates()) {
            candidates |= 1 << side.ordinal();
            weights.put(side, 1);
        }
        // Route decisions are server-owned. Never probe inventories during client prediction.
        if (candidates == 0 || context.pipe().isClientSide()) return new RouteDecision(weights);
        ServerLevel level = world(context.pipe());
        if (level == null) return new RouteDecision(weights);

        BlockPos position = context.pipe().position();
        ItemStack travelling = context.stack();
        if (travelling.isEmpty()) return new RouteDecision(weights);
        int inventories = 0;
        int preferred = 0;
        for (Direction side : context.candidates()) {
            if (context.pipe().endpoint(side) != PipeEndpointKind.BLOCK) continue;
            BlockPos neighbor = position.relative(side);
            if (!level.hasChunkAt(neighbor)
                || !APItemPorts.isInventory(level, neighbor, side.getOpposite())) continue;
            int bit = 1 << side.ordinal();
            inventories |= bit;
            // 4.7.7: match the item and metadata/damage; do not require NBT equality.
            if (APItemPorts.containsMatchingItem(level, neighbor, side.getOpposite(), travelling)
                && APItemPorts.canInsert(level, neighbor, side.getOpposite(), travelling)) {
                preferred |= bit;
            }
        }
        int selected = AdditionRouting.select(candidates, inventories, preferred);
        weights.replaceAll((side, ignored) -> (selected & (1 << side.ordinal())) != 0 ? 1 : 0);
        return new RouteDecision(weights);
    }
}
