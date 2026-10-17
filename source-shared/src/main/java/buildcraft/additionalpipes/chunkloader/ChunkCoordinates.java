package buildcraft.additionalpipes.chunkloader;

/** Dimension-independent chunk coordinates, including negative world coordinates. */
public record ChunkCoordinates(int x, int z) implements Comparable<ChunkCoordinates> {
    @Override public int compareTo(ChunkCoordinates other) {
        int horizontal = Integer.compare(x, other.x);
        return horizontal != 0 ? horizontal : Integer.compare(z, other.z);
    }
}
