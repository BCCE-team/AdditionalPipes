package buildcraft.additionalpipes.client;

import buildcraft.additionalpipes.network.APPacket;
import buildcraft.additionalpipes.network.APMessageType;
import buildcraft.additionalpipes.platform.network.PlatformNetwork;
import buildcraft.lib.gui.GuiIcon;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** Six-direction distribution quota configuration, synchronized by the server. */
public final class DistributionConfigScreen extends Screen {
    private static final int WIDTH = 117, HEIGHT = 130;
    private static final int FIRST_ROW = 14, ROW_STEP = 18;
    private static final GuiIcon BACKGROUND = new GuiIcon(Identifier.parse("additionalpipes:textures/gui/distribution_gui.png"), 0, 0, WIDTH, HEIGHT);
    private APPacket.DistributionView state;
    private final Button[] quotaButtons = new Button[6];
    private int ticks;

    public DistributionConfigScreen(APPacket.DistributionView state) {
        super(Component.translatable("gui.additionalpipes.distribution"));
        this.state = state;
    }

    @Override protected void init() {
        int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
        for (int i = 0; i < quotaButtons.length; i++) {
            final int face = i;
            int y = top + FIRST_ROW + i * ROW_STEP;
            addButton(left + 31, y, 20, 17, "-", b -> change(face, -1));
            quotaButtons[i] = addButton(left + 53, y, 30, 17, "1", b -> { });
            quotaButtons[i].active = false;
            addButton(left + 85, y, 20, 17, "+", b -> change(face, 1));
        }
        updateLabels();
    }

    private Button addButton(int x, int y, int w, int h, String label, Button.OnPress press) {
        return addRenderableWidget(Button.builder(Component.literal(label), press).bounds(x, y, w, h).build());
    }

    private void change(int face, int delta) {
        int next = state.quotas().get(face) + delta;
        if (next < 0 || next > 4096) return;
        List<Integer> quotas = new ArrayList<>(state.quotas());
        quotas.set(face, next);
        if (quotas.stream().allMatch(value -> value == 0)) return;
        state = new APPacket.DistributionView(state.position(), quotas, false);
        updateLabels();
        PlatformNetwork.sendToServer(new APPacket.ValueUpdate(
            APMessageType.DISTRIBUTION_UPDATE, state.position(), (byte)face, next));
    }

    public void refresh(APPacket.DistributionView next) {
        if (!state.position().equals(next.position())) return;
        state = next;
        updateLabels();
    }

    private void updateLabels() {
        for (int i = 0; i < quotaButtons.length; i++)
            if (quotaButtons[i] != null)
                quotaButtons[i].setMessage(Component.literal(Integer.toString(state.quotas().get(i))));
    }

    @Override public void tick() {
        super.tick();
        if (++ticks >= 20) {
            ticks = 0;
            PlatformNetwork.sendToServer(new APPacket.DistributionViewRequest(state.position()));
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - WIDTH) / 2, top = (height - HEIGHT) / 2;
        BACKGROUND.drawAt(graphics, left, top);
        super.render(graphics, mouseX, mouseY, partialTick);
        String heading = GuiTextLayout.fit(title.getString(), WIDTH - 12, font::width);
        graphics.drawString(font, heading, left + (WIDTH-font.width(heading))/2, top + 3, 0x404040, false);
    }

    @Override public boolean isPauseScreen() { return false; }
}
