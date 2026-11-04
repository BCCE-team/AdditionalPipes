package buildcraft.additionalpipes.pipes;

import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.energy.MjAmount;
import buildcraft.api.v2.energy.MjPort;
import buildcraft.api.v2.energy.MjTransferResult;
import buildcraft.api.v2.fluid.FluidComponentPayload;
import buildcraft.api.v2.fluid.FluidVariant;
import buildcraft.api.v2.fluid.FluidVolume;
import buildcraft.api.v2.persistence.ApiCodec;
import buildcraft.api.v2.persistence.CodecResult;
import buildcraft.api.v2.persistence.OpaqueData;
import buildcraft.api.v2.pipe.PipeExecutionContext;
import buildcraft.api.v2.pipe.PipeMutationContext;
import buildcraft.api.v2.pipe.PipeRuntimeComponent;
import buildcraft.api.v2.pipe.PipeView;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Obsidian Fluid Pipe (AdditionalPipes 4.7.7): pulls filled item containers from the
 * world, slowly fills the BuildCraft fluid transport, then ejects the drained item.
 * The fluid and resulting container are held together across chunk unloads.
 */
public final class ObsidianFluidComponent implements PipeRuntimeComponent {
    // MJ is the canonical energy unit. At BCCE's default 1 MJ = 10 FE conversion,
    // these limits preserve the previous 2560 / 640 / 5 / 10 FE energy budget.
    public static final long MJ_CAPACITY_MICRO = 256L * MjAmount.MICRO_MJ_PER_MJ;
    public static final long MAX_MJ_RECEIVE_MICRO = 64L * MjAmount.MICRO_MJ_PER_MJ;
    public static final long MJ_PER_DISTANCE_MICRO = MjAmount.MICRO_MJ_PER_MJ;
    public static final int SUCTION_DISTANCE = 4;
    private static final int RECENT_DROPS = 32;
    private static final int MAX_PAYLOAD_SIZE = 60_000;

    private final int[] recentlyDropped = new int[RECENT_DROPS];
    private int droppedIndex;
    private long storedMicroMj;
    private FluidVolume pending = FluidVolume.empty();
    private ItemStack returnedContainer = ItemStack.EMPTY;
    private PipeView pipe;
    private PipeMutationContext mutation;
    private ServerLevel level;

    private final MjPort power = new MjPort() {
        @Override public MjTransferResult insert(MjAmount offered, OperationMode mode) {
            Objects.requireNonNull(offered, "offered");
            Objects.requireNonNull(mode, "mode");
            long accepted = Math.min(offered.microMj(),
                Math.min(MAX_MJ_RECEIVE_MICRO, MJ_CAPACITY_MICRO - storedMicroMj));
            if (accepted > 0 && mode == OperationMode.EXECUTE) {
                storedMicroMj += accepted;
                changed();
            }
            return MjTransferResult.of(offered, MjAmount.ofMicro(accepted));
        }
        @Override public MjTransferResult extract(MjAmount requested, OperationMode mode) {
            Objects.requireNonNull(requested, "requested");
            Objects.requireNonNull(mode, "mode");
            return MjTransferResult.none(requested);
        }
        @Override public MjAmount stored() { return MjAmount.ofMicro(storedMicroMj); }
        @Override public MjAmount capacity() { return MjAmount.ofMicro(MJ_CAPACITY_MICRO); }
        @Override public boolean canExtract() { return false; }
    };

    public ObsidianFluidComponent() { Arrays.fill(recentlyDropped, -1); }
    public ObsidianFluidComponent(PipeView view) { this(); attached(view); }

    @Override public ResourceLocation typeId() { return APUtilityPipes.OBSIDIAN_COMPONENT; }
    @Override public void attached(PipeView view) {
        pipe = view;
        if (view instanceof PipeMutationContext context) mutation = context;
    }
    @Override public void suspended() { level = null; mutation = null; }
    @Override public void invalidated() { level = null; mutation = null; pipe = null; }
    @Override public Optional<MjPort> mjPort(Direction direction) { return Optional.of(power); }
    public MjPort powerPort() { return power; }

    private void changed() {
        if (mutation != null && !mutation.isClientSide()) mutation.markChanged();
    }

    @Override public void addDrops(List<ItemStack> drops, int fortune) {
        if (!returnedContainer.isEmpty()) drops.add(returnedContainer.copy());
    }

    @Override public void tick(PipeMutationContext context) {
        if (!(context instanceof PipeExecutionContext active) || !active.isServerSide() || !active.isActive()) return;
        if (!(context instanceof buildcraft.transport.internal.pipe.IPipe runtime)
            || !(runtime.getHolder().getPipeWorld() instanceof ServerLevel server)) return;
        mutation = context;
        pipe = context;
        level = server;
        if (!pending.isEmpty()) {
            transferBufferedFluid(context);
            return;
        }
        if (!returnedContainer.isEmpty()) {
            ejectContainer();
            changed();
            return;
        }
        // Suction is an area search, not a one-wide ray. A pipe can have multiple fluid
        // connections, and the nearest dropped container may be beside its exposed end.
        // Entity lookup only visits loaded entities; it must not force-load chunks.
        if (storedMicroMj >= MJ_PER_DISTANCE_MICRO) {
            BlockPos pos = context.position();
            double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
            double maxDistance = SUCTION_DISTANCE + 0.5;
            List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(pos).inflate(SUCTION_DISTANCE));
            // Closest first: do not spend the last MJ on a distant container while a
            // filled bucket is immediately adjacent.
            nearby.sort(java.util.Comparator.comparingDouble(item -> item.distanceToSqr(x, y, z)));
            for (ItemEntity item : nearby) {
                double distanceSquared = item.distanceToSqr(x, y, z);
                if (distanceSquared > maxDistance * maxDistance) continue;
                int distance = Math.max(1, Math.min(SUCTION_DISTANCE,
                    (int) Math.ceil(Math.sqrt(distanceSquared))));
                if (collect(item, distance)) return;
            }
        }
        // Do not drain the MJ buffer while idle. A redstone engine supplies only
        // 0.05 MJ/t, so even a 0.5 MJ/t idle fee prevents the pipe from ever
        // accumulating the 1-4 MJ required for long-range suction.
        // The original AdditionalPipes RFBattery.useEnergy(0, 5, false) is a
        // simulation (false = don't consume), not a recurring energy expense.
    }

    @Override public void entityCollided(PipeView view, Entity entity) {
        if (level != null && entity instanceof ItemEntity item && pending.isEmpty()) {
            collect(item, 0);
        }
    }

    /** The only non-pipe face when exactly one fluid connection is present. */
    private static Direction suctionSide(PipeView view) {
        return view.connectedSides().size() == 1
            ? view.connectedSides().iterator().next().getOpposite() : null;
    }

    private boolean collect(ItemEntity entity, int distance) {
        if (level == null || entity.isRemoved() || !pending.isEmpty() || !returnedContainer.isEmpty()) return false;
        long cost = distance * MJ_PER_DISTANCE_MICRO;
        if (storedMicroMj < cost || recentlyDropped(entity.getId())) return false;
        ItemStack offered = entity.getItem();
        if (offered.isEmpty() || offered.getCount() < 1) return false;
        // Drain a COPY of one item first. Never touch the entity until all checks succeed.
        APFluidContainers.Drained result = APFluidContainers.drainOne(offered);
        if (result == null || result.fluid().isEmpty()) return false;

        pending = result.fluid();
        returnedContainer = result.container().copy();
        if (offered.getCount() == 1) {
            entity.discard();
        } else {
            ItemStack remaining = offered.copy();
            remaining.shrink(1);
            entity.setItem(remaining);
        }
        storedMicroMj -= cost;
        level.playSound(null, entity.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS,
            0.2F, 1.1F);
        changed();
        return true;
    }

    private void transferBufferedFluid(PipeMutationContext context) {
        if (context.connectedSides().isEmpty()) return;
        long remaining = pending.amount().milliBuckets();
        if (remaining <= 0) return;
        // This liquid originates inside the pipe, NOT from any of its connected faces.
        // In BCCE a sided fluidPort(side).insert() marks that face as an INPUT,
        // preventing the same face from OUTPUTTING while the item is being drained.
        // The native flow accepts a null intake face to leave every output available.
        // Let the BuildCraft fluid flow enforce its own transfer rate and capacity.
        long request = Math.min(Integer.MAX_VALUE, remaining);
        long accepted = APObsidianFluidInjection.insert(context,
            FluidVolume.of(pending.requireVariant(), request));
        if (accepted <= 0) return;
        long left = remaining - Math.min(remaining, accepted);
        pending = left == 0 ? FluidVolume.empty() : FluidVolume.of(pending.requireVariant(), left);
        if (left == 0) ejectContainer();
        changed();
    }

    private void ejectContainer() {
        if (level == null || pipe == null || returnedContainer.isEmpty()) {
            returnedContainer = ItemStack.EMPTY;
            return;
        }
        Direction outward = suctionSide(pipe);
        if (outward == null) outward = Direction.UP;
        BlockPos pos = pipe.position();
        ItemEntity dropped = new ItemEntity(level,
            pos.getX() + 0.5 + outward.getStepX() * 0.65,
            pos.getY() + 0.5 + outward.getStepY() * 0.65,
            pos.getZ() + 0.5 + outward.getStepZ() * 0.65,
            returnedContainer.copy());
        dropped.setDeltaMovement(outward.getStepX() * 0.08,
            outward.getStepY() * 0.08 + 0.04, outward.getStepZ() * 0.08);
        if (level.addFreshEntity(dropped)) {
            recentlyDropped[droppedIndex++ % RECENT_DROPS] = dropped.getId();
            returnedContainer = ItemStack.EMPTY;
        }
        // If spawning was cancelled, retain the container until the next tick.
    }

    private boolean recentlyDropped(int id) {
        for (int value : recentlyDropped) if (id == value) return true;
        return false;
    }

    public MjAmount storedMj() { return MjAmount.ofMicro(storedMicroMj); }
    public FluidVolume pendingFluid() { return pending; }
    public ItemStack pendingContainer() { return returnedContainer.copy(); }

    public static final ApiCodec<ObsidianFluidComponent, OpaqueData> CODEC = new ApiCodec<>() {
        @Override public CodecResult<OpaqueData> encode(ObsidianFluidComponent value) {
            try {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(data);
                output.writeByte(2);
                output.writeLong(value.storedMicroMj);
                output.writeBoolean(!value.pending.isEmpty());
                if (!value.pending.isEmpty()) {
                    FluidVariant variant = value.pending.requireVariant();
                    output.writeUTF(variant.fluidId().toString());
                    output.writeLong(value.pending.amount().milliBuckets());
                    FluidComponentPayload components = variant.components();
                    output.writeBoolean(!components.isEmpty());
                    if (!components.isEmpty()) {
                        output.writeUTF(components.formatId().orElseThrow().toString());
                        byte[] payload = components.copyCanonicalBytes();
                        output.writeInt(payload.length);
                        output.write(payload);
                    }
                }
                APAdvancedWoodStackIO.write(output, value.returnedContainer);
                output.flush();
                if (data.size() > MAX_PAYLOAD_SIZE) return CodecResult.failure("Obsidian fluid state too large");
                return CodecResult.success(new OpaqueData(APUtilityPipes.OBSIDIAN_COMPONENT, data.toByteArray()));
            } catch (IOException | RuntimeException exception) {
                return CodecResult.failure("Cannot encode obsidian fluid state");
            }
        }

        @Override public CodecResult<ObsidianFluidComponent> decode(OpaqueData data) {
            if (!APUtilityPipes.OBSIDIAN_COMPONENT.equals(data.format()) || data.size() > MAX_PAYLOAD_SIZE)
                return CodecResult.failure("Invalid obsidian fluid state");
            try {
                DataInputStream input = new DataInputStream(new ByteArrayInputStream(data.bytes()));
                int version = input.readUnsignedByte();
                if (version != 1 && version != 2)
                    return CodecResult.failure("Unknown obsidian fluid state version");
                ObsidianFluidComponent value = new ObsidianFluidComponent();
                if (version == 1) {
                    // Migrate saves written by the first FE-based addon implementation.
                    // Use BCCE's configured MJ/FE boundary rather than a hardcoded ratio.
                    int legacyFe = input.readUnsignedShort();
                    if (legacyFe > 2560) return CodecResult.failure("Invalid legacy obsidian fluid energy");
                    long legacyMj = buildcraft.api.v2.BuildCraftApi.service(
                        buildcraft.api.v2.BuildCraftServices.ENERGY).conversion().feToMicroMjClamped(legacyFe);
                    value.storedMicroMj = Math.min(MJ_CAPACITY_MICRO, legacyMj);
                } else {
                    value.storedMicroMj = input.readLong();
                    if (value.storedMicroMj < 0 || value.storedMicroMj > MJ_CAPACITY_MICRO)
                        return CodecResult.failure("Invalid obsidian fluid energy");
                }
                if (input.readBoolean()) {
                    ResourceLocation fluid = ResourceLocation.tryParse(input.readUTF());
                    long count = input.readLong();
                    if (fluid == null || count <= 0 || count > Integer.MAX_VALUE)
                        return CodecResult.failure("Invalid obsidian fluid buffer");
                    FluidComponentPayload components = FluidComponentPayload.empty();
                    if (input.readBoolean()) {
                        ResourceLocation format = ResourceLocation.tryParse(input.readUTF());
                        int size = input.readInt();
                        if (format == null || size < 1 || size > MAX_PAYLOAD_SIZE || size > input.available())
                            return CodecResult.failure("Invalid obsidian fluid metadata");
                        components = FluidComponentPayload.of(format, input.readNBytes(size));
                    }
                    value.pending = FluidVolume.of(FluidVariant.of(fluid, components), count);
                }
                value.returnedContainer = APAdvancedWoodStackIO.read(input);
                if (input.available() != 0)
                    return CodecResult.failure("Trailing obsidian fluid state bytes");
                return CodecResult.success(value);
            } catch (IOException | RuntimeException exception) {
                return CodecResult.failure("Cannot decode obsidian fluid state");
            }
        }
    };
}
