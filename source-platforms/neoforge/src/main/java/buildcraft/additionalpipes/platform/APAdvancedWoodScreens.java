package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.additionalpipes.client.AdvancedWoodScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = AdditionalPipes.MOD_ID, value = Dist.CLIENT)
public final class APAdvancedWoodScreens {
    private APAdvancedWoodScreens() { }

    @SubscribeEvent
    public static void register(RegisterMenuScreensEvent event) {
        event.register(APContentRegistry.ADVANCED_WOOD_MENU.get(), AdvancedWoodScreen::new);
    }
}
