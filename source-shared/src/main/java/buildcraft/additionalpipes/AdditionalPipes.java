package buildcraft.additionalpipes;

/**
 * Loader-neutral entry point for the new Additional Pipes integration.
 * Content registration is intentionally not implemented in the scaffolding phase.
 */
public final class AdditionalPipes {
    public static final String MOD_ID = "additionalpipes";

    private AdditionalPipes() {
    }

    /** Called by the loader-specific adapters. */
    public static void initialize() {
        // Future API v2 services and pipe registration are initialized here.
    }
}
