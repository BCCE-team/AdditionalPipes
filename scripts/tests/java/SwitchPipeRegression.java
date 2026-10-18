import buildcraft.additionalpipes.pipes.APSwitchPipes;
import buildcraft.additionalpipes.pipes.SwitchPipeBehaviour;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftRegistries;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.content.BuildCraftContentIds;
import buildcraft.api.v2.energy.MjAmount;
import buildcraft.api.v2.pipe.FluidTransportProfile;
import buildcraft.api.v2.pipe.ExternalEnergyTransportProfile;
import buildcraft.api.v2.pipe.PipeComponentType;
import buildcraft.api.v2.pipe.PipeConnectionComponent;
import buildcraft.api.v2.pipe.PipeConnectionContext;
import buildcraft.api.v2.pipe.PipeConnectionDecision;
import buildcraft.api.v2.pipe.PipeType;
import buildcraft.api.v2.pipe.PipeView;
import buildcraft.api.v2.pipe.PowerTransportProfile;
import buildcraft.transport.internal.pipe.IPipe;
import buildcraft.transport.internal.pipe.PipeApi;
import buildcraft.transport.internal.pipe.PipeDefinition;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class SwitchPipeRegression {
    private static int assertions;

    public static void main(String[] args) {
        registerBaseTypes();
        APSwitchPipes.register();
        APSwitchPipes.register();
        require(BuildCraftRegistries.PIPE_COMPONENT_TYPES.data.size() == 1, "component registered twice");
        require(BuildCraftRegistries.PIPE_TYPES.data.size() == 9, "incorrect pipe type registration");

        PipeType item = type(APSwitchPipes.ITEM);
        PipeType fluid = type(APSwitchPipes.FLUID);
        PipeType power = type(APSwitchPipes.POWER);
        PipeType fe = type(APSwitchPipes.FE);
        require(item.itemProfile().isPresent(), "item transport is missing");
        require(item.fluidProfile().isEmpty() && item.mjProfile().isEmpty(), "item medium is incorrect");
        require(fluid.fluidProfile().orElseThrow().equals(new FluidTransportProfile(80)), "fluid flow differs from gold");
        require(power.mjProfile().orElseThrow().maxPerTick().microMj() == 3000, "power capacity differs from average");
        require(power.mjProfile().orElseThrow().resistancePerTick() == 200000, "power resistance differs from average");
        require(fe.externalEnergyProfile().orElseThrow().maxPerTick() == 500, "FE transfer capacity differs from average");
        require(!fe.externalEnergyProfile().orElseThrow().extractor(), "FE switch should not extract energy");
        require(APSwitchPipes.isSwitch(APSwitchPipes.ITEM) && APSwitchPipes.isSwitch(APSwitchPipes.FLUID)
            && APSwitchPipes.isSwitch(APSwitchPipes.POWER) && APSwitchPipes.isSwitch(APSwitchPipes.FE), "switch ids missing");
        require(!APSwitchPipes.isSwitch("pipe_items_teleport"), "unrelated item treated as switch");

        verifyDefinition(APSwitchPipes.ITEM, PipeApi.flowItems, "20", "21");
        verifyDefinition(APSwitchPipes.FLUID, PipeApi.flowFluids, "22", "23");
        verifyDefinition(APSwitchPipes.POWER, PipeApi.flowPower, "16", "17");
        verifyDefinition(APSwitchPipes.FE, PipeApi.flowForgeEnergy, "41", "42");

        BlockPos pos = new BlockPos();
        BlockState state = new BlockState();
        Level level = new Level();
        var componentType = BuildCraftRegistries.PIPE_COMPONENT_TYPES.data.values().iterator().next();
        for (String name : Set.of(APSwitchPipes.ITEM, APSwitchPipes.FLUID, APSwitchPipes.POWER, APSwitchPipes.FE)) {
            PipeView view = new PipeView() {
                public ResourceLocation typeId() { return id(name); }
                public BlockPos position() { return pos; }
            };
            PipeConnectionComponent connection = (PipeConnectionComponent) componentType.create(view);
            var context = new PipeConnectionContext(level, view, Direction.EAST, state);
            level.powered = false;
            BuildCraftServices.PIPES.allowPipe = true;
            require(connection.connection(context) == PipeConnectionDecision.PASS, name + " not open without redstone");
            level.powered = true;
            require(connection.connection(context) == PipeConnectionDecision.DENY, name + " not closed with redstone");
            level.powered = false;
            BuildCraftServices.PIPES.allowPipe = false;
            require(connection.connection(context) == (name.equals(APSwitchPipes.FLUID)
                ? PipeConnectionDecision.DENY : PipeConnectionDecision.PASS), "incorrect external endpoint policy");
        }
        require(APSwitchPipes.createItem(APSwitchPipes.ITEM) != null, "item registration failed");
        require(APSwitchPipes.createItem(APSwitchPipes.FLUID) != null, "fluid registration failed");
        require(APSwitchPipes.createItem(APSwitchPipes.POWER) != null, "power registration failed");
        require(APSwitchPipes.createItem(APSwitchPipes.FE) != null, "FE registration failed");
        try {
            APSwitchPipes.createItem("not_a_switch");
            throw new AssertionError("unknown pipe accepted");
        } catch (IllegalArgumentException expected) {
            assertions++;
        }

        IPipe disconnected = side -> false;
        IPipe connected = side -> true;
        require(new SwitchPipeBehaviour(disconnected).getTextureIndex(Direction.NORTH) == 1, "closed side texture wrong");
        require(new SwitchPipeBehaviour(connected).getTextureIndex(Direction.NORTH) == 0, "connected side texture wrong");
        require(new SwitchPipeBehaviour(disconnected).getTextureIndex(null) == 0, "center texture wrong");
        require(new SwitchPipeBehaviour(connected, new net.minecraft.nbt.CompoundTag())
            .getTextureIndex(Direction.WEST) == 0, "loaded connection texture wrong");
        System.out.println("PASS Switch Pipe runtime contract: " + assertions + " assertions");
    }

    private static void registerBaseTypes() {
        BuildCraftRegistries.PIPE_TYPES.register(BuildCraftContentIds.Pipes.GOLD_FLUID,
            PipeType.builder(BuildCraftContentIds.Pipes.GOLD_FLUID)
                .fluidProfile(new FluidTransportProfile(80)).build(), () -> "buildcrafttransport");
        BuildCraftRegistries.PIPE_TYPES.register(BuildCraftContentIds.Pipes.IRON_POWER,
            PipeType.builder(BuildCraftContentIds.Pipes.IRON_POWER)
                .mjProfile(PowerTransportProfile.fromResistance(MjAmount.ofMicro(1000), 100000, false))
                .build(), () -> "buildcrafttransport");
        BuildCraftRegistries.PIPE_TYPES.register(BuildCraftContentIds.Pipes.GOLD_POWER,
            PipeType.builder(BuildCraftContentIds.Pipes.GOLD_POWER)
                .mjProfile(PowerTransportProfile.fromResistance(MjAmount.ofMicro(5000), 300000, false))
                .build(), () -> "buildcrafttransport");
        BuildCraftRegistries.PIPE_TYPES.register(BuildCraftContentIds.Pipes.IRON_FE,
            PipeType.builder(BuildCraftContentIds.Pipes.IRON_FE)
                .externalEnergyProfile(new ExternalEnergyTransportProfile(100, false))
                .build(), () -> "buildcrafttransport");
        BuildCraftRegistries.PIPE_TYPES.register(BuildCraftContentIds.Pipes.GOLD_FE,
            PipeType.builder(BuildCraftContentIds.Pipes.GOLD_FE)
                .externalEnergyProfile(new ExternalEnergyTransportProfile(900, false))
                .build(), () -> "buildcrafttransport");
    }

    private static void verifyDefinition(String name, Object flow, String a, String b) {
        PipeDefinition.PipeDefinitionBuilder builder = PipeDefinition.PIPE_DEFINITIONS.get(id(name).toString());
        require(builder != null, name + " definition missing");
        require(builder.flow == flow, name + " flow mismatch");
        require(builder.textures.length == 2 && a.equals(builder.textures[0])
            && b.equals(builder.textures[1]), name + " texture variants incorrect");
    }

    private static PipeType type(String name) {
        PipeType t = BuildCraftRegistries.PIPE_TYPES.get(id(name));
        require(t != null, name + " type missing");
        require(t.components().contains(id("switch_connection")), name + " connection component missing");
        return t;
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.tryParse("additionalpipes:" + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
