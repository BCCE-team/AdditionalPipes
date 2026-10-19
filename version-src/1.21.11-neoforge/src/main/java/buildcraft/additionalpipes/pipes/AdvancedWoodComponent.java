package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.APConfiguration;
import buildcraft.additionalpipes.service.APServices;
import buildcraft.additionalpipes.platform.APPlayerDrops;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.content.BuildCraftContentIds;
import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.energy.MjAmount;
import buildcraft.api.v2.energy.MjDemandPort;
import buildcraft.api.v2.energy.MjPort;
import buildcraft.api.v2.energy.MjTransferResult;
import buildcraft.api.v2.item.ItemMatcher;
import buildcraft.api.v2.item.ItemPort;
import buildcraft.api.v2.item.ItemTransferResult;
import buildcraft.api.v2.persistence.ApiCodec;
import buildcraft.api.v2.persistence.CodecResult;
import buildcraft.api.v2.persistence.OpaqueData;
import buildcraft.api.v2.pipe.ItemInjectionRequest;
import buildcraft.api.v2.pipe.ItemPipePort;
import buildcraft.api.v2.pipe.ItemTransitData;
import buildcraft.api.v2.pipe.PipeActivationContext;
import buildcraft.api.v2.pipe.PipeActivationResult;
import buildcraft.api.v2.pipe.PipeConnectionContext;
import buildcraft.api.v2.pipe.PipeConnectionDecision;
import buildcraft.api.v2.pipe.PipeMutationContext;
import buildcraft.api.v2.pipe.PipeRuntimeComponent;
import buildcraft.api.v2.pipe.PipeView;
import buildcraft.api.v2.world.WorldAccessContext;
import buildcraft.transport.internal.pipe.IItemPipe;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class AdvancedWoodComponent implements PipeRuntimeComponent {
    public static final int CAPACITY = 64;
    public static final int ENERGY_PER_ITEM = 1;
    private static final long MICRO_MJ = MjAmount.MICRO_MJ_PER_MJ;
    private static final long CAPACITY_MICRO_MJ = CAPACITY * MICRO_MJ;
    private static final long ENERGY_PER_ITEM_MICRO_MJ = ENERGY_PER_ITEM * MICRO_MJ;
    private static final long MICRO_MJ_PER_RF = MICRO_MJ / 10;
    public static final int SLOW_INTERVAL = 16;
    public static final int FILTER_SLOTS = 9;

    public record Filter(String item, int damage, int count) {
        public Filter {
            if (item == null || item.length() > 128 || (!item.isEmpty() && !item.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")))
                throw new IllegalArgumentException("Invalid filter item");
            if (damage < 0 || damage > 32767) throw new IllegalArgumentException("Invalid damage value");
            if (count < 0 || count > 64 || (item.isEmpty() != (count == 0)))
                throw new IllegalArgumentException("Invalid filter stack size");
        }
        public Filter(String item, int damage) { this(item, damage, item.isEmpty() ? 0 : 1); }
        public static Filter empty() { return new Filter("", 0, 0); }
    }

    private final ItemStack[] filterStacks = new ItemStack[FILTER_SLOTS];
    private final List<ItemStack> pendingLegacyItems = new ArrayList<>();
    private boolean excluded;
    private long energyMicroMj;
    private int ticksSincePull;
    private Direction source;
    private boolean rotationRequested;
    private PipeView pipe;
    private PipeMutationContext mutable;

    private final MjDemandPort powerPort = new MjDemandPort() {
        @Override public MjTransferResult insert(MjAmount offered, OperationMode mode) {
            Objects.requireNonNull(offered);
            Objects.requireNonNull(mode);
            long accepted = Math.min(offered.microMj(), CAPACITY_MICRO_MJ - energyMicroMj);
            if (accepted > 0 && mode == OperationMode.EXECUTE) {
                energyMicroMj += accepted;
                markChanged();
            }
            return MjTransferResult.of(offered, MjAmount.ofMicro(accepted));
        }
        @Override public MjTransferResult extract(MjAmount requested, OperationMode mode) {
            Objects.requireNonNull(requested);
            Objects.requireNonNull(mode);
            return MjTransferResult.none(requested);
        }
        @Override public MjAmount stored() { return MjAmount.ofMicro(energyMicroMj); }
        @Override public MjAmount capacity() { return MjAmount.ofMicro(CAPACITY_MICRO_MJ); }
        @Override public MjAmount requested() { return MjAmount.ofMicro(CAPACITY_MICRO_MJ - energyMicroMj); }
        @Override public boolean canExtract() { return false; }
    };

    public AdvancedWoodComponent() { Arrays.fill(filterStacks, ItemStack.EMPTY); }
    public AdvancedWoodComponent(PipeView pipe) { this(); attached(pipe); }

    @Override public Identifier typeId() { return APInsertionPipes.ADVANCED_COMPONENT; }
    @Override public void attached(PipeView view) {
        pipe = view;
        if (view instanceof PipeMutationContext mutation) mutable = mutation;
    }
    @Override public Optional<MjPort> mjPort(Direction side) {
        return Optional.of(powerPort);
    }
    public MjPort energyPort() { return powerPort; }
    public int storedEnergy() { return (int) (energyMicroMj / MICRO_MJ); }
    public boolean excluded() { return excluded; }
    public void setExcluded(boolean value) { if (excluded != value) { excluded = value; markChanged(); } }
    public Direction source() { return source; }
    public Filter filter(int slot) {
        ItemStack stack = getFilterStack(slot);
        if (stack.isEmpty()) return Filter.empty();
        return new Filter(itemId(stack), stack.getDamageValue(), stack.getCount());
    }
    public Filter[] filters() {
        Filter[] result = new Filter[FILTER_SLOTS];
        for (int i = 0; i < FILTER_SLOTS; i++) result[i] = filter(i);
        return result;
    }
    public PipeView pipe() { return pipe; }
    public ItemStack getFilterStack(int slot) {
        if (slot < 0 || slot >= FILTER_SLOTS) throw new IllegalArgumentException("Invalid filter slot");
        return phantomCopy(filterStacks[slot]);
    }
    public void setFilterStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= FILTER_SLOTS) throw new IllegalArgumentException("Invalid filter slot");
        filterStacks[slot] = phantomCopy(stack);
        markChanged();
    }

    private static ItemStack phantomCopy(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack ghost = stack.copy();
        ghost.setCount(1);
        return ghost;
    }

    public void returnLegacyItems(ServerPlayer player) {
        if (pendingLegacyItems.isEmpty()) return;
        for (ItemStack stored : pendingLegacyItems) {
            ItemStack returned = stored.copy();
            player.getInventory().add(returned);
            if (!returned.isEmpty()) APPlayerDrops.drop(player, returned);
        }
        pendingLegacyItems.clear();
        markChanged();
    }
    public void markFiltersChanged() { markChanged(); }
    private static String itemId(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }

    public ItemStack stackOf(Filter filter) {
        if (filter.item().isEmpty()) return ItemStack.EMPTY;
        for (var item : BuiltInRegistries.ITEM) {
            if (!BuiltInRegistries.ITEM.getKey(item).toString().equals(filter.item())) continue;
            ItemStack stack = new ItemStack(item, filter.count());
            if (stack.isDamageableItem()) stack.setDamageValue(filter.damage());
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override public void addDrops(List<ItemStack> drops, int fortune) {
        for (ItemStack stack : pendingLegacyItems) if (!stack.isEmpty()) drops.add(stack.copy());
    }

    public void setFilter(int slot, Filter value) {
        if (slot < 0 || slot >= FILTER_SLOTS) throw new IllegalArgumentException("Invalid filter slot");
        if (!filter(slot).equals(value)) setFilterStack(slot, stackOf(value));
    }

    public boolean permits(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (ItemStack filter : filterStacks) {
            if (filter.isEmpty() || filter.getItem() != stack.getItem()) continue;
            if (stack.isDamageableItem() || stack.getDamageValue() == filter.getDamageValue()) return !excluded;
        }
        return excluded;
    }

    private void markChanged() {
        if (mutable != null && !mutable.isClientSide()) mutable.markChanged();
    }

    @Override public PipeConnectionDecision connection(PipeConnectionContext context) {
        BlockPos neighbor = context.pipe().position().relative(context.side());
        if (!context.level().hasChunkAt(neighbor)) return PipeConnectionDecision.PASS;
        return BuildCraftApi.service(BuildCraftServices.PIPES).pipe(context.level(), neighbor)
            .map(other -> other.typeId().equals(BuildCraftContentIds.Pipes.WOOD_ITEM)
                || other.typeId().equals(APInsertionPipes.id(APInsertionPipes.ADVANCED_WOOD))
                ? PipeConnectionDecision.DENY : PipeConnectionDecision.PASS)
            .orElse(PipeConnectionDecision.PASS);
    }

    public void rotate(WorldAccessContext world, PipeMutationContext context) {
        Direction old = source;
        int start = old == null ? -1 : old.ordinal();
        for (int i = 1; i <= 6; i++) {
            Direction next = Direction.values()[(start + i) % 6];
            BlockPos neighbor = context.position().relative(next);
            if (world.isLoaded(neighbor) && world.pipeTypeId(neighbor).isEmpty()
                && world.itemPort(neighbor, next.getOpposite()).isPresent()) {
                source = next;
                break;
            }
        }
        if (source != old) { context.markChanged(); }
    }

    @Override public void tick(PipeMutationContext context) {
        if (context.isClientSide()) return;
        Optional<WorldAccessContext> access = context.worldAccess();
        if (access.isEmpty()) return;
        WorldAccessContext world = access.get();
        if (rotationRequested) {
            rotationRequested = false;
            rotate(world, context);
        }
        if (source == null || !validSource(world, context.position(), source)) {
            Direction old = source;
            source = null;
            rotate(world, context);
            if (source == null && old != null) markChanged();
        }
        ticksSincePull++;
        if (source == null || !context.connectedSides().contains(source)) return;
        if (energyMicroMj < CAPACITY_MICRO_MJ
            && (ticksSincePull < SLOW_INTERVAL || energyMicroMj < ENERGY_PER_ITEM_MICRO_MJ)) return;
        int limit = (int) Math.min(64L, energyMicroMj / ENERGY_PER_ITEM_MICRO_MJ);
        if (limit < 1) return;
        ticksSincePull = 0;
        BlockPos input = context.position().relative(source);
        Optional<ItemPort> sourcePort = world.itemPort(input, source.getOpposite());
        Optional<ItemPipePort> targetPort = context.itemPipePort(source);
        if (sourcePort.isEmpty() || targetPort.isEmpty()) return;
        ItemPort inventory = sourcePort.get();
        ItemPipePort pipePort = targetPort.get();
        ItemMatcher matcher = this::permits;
        ItemStack offer = inventory.extract(matcher, limit, OperationMode.SIMULATE).transferred();
        if (offer.isEmpty()) return;
        int accepted = pipePort.inject(new ItemInjectionRequest(offer, source, ItemTransitData.DEFAULT),
            OperationMode.SIMULATE).transferredCount();
        if (accepted < 1) return;
        ItemStack removed = inventory.extract(matcher, Math.min(accepted, offer.getCount()), OperationMode.EXECUTE).transferred();
        if (removed.isEmpty()) return;
        int moved = pipePort.inject(new ItemInjectionRequest(removed, source, ItemTransitData.DEFAULT),
            OperationMode.EXECUTE).transferredCount();
        if (moved < removed.getCount()) {
            ItemStack remainder = removed.copy();
            remainder.shrink(Math.max(0, moved));
            if (!remainder.isEmpty()) inventory.insert(remainder, OperationMode.EXECUTE);
        }
        if (moved > 0) {
            energyMicroMj -= Math.min(moved, removed.getCount()) * ENERGY_PER_ITEM_MICRO_MJ;
            context.markChanged();
        }
    }

    private static boolean validSource(WorldAccessContext world, BlockPos pos, Direction source) {
        BlockPos neighbor = pos.relative(source);
        return world.isLoaded(neighbor) && world.pipeTypeId(neighbor).isEmpty()
            && world.itemPort(neighbor, source.getOpposite()).isPresent();
    }

    @Override public PipeActivationResult activate(PipeActivationContext context) {
        if (APConfiguration.get().filterRightclicks && !context.heldStack().isEmpty()
            && context.heldStack().getItem() instanceof IItemPipe) return PipeActivationResult.PASS;
        UUID id = context.actor().playerId().orElse(null);
        MinecraftServer server = APServices.server();
        ServerPlayer player = server == null || id == null ? null : server.getPlayerList().getPlayer(id);
        if (player == null) return PipeActivationResult.PASS;
        if (context.heldStack().getItem() instanceof buildcraft.lib.internal.tool.IToolWrench) {
            if (context.mode() == OperationMode.EXECUTE) {
                rotationRequested = true;
            }
            return PipeActivationResult.SUCCESS;
        }
        if (context.mode() == OperationMode.EXECUTE && !context.pipe().isClientSide()) {
            APInsertionUiServer.openAdvancedWood(player, this);
        }
        return PipeActivationResult.SUCCESS;
    }

    public static final ApiCodec<AdvancedWoodComponent, OpaqueData> CODEC = new ApiCodec<>() {
        @Override public CodecResult<OpaqueData> encode(AdvancedWoodComponent value) {
            try {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(data);
                out.writeByte(5);
                out.writeBoolean(value.excluded);
                out.writeLong(value.energyMicroMj);
                out.writeByte(value.source == null ? 6 : value.source.ordinal());
                for (ItemStack stack : value.filterStacks) APAdvancedWoodStackIO.write(out, stack);
                out.writeByte(value.pendingLegacyItems.size());
                for (ItemStack stack : value.pendingLegacyItems) APAdvancedWoodStackIO.write(out, stack);
                out.flush();
                return CodecResult.success(new OpaqueData(APInsertionPipes.ADVANCED_COMPONENT, data.toByteArray()));
            } catch (IOException exception) { return CodecResult.failure("Cannot encode advanced wood state"); }
        }
        @Override public CodecResult<AdvancedWoodComponent> decode(OpaqueData data) {
            if (!APInsertionPipes.ADVANCED_COMPONENT.equals(data.format()) || data.size() > 1048576)
                return CodecResult.failure("Invalid advanced wood state");
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(data.bytes()));
                AdvancedWoodComponent value = new AdvancedWoodComponent();
                int format = in.readUnsignedByte();
                if (format == 2 || format == 3 || format == 4 || format == 5) {
                    value.excluded = in.readBoolean();
                    value.energyMicroMj = in.readLong();
                } else if (format == 0 || format == 1) {
                    value.excluded = format == 1;
                    value.energyMicroMj = in.readUnsignedShort() * MICRO_MJ_PER_RF;
                } else return CodecResult.failure("Invalid advanced wood energy data");
                int face = in.readUnsignedByte();
                if (face < 6) value.source = Direction.values()[face];
                else if (face != 6) return CodecResult.failure("Invalid source face");
                for (int i = 0; i < FILTER_SLOTS; i++) {
                    ItemStack stored;
                    if (format == 4 || format == 5) {
                        stored = APAdvancedWoodStackIO.read(in);
                    } else {
                        String item = in.readUTF();
                        int damage = in.readUnsignedShort();
                        int count = format == 3 ? in.readUnsignedByte() : item.isEmpty() ? 0 : 1;
                        Filter filter = new Filter(item, damage, count);
                        stored = value.stackOf(filter);
                        if (filter.count() > 0 && stored.isEmpty())
                            return CodecResult.failure("Unknown advanced wood item");
                    }
                    value.filterStacks[i] = phantomCopy(stored);
                    if (format == 4 && !stored.isEmpty()) value.pendingLegacyItems.add(stored);
                }
                if (format == 5) {
                    int count = in.readUnsignedByte();
                    if (count > FILTER_SLOTS) return CodecResult.failure("Invalid legacy item count");
                    for (int i = 0; i < count; i++) {
                        ItemStack stack = APAdvancedWoodStackIO.read(in);
                        if (stack.isEmpty()) return CodecResult.failure("Invalid legacy item");
                        value.pendingLegacyItems.add(stack);
                    }
                }
                if (in.available() != 0 || value.energyMicroMj < 0 || value.energyMicroMj > CAPACITY_MICRO_MJ)
                    return CodecResult.failure("Invalid advanced wood settings");
                return CodecResult.success(value);
            } catch (IOException | IllegalArgumentException exception) {
                return CodecResult.failure("Invalid advanced wood filter data");
            }
        }
    };
}
