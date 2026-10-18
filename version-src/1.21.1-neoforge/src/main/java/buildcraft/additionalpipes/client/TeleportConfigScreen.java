package buildcraft.additionalpipes.client;

import buildcraft.additionalpipes.api.PipeLocation;
import buildcraft.additionalpipes.network.APPacket;
import buildcraft.additionalpipes.platform.network.PlatformNetwork;
import buildcraft.lib.BCLibSprites;
import buildcraft.lib.gui.GuiIcon;
import buildcraft.lib.gui.ledger.Ledger_Neptune;
import buildcraft.lib.misc.SpriteUtil;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;

public final class TeleportConfigScreen extends Screen {
    private static final int GUI_WIDTH = 228;
    private static final int GUI_HEIGHT = 117;
    private static final int TEXT_COLOR = 0x404040;
    private static final int TITLE_COLOR = 0x0A0C84;
    private static final int CLOSED_WIDTH = 22;
    private static final int CLOSED_HEIGHT = 24;
    private static final int[] STEPS = {-100, -10, -1, 1, 10, 100};
    private static final GuiIcon BACKGROUND = new GuiIcon(ResourceLocation.fromNamespaceAndPath("additionalpipes", "textures/gui/background_generic.png"), 0, 0, GUI_WIDTH, GUI_HEIGHT);

    private APPacket.TeleportView data;
    private GameProfile ownerProfile;
    private Button modeButton;
    private Button accessButton;
    private int ticks;
    private boolean ownerOpen;
    private boolean connectionsOpen;
    private boolean helpOpen;
    private double helpWidth = CLOSED_WIDTH;
    private double helpHeight = CLOSED_HEIGHT;
    private double ownerWidth = CLOSED_WIDTH;
    private double ownerHeight = CLOSED_HEIGHT;
    private double connectionsWidth = CLOSED_WIDTH;
    private double connectionsHeight = CLOSED_HEIGHT;

    public TeleportConfigScreen(APPacket.TeleportView data) {
        super(Component.literal("Teleport Pipe"));
        this.data = data;
        updateProfile();
    }

    @Override
    protected void init() {
        int left = (width - GUI_WIDTH) / 2;
        int top = (height - GUI_HEIGHT) / 2;
        int buttonWidth = (GUI_WIDTH - 24) / 6;

        for (int i = 0; i < STEPS.length; i++) {
            final int step = STEPS[i];
            int x = left + 12 + i * buttonWidth;
            addRenderableWidget(Button.builder(Component.literal(step > 0 ? "+" + step : Integer.toString(step)),
                b -> update((int) Math.max(0, Math.min(999_999L, (long) data.frequency() + step)),
                    data.operation(), data.isPublic()))
                .bounds(x, top + 78, buttonWidth, 20).build());
        }
        modeButton = addRenderableWidget(Button.builder(Component.empty(),
            b -> update(data.frequency(), (byte) ((data.operation() + 1) % 4), data.isPublic()))
            .bounds(left + 12, top + 35, 102, 20).build());
        accessButton = addRenderableWidget(Button.builder(Component.empty(),
            b -> update(data.frequency(), data.operation(), !data.isPublic()))
            .bounds(left + 114, top + 35, 102, 20).build());
        updateButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks >= 20) {
            ticks = 0;
            PlatformNetwork.sendToServer(new APPacket.TeleportViewRequest(data.position()));
        }
        ownerWidth = approach(ownerWidth, ownerOpen ? 158 : CLOSED_WIDTH);
        ownerHeight = approach(ownerHeight, ownerOpen ? 54 : CLOSED_HEIGHT);
        connectionsWidth = approach(connectionsWidth, connectionsOpen ? 180 : CLOSED_WIDTH);
        connectionsHeight = approach(connectionsHeight, connectionsOpen ? 100 : CLOSED_HEIGHT);
        helpWidth = approach(helpWidth, helpOpen ? 176 : CLOSED_WIDTH);
        helpHeight = approach(helpHeight, helpOpen ? 80 : CLOSED_HEIGHT);
    }

    private static double approach(double current, double target) {
        if (current < target) return Math.min(target, current + 15);
        if (current > target) return Math.max(target, current - 15);
        return current;
    }

    private void update(int frequency, byte state, boolean isPublic) {
        APPacket.TeleportView previous = data;
        data = new APPacket.TeleportView(previous.position(), frequency, state, isPublic,
            previous.ownerUUID(), previous.ownerName(), previous.medium(), previous.connectedCount(),
            previous.connections(), false);
        updateButtons();
        PlatformNetwork.sendToServer(new APPacket.TeleportUpdate(data.position(), frequency, isPublic, state));
    }

    public void refresh(APPacket.TeleportView next) {
        if (!data.position().equals(next.position())) return;
        boolean changed = !Objects.equals(data.ownerUUID(), next.ownerUUID())
            || !Objects.equals(data.ownerName(), next.ownerName());
        data = next;
        if (changed) updateProfile();
        updateButtons();
    }

    private void updateProfile() {
        ownerProfile = data.ownerUUID() == null ? null
            : new GameProfile(data.ownerUUID(), data.ownerName());
    }

    private void updateButtons() {
        if (modeButton == null) return;
        modeButton.setMessage(Component.literal(switch (data.operation()) {
            case 0 -> "Disabled";
            case 1 -> "Send Only";
            case 2 -> "Receive Only";
            case 3 -> "Send & Receive";
            default -> "Disabled";
        }));
        accessButton.setMessage(Component.literal(data.isPublic() ? "Public" : "Private"));
    }

    private boolean clickLedger(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int x = (width + GUI_WIDTH) / 2;
        int top = (height - GUI_HEIGHT) / 2 + 5;
        if (inside(mouseX, mouseY, (width - GUI_WIDTH) / 2 - helpWidth, top, helpWidth, helpHeight)) {
            helpOpen = !helpOpen;
            return true;
        }
        if (inside(mouseX, mouseY, x, top, ownerWidth, ownerHeight)) {
            ownerOpen = !ownerOpen;
            return true;
        }
        int connectionsY = top + (int) ownerHeight + 5;
        if (inside(mouseX, mouseY, x, connectionsY, connectionsWidth, connectionsHeight)) {
            connectionsOpen = !connectionsOpen;
            return true;
        }
        return false;
    }

    private static boolean inside(double px, double py, double x, double y, double w, double h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (clickLedger(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        BACKGROUND.drawAt(graphics, (width - GUI_WIDTH) / 2, (height - GUI_HEIGHT) / 2);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawContent(graphics);
    }

    private void drawContent(GuiGraphics graphics) {
        int left = (width - GUI_WIDTH) / 2;
        int top = (height - GUI_HEIGHT) / 2;
        graphics.drawString(font, screenTitle(), left + 70, top + 6, TITLE_COLOR, false);
        graphics.drawString(font, cut("Coords: (" + data.position().x() + ", " + data.position().y() + ", " + data.position().z() + ")", 200), left + 12, top + 20, TEXT_COLOR, false);
        graphics.drawString(font, "Frequency: " + data.frequency(), left + 16, top + 66, TEXT_COLOR, false);
        drawHelpLedger(graphics, left, top + 5);
        drawLedgers(graphics, left + GUI_WIDTH, top + 5);
    }

    private void drawHelpLedger(GuiGraphics graphics, int left, int y) {
        int displayW = (int) helpWidth;
        int displayH = (int) helpHeight;
        int x = left - displayW;
        RenderSystem.setShaderColor(0.80F, 0.60F, 1F, 1F);
        Ledger_Neptune.SPRITE_SPLIT_NEG.draw(graphics, x, y, displayW, displayH);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        GuiIcon.draw(graphics, BCLibSprites.WARNING_MINOR,
            x + displayW - 20, y + 4, x + displayW - 4, y + 20);
        if (displayW >= 145) {
            graphics.drawString(font, "Help", x + 8, y + 5, 0xE1C92F, false);
            graphics.drawString(font, "Pipes link by frequency", x + 8, y + 23, 0x202020, false);
            graphics.drawString(font, "Select frequency with buttons", x + 8, y + 38, 0x202020, false);
            graphics.drawString(font, "Mode sets transfer direction", x + 8, y + 53, 0x202020, false);
        }
    }

    private void drawLedgers(GuiGraphics graphics, int x, int y) {
        int ownerDisplayW = (int) ownerWidth;
        int ownerDisplayH = (int) ownerHeight;
        RenderSystem.setShaderColor(0.88F, 0.94F, 1.0F, 1.0F);
        Ledger_Neptune.SPRITE_SPLIT_POS.draw(graphics, x, y, ownerDisplayW, ownerDisplayH);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        GuiIcon.draw(graphics, SpriteUtil.getFaceSprite(ownerProfile), x + 2, y + 4, x + 18, y + 20);
        var overlay = SpriteUtil.getFaceOverlaySprite(ownerProfile);
        if (overlay != null) GuiIcon.draw(graphics, overlay, x + 1.5, y + 3.5, x + 19, y + 21);

        if (ownerDisplayW >= 125) {
            graphics.drawString(font, "Ownership", x + 25, y + 5, 0xE1C92F, false);
            graphics.drawString(font, cut(data.ownerName().isBlank() ? "Unknown" : data.ownerName(), ownerDisplayW - 33), x + 25, y + 22, 0x000000, false);
        }

        int connectionsY = y + ownerDisplayH + 5;
        int connectionsDisplayW = (int) connectionsWidth;
        int connectionsDisplayH = (int) connectionsHeight;
        RenderSystem.setShaderColor(1F, 0.84F, 0.42F, 1F);
        Ledger_Neptune.SPRITE_SPLIT_POS.draw(graphics, x, connectionsY, connectionsDisplayW, connectionsDisplayH);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        GuiIcon.draw(graphics, BCLibSprites.ENGINE_ACTIVE, x + 2, connectionsY + 4,
            x + 18, connectionsY + 20);

        if (connectionsDisplayW >= 145) {
            String status = (data.operation() & 1) != 0 ? "Outputs: " : "Inputs: ";
            graphics.drawString(font, "Connected Pipes", x + 25, connectionsY + 5, 0xE1C92F, false);
            graphics.drawString(font, status + data.connectedCount(), x + 25, connectionsY + 20, 0x000000, false);
            List<PipeLocation> links = data.connections();
            for (int i = 0; i < links.size(); i++) {
                graphics.drawString(font, cut(connectedText(links.get(i)), connectionsDisplayW - 32),
                    x + 25, connectionsY + 35 + i * 13, 0x000000, false);
            }
        }
    }

    private String connectedText(PipeLocation location) {
        if (data.position().dimension().equals(location.dimension())) {
            return "(" + location.x() + ", " + location.y() + ", " + location.z() + ")";
        }
        return location.dimension() + " (" + location.x() + ", " + location.y() + ", " + location.z() + ")";
    }

    private String cut(String text, int maxWidth) {
        return font.plainSubstrByWidth(text, maxWidth);
    }

    private String screenTitle() {
        return switch (data.medium()) {
            case "ITEMS" -> "Item Teleport Pipe";
            case "FLUIDS" -> "Fluid Teleport Pipe";
            case "POWER" -> "Power Teleport Pipe";
            case "FE" -> "FE Teleport Pipe";
            default -> "Teleport Pipe";
        };
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
