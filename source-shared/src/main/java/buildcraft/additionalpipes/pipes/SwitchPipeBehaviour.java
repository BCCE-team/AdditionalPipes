package buildcraft.additionalpipes.pipes;

import buildcraft.transport.internal.pipe.IPipe;
import buildcraft.transport.internal.pipe.PipeBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

public final class SwitchPipeBehaviour extends PipeBehaviour {
    public SwitchPipeBehaviour(IPipe pipe) {
        super(pipe);
    }

    public SwitchPipeBehaviour(IPipe pipe, CompoundTag tag) {
        super(pipe, tag);
    }

    @Override
    public int getTextureIndex(Direction side) {
        return side != null && !pipe.isConnected(side) ? 1 : 0;
    }
}
