package buildcraft.additionalpipes.platform.chunk;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.additionalpipes.chunkloader.ChunkLoadingHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;

/** Forge's native persistent ticket system. No BCCE internals or reflection. */
public final class PlatformChunkTickets {
    private static boolean initialized;
    private PlatformChunkTickets() { }

    public static synchronized void init() {
        if (initialized) return;
        ForgeChunkManager.setForcedChunkLoadingCallback(AdditionalPipes.MOD_ID, PlatformChunkTickets::validate);
        initialized = true;
    }
    private static void validate(ServerLevel level, ForgeChunkManager.TicketHelper helper) {
        for (BlockPos owner : helper.getBlockTickets().keySet()) {
            if (!ChunkLoadingHandler.isValid(level, owner)) helper.removeAllTickets(owner);
        }
    }
    public static void forceChunk(ServerLevel level, BlockPos owner, ChunkPos chunk, boolean add) {
        ForgeChunkManager.forceChunk(level, AdditionalPipes.MOD_ID, owner, chunk.x, chunk.z, add, true);
    }
}
