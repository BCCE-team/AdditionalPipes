package buildcraft.additionalpipes.network;

import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.chunkloader.ChunkCoordinates;
import buildcraft.additionalpipes.filter.SideFilterData;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable packet payloads. Content event handlers are installed in later port stages. */
public sealed interface APPacket permits APPacket.ValueUpdate, APPacket.TeleportUpdate,
    APPacket.AdvancedWoodUpdate, APPacket.TeleportData, APPacket.ChunkloadRequest,
    APPacket.ChunkloadData, APPacket.JeweledServerUpdate, APPacket.JeweledClientUpdate,
    APPacket.TeleportView, APPacket.TeleportViewRequest {

    APMessageType type();

    /** Distribution and priority share the same classic fields. */
    record ValueUpdate(APMessageType type, PipeLocation position, byte index, int value) implements APPacket {
        public ValueUpdate {
            if (type != APMessageType.DISTRIBUTION_UPDATE && type != APMessageType.PRIORITY_UPDATE) {
                throw new IllegalArgumentException("Not a value-update packet");
            }
            Objects.requireNonNull(position);
            if (index < 0 || index > 5) throw new IllegalArgumentException("Invalid face index");
        }
    }
    record TeleportUpdate(PipeLocation position, int frequency, boolean isPublic, byte operation) implements APPacket {
        public TeleportUpdate {
            Objects.requireNonNull(position);
            if (frequency < 0 || frequency > 999_999 || operation < 0 || operation > 3) throw new IllegalArgumentException("Invalid teleport update");
        }
        @Override public APMessageType type() { return APMessageType.TELEPORT_UPDATE; }
    }
    record TeleportView(PipeLocation position, int frequency, byte operation, boolean isPublic,
                        UUID ownerUUID, String ownerName, String medium, int connectedCount,
                        List<PipeLocation> connections, boolean open) implements APPacket {
        public TeleportView(PipeLocation position, int frequency, byte operation, boolean isPublic,
                            String ownerName, String medium, int connectedCount,
                            List<PipeLocation> connections, boolean open) {
            this(position, frequency, operation, isPublic, null, ownerName, medium, connectedCount, connections, open);
        }
        public TeleportView {
            Objects.requireNonNull(position);
            Objects.requireNonNull(ownerName);
            Objects.requireNonNull(medium);
            Objects.requireNonNull(connections);
            if (frequency < 0 || frequency > 999_999 || operation < 0 || operation > 3
                || ownerName.length() > 64 || medium.length() > 24 || connectedCount < 0
                || connectedCount > 100_000 || connections.size() > 3 || connections.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Invalid teleport GUI state");
            }
            connections = List.copyOf(connections);
        }
        @Override public APMessageType type() { return APMessageType.TELEPORT_VIEW; }
    }
    record TeleportViewRequest(PipeLocation position) implements APPacket {
        public TeleportViewRequest { Objects.requireNonNull(position); }
        @Override public APMessageType type() { return APMessageType.TELEPORT_VIEW_REQUEST; }
    }
    record AdvancedWoodUpdate(PipeLocation position) implements APPacket {
        public AdvancedWoodUpdate { Objects.requireNonNull(position); }
        @Override public APMessageType type() { return APMessageType.ADVANCED_WOOD_UPDATE; }
    }
    record TeleportData(PipeLocation position, int[] locations, UUID owner, String ownerName) implements APPacket {
        public TeleportData {
            Objects.requireNonNull(position);
            Objects.requireNonNull(locations);
            if (locations.length > 1024) throw new IllegalArgumentException("Too many teleport entries");
            locations = locations.clone();
            Objects.requireNonNull(ownerName);
            if (ownerName.length() > 64) throw new IllegalArgumentException("Owner name too long");
        }
        @Override public int[] locations() { return locations.clone(); }
        @Override public APMessageType type() { return APMessageType.TELEPORT_DATA; }
    }
    record ChunkloadRequest() implements APPacket {
        @Override public APMessageType type() { return APMessageType.CHUNKLOAD_REQUEST; }
    }
    record ChunkloadData(String dimension, List<ChunkCoordinates> chunks) implements APPacket {
        public ChunkloadData {
            Objects.requireNonNull(dimension);
            if (dimension.isBlank() || dimension.length() > 256) throw new IllegalArgumentException("Invalid dimension");
            Objects.requireNonNull(chunks);
            if (chunks.size() > 4096 || chunks.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Invalid chunk list");
            chunks = List.copyOf(chunks);
        }
        @Override public APMessageType type() { return APMessageType.CHUNKLOAD_DATA; }
    }
    /** Classic AP's client-to-server filter options packet (1-indexed GUI face). */
    record JeweledServerUpdate(PipeLocation position, byte face, SideFilterData.Options options) implements APPacket {
        public JeweledServerUpdate {
            Objects.requireNonNull(position);
            if (face < 1 || face > 6) throw new IllegalArgumentException("Invalid jeweled face index");
            Objects.requireNonNull(options);
        }
        @Override public APMessageType type() { return APMessageType.JEWELED_UPDATE_SERVER; }
    }
    /** Six per-face option triplets sent to refresh the client GUI. */
    record JeweledClientUpdate(PipeLocation position, List<SideFilterData.Options> sides) implements APPacket {
        public JeweledClientUpdate {
            Objects.requireNonNull(position);
            Objects.requireNonNull(sides);
            if (sides.size() != 6 || sides.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Expected six jeweled sides");
            sides = List.copyOf(sides);
        }
        @Override public APMessageType type() { return APMessageType.JEWELED_UPDATE_CLIENT; }
    }
}
