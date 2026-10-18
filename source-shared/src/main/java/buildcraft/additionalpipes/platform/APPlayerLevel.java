package buildcraft.additionalpipes.platform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class APPlayerLevel {
    private APPlayerLevel() { }

    public static ServerLevel of(ServerPlayer player) {
        return (ServerLevel) player.level();
    }
}
