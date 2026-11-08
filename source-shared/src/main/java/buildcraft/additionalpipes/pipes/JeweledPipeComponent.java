package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.APConfiguration;
import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.filter.SideFilterData;
import buildcraft.additionalpipes.platform.APDimensionIds;
import buildcraft.additionalpipes.platform.APJeweledMenuAccess;
import buildcraft.additionalpipes.platform.APPlayerLevel;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative item routing and real filter inventory from AdditionalPipesBC 4.7.7. */
public final class JeweledPipeComponent implements ItemRouteComponent, PipeActivationComponent, PipeComponentLifecycle {
    public static final int SIDE_COUNT = 6;
    public static final int SLOTS_PER_SIDE = SideFilterData.INVENTORY_SIZE;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_STATE_BYTES = 1_048_576;
    // BCCE limits a single persistent pipe component payload to 64 KiB.
    private static final int MAX_PERSISTED_BYTES = 64 * 1024;

    private final SideFilterData<ItemStack>[] sides;
    private PipeMutationContext mutation;
    private PipeView attached;

    @SuppressWarnings("unchecked")
    public JeweledPipeComponent() {
        sides = (SideFilterData<ItemStack>[]) new SideFilterData<?>[SIDE_COUNT];
        for (int i = 0; i < SIDE_COUNT; i++) sides[i] = new SideFilterData<>();
    }

    public JeweledPipeComponent(PipeView view) { this(); attached(view); }

    @Override public ResourceLocation typeId() { return APJeweledPipes.COMPONENT; }

    @Override public void attached(PipeView pipe) {
        attached = pipe;
        mutation = pipe instanceof PipeMutationContext writable ? writable : null;
    }

    @Override public void invalidated() {
        attached = null;
        mutation = null;
    }

    private void changed() {
        if (mutation != null && !mutation.isClientSide()) mutation.markChanged();
    }

    private static void checkSide(int side) {
        if (side < 0 || side >= SIDE_COUNT) throw new IllegalArgumentException("Invalid jeweled side " + side);
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOTS_PER_SIDE) throw new IllegalArgumentException("Invalid jeweled slot " + slot);
    }

    public ItemStack getItem(int side, int slot) {
        checkSide(side);
        checkSlot(slot);
        ItemStack current = sides[side].get(slot);
        return current == null || current.isEmpty() ? ItemStack.EMPTY : current.copy();
    }

    public void setItem(int side, int slot, ItemStack item) {
        checkSide(side);
        checkSlot(slot);
        ItemStack copy = item == null || item.isEmpty() ? ItemStack.EMPTY : item.copy();
        if (!copy.isEmpty()) copy.setCount(Math.min(copy.getCount(), Math.min(64, copy.getMaxStackSize())));
        sides[side].set(slot, copy);
        changed();
    }

    public ItemStack takeItem(int side, int slot, int amount) {
        if (amount <= 0) return ItemStack.EMPTY;
        ItemStack current = getItem(side, slot);
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = current.split(amount);
        setItem(side, slot, current);
        return taken;
    }

    public ItemStack removeItem(int side, int slot) {
        ItemStack current = getItem(side, slot);
        if (!current.isEmpty()) setItem(side, slot, ItemStack.EMPTY);
        return current;
    }

    public SideFilterData.Options options(int side) {
        checkSide(side);
        return sides[side].options();
    }

    public void setOptions(int side, SideFilterData.Options options) {
        checkSide(side);
        if (options == null) throw new IllegalArgumentException("Missing filter options");
        if (options.equals(sides[side].options())) return;
        sides[side].setOptions(options);
        changed();
    }

    public boolean isEmpty(int side) {
        checkSide(side);
        for (ItemStack stack : sides[side].snapshot()) {
            if (stack != null && !stack.isEmpty()) return false;
        }
        return true;
    }

    private static final SideFilterData.ItemMatcher<ItemStack> MATCHER = new SideFilterData.ItemMatcher<>() {
        @Override public boolean sameItem(ItemStack expected, ItemStack actual) {
            return expected != null && actual != null && !expected.isEmpty() && !actual.isEmpty()
                && expected.getItem() == actual.getItem();
        }
        @Override public boolean sameMetadata(ItemStack expected, ItemStack actual) {
            return expected.getDamageValue() == actual.getDamageValue();
        }
        @Override public boolean sameComponents(ItemStack expected, ItemStack actual) {
            return APJeweledItemMatcher.sameData(expected, actual);
        }
    };

    /** The original pipe gives all matching exits precedence over every unsorted fallback. */
    @Override public RouteDecision route(ItemRouteContext context) {
        EnumMap<Direction, Integer> weights = new EnumMap<>(Direction.class);
        int candidateMask = 0;
        for (Direction candidate : context.candidates()) candidateMask |= 1 << candidate.ordinal();
        int selected = context.stack().isEmpty() ? 0
            : JeweledRouting.select(candidateMask, context.stack(), sides, MATCHER);
        for (Direction candidate : context.candidates()) {
            weights.put(candidate, (selected & (1 << candidate.ordinal())) != 0 ? 1 : 0);
        }
        return new RouteDecision(weights);
    }

    @Override public PipeActivationResult activate(PipeActivationContext context) {
        if (APConfiguration.get().filterRightclicks && !context.heldStack().isEmpty()
            && context.heldStack().getItem() instanceof IItemPipe) return PipeActivationResult.PASS;
        UUID playerId = context.actor().playerId().orElse(null);
        MinecraftServer server = APServices.server();
        ServerPlayer player = server == null || playerId == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player == null || player.isShiftKeyDown()) return PipeActivationResult.PASS;
        if (context.mode() == OperationMode.EXECUTE && !context.pipe().isClientSide()) {
            ServerLevel level = APPlayerLevel.of(player);
            PipeView pipe = attached;
            if (pipe != null && level.hasChunkAt(pipe.position())) {
                var pos = pipe.position();
                APJeweledMenuAccess.open(player, this,
                    new PipeLocation(APDimensionIds.of(level), pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return PipeActivationResult.SUCCESS;
    }

    /** Loot evaluation is read-only and may be called more than once by BCCE. */
    @Override public void addDrops(List<ItemStack> drops, int fortune) {
        for (SideFilterData<ItemStack> side : sides) {
            for (ItemStack stack : side.snapshot()) {
                if (stack != null && !stack.isEmpty()) drops.add(stack.copy());
            }
        }
    }

    public static final ApiCodec<JeweledPipeComponent, OpaqueData> CODEC = new ApiCodec<>() {
        @Override public CodecResult<OpaqueData> encode(JeweledPipeComponent component) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                output.writeByte(FORMAT_VERSION);
                for (int side = 0; side < SIDE_COUNT; side++) {
                    SideFilterData.Options options = component.options(side);
                    int flags = (options.matchMetadata() ? 1 : 0)
                        | (options.matchNBT() ? 2 : 0) | (options.acceptUnsortedItems() ? 4 : 0);
                    output.writeByte(flags);
                    for (int slot = 0; slot < SLOTS_PER_SIDE; slot++) {
                        APAdvancedWoodStackIO.write(output, component.getItem(side, slot));
                    }
                    if (bytes.size() > MAX_STATE_BYTES) return CodecResult.failure("Jeweled pipe inventory too large");
                }
                output.flush();
                // Filter stacks often share repeated registry IDs/components. Compress the complete
                // six-side inventory so a populated 162-slot pipe fits BCCE's persistence budget.
                ByteArrayOutputStream compressed = new ByteArrayOutputStream();
                try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
                    deflater.write(bytes.toByteArray());
                }
                if (compressed.size() > MAX_PERSISTED_BYTES) {
                    return CodecResult.failure("Jeweled pipe filters exceed BCCE persistence limit");
                }
                return CodecResult.success(new OpaqueData(APJeweledPipes.COMPONENT, compressed.toByteArray()));
            } catch (IOException | RuntimeException error) {
                return CodecResult.failure("Cannot encode jeweled pipe filters");
            }
        }

        @Override public CodecResult<JeweledPipeComponent> decode(OpaqueData data) {
            if (!APJeweledPipes.COMPONENT.equals(data.format()) || data.size() > MAX_PERSISTED_BYTES) {
                return CodecResult.failure("Invalid jeweled pipe data");
            }
            try {
                // Bound decompression before parsing: corrupted saves must not allocate unlimited memory.
                ByteArrayOutputStream unpacked = new ByteArrayOutputStream();
                try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(data.bytes()))) {
                    byte[] chunk = new byte[4096];
                    int count;
                    while ((count = inflater.read(chunk)) >= 0) {
                        if (count == 0) continue;
                        if (count > MAX_STATE_BYTES - unpacked.size()) {
                            return CodecResult.failure("Jeweled pipe filters exceed decompression limit");
                        }
                        unpacked.write(chunk, 0, count);
                    }
                }
                DataInputStream input = new DataInputStream(new ByteArrayInputStream(unpacked.toByteArray()));
                if (input.readUnsignedByte() != FORMAT_VERSION) return CodecResult.failure("Unknown jeweled pipe format");
                JeweledPipeComponent component = new JeweledPipeComponent();
                for (int side = 0; side < SIDE_COUNT; side++) {
                    int flags = input.readUnsignedByte();
                    if ((flags & ~7) != 0) return CodecResult.failure("Invalid jeweled filter options");
                    component.sides[side].setOptions(new SideFilterData.Options(
                        (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0));
                    for (int slot = 0; slot < SLOTS_PER_SIDE; slot++) {
                        ItemStack stack = APAdvancedWoodStackIO.read(input);
                        if (!stack.isEmpty() && (stack.getCount() < 1
                            || stack.getCount() > Math.min(64, stack.getMaxStackSize()))) {
                            return CodecResult.failure("Invalid jeweled filter stack count");
                        }
                        component.sides[side].set(slot, stack);
                    }
                }
                if (input.available() != 0) return CodecResult.failure("Trailing jeweled pipe data");
                return CodecResult.success(component);
            } catch (IOException | RuntimeException error) {
                return CodecResult.failure("Cannot decode jeweled pipe filters");
            }
        }
    };
}
