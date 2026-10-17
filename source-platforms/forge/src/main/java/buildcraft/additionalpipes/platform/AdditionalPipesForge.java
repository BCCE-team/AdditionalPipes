package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.AdditionalPipes;
import net.minecraftforge.fml.common.Mod;

/** Legacy Forge 1.19.2/1.20.1 adapter. */
@Mod(AdditionalPipes.MOD_ID)
public final class AdditionalPipesForge {
    public AdditionalPipesForge() {
        AdditionalPipes.initialize();
    }
}
