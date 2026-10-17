package buildcraft.additionalpipes.service;

import buildcraft.additionalpipes.api.ITeleportPipe;
import java.util.Objects;
import java.util.UUID;

/** Centralises classic teleport ownership and public-channel rules. */
public final class TeleportPermissions {
    private TeleportPermissions() { }

    /** Only the owner may edit a private pipe, unless the actor has operator privileges. */
    public static boolean mayEdit(ITeleportPipe pipe, UUID playerId, boolean privileged) {
        Objects.requireNonNull(pipe, "pipe");
        return privileged || pipe.isPublic() ||
            (playerId != null && playerId.equals(pipe.getOwnerUUID()));
    }

    public static boolean mayConnect(ITeleportPipe source, ITeleportPipe destination) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        return source.isPublic() ? destination.isPublic()
            : source.getOwnerUUID() != null && source.getOwnerUUID().equals(destination.getOwnerUUID());
    }
}
