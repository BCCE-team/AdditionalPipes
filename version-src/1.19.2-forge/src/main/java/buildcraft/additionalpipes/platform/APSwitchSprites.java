package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.AdditionalPipes;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdditionalPipes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class APSwitchSprites {
    private APSwitchSprites() { }

    @SubscribeEvent
    public static void registerSprites(TextureStitchEvent.Pre event) {
        if (!InventoryMenu.BLOCK_ATLAS.equals(event.getAtlas().location())) return;
        for (String texture : new String[]{"16", "17", "20", "21", "22", "23"}) {
            event.addSprite(new ResourceLocation(AdditionalPipes.MOD_ID, "block/" + texture));
        }
    }
}
