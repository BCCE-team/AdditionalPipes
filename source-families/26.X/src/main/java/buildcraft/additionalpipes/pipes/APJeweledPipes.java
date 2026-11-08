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
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** Original Jeweled Transport Pipe: six independent 27-slot item filters. */
public final class APJeweledPipes {
    public static final String JEWELED = "pipe_items_jeweled";
    public static final Identifier COMPONENT = id("jeweled_side_filters");
    private static boolean registered;

    private APJeweledPipes() { }

    public static boolean isJeweled(String path) { return JEWELED.equals(path); }

    public static Identifier id(String path) {
        return Identifier.parse(AdditionalPipes.MOD_ID + ":" + path);
    }

    public static synchronized void register() {
        if (registered) return;
        BuildCraftApi.registry(BuildCraftRegistries.PIPE_COMPONENT_TYPES).register(COMPONENT,
            new PipeComponentType<>(COMPONENT, JeweledPipeComponent::new,
                PersistentType.<JeweledPipeComponent, OpaqueData>builder(
                    COMPONENT, 1, JeweledPipeComponent.CODEC).build()), () -> AdditionalPipes.MOD_ID);
        BuildCraftApi.registry(BuildCraftRegistries.PIPE_TYPES).register(id(JEWELED),
            PipeType.builder(id(JEWELED))
                .itemProfile(new ItemTransportProfile(64, 1))
                .component(COMPONENT).build(), () -> AdditionalPipes.MOD_ID);

        PipeDefinitionBuilder jeweled = new PipeDefinitionBuilder();
        jeweled.identifier = id(JEWELED);
        // 4.7.7 textures: central index 34; down/up/north/south/west 35..39; east 34.
        jeweled.texPrefixDirect("additionalpipes:block/")
            .texSuffixes("34", "35", "36", "37", "38", "39")
            .logic(JeweledPipeBehaviour::new, JeweledPipeBehaviour::new)
            .flow(PipeApi.flowItems).disableColouring().define();
        registered = true;
    }

    public static Item createItem(String path) {
        if (!isJeweled(path)) throw new IllegalArgumentException("Unknown jeweled pipe: " + path);
        return BuildCraftApi.service(BuildCraftServices.PIPES).createItem(id(path));
    }
}
