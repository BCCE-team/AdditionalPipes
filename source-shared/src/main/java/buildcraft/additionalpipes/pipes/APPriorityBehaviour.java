package buildcraft.additionalpipes.pipes;

import buildcraft.transport.internal.pipe.IPipe;
import buildcraft.transport.internal.pipe.PipeFaceTex;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStructure;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

public final class APPriorityBehaviour extends PipeBehaviourStructure {
    public APPriorityBehaviour(IPipe pipe) { super(pipe); }
    public APPriorityBehaviour(IPipe pipe, CompoundTag tag) { super(pipe, tag); }

    @Override public PipeFaceTex getTextureData(Direction face) {
        return PipeFaceTex.get(face == null || face == Direction.EAST ? 0 : face.ordinal() + 1);
    }
}
