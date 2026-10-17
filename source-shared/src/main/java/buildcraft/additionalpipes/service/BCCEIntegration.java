package buildcraft.additionalpipes.service;

import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.additionalpipes.utils.Log;

/** Checks the public BCCE v2 runtime; never touches buildcraft.lib.internal classes. */
public final class BCCEIntegration {
    private BCCEIntegration() { }

    public static void verifyRuntime() {
        var runtime = BuildCraftApi.runtime();
        var version = runtime.version();
        if (version.major() != 2 || version.minor() < 1) {
            throw new IllegalStateException("Additional Pipes requires BCCE API 2.1+, got " + version);
        }
        if (runtime.service(BuildCraftServices.PIPES).isEmpty()) {
            throw new IllegalStateException("BCCE API v2 PipeService is unavailable");
        }
        Log.info("BCCE public API " + version + " is available for Additional Pipes");
    }
}
