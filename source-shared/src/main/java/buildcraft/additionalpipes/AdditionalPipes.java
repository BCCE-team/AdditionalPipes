package buildcraft.additionalpipes;

import buildcraft.additionalpipes.service.APServices;
import java.nio.file.Path;

/** Loader-neutral entry point. Runtime service setup is independent of content registration. */
public final class AdditionalPipes {
    public static final String MOD_ID = "additionalpipes";
    private AdditionalPipes() { }

    /** Called by Forge/NeoForge constructors. */
    public static void initialize() {
        APServices.initialize(Path.of("config"));
    }
}
