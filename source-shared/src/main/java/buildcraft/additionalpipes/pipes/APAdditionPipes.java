package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftRegistries;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.pipe.ItemTransportProfile;
import buildcraft.api.v2.pipe.PipeComponentType;
import buildcraft.api.v2.pipe.PipeType;
import buildcraft.transport.internal.pipe.PipeApi;
import buildcraft.transport.internal.pipe.PipeDefinition.PipeDefinitionBuilder;
import buildcraft.transport.pipe.behaviour.PipeBehaviourStructure;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Addition Transport Pipe from AdditionalPipesBC 4.7.7, not a decorative block. */
public final class APAdditionPipes {
    public static final String ADDITION = "pipe_items_addition";
    public static final ResourceLocation COMPONENT = id("addition_inventory_routing");
    private static boolean registered;

    private APAdditionPipes() { }

    public static boolean isAddition(String path) { return ADDITION.equals(path); }
    private static ResourceLocation id(String path) {
        return ResourceLocation.tryParse(AdditionalPipes.MOD_ID + ":" + path);
    }

    public static synchronized void register() {
        if (registered) return;
        var components = BuildCraftApi.registry(BuildCraftRegistries.PIPE_COMPONENT_TYPES);
        components.register(COMPONENT,
            new PipeComponentType<>(COMPONENT, AdditionPipeComponent::new, null),
            () -> AdditionalPipes.MOD_ID);
        var types = BuildCraftApi.registry(BuildCraftRegistries.PIPE_TYPES);
        types.register(id(ADDITION), PipeType.builder(id(ADDITION))
            .itemProfile(new ItemTransportProfile(64, 1))
            .component(COMPONENT).build(), () -> AdditionalPipes.MOD_ID);

        PipeDefinitionBuilder pipe = new PipeDefinitionBuilder();
        pipe.identifier = id(ADDITION);
        // Original PipeItemsAddition uses texture index 4 on every face.
        pipe.texPrefixDirect("additionalpipes:block/").texSuffixes("4")
            .logic(PipeBehaviourStructure::new, PipeBehaviourStructure::new)
            .flow(PipeApi.flowItems).disableColouring().define();
        registered = true;
    }

    public static Item createItem(String path) {
        if (!isAddition(path)) throw new IllegalArgumentException("Unknown addition pipe: " + path);
        return BuildCraftApi.service(BuildCraftServices.PIPES).createItem(id(path));
    }
}
