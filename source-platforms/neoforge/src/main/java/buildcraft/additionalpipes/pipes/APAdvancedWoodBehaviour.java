package buildcraft.additionalpipes.pipes;

import buildcraft.transport.internal.pipe.IPipe;
import buildcraft.transport.api2.ApiPortCapabilities;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.capabilities.BlockCapability;

public final class APAdvancedWoodBehaviour extends APAdvancedWoodBehaviourBase {
    public APAdvancedWoodBehaviour(IPipe pipe) { super(pipe); }
    public APAdvancedWoodBehaviour(IPipe pipe, CompoundTag tag) { super(pipe, tag); }

    @Override public <T> T getCapability(BlockCapability<T, Direction> capability, Direction facing) {
        T mj = ApiPortCapabilities.external(mjEndpoint, capability);
        return mj != null ? mj : super.getCapability(capability, facing);
    }
}
