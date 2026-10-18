package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftRegistries;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.content.BuildCraftContentIds;
import buildcraft.api.v2.pipe.ItemTransportProfile;
import buildcraft.api.v2.pipe.PipeComponentType;
import buildcraft.api.v2.pipe.PipeType;
import buildcraft.api.v2.persistence.OpaqueData;
import buildcraft.api.v2.persistence.PersistentType;
import buildcraft.transport.internal.pipe.PipeApi;
import buildcraft.transport.internal.pipe.PipeFlowType;
import buildcraft.transport.internal.pipe.PipeDefinition.PipeDefinitionBuilder;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStructure;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public final class APTeleportPipes {
    public static final String ITEMS = "pipe_items_teleport";
    public static final String FLUIDS = "pipe_fluids_teleport";
    public static final String POWER = "pipe_power_teleport";
    public static final String FE = "pipe_fe_teleport";
    public static final Identifier COMPONENT_ID = id("teleport_endpoint");
    private static final Set<String> IDS = Set.of(ITEMS, FLUIDS, POWER, FE);
    private static boolean registered;

    private APTeleportPipes() { }

    public static boolean isTeleport(String name) { return IDS.contains(name); }

    public static Identifier id(String path) { return Identifier.parse(AdditionalPipes.MOD_ID + ":" + path); }

    public static synchronized void register() {
        if (registered) return;
        var components = BuildCraftApi.registry(BuildCraftRegistries.PIPE_COMPONENT_TYPES);
        components.register(COMPONENT_ID, new PipeComponentType<>(COMPONENT_ID,
            TeleportPipeComponent::new, PersistentType.<TeleportPipeComponent, OpaqueData>builder(
                COMPONENT_ID, 1, TeleportPipeComponent.CODEC).build()), () -> AdditionalPipes.MOD_ID);

        var types = BuildCraftApi.registry(BuildCraftRegistries.PIPE_TYPES);
        PipeType goldFluid = requireType(BuildCraftContentIds.Pipes.GOLD_FLUID);
        PipeType goldPower = requireType(BuildCraftContentIds.Pipes.GOLD_POWER);
        PipeType goldFe = requireType(BuildCraftContentIds.Pipes.GOLD_FE);

        types.register(id(ITEMS), PipeType.builder(id(ITEMS))
            .itemProfile(new ItemTransportProfile(64, 1))
            .component(COMPONENT_ID).build(), () -> AdditionalPipes.MOD_ID);
        types.register(id(FLUIDS), PipeType.builder(id(FLUIDS))
            .fluidProfile(goldFluid.fluidProfile().orElseThrow())
            .component(COMPONENT_ID).build(), () -> AdditionalPipes.MOD_ID);
        types.register(id(POWER), PipeType.builder(id(POWER))
            .mjProfile(goldPower.mjProfile().orElseThrow())
            .component(COMPONENT_ID).build(), () -> AdditionalPipes.MOD_ID);
        types.register(id(FE), PipeType.builder(id(FE))
            .externalEnergyProfile(goldFe.externalEnergyProfile().orElseThrow())
            .component(COMPONENT_ID).build(), () -> AdditionalPipes.MOD_ID);

        define(ITEMS, PipeApi.flowItems, "0");
        define(FLUIDS, PipeApi.flowFluids, "2");
        define(POWER, PipeApi.flowPower, "3");
        define(FE, PipeApi.flowForgeEnergy, "43");
        registered = true;
    }

    public static Item createItem(String path) {
        if (!isTeleport(path)) throw new IllegalArgumentException("Unknown teleport pipe: " + path);
        return BuildCraftApi.service(BuildCraftServices.PIPES).createItem(id(path));
    }

    private static PipeType requireType(Identifier id) {
        PipeType value = BuildCraftApi.registry(BuildCraftRegistries.PIPE_TYPES).get(id);
        if (value == null) throw new IllegalStateException("Missing BuildCraft pipe type " + id);
        return value;
    }

    private static void define(String path, PipeFlowType flow, String texture) {
        PipeDefinitionBuilder builder = new PipeDefinitionBuilder();
        builder.identifier = id(path);
        builder.texPrefixDirect("additionalpipes:block/")
            .texSuffixes(texture)
            .logic(PipeBehaviourStructure::new, PipeBehaviourStructure::new)
            .flow(flow)
            .disableColouring()
            .define();
    }
}
