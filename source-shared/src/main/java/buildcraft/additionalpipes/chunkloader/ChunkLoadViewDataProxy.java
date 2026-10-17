package buildcraft.additionalpipes.chunkloader;

import buildcraft.additionalpipes.APConfiguration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Headless, thread-safe service behind the old boundary-visualization feature. The
 * actual lasers/keybind/rendering are deliberately deferred with the client content.
 */
public final class ChunkLoadViewDataProxy {
    public static final int MAX_SIGHT_RANGE = 31;
    public static final int AUTO_REFRESH_TICKS = 100;
    private String clientDimension = "";
    private List<ChunkCoordinates> clientChunks = List.of();
    private boolean visible;
    private long lastRefreshTick = Long.MIN_VALUE;

    /** Filter already-known ticket positions; never scans/loads absent world chunks. */
    public static List<ChunkCoordinates> chunksNear(
        int playerBlockX, int playerBlockZ, int radius, Collection<ChunkCoordinates> forcedChunks) {
        Objects.requireNonNull(forcedChunks, "forcedChunks");
        int r = Math.max(0, Math.min(MAX_SIGHT_RANGE, radius));
        int cx = Math.floorDiv(playerBlockX, 16);
        int cz = Math.floorDiv(playerBlockZ, 16);
        TreeSet<ChunkCoordinates> matching = new TreeSet<>();
        for (ChunkCoordinates position : forcedChunks) {
            if (Math.abs((long) position.x() - cx) <= r && Math.abs((long) position.z() - cz) <= r) {
                matching.add(position);
            }
        }
        return List.copyOf(matching);
    }

    /** Returns true only when the client's displayed set actually changed. */
    public synchronized boolean receive(String dimension, Collection<ChunkCoordinates> chunks) {
        Objects.requireNonNull(dimension, "dimension");
        if (chunks.size() > 4096) throw new IllegalArgumentException("Too many visible chunks");
        List<ChunkCoordinates> sorted = new ArrayList<>(new TreeSet<>(chunks));
        if (clientDimension.equals(dimension) && clientChunks.equals(sorted)) return false;
        clientDimension = dimension;
        clientChunks = List.copyOf(sorted);
        return true;
    }

    public synchronized List<ChunkCoordinates> visibleChunks() { return clientChunks; }
    public synchronized String dimension() { return clientDimension; }
    public synchronized boolean visible() { return visible; }
    public synchronized void setVisible(boolean value) { visible = value; }

    /** Monotonic ticks supplied by the client adapter. Never send a request per world tick. */
    public synchronized boolean shouldRequest(long clientTick) {
        if (!visible || !APConfiguration.get().chunkSightAutorefresh) return false;
        if (lastRefreshTick != Long.MIN_VALUE && clientTick >= lastRefreshTick
            && clientTick - lastRefreshTick < AUTO_REFRESH_TICKS) return false;
        lastRefreshTick = clientTick;
        return true;
    }

    public synchronized void reset() {
        clientDimension = "";
        clientChunks = List.of();
        visible = false;
        lastRefreshTick = Long.MIN_VALUE;
    }
}
