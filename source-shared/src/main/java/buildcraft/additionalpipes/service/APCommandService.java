package buildcraft.additionalpipes.service;

import buildcraft.additionalpipes.api.ITeleportPipe;
import buildcraft.additionalpipes.api.PipeType;
import buildcraft.additionalpipes.api.TeleportManagerBase;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Parser for the original /additionalpipes teleport <media> diagnostic command. */
public final class APCommandService {
    private APCommandService() { }

    public static List<String> listTeleportEndpoints(
        TeleportManagerBase manager, String medium, int maximum) {
        Objects.requireNonNull(manager);
        if (maximum < 0 || maximum > 1000) throw new IllegalArgumentException("Invalid result limit");
        PipeType type = switch (medium.toLowerCase(Locale.ROOT)) {
            case "item", "items" -> PipeType.ITEMS;
            case "fluid", "fluids" -> PipeType.FLUIDS;
            case "power" -> PipeType.POWER;
            case "logistic", "logistics" -> PipeType.LOGISTICS;
            default -> throw new IllegalArgumentException("Unknown teleport medium: " + medium);
        };
        List<String> result = new ArrayList<>();
        for (ITeleportPipe pipe : manager.getPipes(type)) {
            if (result.size() == maximum) break;
            var loc = pipe.getPosition();
            result.add("[" + loc.dimension() + " " + loc.x() + ", " + loc.y() + ", " + loc.z() + "] "
                + Objects.toString(pipe.getOwnerName(), "") + " (" + pipe.getFrequency() + ")");
        }
        return List.copyOf(result);
    }
}
