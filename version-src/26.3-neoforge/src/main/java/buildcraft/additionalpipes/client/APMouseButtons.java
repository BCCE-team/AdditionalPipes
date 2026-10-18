package buildcraft.additionalpipes.client;

import buildcraft.lib.compat.minecraft.gui.BCGuiInput;

public final class APMouseButtons {
    private APMouseButtons() { }

    public static int legacy(int button) {
        return BCGuiInput.legacyButton(button);
    }
}
