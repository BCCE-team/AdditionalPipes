package buildcraft.additionalpipes.pipes;

import buildcraft.lib.internal.mj.IMjReceiver;
import buildcraft.lib.internal.mj.MjCapabilities;
import buildcraft.lib.internal.mj.MjCapabilityHelper;
import buildcraft.transport.api2.ApiPortCapabilities;
import buildcraft.transport.internal.pipe.IPipe;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

/** Native MJ endpoint; BCCE itself controls optional MJ -> FE compatibility. */
public final class APObsidianFluidBehaviour extends APObsidianFluidBehaviourBase {
    public APObsidianFluidBehaviour(IPipe pipe) { super(pipe); }
    public APObsidianFluidBehaviour(IPipe pipe, CompoundTag tag) { super(pipe, tag); }

    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction facing) {
        IMjReceiver receiver = ApiPortCapabilities.external(mjEndpoint, MjCapabilities.CAP_RECEIVER).orElse(null);
        if (receiver != null) {
            // MjCapabilityHelper only publishes FE in BCCE's enabled auto-conversion modes.
            LazyOptional<T> power = new MjCapabilityHelper(receiver).getCapability(capability, facing);
            if (power.isPresent()) return power;
        }
        return super.getCapability(capability, facing);
    }
}
