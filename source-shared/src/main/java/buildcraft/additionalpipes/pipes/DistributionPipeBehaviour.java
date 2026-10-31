package buildcraft.additionalpipes.pipes;

import buildcraft.transport.internal.pipe.IPipe;
import buildcraft.transport.internal.pipe.PipeFaceTex;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStructure;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/** The six original painted face textures (down, up, north, south, west, east). */
public final class DistributionPipeBehaviour extends PipeBehaviourStructure {
    public DistributionPipeBehaviour(IPipe pipe) { super(pipe); }
    public DistributionPipeBehaviour(IPipe pipe, CompoundTag data) { super(pipe, data); }
    @Override public PipeFaceTex getTextureData(Direction face) {
        return PipeFaceTex.get(face == null ? 0 : face.ordinal());
    }
}
