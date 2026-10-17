package buildcraft.additionalpipes.platform.chunk;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.additionalpipes.chunkloader.ChunkLoadingHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

/** NeoForge 26.X ticket controller. */
public final class PlatformChunkTickets {
    private static final TicketController CONTROLLER = new TicketController(
        Identifier.fromNamespaceAndPath(AdditionalPipes.MOD_ID, "chunk_loader"), PlatformChunkTickets::validate);
    private PlatformChunkTickets() { }
    public static void init() { }
    public static void registerTicketController(RegisterTicketControllersEvent event) { event.register(CONTROLLER); }
    private static void validate(ServerLevel level, TicketHelper helper) {
        for (BlockPos owner : helper.getBlockTickets().keySet()) {
            if (!ChunkLoadingHandler.isValid(level, owner)) helper.removeAllTickets(owner);
        }
    }
    public static void forceChunk(ServerLevel level, BlockPos owner, ChunkPos chunk, boolean add) {
        CONTROLLER.forceChunk(level, owner, chunk.x, chunk.z, add, true);
    }
}
