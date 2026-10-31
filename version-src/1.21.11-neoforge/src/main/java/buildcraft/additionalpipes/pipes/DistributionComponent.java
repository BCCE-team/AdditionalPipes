package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.APConfiguration;
import buildcraft.additionalpipes.service.APServices;
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
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Stateful, per-stack quota routing from Additional Pipes 4.7.7. */
public final class DistributionComponent implements ItemRouteComponent, PipeComponentLifecycle, PipeActivationComponent {
    private final DistributionCycle cycle;
    private PipeMutationContext mutable;

    public DistributionComponent() { this(new DistributionCycle()); }
    private DistributionComponent(DistributionCycle cycle) { this.cycle = cycle; }

    @Override public Identifier typeId() { return APUtilityPipes.DISTRIBUTION_COMPONENT; }
    @Override public void attached(PipeView pipe) {
        mutable = pipe instanceof PipeMutationContext mutation ? mutation : null;
    }

    public int[] quotas() { return cycle.quotas(); }
    public boolean setQuota(int face, int value) {
        boolean changed = cycle.setQuota(face, value);
        if (changed && mutable != null && !mutable.isClientSide()) mutable.markChanged();
        return changed;
    }

    @Override public RouteDecision route(ItemRouteContext context) {
        int candidateMask = 0;
        for (Direction side : context.candidates()) candidateMask |= 1 << side.ordinal();
        // Routing is a server-authoritative, persistent operation. Never consume quota during client prediction.
        int selected = context.pipe().isClientSide() ? -1 : cycle.select(context.stack().getCount(), candidateMask);
        EnumMap<Direction, Integer> weights = new EnumMap<>(Direction.class);
        for (Direction side : context.candidates()) weights.put(side, side.ordinal() == selected ? 1 : 0);
        if (selected >= 0 && mutable != null && !mutable.isClientSide()) mutable.markChanged();
        return new RouteDecision(weights);
    }

    @Override public PipeActivationResult activate(PipeActivationContext context) {
        if (APConfiguration.get().filterRightclicks && !context.heldStack().isEmpty()
            && context.heldStack().getItem() instanceof IItemPipe) return PipeActivationResult.PASS;
        if (context.mode() == OperationMode.EXECUTE && !context.pipe().isClientSide()) {
            UUID playerId = context.actor().playerId().orElse(null);
            MinecraftServer server = APServices.server();
            ServerPlayer player = playerId == null || server == null ? null : server.getPlayerList().getPlayer(playerId);
            if (player != null && !player.isShiftKeyDown()) APDistributionUiServer.open(player, this, context.pipe());
        }
        return PipeActivationResult.SUCCESS;
    }

    public static final ApiCodec<DistributionComponent, OpaqueData> CODEC = new ApiCodec<>() {
        @Override public CodecResult<OpaqueData> encode(DistributionComponent component) {
            ByteBuffer buffer = ByteBuffer.allocate(32);
            for (int quota : component.cycle.quotas()) buffer.putInt(quota);
            buffer.putInt(component.cycle.selected()).putInt(component.cycle.sent());
            return CodecResult.success(new OpaqueData(APUtilityPipes.DISTRIBUTION_COMPONENT, buffer.array()));
        }
        @Override public CodecResult<DistributionComponent> decode(OpaqueData data) {
            if (!APUtilityPipes.DISTRIBUTION_COMPONENT.equals(data.format()) || data.size() != 32)
                return CodecResult.failure("Invalid distribution payload");
            ByteBuffer buffer = ByteBuffer.wrap(data.bytes());
            int[] quotas = new int[6];
            for (int i = 0; i < 6; i++) quotas[i] = buffer.getInt();
            int selected = buffer.getInt(), sent = buffer.getInt();
            try { return CodecResult.success(new DistributionComponent(new DistributionCycle(quotas, selected, sent))); }
            catch (IllegalArgumentException ex) { return CodecResult.failure("Invalid distribution quota/cursor"); }
        }
    };
}
