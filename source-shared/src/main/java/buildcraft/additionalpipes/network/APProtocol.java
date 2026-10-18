package buildcraft.additionalpipes.network;

import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.chunkloader.ChunkCoordinates;
import buildcraft.additionalpipes.filter.SideFilterData;
import buildcraft.additionalpipes.utils.NetworkUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AP-specific, bounded wire format. Not compatible with the 1.8 SimpleNetworkWrapper.
 * Checks version, direction, sizes and trailing bytes before server-thread dispatch.
 */
public final class APProtocol {
    public static final int VERSION = 2;
    public static final int MAX_PACKET_BYTES = 32768;
    private APProtocol() { }

    public static byte[] encode(APPacket packet) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(VERSION);
        out.writeByte(packet.type().id());
        switch (packet.type()) {
            case DISTRIBUTION_UPDATE, PRIORITY_UPDATE -> {
                APPacket.ValueUpdate message = (APPacket.ValueUpdate) packet;
                location(out, message.position());
                out.writeByte(message.index());
                out.writeInt(message.value());
            }
            case TELEPORT_UPDATE -> {
                APPacket.TeleportUpdate message = (APPacket.TeleportUpdate) packet;
                location(out, message.position());
                out.writeInt(message.frequency());
                out.writeBoolean(message.isPublic());
                out.writeByte(message.operation());
            }
            case TELEPORT_VIEW -> {
                APPacket.TeleportView message = (APPacket.TeleportView) packet;
                location(out, message.position());
                out.writeInt(message.frequency());
                out.writeByte(message.operation());
                out.writeBoolean(message.isPublic());
                out.writeBoolean(message.ownerUUID() != null);
                if (message.ownerUUID() != null) {
                    out.writeLong(message.ownerUUID().getMostSignificantBits());
                    out.writeLong(message.ownerUUID().getLeastSignificantBits());
                }
                out.writeUTF(message.ownerName());
                out.writeUTF(message.medium());
                out.writeInt(message.connectedCount());
                out.writeByte(message.connections().size());
                for (PipeLocation connected : message.connections()) location(out, connected);
                out.writeBoolean(message.open());
            }
            case TELEPORT_VIEW_REQUEST -> location(out, ((APPacket.TeleportViewRequest) packet).position());
            case ADVANCED_WOOD_UPDATE -> location(out, ((APPacket.AdvancedWoodUpdate) packet).position());
            case TELEPORT_DATA -> {
                APPacket.TeleportData message = (APPacket.TeleportData) packet;
                location(out, message.position());
                out.writeShort(message.locations().length);
                for (int item : message.locations()) out.writeInt(item);
                out.writeBoolean(message.owner() != null);
                if (message.owner() != null) {
                    out.writeLong(message.owner().getMostSignificantBits());
                    out.writeLong(message.owner().getLeastSignificantBits());
                }
                out.writeUTF(message.ownerName());
            }
            case CHUNKLOAD_REQUEST -> { }
            case CHUNKLOAD_DATA -> {
                APPacket.ChunkloadData message = (APPacket.ChunkloadData) packet;
                out.writeUTF(message.dimension());
                out.writeShort(message.chunks().size());
                for (ChunkCoordinates position : message.chunks()) {
                    out.writeInt(position.x());
                    out.writeInt(position.z());
                }
            }
            case JEWELED_UPDATE_SERVER -> {
                APPacket.JeweledServerUpdate message = (APPacket.JeweledServerUpdate) packet;
                location(out, message.position());
                out.writeByte(message.face());
                out.writeBoolean(message.options().acceptUnsortedItems());
                out.writeBoolean(message.options().matchNBT());
                out.writeBoolean(message.options().matchMetadata());
            }
            case JEWELED_UPDATE_CLIENT -> {
                APPacket.JeweledClientUpdate message = (APPacket.JeweledClientUpdate) packet;
                location(out, message.position());
                boolean[] accept = new boolean[6], nbt = new boolean[6], meta = new boolean[6];
                for (int i = 0; i < 6; i++) {
                    var options = message.sides().get(i);
                    accept[i] = options.acceptUnsortedItems();
                    nbt[i] = options.matchNBT();
                    meta[i] = options.matchMetadata();
                }
                NetworkUtils.writeBooleanArray(out, accept);
                NetworkUtils.writeBooleanArray(out, nbt);
                NetworkUtils.writeBooleanArray(out, meta);
            }
        }
        out.flush();
        if (bytes.size() > MAX_PACKET_BYTES) throw new IOException("Additional Pipes packet too large");
        return bytes.toByteArray();
    }

    public static APPacket decode(byte[] bytes, boolean serverbound) throws IOException {
        if (bytes == null || bytes.length < 2 || bytes.length > MAX_PACKET_BYTES) {
            throw new IOException("Invalid Additional Pipes packet length");
        }
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        int protocol = in.readUnsignedByte();
        if (protocol != VERSION) throw new IOException("Additional Pipes protocol version mismatch: " + protocol);
        APMessageType type = APMessageType.byId(in.readUnsignedByte());
        if (type.serverbound() != serverbound) throw new IOException("Wrong packet direction: " + type);
        APPacket decoded;
        switch (type) {
            case DISTRIBUTION_UPDATE, PRIORITY_UPDATE -> {
                PipeLocation location = location(in);
                byte face = in.readByte();
                int value = in.readInt();
                decoded = new APPacket.ValueUpdate(type, location, face, value);
            }
            case TELEPORT_UPDATE -> {
                PipeLocation location = location(in);
                int frequency = in.readInt();
                boolean publicAccess = in.readBoolean();
                byte operation = in.readByte();
                decoded = new APPacket.TeleportUpdate(location, frequency, publicAccess, operation);
            }
            case TELEPORT_VIEW -> {
                PipeLocation pos = location(in);
                int frequency = in.readInt();
                byte operation = in.readByte();
                boolean publicAccess = in.readBoolean();
                UUID ownerUUID = in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null;
                String ownerName = limitedUtf(in, 64);
                String medium = limitedUtf(in, 24);
                int connectedCount = in.readInt();
                int count = in.readUnsignedByte();
                if (count > 3) throw new IOException("Invalid connected-pipe count");
                List<PipeLocation> linked = new ArrayList<>(count);
                for (int i = 0; i < count; i++) linked.add(location(in));
                decoded = new APPacket.TeleportView(pos, frequency, operation, publicAccess,
                    ownerUUID, ownerName, medium, connectedCount, linked, in.readBoolean());
            }
            case TELEPORT_VIEW_REQUEST -> decoded = new APPacket.TeleportViewRequest(location(in));
            case ADVANCED_WOOD_UPDATE -> decoded = new APPacket.AdvancedWoodUpdate(location(in));
            case TELEPORT_DATA -> {
                PipeLocation pos = location(in);
                int count = limitedSize(in, 1024);
                int[] locations = new int[count];
                for (int i = 0; i < count; i++) locations[i] = in.readInt();
                UUID owner = in.readBoolean() ? new UUID(in.readLong(), in.readLong()) : null;
                decoded = new APPacket.TeleportData(pos, locations, owner, limitedUtf(in, 64));
            }
            case CHUNKLOAD_REQUEST -> decoded = new APPacket.ChunkloadRequest();
            case CHUNKLOAD_DATA -> {
                String dimension = limitedUtf(in, 256);
                int count = limitedSize(in, 4096);
                List<ChunkCoordinates> locations = new ArrayList<>(count);
                for (int i = 0; i < count; i++) locations.add(new ChunkCoordinates(in.readInt(), in.readInt()));
                decoded = new APPacket.ChunkloadData(dimension, locations);
            }
            case JEWELED_UPDATE_SERVER -> {
                PipeLocation pos = location(in);
                byte side = in.readByte();
                boolean acceptUnsorted = in.readBoolean();
                boolean matchNBT = in.readBoolean();
                boolean matchMetadata = in.readBoolean();
                decoded = new APPacket.JeweledServerUpdate(pos, side,
                    new SideFilterData.Options(matchMetadata, matchNBT, acceptUnsorted));
            }
            case JEWELED_UPDATE_CLIENT -> {
                PipeLocation pos = location(in);
                boolean[] accept = NetworkUtils.readBooleanArray(in, 6);
                boolean[] nbt = NetworkUtils.readBooleanArray(in, 6);
                boolean[] meta = NetworkUtils.readBooleanArray(in, 6);
                List<SideFilterData.Options> opts = new ArrayList<>(6);
                for (int i = 0; i < 6; i++) opts.add(new SideFilterData.Options(meta[i], nbt[i], accept[i]));
                decoded = new APPacket.JeweledClientUpdate(pos, opts);
            }
            default -> throw new IOException("Unknown packet type " + type);
        }
        if (in.available() != 0) throw new IOException("Additional Pipes packet contains trailing bytes");
        return decoded;
    }

    private static void location(DataOutputStream out, PipeLocation pos) throws IOException {
        out.writeUTF(pos.dimension());
        out.writeInt(pos.x());
        out.writeInt(pos.y());
        out.writeInt(pos.z());
    }
    private static PipeLocation location(DataInputStream in) throws IOException {
        return new PipeLocation(limitedUtf(in, 256), in.readInt(), in.readInt(), in.readInt());
    }
    private static String limitedUtf(DataInputStream in, int maxChars) throws IOException {
        String value = in.readUTF();
        if (value.length() > maxChars) throw new IOException("Oversized string in Additional Pipes packet");
        return value;
    }
    private static int limitedSize(DataInputStream in, int maximum) throws IOException {
        int count = in.readUnsignedShort();
        if (count > maximum) throw new IOException("Too many entries in Additional Pipes packet: " + count);
        return count;
    }
}
