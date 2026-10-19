package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.additionalpipes.content.APContent;
import buildcraft.additionalpipes.pipes.APSwitchPipes;
import buildcraft.additionalpipes.pipes.APTeleportPipes;
import buildcraft.additionalpipes.pipes.APInsertionPipes;
import buildcraft.additionalpipes.pipes.AdvancedWoodMenu;
import net.minecraft.world.inventory.MenuType;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Material;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class APContentRegistry {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AdditionalPipes.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AdditionalPipes.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, AdditionalPipes.MOD_ID);
    public static final RegistryObject<MenuType<AdvancedWoodMenu>> ADVANCED_WOOD_MENU =
        MENUS.register("advanced_wood", () -> new MenuType<>(AdvancedWoodMenu::new));
    private static final Map<String, RegistryObject<Item>> ALL_ITEMS = new LinkedHashMap<>();
    private static final CreativeModeTab TAB = new CreativeModeTab("additionalpipes") {
        @Override
        public ItemStack makeIcon() {
            return ALL_ITEMS.get("pipe_items_teleport").get().getDefaultInstance();
        }
    };

    static {
        for (APContent.Definition definition : APContent.BLOCKS) {
            String id = definition.id();
            if (APSwitchPipes.isSwitch(id) || APTeleportPipes.isTeleport(id) || APInsertionPipes.isInsertion(id)) {
                ALL_ITEMS.put(id, ITEMS.register(id, () -> APSwitchPipes.isSwitch(id) ? APSwitchPipes.createItem(id) : APTeleportPipes.isTeleport(id) ? APTeleportPipes.createItem(id) : APInsertionPipes.createItem(id)));
                continue;
            }
            RegistryObject<Block> block = BLOCKS.register(id, () -> new Block(
                BlockBehaviour.Properties.of(Material.GLASS).strength(0.6F).noOcclusion()));
            ALL_ITEMS.put(id, ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties().tab(TAB))));
        }
        ALL_ITEMS.put(APContent.DOG_DEAGGRAVATOR, ITEMS.register(APContent.DOG_DEAGGRAVATOR,
            () -> new Item(new Item.Properties().tab(TAB))));
    }

    private APContentRegistry() { }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
    }
}
