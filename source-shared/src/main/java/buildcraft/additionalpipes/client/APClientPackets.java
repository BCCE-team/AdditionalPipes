package buildcraft.additionalpipes.client;

import buildcraft.additionalpipes.network.APPacket;
import buildcraft.additionalpipes.network.APProtocol;
import buildcraft.additionalpipes.service.APServices;
import buildcraft.additionalpipes.utils.Log;
import java.io.IOException;
import net.minecraft.client.Minecraft;

public final class APClientPackets {
    private APClientPackets() { }

    public static void receive(byte[] bytes) {
        APPacket packet;
        try {
            packet = APProtocol.decode(bytes, false);
        } catch (IOException | IllegalArgumentException error) {
            Log.warn("Discarding invalid AP packet: " + error.getMessage());
            return;
        }
        if (packet instanceof APPacket.TeleportView view) {
            Minecraft client = Minecraft.getInstance();
            if (view.open()) APClientScreenAccess.open(client, new TeleportConfigScreen(view));
            else if (APClientScreenAccess.current(client) instanceof TeleportConfigScreen screen) screen.refresh(view);
            return;
        }
        if (packet instanceof APPacket.InsertionView view) {
            if (!view.priority()) return;
            Minecraft client = Minecraft.getInstance();
            if (view.open()) APClientScreenAccess.open(client, new InsertionConfigScreen(view));
            else if (APClientScreenAccess.current(client) instanceof InsertionConfigScreen screen) screen.refresh(view);
            return;
        }
        APServices.packets().receive(bytes, false, null);
    }
}
