package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.additionalpipes.content.APContent;
import buildcraft.additionalpipes.pipes.APSwitchPipes;
import buildcraft.additionalpipes.pipes.APTeleportPipes;
import buildcraft.additionalpipes.pipes.APInsertionPipes;
import buildcraft.additionalpipes.pipes.AdvancedWoodMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AdditionalPipes.MOD_ID);
    public static final Supplier<MenuType<AdvancedWoodMenu>> ADVANCED_WOOD_MENU =
        MENUS.register("advanced_wood", () -> new MenuType<>(AdvancedWoodMenu::new, FeatureFlags.DEFAULT_FLAGS));
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AdditionalPipes.MOD_ID);
    private static final Map<String, Supplier<? extends Item>> ALL_ITEMS = new LinkedHashMap<>();

    static {
        for (APContent.Definition definition : APContent.BLOCKS) {
            String id = definition.id();
            if (APSwitchPipes.isSwitch(id) || APTeleportPipes.isTeleport(id) || APInsertionPipes.isInsertion(id)) {
                ALL_ITEMS.put(id, ITEMS.register(id, () -> APSwitchPipes.isSwitch(id) ? APSwitchPipes.createItem(id) : APTeleportPipes.isTeleport(id) ? APTeleportPipes.createItem(id) : APInsertionPipes.createItem(id)));
                continue;
            }
            Supplier<? extends Block> block = BLOCKS.register(id, () -> new Block(
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
        MENUS.register(bus);
        TABS.register(bus);
    }
}
