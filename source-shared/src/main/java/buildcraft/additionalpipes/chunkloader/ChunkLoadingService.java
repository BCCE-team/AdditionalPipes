package buildcraft.additionalpipes.chunkloader;

import buildcraft.additionalpipes.APConfiguration;
import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.platform.APDimensionIds;
import buildcraft.additionalpipes.platform.chunk.PlatformChunkTickets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Server-side ticket reconciliation. A block registers its own position when added later;
 * idempotent diffs prevent leaked tickets on radius changes and block removal.
 */
public final class ChunkLoadingService {
    private record Owner(PipeLocation location) { }
    private record Active(ServerLevel world, BlockPos owner, Set<ChunkCoordinates> chunks) { }
    private final Map<Owner, Active> active = new HashMap<>();

    public synchronized void update(ServerLevel level, BlockPos owner, int radius) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(owner, "owner");
        if (radius < 0 || radius > APConfiguration.get().chunkLoaderMaxRadius) {
            throw new IllegalArgumentException("Chunk load radius exceeds configured limit");
        }
        Owner key = key(level, owner);
        Set<ChunkCoordinates> next = ChunkLoadArea.square(owner.getX(), owner.getZ(), radius);
        Active previous = active.get(key);
        Set<ChunkCoordinates> old = previous == null ? Set.of() : previous.chunks();
        // Force first, then unforce. Changing a square must not temporarily unload shared chunks.
        for (ChunkCoordinates pos : next) {
            if (!old.contains(pos)) PlatformChunkTickets.forceChunk(level, owner, new ChunkPos(pos.x(), pos.z()), true);
        }
        for (ChunkCoordinates pos : old) {
            if (!next.contains(pos)) PlatformChunkTickets.forceChunk(level, owner, new ChunkPos(pos.x(), pos.z()), false);
        }
        active.put(key, new Active(level, owner.immutable(), next));
    }

    public synchronized void release(ServerLevel level, BlockPos owner) {
        Active removed = active.remove(key(level, owner));
        if (removed == null) return;
        for (ChunkCoordinates pos : removed.chunks()) {
            PlatformChunkTickets.forceChunk(removed.world(), removed.owner(), new ChunkPos(pos.x(), pos.z()), false);
        }
    }

    public synchronized Set<ChunkCoordinates> allForcedIn(ServerLevel world) {
        Set<ChunkCoordinates> result = new HashSet<>();
        for (Active entry : active.values()) {
            if (entry.world() == world) result.addAll(entry.chunks());
        }
        return Set.copyOf(result);
    }

    public synchronized void releaseAll() {
        for (Active entry : active.values()) {
            for (ChunkCoordinates pos : entry.chunks()) {
                PlatformChunkTickets.forceChunk(entry.world(), entry.owner(), new ChunkPos(pos.x(), pos.z()), false);
            }
        }
        active.clear();
    }

    /** For server replacement after an abrupt shutdown; no ticket calls. */
    public synchronized void clearTracking() { active.clear(); }

    private static Owner key(ServerLevel level, BlockPos pos) {
        return new Owner(new PipeLocation(APDimensionIds.of(level), pos.getX(), pos.getY(), pos.getZ()));
    }
}
