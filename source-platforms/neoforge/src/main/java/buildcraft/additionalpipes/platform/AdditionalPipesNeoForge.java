package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.AdditionalPipes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** NeoForge 1.21.X/26.X adapter. Keep loader code out of shared sources. */
@Mod(AdditionalPipes.MOD_ID)
public final class AdditionalPipesNeoForge {
    public AdditionalPipesNeoForge(IEventBus modBus) {
        AdditionalPipes.initialize();
        // Registries and mod-lifecycle handlers will be attached to modBus later.
    }
}
