package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.network.APPacket;
import buildcraft.additionalpipes.network.APMessageType;
import buildcraft.additionalpipes.platform.APDimensionIds;
import buildcraft.additionalpipes.platform.APPlayerLevel;
import buildcraft.additionalpipes.platform.APAdvancedWoodMenuAccess;
import buildcraft.additionalpipes.platform.network.PlatformNetwork;
import buildcraft.additionalpipes.service.APServices;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class APInsertionUiServer {
    private record Session(PipeLocation position, long tick) { }
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final int LIFETIME = 1200;
    private static final int MAX_DISTANCE_SQUARED = 64;

    private APInsertionUiServer() { }

    public static void reset() { SESSIONS.clear(); }

    public static void openPriority(ServerPlayer player, PriorityInsertionComponent component) {
        if (component.pipe() == null || component.level() == null) return;
        PipeLocation position = loc(component.level(), component.pipe().position());
        if (!near(player, position)) return;
        SESSIONS.put(player.getUUID(), new Session(position, APPlayerLevel.of(player).getGameTime()));
        PlatformNetwork.sendToPlayer(player, snapshotPriority(position, component, true));
    }

    public static void openAdvancedWood(ServerPlayer player, AdvancedWoodComponent component) {
        if (component.pipe() == null) return;
        ServerLevel level = APPlayerLevel.of(player);
        if (!BuildCraftApi.service(BuildCraftServices.PIPES).pipe(level, component.pipe().position())
            .flatMap(pipe -> pipe.component(APInsertionPipes.ADVANCED_COMPONENT))
            .filter(other -> other == component).isPresent()) return;
        PipeLocation position = loc(level, component.pipe().position());
        if (!near(player, position)) return;
        component.returnLegacyItems(player);
        APAdvancedWoodMenuAccess.open(player, component, position);
    }

    public static boolean authorized(UUID playerId, APPacket packet) {
        PipeLocation pos = packet instanceof APPacket.ValueUpdate update
            ? update.type() == APMessageType.PRIORITY_UPDATE ? update.position() : null
            : packet instanceof APPacket.InsertionViewRequest request ? request.position() : null;
        if (pos == null) return false;
        MinecraftServer server = APServices.server();
        if (server == null || !server.isSameThread()) return false;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || !near(player, pos)) return false;
        Session session = SESSIONS.get(playerId);
        if (session == null || !session.position().equals(pos)) return false;
        long tick = APPlayerLevel.of(player).getGameTime();
        if (tick < session.tick() || tick - session.tick() > LIFETIME) {
            SESSIONS.remove(playerId);
            return false;
        }
        return locate(player, pos) instanceof PriorityInsertionComponent;
    }

    public static void handle(UUID playerId, APPacket packet) {
        MinecraftServer server = APServices.server();
        if (server == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return;
        PipeLocation pos = packet instanceof APPacket.ValueUpdate update ? update.position()
            : packet instanceof APPacket.InsertionViewRequest request ? request.position() : null;
        if (pos == null) return;
        Object component = locate(player, pos);
        if (component instanceof PriorityInsertionComponent priority) {
            if (packet instanceof APPacket.ValueUpdate update
                && update.type() == APMessageType.PRIORITY_UPDATE && update.value() >= 0 && update.value() <= 6) {
                priority.setPriority(update.index(), update.value());
            }
            PlatformNetwork.sendToPlayer(player, snapshotPriority(pos, priority, false));
        }
        SESSIONS.put(playerId, new Session(pos, APPlayerLevel.of(player).getGameTime()));
    }

    private static Object locate(ServerPlayer player, PipeLocation location) {
        ServerLevel world = APPlayerLevel.of(player);
        if (!APDimensionIds.of(world).equals(location.dimension())) return null;
        BlockPos pos = new BlockPos(location.x(), location.y(), location.z());
        if (!world.hasChunkAt(pos)) return null;
        return BuildCraftApi.service(BuildCraftServices.PIPES).pipe(world, pos)
            .map(pipe -> {
                if (APInsertionPipes.id(APInsertionPipes.PRIORITY).equals(pipe.typeId()))
                    return pipe.component(APInsertionPipes.PRIORITY_COMPONENT).orElse(null);
                if (APInsertionPipes.id(APInsertionPipes.ADVANCED_WOOD).equals(pipe.typeId()))
                    return pipe.component(APInsertionPipes.ADVANCED_COMPONENT).orElse(null);
                return null;
            }).orElse(null);
    }

    private static boolean near(ServerPlayer player, PipeLocation loc) {
        if (!APDimensionIds.of(APPlayerLevel.of(player)).equals(loc.dimension())) return false;
        BlockPos pos = player.blockPosition();
        long dx = (long) pos.getX() - loc.x(), dy = (long) pos.getY() - loc.y(), dz = (long) pos.getZ() - loc.z();
        return dx * dx + dy * dy + dz * dz <= MAX_DISTANCE_SQUARED;
    }

    private static PipeLocation loc(ServerLevel world, BlockPos pos) {
        return new PipeLocation(APDimensionIds.of(world), pos.getX(), pos.getY(), pos.getZ());
    }

    private static APPacket.InsertionView snapshotPriority(PipeLocation pos,
                                                            PriorityInsertionComponent priority, boolean open) {
        List<Integer> weights = Arrays.stream(priority.priorities()).boxed().toList();
        return new APPacket.InsertionView(pos, true, false, 0, 6, weights,
            List.of("", "", "", "", "", "", "", "", ""), List.of(0, 0, 0, 0, 0, 0, 0, 0, 0), open);
    }

}
