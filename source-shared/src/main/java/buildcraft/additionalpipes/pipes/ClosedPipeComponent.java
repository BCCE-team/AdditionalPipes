package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.platform.APClosedPipeMenuAccess;
import buildcraft.additionalpipes.platform.APDimensionIds;
import buildcraft.additionalpipes.platform.APPlayerLevel;
import buildcraft.additionalpipes.service.APServices;
import buildcraft.api.v2.OperationMode;
import buildcraft.api.v2.persistence.ApiCodec;
import buildcraft.api.v2.persistence.CodecResult;
import buildcraft.api.v2.persistence.OpaqueData;
import buildcraft.api.v2.pipe.PipeActivationContext;
import buildcraft.api.v2.pipe.PipeActivationResult;
import buildcraft.api.v2.pipe.PipeMutationContext;
import buildcraft.api.v2.pipe.PipeRuntimeComponent;
import buildcraft.api.v2.pipe.PipeView;
import buildcraft.transport.internal.pipe.IPipeHolder.PipeMessageReceiver;
import buildcraft.transport.pipe.Pipe;
import buildcraft.lib.misc.StackUtil;
import buildcraft.transport.internal.pipe.IItemPipe;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class ClosedPipeComponent implements PipeRuntimeComponent {
    public static final int SLOT_COUNT = 10;
    public static final int GUI_SLOT_COUNT = 9;
    private final ItemStack[] items = new ItemStack[SLOT_COUNT];
    private PipeMutationContext mutation;
    private PipeView pipe;

    public ClosedPipeComponent() { Arrays.fill(items, ItemStack.EMPTY); }
    public ClosedPipeComponent(PipeView view) { this(); attached(view); }

    @Override public ResourceLocation typeId() { return APStoragePipes.CLOSED_COMPONENT; }
    @Override public void attached(PipeView view) {
        pipe = view;
        if (view instanceof PipeMutationContext context) mutation = context;
    }

    @Override public void tick(PipeMutationContext context) {
        if (mutation == null) attached(context);
    }

    private void changed() {
        if (mutation == null || mutation.isClientSide()) return;
        mutation.markChanged();
        if (pipe instanceof Pipe runtime) {
            runtime.getHolder().scheduleNetworkUpdate(PipeMessageReceiver.BEHAVIOUR);
        }
    }

    public ItemStack getItem(int slot) { return items[slot]; }
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= GUI_SLOT_COUNT) return;
        ItemStack copy = stack == null ? ItemStack.EMPTY : stack.copy();
        if (!copy.isEmpty()) copy.setCount(Math.min(copy.getCount(), Math.min(64, copy.getMaxStackSize())));
        items[slot] = copy;
        revealOverflow();
        changed();
    }
    public ItemStack takeItem(int slot, int count) {
        if (slot < 0 || slot >= GUI_SLOT_COUNT || count <= 0) return ItemStack.EMPTY;
        ItemStack current = items[slot];
        if (current.isEmpty()) return ItemStack.EMPTY;
        ItemStack removed = current.split(count);
        if (current.isEmpty()) items[slot] = ItemStack.EMPTY;
        revealOverflow();
        changed();
        return removed;
    }
    public ItemStack removeItem(int slot) {
        if (slot < 0 || slot >= GUI_SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack removed = items[slot];
        items[slot] = ItemStack.EMPTY;
        revealOverflow();
        changed();
        return removed;
    }
    public void collectDrops(java.util.Collection<ItemStack> drops) {
        for (ItemStack stack : items) if (!stack.isEmpty()) drops.add(stack.copy());
    }
    public boolean isEmpty() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
        return true;
    }
    public void markInventoryChanged() { revealOverflow(); changed(); }

    private void revealOverflow() {
        if (items[GUI_SLOT_COUNT].isEmpty()) return;
        for (int i = 0; i < GUI_SLOT_COUNT; i++) {
            if (items[i].isEmpty()) {
                items[i] = items[GUI_SLOT_COUNT];
                items[GUI_SLOT_COUNT] = ItemStack.EMPTY;
                break;
            }
        }
    }

    public ItemStack absorb(ItemStack incoming) {
        if (incoming == null || incoming.isEmpty()) return ItemStack.EMPTY;
        ItemStack remainder = incoming.copy();
        for (int i = 0; i < items.length && !remainder.isEmpty(); i++) {
            ItemStack existing = items[i];
            if (existing.isEmpty() || !StackUtil.canMerge(existing, remainder)) continue;
            int limit = Math.min(64, existing.getMaxStackSize());
            int move = Math.min(remainder.getCount(), limit - existing.getCount());
            if (move > 0) { existing.grow(move); remainder.shrink(move); }
        }
        for (int i = 0; i < items.length && !remainder.isEmpty(); i++) {
            if (!items[i].isEmpty()) continue;
            int move = Math.min(remainder.getCount(), Math.min(64, remainder.getMaxStackSize()));
            ItemStack placed = remainder.copy();
            placed.setCount(move);
            items[i] = placed;
            remainder.shrink(move);
        }
        if (remainder.getCount() != incoming.getCount()) changed();
        return remainder;
    }

    @Override public PipeActivationResult activate(PipeActivationContext context) {
        if (!context.heldStack().isEmpty()
            && context.heldStack().getItem() instanceof IItemPipe) return PipeActivationResult.PASS;
        UUID id = context.actor().playerId().orElse(null);
        MinecraftServer server = APServices.server();
        ServerPlayer player = server == null || id == null ? null : server.getPlayerList().getPlayer(id);
        if (player == null) return PipeActivationResult.PASS;
        if (context.mode() == OperationMode.EXECUTE && !context.pipe().isClientSide()) {
            ServerLevel level = APPlayerLevel.of(player);
            if (pipe != null && level.hasChunkAt(pipe.position())) {
                var pos = pipe.position();
                APClosedPipeMenuAccess.open(player, this,
                    new PipeLocation(APDimensionIds.of(level), pos.getX(), pos.getY(), pos.getZ()));
            }
        }
        return PipeActivationResult.SUCCESS;
    }

    public static final ApiCodec<ClosedPipeComponent, OpaqueData> CODEC = new ApiCodec<>() {
        @Override public CodecResult<OpaqueData> encode(ClosedPipeComponent value) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes);
                out.writeByte(1);
                for (ItemStack stack : value.items) APAdvancedWoodStackIO.write(out, stack);
                out.flush();
                return CodecResult.success(new OpaqueData(APStoragePipes.CLOSED_COMPONENT, bytes.toByteArray()));
            } catch (IOException ex) { return CodecResult.failure("Cannot encode closed pipe inventory"); }
        }
        @Override public CodecResult<ClosedPipeComponent> decode(OpaqueData data) {
            if (!APStoragePipes.CLOSED_COMPONENT.equals(data.format()) || data.size() > 1_048_576)
                return CodecResult.failure("Invalid closed pipe inventory");
            try {
                DataInputStream in = new DataInputStream(new ByteArrayInputStream(data.bytes()));
                if (in.readUnsignedByte() != 1) return CodecResult.failure("Unknown closed pipe inventory version");
                ClosedPipeComponent component = new ClosedPipeComponent();
                for (int i = 0; i < SLOT_COUNT; i++) component.items[i] = APAdvancedWoodStackIO.read(in);
                if (in.available() != 0) return CodecResult.failure("Trailing closed pipe inventory bytes");
                return CodecResult.success(component);
            } catch (IOException | RuntimeException ex) {
                return CodecResult.failure("Cannot decode closed pipe inventory");
            }
        }
    };
}
