package buildcraft.additionalpipes.platform;

import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.pipes.AdvancedWoodComponent;
import buildcraft.additionalpipes.pipes.AdvancedWoodMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkHooks;

public final class APAdvancedWoodMenuAccess {
    private APAdvancedWoodMenuAccess() { }

    public static void open(ServerPlayer player, AdvancedWoodComponent component, PipeLocation position) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
            (id, inventory, opener) -> new AdvancedWoodMenu(id, inventory, component, position),
            Component.translatable("gui.additionalpipes.advanced_wood")));
    }
}
