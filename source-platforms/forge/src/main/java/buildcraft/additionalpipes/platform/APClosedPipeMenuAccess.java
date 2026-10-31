package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.pipes.ClosedPipeComponent;
import buildcraft.additionalpipes.pipes.ClosedPipeMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

public final class APClosedPipeMenuAccess {
    private APClosedPipeMenuAccess() { }
    public static void open(ServerPlayer player, ClosedPipeComponent component, PipeLocation position) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
            (id, inventory, opener) -> new ClosedPipeMenu(id, inventory, component, position),
            Component.translatable("gui.additionalpipes.closed")));
    }
}
