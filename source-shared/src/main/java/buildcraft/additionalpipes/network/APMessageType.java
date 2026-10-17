package buildcraft.additionalpipes.network;

/** Legacy AP message discriminator IDs, reused by the new bounded binary protocol. */
public enum APMessageType {
    DISTRIBUTION_UPDATE(0, true), TELEPORT_UPDATE(1, true), ADVANCED_WOOD_UPDATE(2, true),
    TELEPORT_DATA(3, false), CHUNKLOAD_REQUEST(4, true), CHUNKLOAD_DATA(5, false),
    PRIORITY_UPDATE(6, true), JEWELED_UPDATE_SERVER(7, true), JEWELED_UPDATE_CLIENT(8, false);

    private final int id;
    private final boolean serverbound;
    APMessageType(int id, boolean serverbound) { this.id = id; this.serverbound = serverbound; }
    public int id() { return id; }
    public boolean serverbound() { return serverbound; }
    public static APMessageType byId(int id) throws java.io.IOException {
        for (APMessageType value : values()) if (value.id == id) return value;
        throw new java.io.IOException("Unknown Additional Pipes packet discriminator " + id);
    }
}
