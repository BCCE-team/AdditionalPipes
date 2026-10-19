package buildcraft.additionalpipes.pipes;

import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.energy.MjPort;
import buildcraft.api.v2.pipe.PipeExternalEndpoint;
import buildcraft.transport.internal.pipe.IPipe;
import buildcraft.transport.internal.pipe.PipeFaceTex;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStructure;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public abstract class APAdvancedWoodBehaviourBase extends PipeBehaviourStructure {
    protected final PipeExternalEndpoint mjEndpoint = new PipeExternalEndpoint() {
        @Override public Optional<MjPort> mjPort() { return component().map(AdvancedWoodComponent::energyPort); }
    };

    protected APAdvancedWoodBehaviourBase(IPipe pipe) { super(pipe); }
    protected APAdvancedWoodBehaviourBase(IPipe pipe, CompoundTag tag) { super(pipe, tag); }

    public Optional<AdvancedWoodComponent> component() {
        Level level = pipe.getHolder().getPipeWorld();
        if (level == null || !level.hasChunkAt(pipe.getHolder().getPipePos())) return Optional.empty();
        return BuildCraftApi.service(BuildCraftServices.PIPES).pipe(level, pipe.getHolder().getPipePos())
            .flatMap(view -> view.component(APInsertionPipes.ADVANCED_COMPONENT))
            .filter(AdvancedWoodComponent.class::isInstance)
            .map(AdvancedWoodComponent.class::cast);
    }

    @Override public PipeFaceTex getTextureData(Direction face) {
        return PipeFaceTex.get(face != null && component().map(c -> face == c.source()).orElse(false) ? 1 : 0);
    }
}
