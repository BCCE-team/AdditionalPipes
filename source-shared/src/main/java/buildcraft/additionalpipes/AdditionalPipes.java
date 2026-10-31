package buildcraft.additionalpipes;

import buildcraft.additionalpipes.service.APServices;
import buildcraft.additionalpipes.pipes.APSwitchPipes;
import buildcraft.additionalpipes.pipes.APTeleportPipes;
import buildcraft.additionalpipes.pipes.APInsertionPipes;
import buildcraft.additionalpipes.pipes.APStoragePipes;
import buildcraft.additionalpipes.pipes.APUtilityPipes;
import java.nio.file.Path;
import net.minecraft.world.item.Item;

/** Loader-neutral entry point. Services and content have separate registration lifecycles. */
public final class AdditionalPipes {
    public static final String MOD_ID = "additionalpipes";
    private static boolean pipeTypesRegistered;

    private AdditionalPipes() { }

    /** Called by loader constructors; BCCE's pipe registry may not exist yet. */
    public static void initialize() {
        APServices.initialize(Path.of("config"));
    }

    /**
     * Called only from the platform's deferred pipe item suppliers (ITEM registration event).
     * All mod constructors, including BCCE transport, have finished by then, while BCCE API
     * registration is still open. Do not call from an @Mod constructor or common setup event.
     */
    public static synchronized void ensurePipeTypesRegistered() {
        if (pipeTypesRegistered) return;
        APSwitchPipes.register();
        APTeleportPipes.register();
        APInsertionPipes.register();
        APStoragePipes.register();
        APUtilityPipes.register();
        pipeTypesRegistered = true;
    }

    public static synchronized Item createPipeItem(String path) {
        if (!APSwitchPipes.isSwitch(path) && !APTeleportPipes.isTeleport(path)
            && !APInsertionPipes.isInsertion(path) && !APStoragePipes.isStorage(path)
            && !APUtilityPipes.isUtility(path)) {
            throw new IllegalArgumentException("Unknown Additional Pipes pipe: " + path);
        }
        ensurePipeTypesRegistered();
        if (APSwitchPipes.isSwitch(path)) return APSwitchPipes.createItem(path);
        if (APTeleportPipes.isTeleport(path)) return APTeleportPipes.createItem(path);
        if (APInsertionPipes.isInsertion(path)) return APInsertionPipes.createItem(path);
        if (APStoragePipes.isStorage(path)) return APStoragePipes.createItem(path);
        return APUtilityPipes.createItem(path);
    }
}
