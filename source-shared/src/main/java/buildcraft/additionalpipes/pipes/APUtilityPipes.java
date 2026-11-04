package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftRegistries;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.content.BuildCraftContentIds;
import buildcraft.api.v2.persistence.OpaqueData;
import buildcraft.api.v2.persistence.PersistentType;
import buildcraft.api.v2.pipe.ItemTransportProfile;
import buildcraft.api.v2.pipe.PipeComponentType;
import buildcraft.api.v2.pipe.PipeType;
import buildcraft.transport.internal.pipe.PipeApi;
import buildcraft.transport.internal.pipe.PipeDefinition.PipeDefinitionBuilder;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStructure;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Custom utility pipes backed by real BCCE pipe definitions. */
public final class APUtilityPipes {
    public static final String DISTRIBUTION = "pipe_items_distribution";
    public static final String WATER_PUMP = "pipe_fluids_water_pump";
    public static final String OBSIDIAN = "pipe_fluids_obsidian";
    public static final ResourceLocation DISTRIBUTION_COMPONENT = id("distribution_quota");
    public static final ResourceLocation WATER_PUMP_COMPONENT = id("water_pump_source");
    public static final ResourceLocation OBSIDIAN_COMPONENT = id("obsidian_fluid_drain");
    private static boolean registered;

    private APUtilityPipes() { }
    public static boolean isUtility(String path) {
        return DISTRIBUTION.equals(path) || WATER_PUMP.equals(path) || OBSIDIAN.equals(path);
    }
    public static ResourceLocation id(String path) {
        return ResourceLocation.tryParse(AdditionalPipes.MOD_ID + ":" + path);
    }

    public static synchronized void register() {
        if (registered) return;
        var components = BuildCraftApi.registry(BuildCraftRegistries.PIPE_COMPONENT_TYPES);
        components.register(DISTRIBUTION_COMPONENT, new PipeComponentType<>(DISTRIBUTION_COMPONENT,
            pipe -> new DistributionComponent(), PersistentType.<DistributionComponent, OpaqueData>builder(
                DISTRIBUTION_COMPONENT, 1, DistributionComponent.CODEC).build()), () -> AdditionalPipes.MOD_ID);
        components.register(WATER_PUMP_COMPONENT, new PipeComponentType<>(WATER_PUMP_COMPONENT,
            pipe -> new WaterPumpComponent(), null), () -> AdditionalPipes.MOD_ID);
        components.register(OBSIDIAN_COMPONENT, new PipeComponentType<>(OBSIDIAN_COMPONENT,
            ObsidianFluidComponent::new, PersistentType.<ObsidianFluidComponent, OpaqueData>builder(
                OBSIDIAN_COMPONENT, 1, ObsidianFluidComponent.CODEC).build()), () -> AdditionalPipes.MOD_ID);

        var types = BuildCraftApi.registry(BuildCraftRegistries.PIPE_TYPES);
        PipeType goldFluid = BuildCraftApi.registry(BuildCraftRegistries.PIPE_TYPES)
            .get(BuildCraftContentIds.Pipes.GOLD_FLUID);
        if (goldFluid == null) throw new IllegalStateException("BCCE gold fluid pipe not registered");
        types.register(id(DISTRIBUTION), PipeType.builder(id(DISTRIBUTION))
            .itemProfile(new ItemTransportProfile(64, 1))
            .component(DISTRIBUTION_COMPONENT).build(), () -> AdditionalPipes.MOD_ID);
        types.register(id(WATER_PUMP), PipeType.builder(id(WATER_PUMP))
            .fluidProfile(goldFluid.fluidProfile().orElseThrow())
            .component(WATER_PUMP_COMPONENT).build(), () -> AdditionalPipes.MOD_ID);
        types.register(id(OBSIDIAN), PipeType.builder(id(OBSIDIAN))
            .fluidProfile(goldFluid.fluidProfile().orElseThrow())
            .component(OBSIDIAN_COMPONENT).build(), () -> AdditionalPipes.MOD_ID);

        PipeDefinitionBuilder distribution = new PipeDefinitionBuilder();
        distribution.identifier = id(DISTRIBUTION);
        distribution.texPrefixDirect("additionalpipes:block/")
            .texSuffixes("10", "11", "12", "13", "14", "15")
            .logic(DistributionPipeBehaviour::new, DistributionPipeBehaviour::new)
            .flow(PipeApi.flowItems).disableColouring().define();
        PipeDefinitionBuilder pump = new PipeDefinitionBuilder();
        pump.identifier = id(WATER_PUMP);
        pump.texPrefixDirect("additionalpipes:block/").texSuffixes("24")
            .logic(PipeBehaviourStructure::new, PipeBehaviourStructure::new)
            .flow(PipeApi.flowFluids).disableColouring().define();
        PipeDefinitionBuilder obsidian = new PipeDefinitionBuilder();
        obsidian.identifier = id(OBSIDIAN);
        obsidian.texPrefixDirect("additionalpipes:block/").texSuffixes("31")
            .logic(APObsidianFluidBehaviour::new, APObsidianFluidBehaviour::new)
            .flow(PipeApi.flowFluids).disableColouring().define();
        registered = true;
    }

    public static Item createItem(String path) {
        if (!isUtility(path)) throw new IllegalArgumentException("Unknown utility pipe: " + path);
        return BuildCraftApi.service(BuildCraftServices.PIPES).createItem(id(path));
    }
}
