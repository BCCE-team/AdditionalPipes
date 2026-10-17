package buildcraft.additionalpipes.utils;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/** UUIDs, never mutable display names, identify teleport owners. */
public final class PlayerUtils {
    private PlayerUtils() { }
    public static UUID getUUID(Player player) { return Objects.requireNonNull(player).getUUID(); }
}
