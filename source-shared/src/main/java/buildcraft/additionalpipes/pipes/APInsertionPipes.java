package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftRegistries;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.persistence.OpaqueData;
import buildcraft.api.v2.persistence.PersistentType;
import buildcraft.api.v2.pipe.ItemTransportProfile;
import buildcraft.api.v2.pipe.PipeComponentType;
import buildcraft.api.v2.pipe.PipeType;
import buildcraft.transport.internal.pipe.PipeApi;
import buildcraft.transport.internal.pipe.PipeDefinition.PipeDefinitionBuilder;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class APInsertionPipes {
    public static final String ADVANCED_WOOD = "pipe_items_adv_wood";
    public static final String PRIORITY = "pipe_items_priority";
    public static final ResourceLocation ADVANCED_COMPONENT = id("advanced_wood_filter");
    public static final ResourceLocation PRIORITY_COMPONENT = id("priority_insertion_routing");
    private static final Set<String> IDS = Set.of(ADVANCED_WOOD, PRIORITY);
    private static boolean registered;

    private APInsertionPipes() { }

    public static boolean isInsertion(String path) { return IDS.contains(path); }

    public static ResourceLocation id(String path) {
        return ResourceLocation.tryParse(AdditionalPipes.MOD_ID + ":" + path);
    }

    public static synchronized void register() {
        if (registered) return;
        var components = BuildCraftApi.registry(BuildCraftRegistries.PIPE_COMPONENT_TYPES);
        components.register(ADVANCED_COMPONENT, new PipeComponentType<>(ADVANCED_COMPONENT,
            AdvancedWoodComponent::new, PersistentType.<AdvancedWoodComponent, OpaqueData>builder(
                ADVANCED_COMPONENT, 1, AdvancedWoodComponent.CODEC).build()), () -> AdditionalPipes.MOD_ID);
        components.register(PRIORITY_COMPONENT, new PipeComponentType<>(PRIORITY_COMPONENT,
            PriorityInsertionComponent::new, PersistentType.<PriorityInsertionComponent, OpaqueData>builder(
                PRIORITY_COMPONENT, 1, PriorityInsertionComponent.CODEC).build()), () -> AdditionalPipes.MOD_ID);

        var types = BuildCraftApi.registry(BuildCraftRegistries.PIPE_TYPES);
        types.register(id(ADVANCED_WOOD), PipeType.builder(id(ADVANCED_WOOD))
            .itemProfile(new ItemTransportProfile(64, 1))
            .component(ADVANCED_COMPONENT).build(), () -> AdditionalPipes.MOD_ID);
        types.register(id(PRIORITY), PipeType.builder(id(PRIORITY))
            .itemProfile(new ItemTransportProfile(64, 1))
            .component(PRIORITY_COMPONENT).build(), () -> AdditionalPipes.MOD_ID);

        PipeDefinitionBuilder advancedWood = new PipeDefinitionBuilder();
        advancedWood.identifier = id(ADVANCED_WOOD);
        advancedWood.texPrefixDirect("additionalpipes:block/")
            .texSuffixes("6", "7")
            .logic(APAdvancedWoodBehaviour::new, APAdvancedWoodBehaviour::new)
            .flow(PipeApi.flowItems)
            .disableColouring()
            .define();
        PipeDefinitionBuilder priority = new PipeDefinitionBuilder();
        priority.identifier = id(PRIORITY);
        priority.texPrefixDirect("additionalpipes:block/")
            .texSuffixes("25", "26", "27", "28", "29", "30")
            .logic(APPriorityBehaviour::new, APPriorityBehaviour::new)
            .flow(PipeApi.flowItems)
            .disableColouring()
            .define();
        registered = true;
    }

    public static Item createItem(String path) {
        if (!IDS.contains(path)) throw new IllegalArgumentException("Unknown insertion pipe: " + path);
        return BuildCraftApi.service(BuildCraftServices.PIPES).createItem(id(path));
    }
}
