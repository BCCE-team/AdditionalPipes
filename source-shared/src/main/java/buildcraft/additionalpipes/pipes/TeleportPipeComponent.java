package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.APConfiguration;
import buildcraft.additionalpipes.api.ITeleportPipe;
import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.api.PipeType;
import buildcraft.additionalpipes.platform.APDimensionIds;
import buildcraft.additionalpipes.service.APServices;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.energy.MjAmount;
import buildcraft.api.v2.fluid.FluidAmount;
import buildcraft.api.v2.fluid.FluidMatcher;
import buildcraft.api.v2.permission.AutomationActor;
import buildcraft.api.v2.persistence.ApiCodec;
import buildcraft.api.v2.persistence.CodecResult;
import buildcraft.api.v2.persistence.OpaqueData;
import buildcraft.api.v2.pipe.ItemTransitComponent;
import buildcraft.api.v2.pipe.ItemTransitContext;
import buildcraft.api.v2.pipe.ItemTransitPhase;
import buildcraft.api.v2.pipe.PipeActivationComponent;
import buildcraft.api.v2.pipe.PipeActivationContext;
import buildcraft.api.v2.pipe.PipeActivationResult;
import buildcraft.api.v2.pipe.PipeComponentLifecycle;
import buildcraft.api.v2.pipe.PipeConnectionComponent;
import buildcraft.api.v2.pipe.PipeConnectionContext;
import buildcraft.api.v2.pipe.PipeConnectionDecision;
import buildcraft.api.v2.pipe.PipeMutationContext;
import buildcraft.api.v2.pipe.PipeResourceTickComponent;
import buildcraft.api.v2.pipe.PipeResourceTickContext;
import buildcraft.api.v2.pipe.PipeTickComponent;
import buildcraft.api.v2.pipe.PipeView;
import buildcraft.api.v2.pipe.PipeMedium;
import buildcraft.api.v2.pipe.PipeResourceService;
import buildcraft.transport.internal.pipe.IItemPipe;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class TeleportPipeComponent implements ITeleportPipe, PipeTickComponent,
    PipeComponentLifecycle, PipeConnectionComponent, PipeActivationComponent,
    ItemTransitComponent, PipeResourceTickComponent {

    private static final String TRANSIT_KEY = "additionalpipes:teleported";
    private static final byte[] TRANSIT_MARK = {1};
    private static final int MAX_FREQUENCY = 999_999;
    private static final long MAX_MJ_PER_TICK = MjAmount.MICRO_MJ_PER_MJ * 64L;
    private static final long MAX_FE_PER_TICK = 4096L;
    private static final int MAX_FLUID_PER_TICK = 1000;

    private int frequency;
    private byte state = 1;
    private boolean publicAccess;
    private UUID owner;
    private String ownerName = "";
    private PipeView pipe;
    private PipeMutationContext mutable;
    private ServerLevel world;
    private boolean active;
    private boolean indexed;
    private int nextReceiver;

    public TeleportPipeComponent() { }
    public TeleportPipeComponent(PipeView pipe) { attached(pipe); }

    @Override public ResourceLocation typeId() { return APTeleportPipes.COMPONENT_ID; }
    @Override public int getFrequency() { return frequency; }
    @Override public byte getState() { return state; }
    @Override public boolean isPublic() { return publicAccess; }
    @Override public UUID getOwnerUUID() { return owner; }
    @Override public String getOwnerName() { return ownerName; }

    @Override public PipeType getType() {
        String id = pipe == null ? "" : pipe.typeId().getPath();
        return switch (id) {
            case APTeleportPipes.ITEMS -> PipeType.ITEMS;
            case APTeleportPipes.FLUIDS -> PipeType.FLUIDS;
            case APTeleportPipes.POWER -> PipeType.POWER;
            case APTeleportPipes.FE -> PipeType.FE;
            default -> throw new IllegalStateException("Unexpected teleport pipe type " + id);
        };
    }

    @Override public PipeLocation getPosition() {
        if (world == null || pipe == null) throw new IllegalStateException("Teleport pipe is not loaded");
        BlockPos pos = pipe.position();
        return new PipeLocation(APDimensionIds.of(world), pos.getX(), pos.getY(), pos.getZ());
    }

    @Override public void setFrequency(int value) {
        if (value < 0 || value > MAX_FREQUENCY) throw new IllegalArgumentException("Invalid frequency");
        if (value == frequency) return;
        unindex();
        frequency = value;
        index();
        changed();
    }
    @Override public void setState(byte value) {
        if (value < 0 || value > 3) throw new IllegalArgumentException("Invalid teleport mode");
        if (state != value) { state = value; changed(); }
    }
    @Override public void setPublic(boolean value) {
        if (publicAccess != value) { publicAccess = value; changed(); }
    }

    public void setOwner(UUID id, String name) {
        owner = Objects.requireNonNull(id, "owner");
        ownerName = Objects.requireNonNull(name, "name");
        if (ownerName.length() > 64) ownerName = ownerName.substring(0, 64);
        changed();
    }

    public boolean canModify(UUID id) {
        return id != null && (owner == null || owner.equals(id) || publicAccess);
    }

    private void changed() {
        if (mutable != null && !mutable.isClientSide()) mutable.markChanged();
    }

    @Override public void attached(PipeView view) {
        pipe = Objects.requireNonNull(view, "pipe");
        if (view instanceof PipeMutationContext mutation) mutable = mutation;
    }
    @Override public void activated() { active = true; }
    @Override public void suspended() { unindex(); world = null; active = false; }
    @Override public void removed() { unindex(); world = null; active = false; }
    @Override public void invalidated() { unindex(); world = null; active = false; }

    private void unindex() {
        if (!indexed) return;
        APServices.teleports().remove(this, frequency);
        indexed = false;
    }
    private void index() {
        if (!indexed && active && world != null && !pipe.isClientSide()) {
            APServices.teleports().add(this, frequency);
            indexed = true;
        }
    }

    private boolean live() {
        if (world == null || pipe == null || !active || !world.hasChunkAt(pipe.position())) return false;
        return BuildCraftApi.service(BuildCraftServices.PIPES).pipe(world, pipe.position())
            .flatMap(view -> view.component(typeId())).orElse(null) == this;
    }

    private ServerLevel locateWorld() {
        if (live()) return world;
        unindex();
        world = null;
        MinecraftServer server = APServices.server();
        if (server == null || !server.isSameThread() || pipe == null || !active) return null;
        for (ServerLevel level : server.getAllLevels()) {
            BlockPos pos = pipe.position();
            if (!level.hasChunkAt(pos)) continue;
            if (BuildCraftApi.service(BuildCraftServices.PIPES).pipe(level, pos)
                .flatMap(view -> view.component(typeId())).orElse(null) == this) {
                world = level;
                index();
                return level;
            }
        }
        return null;
    }

    @Override public boolean isValid() { return live(); }

    @Override public void tick(PipeMutationContext context) {
        if (!context.isClientSide()) locateWorld();
    }

    @Override public PipeConnectionDecision connection(PipeConnectionContext context) {
        BlockPos next = context.pipe().position().relative(context.side());
        return BuildCraftApi.service(BuildCraftServices.PIPES).pipe(context.level(), next)
            .map(view -> APTeleportPipes.isTeleport(view.typeId().getPath())
                ? PipeConnectionDecision.DENY : PipeConnectionDecision.PASS)
            .orElse(PipeConnectionDecision.PASS);
    }

    public BlockPos blockPosition() {
        if (pipe == null) throw new IllegalStateException("Teleport pipe is not attached");
        return pipe.position();
    }

    @Override public PipeActivationResult activate(PipeActivationContext context) {
        if (APConfiguration.get().filterRightclicks && context.heldStack() != null
            && !context.heldStack().isEmpty() && context.heldStack().getItem() instanceof IItemPipe) {
            return PipeActivationResult.PASS;
        }
        if (context.mode() != OperationMode.EXECUTE || context.pipe().isClientSide()) {
            return PipeActivationResult.SUCCESS;
        }
        MinecraftServer server = APServices.server();
        if (server == null) return PipeActivationResult.PASS;
        UUID id = context.actor().playerId().orElse(null);
        if (id == null) return PipeActivationResult.PASS;
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) return PipeActivationResult.PASS;
        if (owner == null) setOwner(id, context.actor().playerName().orElse(player.getName().getString()));
        if (!canModify(id)) {
            TeleportUiServer.denied(player, ownerName);
            return PipeActivationResult.SUCCESS;
        }
        locateWorld();
        if (world != null) TeleportUiServer.open(player, this);
        return PipeActivationResult.SUCCESS;
    }

    public static String modeName(int value) {
        return switch (value) {
            case 0 -> "off";
            case 1 -> "send";
            case 2 -> "receive";
            case 3 -> "both";
            default -> "invalid";
        };
    }

    @Override public void onTransit(ItemTransitContext context) {
        if (context.phase() != ItemTransitPhase.CENTER || getType() != PipeType.ITEMS) return;
        if (context.payload().get(TRANSIT_KEY).isPresent()) {
            context.setPayload(context.payload().without(TRANSIT_KEY));
            return;
        }
        if (!canSend() || locateWorld() == null) return;
        List<TeleportPipeComponent> candidates = receivers();
        for (int i = 0; i < candidates.size(); i++) {
            TeleportPipeComponent target = candidates.get((i + nextReceiver) % candidates.size());
            if (target.world == null || target.pipe == null) continue;
            List<Direction> exits = target.pipe.connectedSides().stream()
                .sorted(Comparator.comparingInt(Direction::ordinal)).toList();
            for (Direction exit : exits) {
                if (!context.setPayload(context.payload().with(TRANSIT_KEY, TRANSIT_MARK))) return;
                var result = context.transfer(target.world, target.pipe.position(), exit.getOpposite(), OperationMode.EXECUTE);
                context.setPayload(context.payload().without(TRANSIT_KEY));
                if (result.succeeded()) {
                    nextReceiver = (nextReceiver + i + 1) % candidates.size();
                    return;
                }
            }
        }
    }

    @Override public void onResourceTick(PipeResourceTickContext context) {
        if (!active || !canSend()) return;
        if (world == null && context.world() instanceof ServerLevel level) {
            world = level;
            index();
        }
        if (world == null || !live()) { locateWorld(); return; }
        if (context.medium() != switch (getType()) {
            case FLUIDS -> PipeMedium.FLUID;
            case POWER -> PipeMedium.MJ;
            case FE -> PipeMedium.EXTERNAL_ENERGY;
            default -> PipeMedium.ITEM;
        }) return;
        List<TeleportPipeComponent> candidates = receivers();
        if (candidates.isEmpty()) return;
        PipeResourceService service = context.transfers();
        var actor = owner == null ? AutomationActor.unknown()
            : AutomationActor.machineOwner(owner, ownerName, APTeleportPipes.id("teleport_pipe"));
        List<Direction> sources = context.pipe().connectedSides().stream()
            .sorted(Comparator.comparingInt(Direction::ordinal)).toList();
        for (int i = 0; i < candidates.size(); i++) {
            TeleportPipeComponent target = candidates.get((i + nextReceiver) % candidates.size());
            if (target.world == null || target.pipe == null) continue;
            List<Direction> targetSides = target.pipe.connectedSides().stream()
                .sorted(Comparator.comparingInt(Direction::ordinal)).toList();
            for (Direction from : sources) {
                for (Direction into : targetSides) {
                    boolean moved = switch (getType()) {
                        case FLUIDS -> service.transferFluid(world, pipe.position(), from,
                            target.world, target.pipe.position(), into, FluidMatcher.any(),
                            FluidAmount.of(MAX_FLUID_PER_TICK), actor, OperationMode.EXECUTE)
                            .transferred().amount().milliBuckets() > 0;
                        case POWER -> service.transferMj(world, pipe.position(), from,
                            target.world, target.pipe.position(), into,
                            MjAmount.ofMicro(MAX_MJ_PER_TICK), actor, OperationMode.EXECUTE)
                            .transferred().microMj() > 0;
                        case FE -> service.transferExternalEnergy(world, pipe.position(), from,
                            target.world, target.pipe.position(), into,
                            MAX_FE_PER_TICK, actor, OperationMode.EXECUTE).transferred() > 0;
                        default -> false;
                    };
                    if (moved) {
                        nextReceiver = (nextReceiver + i + 1) % candidates.size();
                        return;
                    }
                }
            }
        }
    }

    private List<TeleportPipeComponent> receivers() {
        List<TeleportPipeComponent> result = new ArrayList<>();
        for (ITeleportPipe endpoint : APServices.teleports().getConnectedPipes(this, false, true)) {
            if (endpoint instanceof TeleportPipeComponent candidate && candidate.live()
                && candidate.pipe != null && !candidate.pipe.connectedSides().isEmpty()) result.add(candidate);
        }
        return result;
    }

    public static final ApiCodec<TeleportPipeComponent, OpaqueData> CODEC = new ApiCodec<>() {
        @Override public CodecResult<OpaqueData> encode(TeleportPipeComponent endpoint) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeInt(endpoint.frequency);
                out.writeByte(endpoint.state);
                out.writeBoolean(endpoint.publicAccess);
                out.writeBoolean(endpoint.owner != null);
                if (endpoint.owner != null) {
                    out.writeLong(endpoint.owner.getMostSignificantBits());
                    out.writeLong(endpoint.owner.getLeastSignificantBits());
                }
                out.writeUTF(endpoint.ownerName);
                out.flush();
                return CodecResult.success(new OpaqueData(APTeleportPipes.COMPONENT_ID, bytes.toByteArray()));
            } catch (IOException e) { return CodecResult.failure("Cannot encode teleport state"); }
        }
        @Override public CodecResult<TeleportPipeComponent> decode(OpaqueData payload) {
            if (!APTeleportPipes.COMPONENT_ID.equals(payload.format()) || payload.size() > 256)
                return CodecResult.failure("Invalid teleport state payload");
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload.bytes()));
                int freq = in.readInt();
                int mode = in.readUnsignedByte();
                boolean isPublic = in.readBoolean();
                UUID uuid = in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null;
                String name = in.readUTF();
                if (in.available() != 0 || freq < 0 || freq > MAX_FREQUENCY || mode > 3 || name.length() > 64)
                    return CodecResult.failure("Invalid teleport settings");
                TeleportPipeComponent value = new TeleportPipeComponent();
                value.frequency = freq;
                value.state = (byte) mode;
                value.publicAccess = isPublic;
                value.owner = uuid;
                value.ownerName = name;
                return CodecResult.success(value);
            } catch (IOException ex) { return CodecResult.failure("Truncated teleport settings"); }
        }
    };
}
