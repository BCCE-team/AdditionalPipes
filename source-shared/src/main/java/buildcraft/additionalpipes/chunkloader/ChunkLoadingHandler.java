package buildcraft.additionalpipes.chunkloader;

import java.util.Objects;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Ticket revalidation: only an actual registered AP loader may retain persisted tickets. */
public final class ChunkLoadingHandler {
    private static volatile BiPredicate<ServerLevel, BlockPos> isActiveLoader = (level, pos) -> false;
    private ChunkLoadingHandler() { }

    /** Installed once the AP chunk-loader block entity has been ported. */
    public static void setOwnerValidator(BiPredicate<ServerLevel, BlockPos> predicate) {
        isActiveLoader = Objects.requireNonNull(predicate, "predicate");
    }
    public static boolean isValid(ServerLevel level, BlockPos owner) {
        // Do not resolve/force unloaded chunks here. The loader's validator handles that.
        return isActiveLoader.test(level, owner);
    }
}
