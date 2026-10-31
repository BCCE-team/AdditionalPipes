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

public final class APStoragePipes {
    public static final String CLOSED = "pipe_items_closed";
    public static final String GRAVITY = "pipe_items_gravity_feed";
    public static final ResourceLocation CLOSED_COMPONENT = id("closed_pipe_storage");
    public static final ResourceLocation GRAVITY_COMPONENT = id("gravity_feed_extractor");
    private static final Set<String> IDS = Set.of(CLOSED, GRAVITY);
    private static boolean registered;

    private APStoragePipes() { }
    public static boolean isStorage(String path) { return IDS.contains(path); }
    public static ResourceLocation id(String path) {
        return ResourceLocation.tryParse(AdditionalPipes.MOD_ID + ":" + path);
    }

    public static synchronized void register() {
        if (registered) return;
        var components = BuildCraftApi.registry(BuildCraftRegistries.PIPE_COMPONENT_TYPES);
        components.register(CLOSED_COMPONENT, new PipeComponentType<>(CLOSED_COMPONENT,
            ClosedPipeComponent::new, PersistentType.<ClosedPipeComponent, OpaqueData>builder(
                CLOSED_COMPONENT, 1, ClosedPipeComponent.CODEC).build()), () -> AdditionalPipes.MOD_ID);
        components.register(GRAVITY_COMPONENT, new PipeComponentType<>(GRAVITY_COMPONENT,
            GravityFeedComponent::new, null), () -> AdditionalPipes.MOD_ID);
        var types = BuildCraftApi.registry(BuildCraftRegistries.PIPE_TYPES);
        types.register(id(CLOSED), PipeType.builder(id(CLOSED))
            .itemProfile(new ItemTransportProfile(64, 1))
            .component(CLOSED_COMPONENT).build(), () -> AdditionalPipes.MOD_ID);
        types.register(id(GRAVITY), PipeType.builder(id(GRAVITY))
            .itemProfile(new ItemTransportProfile(64, 1))
            .component(GRAVITY_COMPONENT).build(), () -> AdditionalPipes.MOD_ID);

        PipeDefinitionBuilder closed = new PipeDefinitionBuilder();
        closed.identifier = id(CLOSED);
        closed.texPrefixDirect("additionalpipes:block/").texSuffixes("18", "19")
            .logic(ClosedPipeBehaviour::new, ClosedPipeBehaviour::new)
            .flow(PipeApi.flowItems).disableColouring().define();
        PipeDefinitionBuilder gravity = new PipeDefinitionBuilder();
        gravity.identifier = id(GRAVITY);
        gravity.texPrefixDirect("additionalpipes:block/").texSuffixes("32", "33")
            .logic(GravityFeedBehaviour::new, GravityFeedBehaviour::new)
            .flow(PipeApi.flowItems).disableColouring().define();
        registered = true;
    }

    public static Item createItem(String path) {
        if (!IDS.contains(path)) throw new IllegalArgumentException("Unknown storage pipe: " + path);
        return BuildCraftApi.service(BuildCraftServices.PIPES).createItem(id(path));
    }
}
