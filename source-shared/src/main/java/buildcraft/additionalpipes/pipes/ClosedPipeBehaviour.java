package buildcraft.additionalpipes.pipes;

import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.transport.internal.pipe.IPipe;
import buildcraft.transport.internal.pipe.PipeEventHandler;
import buildcraft.transport.internal.pipe.PipeEventItem;
import buildcraft.transport.internal.pipe.PipeFaceTex;
import buildcraft.lib.net.BCNetworkSide;
import buildcraft.lib.net.BCPacketContext;
import java.io.IOException;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStructure;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class ClosedPipeBehaviour extends PipeBehaviourStructure {
    private boolean clientOccupied;
    public ClosedPipeBehaviour(IPipe pipe) { super(pipe); }
    public ClosedPipeBehaviour(IPipe pipe, CompoundTag tag) { super(pipe, tag); }

    private Optional<ClosedPipeComponent> component() {
        Level level = pipe.getHolder().getPipeWorld();
        if (level == null || !level.hasChunkAt(pipe.getHolder().getPipePos())) return Optional.empty();
        return BuildCraftApi.service(BuildCraftServices.PIPES).pipe(level, pipe.getHolder().getPipePos())
            .flatMap(view -> view.component(APStoragePipes.CLOSED_COMPONENT))
            .filter(ClosedPipeComponent.class::isInstance).map(ClosedPipeComponent.class::cast);
    }

    @PipeEventHandler
    public void onDrop(PipeEventItem.Drop event) {
        if (pipe.getHolder().getPipeWorld().isClientSide()) return;
        ItemStack falling = event.getStack();
        if (falling.isEmpty()) return;
        component().ifPresent(storage -> event.setStack(storage.absorb(falling)));
    }

    @Override public void writePayload(FriendlyByteBuf buffer, BCNetworkSide side) {
        super.writePayload(buffer, side);
        if (side == BCNetworkSide.SERVER)
            buffer.writeBoolean(component().map(storage -> !storage.isEmpty()).orElse(false));
    }

    @Override public void readPayload(FriendlyByteBuf buffer, BCNetworkSide side, BCPacketContext context) throws IOException {
        super.readPayload(buffer, side, context);
        if (side == BCNetworkSide.CLIENT) {
            boolean occupied = buffer.readBoolean();
            if (clientOccupied != occupied) {
                clientOccupied = occupied;
                pipe.getHolder().scheduleRenderUpdate();
            }
        }
    }

    @Override public void addDrops(NonNullList<ItemStack> drops, int fortune) {
        component().ifPresent(storage -> storage.collectDrops(drops));
    }

    @Override public PipeFaceTex getTextureData(Direction face) {
        Level level = pipe.getHolder().getPipeWorld();
        boolean filled = level != null && !level.isClientSide()
            ? component().map(storage -> !storage.isEmpty()).orElse(false) : clientOccupied;
        return PipeFaceTex.get(filled ? 1 : 0);
    }
}
