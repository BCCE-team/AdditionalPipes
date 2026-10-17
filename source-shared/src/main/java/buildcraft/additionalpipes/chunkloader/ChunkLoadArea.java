package buildcraft.additionalpipes.chunkloader;

import java.util.LinkedHashSet;
import java.util.Set;

/** Original AP's square loader area: radius 1 = 3 x 3 chunks. */
public final class ChunkLoadArea {
    private ChunkLoadArea() { }

    public static Set<ChunkCoordinates> square(int ownerBlockX, int ownerBlockZ, int radius) {
        if (radius < 0 || radius > 8) throw new IllegalArgumentException("Chunk radius must be between 0 and 8");
        int centerX = Math.floorDiv(ownerBlockX, 16);
        int centerZ = Math.floorDiv(ownerBlockZ, 16);
        Set<ChunkCoordinates> result = new LinkedHashSet<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                result.add(new ChunkCoordinates(centerX + x, centerZ + z));
            }
        }
        return Set.copyOf(result);
    }
}
