import buildcraft.additionalpipes.pipes.APTeleportPipes;
import buildcraft.additionalpipes.pipes.TeleportPipeComponent;
import buildcraft.api.v2.BuildCraftRegistries;
import buildcraft.api.v2.content.BuildCraftContentIds;
import buildcraft.api.v2.energy.MjAmount;
import buildcraft.api.v2.pipe.ExternalEnergyTransportProfile;
import buildcraft.api.v2.pipe.FluidTransportProfile;
import buildcraft.api.v2.pipe.PipeType;
import buildcraft.api.v2.pipe.PowerTransportProfile;
import buildcraft.transport.internal.pipe.PipeApi;
import buildcraft.transport.internal.pipe.PipeDefinition;
import buildcraft.api.v2.persistence.OpaqueData;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public final class TeleportPipeRegression {
    private static int assertions;

    public static void main(String[] args) {
        registerBases();
        APTeleportPipes.register();
        APTeleportPipes.register();
        require(BuildCraftRegistries.PIPE_COMPONENT_TYPES.data.size() == 1, "duplicate teleport component");
        require(BuildCraftRegistries.PIPE_TYPES.data.size() == 7, "wrong number of types");
        check(APTeleportPipes.ITEMS, PipeApi.flowItems, "0");
        check(APTeleportPipes.FLUIDS, PipeApi.flowFluids, "2");
        check(APTeleportPipes.POWER, PipeApi.flowPower, "3");
        check(APTeleportPipes.FE, PipeApi.flowForgeEnergy, "43");
        require(APTeleportPipes.isTeleport(APTeleportPipes.FE), "FE teleport missing");
        require(!APTeleportPipes.isTeleport("pipe_fe_switch"), "incorrect teleport classification");
        require(BuildCraftRegistries.PIPE_TYPES.get(id(APTeleportPipes.ITEMS)).itemProfile().isPresent(), "item medium");
        require(BuildCraftRegistries.PIPE_TYPES.get(id(APTeleportPipes.FLUIDS)).fluidProfile().isPresent(), "fluid medium");
        require(BuildCraftRegistries.PIPE_TYPES.get(id(APTeleportPipes.POWER)).mjProfile().isPresent(), "MJ medium");
        require(BuildCraftRegistries.PIPE_TYPES.get(id(APTeleportPipes.FE)).externalEnergyProfile().isPresent(), "FE medium");
        for (String pipe : new String[]{APTeleportPipes.ITEMS, APTeleportPipes.FLUIDS,
            APTeleportPipes.POWER, APTeleportPipes.FE}) {
            require(APTeleportPipes.createItem(pipe) != null, pipe + " missing item");
        }
        TeleportPipeComponent src = new TeleportPipeComponent();
        require(src.getFrequency() == 0, "default frequency");
        require(src.getState() == 1, "default send mode");
        require(!src.canReceive() && src.canSend(), "default send-only mode");
        require(!src.isPublic(), "private by default");
        UUID owner = UUID.fromString("a77efcd0-9668-4bc0-bf73-4b03baa237c9");
        src.setOwner(owner, "Tester");
        src.setFrequency(35);
        src.setState((byte)3);
        src.setPublic(true);
        require(src.canSend() && src.canReceive(), "both mode");
        require(src.canModify(owner), "owner cannot modify");
        require(src.canModify(UUID.randomUUID()), "public pipe should be adjustable");
        OpaqueData persisted = TeleportPipeComponent.CODEC.encode(src).valueOrThrow();
        TeleportPipeComponent restored = TeleportPipeComponent.CODEC.decode(persisted).valueOrThrow();
        require(restored.getFrequency() == 35, "frequency persistence");
        require(restored.getState() == 3, "mode persistence");
        require(restored.isPublic(), "public/private persistence");
        require(owner.equals(restored.getOwnerUUID()), "owner UUID persistence");
        require("Tester".equals(restored.getOwnerName()), "owner name persistence");
        restored.setPublic(false);
        require(!restored.canModify(UUID.randomUUID()), "private pipe permission leak");
        restored.setState((byte)0);
        require(!restored.canSend() && !restored.canReceive(), "off mode");
        requireFailure(() -> src.setFrequency(-1));
        requireFailure(() -> src.setFrequency(1_000_000));
        requireFailure(() -> src.setState((byte)-1));
        requireFailure(() -> src.setState((byte)4));
        try {
            APTeleportPipes.createItem("not_teleport");
            throw new AssertionError("unknown item accepted");
        } catch (IllegalArgumentException expected) { assertions++; }
        System.out.println("PASS Teleport Pipe runtime contract: " + assertions + " assertions");
    }

    private static void registerBases() {
        BuildCraftRegistries.PIPE_TYPES.register(BuildCraftContentIds.Pipes.GOLD_FLUID,
            PipeType.builder(BuildCraftContentIds.Pipes.GOLD_FLUID)
                .fluidProfile(new FluidTransportProfile(80)).build(), () -> "buildcrafttransport");
        BuildCraftRegistries.PIPE_TYPES.register(BuildCraftContentIds.Pipes.GOLD_POWER,
            PipeType.builder(BuildCraftContentIds.Pipes.GOLD_POWER)
                .mjProfile(PowerTransportProfile.fromResistance(MjAmount.ofMicro(5000), 300000, false))
                .build(), () -> "buildcrafttransport");
        BuildCraftRegistries.PIPE_TYPES.register(BuildCraftContentIds.Pipes.GOLD_FE,
            PipeType.builder(BuildCraftContentIds.Pipes.GOLD_FE)
                .externalEnergyProfile(new ExternalEnergyTransportProfile(900, false))
                .build(), () -> "buildcrafttransport");
    }

    private static void check(String name, Object flow, String tex) {
        PipeDefinition.PipeDefinitionBuilder builder = PipeDefinition.PIPE_DEFINITIONS.get(id(name).toString());
        require(builder != null, name + " missing pipe definition");
        require(builder.flow == flow, name + " flow is wrong");
        require(builder.textures.length == 1 && tex.equals(builder.textures[0]), name + " texture incorrect");
        require(BuildCraftRegistries.PIPE_TYPES.get(id(name)).components().contains(APTeleportPipes.COMPONENT_ID),
            name + " missing teleport component");
    }

    private static ResourceLocation id(String path) { return ResourceLocation.tryParse("additionalpipes:" + path); }
    private static void requireFailure(Runnable operation) {
        try { operation.run(); throw new AssertionError("Invalid configuration accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
        assertions++;
    }
}
