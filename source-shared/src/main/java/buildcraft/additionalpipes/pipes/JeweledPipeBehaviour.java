package buildcraft.additionalpipes.pipes;

import buildcraft.transport.internal.pipe.IPipe;
import buildcraft.transport.internal.pipe.PipeFaceTex;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStructure;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/** Reproduces the orientation-dependent face textures of AdditionalPipesBC 4.7.7. */
public final class JeweledPipeBehaviour extends PipeBehaviourStructure {
    public JeweledPipeBehaviour(IPipe pipe) { super(pipe); }
    public JeweledPipeBehaviour(IPipe pipe, CompoundTag tag) { super(pipe, tag); }

    @Override public PipeFaceTex getTextureData(Direction face) {
        return PipeFaceTex.get(face == null || face == Direction.EAST ? 0 : face.ordinal() + 1);
    }
}
