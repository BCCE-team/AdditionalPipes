package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.network.APMessageType;
import buildcraft.additionalpipes.network.APPacket;
import buildcraft.additionalpipes.platform.APDimensionIds;
import buildcraft.additionalpipes.platform.APPlayerLevel;
import buildcraft.additionalpipes.platform.network.PlatformNetwork;
import buildcraft.additionalpipes.service.APServices;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import buildcraft.api.v2.pipe.PipeView;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative GUI: no arbitrary client writes to remote pipes. */
public final class APDistributionUiServer {
    private record Session(PipeLocation position, long tick) { }
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final long SESSION_TICKS = 1200;
    private static final long MAX_DISTANCE_SQUARED = 64;

    private APDistributionUiServer() { }
    public static void reset() { SESSIONS.clear(); }

    public static void open(ServerPlayer player, DistributionComponent component, PipeView pipe) {
        if (player == null || component == null || pipe == null) return;
        ServerLevel level = APPlayerLevel.of(player);
        PipeLocation position = location(level, pipe.position());
        if (!near(player, position) || find(player, position) != component) return;
        SESSIONS.put(player.getUUID(), new Session(position, level.getGameTime()));
        PlatformNetwork.sendToPlayer(player, snapshot(position, component, true));
    }

    public static boolean authorized(UUID sender, APPacket packet) {
        PipeLocation position = packet instanceof APPacket.ValueUpdate update && update.type() == APMessageType.DISTRIBUTION_UPDATE
            ? update.position() : packet instanceof APPacket.DistributionViewRequest request ? request.position() : null;
        MinecraftServer server = APServices.server();
        if (position == null || server == null || !server.isSameThread()) return false;
        ServerPlayer player = server.getPlayerList().getPlayer(sender);
        if (player == null || !near(player, position)) return false;
        Session session = SESSIONS.get(sender);
        if (session == null || !session.position().equals(position)) return false;
        long now = APPlayerLevel.of(player).getGameTime();
        if (now < session.tick() || now - session.tick() > SESSION_TICKS) {
            SESSIONS.remove(sender);
            return false;
        }
        return find(player, position) != null;
    }

    public static void handle(UUID sender, APPacket packet) {
        MinecraftServer server = APServices.server();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(sender);
        if (player == null) return;
        PipeLocation position = packet instanceof APPacket.ValueUpdate update ? update.position()
            : packet instanceof APPacket.DistributionViewRequest request ? request.position() : null;
        if (position == null) return;
        DistributionComponent component = find(player, position);
        if (component == null) return;
        if (packet instanceof APPacket.ValueUpdate update && update.type() == APMessageType.DISTRIBUTION_UPDATE
            && update.value() >= 0 && update.value() <= DistributionCycle.MAX_QUOTA) {
            component.setQuota(update.index(), update.value());
        }
        PlatformNetwork.sendToPlayer(player, snapshot(position, component, false));
        SESSIONS.put(sender, new Session(position, APPlayerLevel.of(player).getGameTime()));
    }

    private static APPacket.DistributionView snapshot(PipeLocation position, DistributionComponent component, boolean open) {
        return new APPacket.DistributionView(position, Arrays.stream(component.quotas()).boxed().toList(), open);
    }

    private static DistributionComponent find(ServerPlayer player, PipeLocation location) {
        ServerLevel level = APPlayerLevel.of(player);
        if (!APDimensionIds.of(level).equals(location.dimension())) return null;
        BlockPos pos = new BlockPos(location.x(), location.y(), location.z());
        if (!level.hasChunkAt(pos)) return null;
        return BuildCraftApi.service(BuildCraftServices.PIPES).pipe(level, pos)
            .filter(pipe -> APUtilityPipes.id(APUtilityPipes.DISTRIBUTION).equals(pipe.typeId()))
            .flatMap(pipe -> pipe.component(APUtilityPipes.DISTRIBUTION_COMPONENT))
            .filter(DistributionComponent.class::isInstance).map(DistributionComponent.class::cast).orElse(null);
    }

    private static boolean near(ServerPlayer player, PipeLocation location) {
        if (!APDimensionIds.of(APPlayerLevel.of(player)).equals(location.dimension())) return false;
        BlockPos pos = player.blockPosition();
        long dx = (long)pos.getX()-location.x(), dy = (long)pos.getY()-location.y(), dz = (long)pos.getZ()-location.z();
        return dx*dx + dy*dy + dz*dz <= MAX_DISTANCE_SQUARED;
    }

    private static PipeLocation location(ServerLevel level, BlockPos pos) {
        return new PipeLocation(APDimensionIds.of(level), pos.getX(), pos.getY(), pos.getZ());
    }
}
