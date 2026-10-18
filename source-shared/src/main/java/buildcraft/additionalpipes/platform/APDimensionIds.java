package buildcraft.additionalpipes.platform;

import net.minecraft.server.level.ServerLevel;

public final class APDimensionIds {
    private APDimensionIds() { }

    public static String of(ServerLevel level) {
        return level.dimension().location().toString();
    }
}
