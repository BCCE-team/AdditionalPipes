package buildcraft.additionalpipes.pipes;

import buildcraft.additionalpipes.api.ITeleportPipe;
import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.network.APPacket;
import buildcraft.additionalpipes.platform.APDimensionIds;
import buildcraft.additionalpipes.platform.APPlayerLevel;
import buildcraft.additionalpipes.platform.network.PlatformNetwork;
import buildcraft.additionalpipes.service.APServices;
import buildcraft.api.v2.BuildCraftApi;
import buildcraft.api.v2.BuildCraftServices;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class TeleportUiServer {
    private record Session(PipeLocation position, long tick) { }
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_OPEN = new HashMap<>();
    private static final Map<UUID, Long> LAST_DENIED = new HashMap<>();
    private static final Map<UUID, Long> LAST_VIEW = new HashMap<>();
    private static final int MAX_DISTANCE_SQUARED = 64;
    private static final int SESSION_LIFETIME_TICKS = 1200;

    private TeleportUiServer() { }

    public static void reset() {
        SESSIONS.clear();
        LAST_OPEN.clear();
        LAST_DENIED.clear();
        LAST_VIEW.clear();
    }

    public static void denied(ServerPlayer player, String ownerName) {
        long now = APPlayerLevel.of(player).getGameTime();
        Long last = LAST_DENIED.get(player.getUUID());
        if (last != null && now >= last && now - last < 20) return;
        LAST_DENIED.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal("Access denied! This pipe belongs to " + ownerName));
    }

    public static void open(ServerPlayer player, TeleportPipeComponent pipe) {
        if (!APPlayerLevel.of(player).hasChunkAt(pipe.blockPosition())) return;
        if (!pipe.canModify(player.getUUID())) return;
        PipeLocation location = pipe.getPosition();
        if (!near(player, location)) return;
        long tick = APPlayerLevel.of(player).getGameTime();
        Session previous = SESSIONS.get(player.getUUID());
        Long last = LAST_OPEN.get(player.getUUID());
        if (previous != null && previous.position().equals(location) && last != null
            && tick >= last && tick - last < 8) return;
        SESSIONS.put(player.getUUID(), new Session(location, tick));
        LAST_OPEN.put(player.getUUID(), tick);
        PlatformNetwork.sendToPlayer(player, snapshot(pipe, true));
    }

    public static boolean authorized(UUID playerId, APPacket packet) {
        if (!(packet instanceof APPacket.TeleportUpdate update)) return false;
        return canAccess(playerId, update.position());
    }

    public static boolean authorizedViewRequest(UUID playerId, APPacket packet) {
        if (!(packet instanceof APPacket.TeleportViewRequest request)) return false;
        return canAccess(playerId, request.position());
    }

    private static boolean canAccess(UUID playerId, PipeLocation position) {
        var server = APServices.server();
        if (server == null || !server.isSameThread()) return false;
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return false;
        Session session = SESSIONS.get(playerId);
        if (session == null || !session.position().equals(position)) return false;
        long now = APPlayerLevel.of(player).getGameTime();
        if (now < session.tick() || now - session.tick() > SESSION_LIFETIME_TICKS) {
            SESSIONS.remove(playerId);
            return false;
        }
        if (!near(player, position)) return false;
        TeleportPipeComponent component = find(player, position);
        return component != null && component.canModify(playerId);
    }

    public static void apply(UUID playerId, APPacket packet) {
        APPacket.TeleportUpdate update = (APPacket.TeleportUpdate) packet;
        ServerPlayer player = APServices.server().getPlayerList().getPlayer(playerId);
        if (player == null) return;
        TeleportPipeComponent component = find(player, update.position());
        if (component == null || !component.canModify(playerId)) return;
        if (component.getOwnerUUID() == null) component.setOwner(playerId, player.getName().getString());
        component.setFrequency(update.frequency());
        component.setState(update.operation());
        component.setPublic(update.isPublic());
        SESSIONS.put(playerId, new Session(update.position(), APPlayerLevel.of(player).getGameTime()));
        PlatformNetwork.sendToPlayer(player, snapshot(component, false));
    }

    public static void refresh(UUID playerId, APPacket packet) {
        APPacket.TeleportViewRequest request = (APPacket.TeleportViewRequest) packet;
        ServerPlayer player = APServices.server().getPlayerList().getPlayer(playerId);
        if (player == null) return;
        long now = APPlayerLevel.of(player).getGameTime();
        Long previous = LAST_VIEW.get(playerId);
        if (previous != null && now >= previous && now - previous < 10) return;
        TeleportPipeComponent pipe = find(player, request.position());
        if (pipe == null || !pipe.canModify(playerId)) return;
        LAST_VIEW.put(playerId, now);
        SESSIONS.put(playerId, new Session(request.position(), now));
        PlatformNetwork.sendToPlayer(player, snapshot(pipe, false));
    }

    private static TeleportPipeComponent find(ServerPlayer player, PipeLocation location) {
        ServerLevel level = APPlayerLevel.of(player);
        if (!APDimensionIds.of(level).equals(location.dimension())) return null;
        BlockPos pos = new BlockPos(location.x(), location.y(), location.z());
        if (!level.hasChunkAt(pos)) return null;
        return BuildCraftApi.service(BuildCraftServices.PIPES).pipe(level, pos)
            .flatMap(view -> view.component(APTeleportPipes.COMPONENT_ID))
            .filter(TeleportPipeComponent.class::isInstance)
            .map(TeleportPipeComponent.class::cast).orElse(null);
    }

    private static boolean near(ServerPlayer player, PipeLocation location) {
        ServerLevel level = APPlayerLevel.of(player);
        if (!APDimensionIds.of(level).equals(location.dimension())) return false;
        BlockPos pos = player.blockPosition();
        long dx = (long) pos.getX() - location.x();
        long dy = (long) pos.getY() - location.y();
        long dz = (long) pos.getZ() - location.z();
        return dx * dx + dy * dy + dz * dz <= MAX_DISTANCE_SQUARED;
    }

    private static APPacket.TeleportView snapshot(TeleportPipeComponent pipe, boolean open) {
        boolean sending = pipe.canSend();
        List<ITeleportPipe> peers = new ArrayList<>(APServices.teleports().getConnectedPipes(
            pipe, !sending, sending));
        List<PipeLocation> locations = new ArrayList<>();
        for (ITeleportPipe peer : peers) {
            if (locations.size() == 3) break;
            locations.add(peer.getPosition());
        }
        return new APPacket.TeleportView(pipe.getPosition(), pipe.getFrequency(), pipe.getState(),
            pipe.isPublic(), pipe.getOwnerUUID(), pipe.getOwnerName(), pipe.getType().name(), peers.size(), locations, open);
    }
}
