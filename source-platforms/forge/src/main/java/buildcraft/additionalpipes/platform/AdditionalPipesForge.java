package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.additionalpipes.platform.chunk.PlatformChunkTickets;
import buildcraft.additionalpipes.platform.network.PlatformNetwork;
import buildcraft.additionalpipes.service.APServices;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AdditionalPipes.MOD_ID)
public final class AdditionalPipesForge {
    public AdditionalPipesForge() {
        AdditionalPipes.initialize();
        APContentRegistry.register(FMLJavaModLoadingContext.get().getModEventBus());
        PlatformChunkTickets.init();
        PlatformNetwork.init();
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent event) ->
            APServices.serverStarted(event.getServer().getWorldPath(LevelResource.ROOT), event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> APServices.serverStopped());
    }
}
