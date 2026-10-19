package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.APConfiguration;
import buildcraft.additionalpipes.platform.APDimensionIds;
import buildcraft.additionalpipes.service.APServices;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.persistence.ApiCodec;
import buildcraft.api.v2.persistence.CodecResult;
import buildcraft.api.v2.persistence.OpaqueData;
import buildcraft.api.v2.pipe.ItemRouteComponent;
import buildcraft.api.v2.pipe.ItemRouteContext;
import buildcraft.api.v2.pipe.PipeActivationComponent;
import buildcraft.api.v2.pipe.PipeActivationContext;
import buildcraft.api.v2.pipe.PipeActivationResult;
import buildcraft.api.v2.pipe.PipeComponentLifecycle;
import buildcraft.api.v2.pipe.PipeMutationContext;
import buildcraft.api.v2.pipe.PipeView;
import buildcraft.api.v2.pipe.RouteDecision;
import buildcraft.transport.internal.pipe.IItemPipe;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class PriorityInsertionComponent implements ItemRouteComponent,
    PipeComponentLifecycle, PipeActivationComponent {

    private final int[] priorities = {1, 1, 1, 1, 1, 1};
    private PipeView pipe;
    private PipeMutationContext mutable;
    private ServerLevel cachedWorld;

    public PriorityInsertionComponent() { }
    public PriorityInsertionComponent(PipeView view) { attached(view); }

    @Override public Identifier typeId() { return APInsertionPipes.PRIORITY_COMPONENT; }
    @Override public void attached(PipeView view) {
        pipe = view;
        if (view instanceof PipeMutationContext mutation) mutable = mutation;
    }
    @Override public void suspended() { cachedWorld = null; }
    @Override public void invalidated() { cachedWorld = null; }

    public int[] priorities() { return priorities.clone(); }
    public int priority(int face) { return priorities[face]; }

    public void setPriority(int face, int value) {
        if (face < 0 || face >= 6 || value < 0 || value > 6) throw new IllegalArgumentException("Invalid priority");
        if (priorities[face] == value) return;
        priorities[face] = value;
        boolean any = false;
        for (int p : priorities) if (p > 0) any = true;
        if (!any) Arrays.fill(priorities, 1);
        if (mutable != null && !mutable.isClientSide()) mutable.markChanged();
    }

    private ServerLevel locateWorld() {
        if (pipe == null) return null;
        MinecraftServer server = APServices.server();
        if (server == null || !server.isSameThread()) return null;
        if (valid(cachedWorld)) return cachedWorld;
        cachedWorld = null;
        for (ServerLevel level : server.getAllLevels()) {
            if (valid(level)) { cachedWorld = level; break; }
        }
        return cachedWorld;
    }

    private boolean valid(ServerLevel level) {
        return level != null && pipe != null && level.hasChunkAt(pipe.position())
            && BuildCraftApi.service(BuildCraftServices.PIPES).pipe(level, pipe.position())
                .flatMap(view -> view.component(typeId())).orElse(null) == this;
    }

    @Override public RouteDecision route(ItemRouteContext context) {
        EnumMap<Direction, Integer> weights = new EnumMap<>(Direction.class);
        for (Direction candidate : context.candidates()) weights.put(candidate, 1);
        ServerLevel level = locateWorld();
        if (level == null) return new RouteDecision(weights);
        BlockPos pos = context.pipe().position();
        for (int priority = 6; priority >= 1; priority--) {
            boolean anyInventory = false;
            EnumMap<Direction, Integer> selected = new EnumMap<>(Direction.class);
            for (Direction side : Direction.values()) {
                if (priorities[side.ordinal()] != priority || !context.candidates().contains(side)
                    || context.pipe().endpoint(side) != buildcraft.api.v2.pipe.PipeEndpointKind.BLOCK)
                    continue;
                BlockPos destination = pos.relative(side);
                if (!level.hasChunkAt(destination) || !APItemPorts.isInventory(level, destination, side.getOpposite())) continue;
                anyInventory = true;
                if (APItemPorts.canInsert(level, destination, side.getOpposite(), context.stack())) {
                    selected.put(side, 1);
                }
            }
            if (anyInventory) {
                if (!selected.isEmpty()) {
                    weights.replaceAll((side, w) -> selected.containsKey(side) ? 1 : 0);
                }
                break;
            }
        }
        return new RouteDecision(weights);
    }

    @Override public PipeActivationResult activate(PipeActivationContext context) {
        if (APConfiguration.get().filterRightclicks && !context.heldStack().isEmpty()
            && context.heldStack().getItem() instanceof IItemPipe) return PipeActivationResult.PASS;
        if (context.mode() == OperationMode.EXECUTE && !context.pipe().isClientSide()) {
            UUID id = context.actor().playerId().orElse(null);
            MinecraftServer server = APServices.server();
            ServerPlayer player = id == null || server == null ? null : server.getPlayerList().getPlayer(id);
            if (player != null && !player.isShiftKeyDown()) APInsertionUiServer.openPriority(player, this);
        }
        return PipeActivationResult.SUCCESS;
    }

    public PipeView pipe() { return pipe; }
    public ServerLevel level() { return locateWorld(); }

    public static final ApiCodec<PriorityInsertionComponent, OpaqueData> CODEC = new ApiCodec<>() {
        @Override public CodecResult<OpaqueData> encode(PriorityInsertionComponent instance) {
            byte[] bytes = new byte[6];
            for (int i = 0; i < 6; i++) bytes[i] = (byte) instance.priorities[i];
            return CodecResult.success(new OpaqueData(APInsertionPipes.PRIORITY_COMPONENT, bytes));
        }
        @Override public CodecResult<PriorityInsertionComponent> decode(OpaqueData data) {
            if (!APInsertionPipes.PRIORITY_COMPONENT.equals(data.format()) || data.size() != 6)
                return CodecResult.failure("Invalid priority insertion state");
            PriorityInsertionComponent instance = new PriorityInsertionComponent();
            byte[] bytes = data.bytes();
            boolean any = false;
            for (int i = 0; i < 6; i++) {
                int priority = Byte.toUnsignedInt(bytes[i]);
                if (priority > 6) return CodecResult.failure("Invalid side priority");
                instance.priorities[i] = priority;
                any |= priority > 0;
            }
            if (!any) Arrays.fill(instance.priorities, 1);
            return CodecResult.success(instance);
        }
    };
}
