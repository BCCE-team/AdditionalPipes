package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.additionalpipes.platform.chunk.PlatformChunkTickets;
import buildcraft.additionalpipes.platform.network.PlatformNetwork;
import buildcraft.additionalpipes.service.APServices;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(AdditionalPipes.MOD_ID)
public final class AdditionalPipesNeoForge {
    public AdditionalPipesNeoForge(IEventBus modBus) {
        AdditionalPipes.initialize();
        APContentRegistry.register(modBus);
        modBus.addListener(PlatformChunkTickets::registerTicketController);
        modBus.addListener(PlatformNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) ->
            APServices.serverStarted(event.getServer().getWorldPath(LevelResource.ROOT), event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> APServices.serverStopped());
    }
}
