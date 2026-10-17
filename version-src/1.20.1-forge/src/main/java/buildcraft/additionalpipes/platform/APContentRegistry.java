package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.additionalpipes.content.APContent;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class APContentRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AdditionalPipes.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AdditionalPipes.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AdditionalPipes.MOD_ID);
    private static final Map<String, RegistryObject<Item>> ALL_ITEMS = new LinkedHashMap<>();

    static {
        for (APContent.Definition definition : APContent.BLOCKS) {
            String id = definition.id();
            RegistryObject<Block> block = BLOCKS.register(id, () -> new Block(
                BlockBehaviour.Properties.of().strength(0.6F).noOcclusion()));
            ALL_ITEMS.put(id, ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
        }
        ALL_ITEMS.put(APContent.DOG_DEAGGRAVATOR, ITEMS.register(APContent.DOG_DEAGGRAVATOR,
            () -> new Item(new Item.Properties())));
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
