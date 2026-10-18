package buildcraft.additionalpipes.network;

import buildcraft.additionalpipes.APConfiguration;
import buildcraft.additionalpipes.chunkloader.ChunkLoadViewDataProxy;
import buildcraft.additionalpipes.platform.APDimensionIds;
import buildcraft.additionalpipes.service.APServices;
import buildcraft.additionalpipes.utils.Log;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

/** Authenticated, rate-limited chunk-border queries; no arbitrary chunk loading. */
public final class ChunkloadRequestHandler {
    private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();
    private static final int MIN_INTERVAL_TICKS = 20;
    private ChunkloadRequestHandler() { }

    /** Must run on the server thread after the transport has identified ServerPlayer. */
    public static void receive(ServerPlayer player, byte[] payload, Consumer<APPacket> reply) {
        final APPacket packet;
        try { packet = APProtocol.decode(payload, true); }
        catch (IOException | IllegalArgumentException e) {
            Log.warn("Rejected malformed AP client packet: " + e.getMessage());
            return;
        }
        if (!(packet instanceof APPacket.ChunkloadRequest)) {
            APServices.packets().receive(payload, true, player.getUUID());
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Long previous = LAST_REQUEST.get(player.getUUID());
        if (previous != null && now >= previous && now - previous < MIN_INTERVAL_TICKS) return;
        LAST_REQUEST.put(player.getUUID(), now);
        var near = ChunkLoadViewDataProxy.chunksNear(
            player.blockPosition().getX(), player.blockPosition().getZ(),
            APConfiguration.get().chunkSightRange,
            APServices.chunkLoading().allForcedIn(level));
        reply.accept(new APPacket.ChunkloadData(
            APDimensionIds.of(level), near));
    }

    public static void reset() { LAST_REQUEST.clear(); }
}
