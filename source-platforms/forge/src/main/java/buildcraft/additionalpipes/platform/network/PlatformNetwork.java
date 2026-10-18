package buildcraft.additionalpipes.platform.network;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.additionalpipes.client.APClientPackets;
import buildcraft.additionalpipes.network.APPacket;
import buildcraft.additionalpipes.network.APProtocol;
import buildcraft.additionalpipes.network.ChunkloadRequestHandler;
import buildcraft.additionalpipes.service.APServices;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Forge 1.19.2/1.20.1 SimpleChannel carrying the validated AP v1 envelope. */
public final class PlatformNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(AdditionalPipes.MOD_ID, "services"),
        () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;
    private PlatformNetwork() { }

    private record ServerPayload(byte[] bytes) { }
    private record ClientPayload(byte[] bytes) { }

    public static synchronized void init() {
        if (registered) return;
        CHANNEL.registerMessage(0, ServerPayload.class,
            (p, buf) -> buf.writeByteArray(p.bytes()),
            buf -> new ServerPayload(buf.readByteArray(APProtocol.MAX_PACKET_BYTES)),
            (p, supplier) -> {
                var context = supplier.get();
                ServerPlayer player = context.getSender();
                if (player != null) context.enqueueWork(() ->
                    ChunkloadRequestHandler.receive(player, p.bytes(), reply -> sendToPlayer(player, reply)));
                context.setPacketHandled(true);
            }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, ClientPayload.class,
            (p, buf) -> buf.writeByteArray(p.bytes()),
            buf -> new ClientPayload(buf.readByteArray(APProtocol.MAX_PACKET_BYTES)),
            (p, supplier) -> {
                var context = supplier.get();
                context.enqueueWork(() -> APClientPackets.receive(p.bytes()));
                context.setPacketHandled(true);
            }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        registered = true;
    }

    public static void sendToServer(APPacket message) {
        requireDirection(message, true);
        CHANNEL.sendToServer(new ServerPayload(encode(message)));
    }

    public static void sendToPlayer(ServerPlayer player, APPacket message) {
        Objects.requireNonNull(player);
        requireDirection(message, false);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientPayload(encode(message)));
    }

    private static void requireDirection(APPacket message, boolean serverbound) {
        if (message.type().serverbound() != serverbound) throw new IllegalArgumentException("Invalid AP packet direction");
    }
    private static byte[] encode(APPacket message) {
        try { return APProtocol.encode(message); }
        catch (IOException e) { throw new IllegalArgumentException("Invalid AP packet", e); }
    }
}
