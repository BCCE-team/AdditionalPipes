package buildcraft.additionalpipes.service;

import buildcraft.additionalpipes.APConfiguration;
import buildcraft.additionalpipes.api.TeleportManagerBase;
import buildcraft.additionalpipes.chunkloader.ChunkLoadViewDataProxy;
import buildcraft.additionalpipes.chunkloader.ChunkLoadingService;
import buildcraft.additionalpipes.network.PacketHandler;
import buildcraft.additionalpipes.network.APMessageType;
import buildcraft.additionalpipes.network.APPacket;
import buildcraft.additionalpipes.network.ChunkloadRequestHandler;
import buildcraft.additionalpipes.pipes.TeleportManager;
import buildcraft.additionalpipes.utils.Log;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Service lifetime is tied to a logical Minecraft server, not to client/server classloading. */
public final class APServices {
    private static final TeleportManager TELEPORTS = new TeleportManager();
    private static final ChunkLoadingService CHUNKS = new ChunkLoadingService();
    private static final ChunkLoadViewDataProxy CHUNK_VIEW = new ChunkLoadViewDataProxy();
    private static final PacketHandler PACKETS = new PacketHandler();
    private static Path saveRoot;
    private static boolean initialized;

    private APServices() { }
    public static TeleportManagerBase teleports() { return TELEPORTS; }
    public static ChunkLoadingService chunkLoading() { return CHUNKS; }
    public static ChunkLoadViewDataProxy chunkView() { return CHUNK_VIEW; }
    public static PacketHandler packets() { return PACKETS; }

    public static synchronized void initialize(Path configDirectory) {
        if (initialized) return;
        try { APConfiguration.load(Objects.requireNonNull(configDirectory, "configDirectory")); }
        catch (IOException e) { throw new IllegalStateException("Cannot load Additional Pipes config", e); }
        PACKETS.registerClientHandler(APMessageType.CHUNKLOAD_DATA, packet -> {
            APPacket.ChunkloadData data = (APPacket.ChunkloadData) packet;
            CHUNK_VIEW.receive(data.dimension(), data.chunks());
        });
        initialized = true;
        Log.info("Additional Pipes service layer initialized (no gameplay content registered)");
    }

    public static synchronized void serverStarted(Path root) {
        Objects.requireNonNull(root, "root");
        if (saveRoot != null) serverStopped();
        BCCEIntegration.verifyRuntime();
        TELEPORTS.reset();
        CHUNKS.clearTracking();
        CHUNK_VIEW.reset();
        ChunkloadRequestHandler.reset();
        saveRoot = root.toAbsolutePath().normalize();
        try { TeleportFrequencyStore.load(saveRoot, TELEPORTS); }
        catch (IOException e) { Log.error("Failed to load teleport frequency names", e); }
    }

    public static synchronized void serverStopped() {
        if (saveRoot != null) {
            try { TeleportFrequencyStore.save(saveRoot, TELEPORTS); }
            catch (IOException e) { Log.error("Failed to save teleport frequency names", e); }
        }
        // Explicitly release remaining tickets; weak lifecycle guarantees are not sufficient.
        CHUNKS.releaseAll();
        TELEPORTS.reset();
        CHUNK_VIEW.reset();
        ChunkloadRequestHandler.reset();
        saveRoot = null;
    }
}
