package buildcraft.additionalpipes.platform.network;

import buildcraft.additionalpipes.AdditionalPipes;
import buildcraft.additionalpipes.client.APClientPackets;
import buildcraft.additionalpipes.network.APPacket;
import buildcraft.additionalpipes.network.APProtocol;
import buildcraft.additionalpipes.network.ChunkloadRequestHandler;
import buildcraft.additionalpipes.service.APServices;
import java.io.IOException;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** NeoForge 26.X payload channel. */
public final class PlatformNetwork {
    private static final CustomPacketPayload.Type<WirePayload> TYPE = new CustomPacketPayload.Type<>(
        Identifier.fromNamespaceAndPath(AdditionalPipes.MOD_ID, "services"));
    public record WirePayload(byte[] bytes) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static final StreamCodec<RegistryFriendlyByteBuf, WirePayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeByteArray(payload.bytes()),
        buf -> new WirePayload(buf.readByteArray(APProtocol.MAX_PACKET_BYTES)));
    private PlatformNetwork() { }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playBidirectional(TYPE, CODEC,
            PlatformNetwork::handlePayload, PlatformNetwork::handlePayload);
    }

    private static void handlePayload(WirePayload payload, IPayloadContext context) {
        if (context.flow() == PacketFlow.SERVERBOUND) {
            handleServerbound(payload, context);
        } else {
            handleClientbound(payload, context);
        }
    }

    private static void handleClientbound(WirePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> APClientPackets.receive(payload.bytes()));
    }

    private static void handleServerbound(WirePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> ChunkloadRequestHandler.receive(
            player, payload.bytes(), reply -> sendToPlayer(player, reply)));
    }

    public static void sendToServer(APPacket message) {
        if (!message.type().serverbound()) throw new IllegalArgumentException("Wrong AP packet direction");
        ClientPacketDistributor.sendToServer(new WirePayload(encode(message)));
    }

    public static void sendToPlayer(ServerPlayer player, APPacket message) {
        Objects.requireNonNull(player);
        if (message.type().serverbound()) throw new IllegalArgumentException("Wrong AP packet direction");
        PacketDistributor.sendToPlayer(player, new WirePayload(encode(message)));
    }

    private static byte[] encode(APPacket message) {
        try { return APProtocol.encode(message); }
        catch (IOException e) { throw new IllegalArgumentException("Invalid AP packet", e); }
    }
}
