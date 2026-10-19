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
    APPacket.TeleportView, APPacket.TeleportViewRequest, APPacket.InsertionView,
    APPacket.InsertionViewRequest, APPacket.AdvancedWoodFilterUpdate {

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
    record InsertionView(PipeLocation position, boolean priority, boolean excluded, int energy,
                         int sourceFace, List<Integer> priorities, List<String> filters,
                         List<Integer> damages, boolean open) implements APPacket {
        public InsertionView {
            Objects.requireNonNull(position);
            Objects.requireNonNull(priorities);
            Objects.requireNonNull(filters);
            Objects.requireNonNull(damages);
            if (priorities.size() != 6 || filters.size() != 9 || damages.size() != 9
                || energy < 0 || energy > 64 || sourceFace < 0 || sourceFace > 6)
                throw new IllegalArgumentException("Invalid insertion pipe view");
            for (int weight : priorities) if (weight < 0 || weight > 6)
                throw new IllegalArgumentException("Invalid priority value");
            for (int i = 0; i < 9; i++) {
                String name = Objects.requireNonNull(filters.get(i));
                if (name.length() > 128 || damages.get(i) < 0 || damages.get(i) > 32767)
                    throw new IllegalArgumentException("Invalid filter slot");
            }
            priorities = List.copyOf(priorities);
            filters = List.copyOf(filters);
            damages = List.copyOf(damages);
        }
        @Override public APMessageType type() { return APMessageType.INSERTION_VIEW; }
    }
    record InsertionViewRequest(PipeLocation position) implements APPacket {
        public InsertionViewRequest { Objects.requireNonNull(position); }
        @Override public APMessageType type() { return APMessageType.INSERTION_VIEW_REQUEST; }
    }
    record AdvancedWoodFilterUpdate(PipeLocation position, int slot, String item, int damage) implements APPacket {
        public AdvancedWoodFilterUpdate {
            Objects.requireNonNull(position);
            Objects.requireNonNull(item);
            if (slot < 0 || slot > 8 || item.length() > 128 || damage < 0 || damage > 32767
                || !item.isEmpty() && !item.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))
                throw new IllegalArgumentException("Invalid advanced wood filter update");
        }
        @Override public APMessageType type() { return APMessageType.ADVANCED_WOOD_FILTER; }
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
