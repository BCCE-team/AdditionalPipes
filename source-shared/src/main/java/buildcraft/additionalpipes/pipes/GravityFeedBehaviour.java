package buildcraft.additionalpipes.pipes;

import buildcraft.transport.internal.pipe.IPipe;
import buildcraft.transport.internal.pipe.PipeFaceTex;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStructure;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

public final class GravityFeedBehaviour extends PipeBehaviourStructure {
    public GravityFeedBehaviour(IPipe pipe) { super(pipe); }
    public GravityFeedBehaviour(IPipe pipe, CompoundTag tag) { super(pipe, tag); }

    @Override public PipeFaceTex getTextureData(Direction face) {
        return PipeFaceTex.get(face == Direction.UP ? 1 : 0);
    }
}
