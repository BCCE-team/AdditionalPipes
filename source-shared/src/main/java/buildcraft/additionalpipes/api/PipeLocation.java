package buildcraft.additionalpipes.api;

import java.util.Objects;

/** Position with its dimension: an unloaded pipe is never resolved by loading a chunk. */
public record PipeLocation(String dimension, int x, int y, int z) {
    public PipeLocation {
        Objects.requireNonNull(dimension, "dimension");
        if (dimension.isBlank() || dimension.length() > 256) throw new IllegalArgumentException("Invalid dimension");
    }
}
