package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.additionalpipes.content.APContent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class APContentRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, AdditionalPipes.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, AdditionalPipes.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AdditionalPipes.MOD_ID);
    private static final Map<String, Supplier<? extends Item>> ALL_ITEMS = new LinkedHashMap<>();

    static {
        for (APContent.Definition definition : APContent.BLOCKS) {
            String id = definition.id();
            Supplier<? extends Block> block = BLOCKS.register(id, () -> new Block(
                BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath(AdditionalPipes.MOD_ID, id)))
                    .strength(0.6F).noOcclusion()));
            ALL_ITEMS.put(id, ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties().setId(
                ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AdditionalPipes.MOD_ID, id))))));
        }
        ALL_ITEMS.put(APContent.DOG_DEAGGRAVATOR, ITEMS.register(APContent.DOG_DEAGGRAVATOR,
            () -> new Item(new Item.Properties().setId(
                ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(AdditionalPipes.MOD_ID, APContent.DOG_DEAGGRAVATOR))))));
        TABS.register("additional_pipes", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.additionalpipes"))
            .icon(() -> new ItemStack(ALL_ITEMS.get("pipe_items_teleport").get()))
            .displayItems((parameters, output) -> ALL_ITEMS.values().forEach(entry -> output.accept(entry.get())))
            .build());
    }

    private APContentRegistry() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
    }
}
