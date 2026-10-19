package buildcraft.additionalpipes.pipes;

import buildcraft.transport.internal.pipe.IPipe;
import buildcraft.transport.api2.ApiPortCapabilities;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

public final class APAdvancedWoodBehaviour extends APAdvancedWoodBehaviourBase {
    public APAdvancedWoodBehaviour(IPipe pipe) { super(pipe); }
    public APAdvancedWoodBehaviour(IPipe pipe, CompoundTag tag) { super(pipe, tag); }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction facing) {
        LazyOptional<T> mj = ApiPortCapabilities.external(mjEndpoint, capability);
        return mj.isPresent() ? mj : super.getCapability(capability, facing);
    }
}
