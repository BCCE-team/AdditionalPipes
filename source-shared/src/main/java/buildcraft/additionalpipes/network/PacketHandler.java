package buildcraft.additionalpipes.network;

import buildcraft.additionalpipes.utils.Log;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * A bounded decoder and authoritative handler registry. The physical network adapter
 * MUST schedule receive() on the correct logical main thread, never the Netty thread.
 * Mutating client-to-server packets are rejected until a content module installs an
 * explicit per-packet authorization predicate. No arbitrary block edits are permitted.
 */
public final class PacketHandler {
    private record ServerHandler(BiPredicate<UUID, APPacket> authorization, BiConsumer<UUID, APPacket> apply) { }
    private final EnumMap<APMessageType, ServerHandler> server = new EnumMap<>(APMessageType.class);
    private final EnumMap<APMessageType, Consumer<APPacket>> client = new EnumMap<>(APMessageType.class);

    public synchronized void registerServerHandler(APMessageType type,
        BiPredicate<UUID, APPacket> authorized, BiConsumer<UUID, APPacket> apply) {
        if (!type.serverbound()) throw new IllegalArgumentException("Not a serverbound AP packet: " + type);
        server.put(type, new ServerHandler(Objects.requireNonNull(authorized), Objects.requireNonNull(apply)));
    }

    public synchronized void registerClientHandler(APMessageType type, Consumer<APPacket> apply) {
        if (type.serverbound()) throw new IllegalArgumentException("Not a clientbound AP packet: " + type);
        client.put(type, Objects.requireNonNull(apply));
    }

    public void receive(byte[] bytes, boolean serverbound, UUID sender) {
        final APPacket packet;
        try { packet = APProtocol.decode(bytes, serverbound); }
        catch (IOException | IllegalArgumentException e) {
            Log.warn("Discarding invalid Additional Pipes packet: " + e.getMessage());
            return;
        }
        if (serverbound) {
            if (sender == null) return;
            ServerHandler handler;
            synchronized (this) { handler = server.get(packet.type()); }
            if (handler != null && handler.authorization().test(sender, packet)) {
                handler.apply().accept(sender, packet);
            }
        } else {
            Consumer<APPacket> handler;
            synchronized (this) { handler = client.get(packet.type()); }
            if (handler != null) handler.accept(packet);
        }
    }

    public synchronized void clearHandlers() { server.clear(); client.clear(); }
}
