package buildcraft.additionalpipes.pipes;

import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.pipe.PipeExternalEndpoint;
import buildcraft.api.v2.energy.MjPort;
import buildcraft.transport.internal.pipe.IPipe;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStructure;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/** Resolves the pipe's canonical MJ receiver through BCCE API v2. */
public abstract class APObsidianFluidBehaviourBase extends PipeBehaviourStructure {
    protected final PipeExternalEndpoint mjEndpoint = new PipeExternalEndpoint() {
        @Override public Optional<MjPort> mjPort() {
            return component().map(ObsidianFluidComponent::powerPort);
        }
    };

    protected APObsidianFluidBehaviourBase(IPipe pipe) { super(pipe); }
    protected APObsidianFluidBehaviourBase(IPipe pipe, CompoundTag tag) { super(pipe, tag); }

    public Optional<ObsidianFluidComponent> component() {
        Level level = pipe.getHolder().getPipeWorld();
        if (level == null || !level.hasChunkAt(pipe.getHolder().getPipePos())) return Optional.empty();
        return BuildCraftApi.service(BuildCraftServices.PIPES).pipe(level, pipe.getHolder().getPipePos())
            .flatMap(view -> view.component(APUtilityPipes.OBSIDIAN_COMPONENT))
            .filter(ObsidianFluidComponent.class::isInstance)
            .map(ObsidianFluidComponent.class::cast);
    }
}
