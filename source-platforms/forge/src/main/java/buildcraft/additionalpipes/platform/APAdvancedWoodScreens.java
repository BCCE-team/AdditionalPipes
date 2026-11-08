package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.additionalpipes.client.AdvancedWoodScreen;
import buildcraft.additionalpipes.client.ClosedPipeScreen;
import buildcraft.additionalpipes.client.JeweledScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = AdditionalPipes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class APAdvancedWoodScreens {
    private APAdvancedWoodScreens() { }

    @SubscribeEvent
    public static void register(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(APContentRegistry.ADVANCED_WOOD_MENU.get(), AdvancedWoodScreen::new);
            MenuScreens.register(APContentRegistry.CLOSED_PIPE_MENU.get(), ClosedPipeScreen::new);
            MenuScreens.register(APContentRegistry.JEWELED_MENU.get(), JeweledScreen::new);
        });
    }
}
