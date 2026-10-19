package buildcraft.additionalpipes.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class APPlayerDrops {
    private APPlayerDrops() { }

    public static void drop(ServerPlayer player, ItemStack stack) {
        player.drop(stack, false);
    }
}
