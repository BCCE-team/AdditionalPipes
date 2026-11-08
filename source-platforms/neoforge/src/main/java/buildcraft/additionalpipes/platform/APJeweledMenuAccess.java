package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.pipes.JeweledMenu;
import buildcraft.additionalpipes.pipes.JeweledPipeComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public final class APJeweledMenuAccess {
    private APJeweledMenuAccess() { }
    public static void open(ServerPlayer player, JeweledPipeComponent component, PipeLocation position) {
        player.openMenu(new SimpleMenuProvider(
            (id, inventory, opener) -> new JeweledMenu(id, inventory, component, position),
            Component.translatable("gui.additionalpipes.jeweled")));
    }
}
