package buildcraft.additionalpipes.api;

import java.util.UUID;

/**
 * A live, server-owned teleport endpoint. The eventual API v2 PipeComponent implements
 * this through an adapter; the registry does not depend on BCCE's private TileGenericPipe.
 */
public interface ITeleportPipe {
    void setFrequency(int frequency);
    int getFrequency();
    byte getState(); // bit 0 = can send, bit 1 = can receive
    void setState(byte state);
    boolean isPublic();
    void setPublic(boolean isPublic);
    PipeType getType();
    PipeLocation getPosition();
    UUID getOwnerUUID();
    String getOwnerName();
    default boolean canSend() { return (getState() & 1) != 0; }
    default boolean canReceive() { return (getState() & 2) != 0; }
    /** Must not load chunks or create world objects. */
    default boolean isValid() { return true; }
}
