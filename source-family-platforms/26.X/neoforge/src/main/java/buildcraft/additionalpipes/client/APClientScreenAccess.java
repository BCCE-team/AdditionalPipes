package buildcraft.additionalpipes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class APClientScreenAccess {
    private APClientScreenAccess() { }

    public static void open(Minecraft client, Screen screen) {
        client.gui.setScreen(screen);
    }

    public static Screen current(Minecraft client) {
        return client.gui.screen();
    }
}
