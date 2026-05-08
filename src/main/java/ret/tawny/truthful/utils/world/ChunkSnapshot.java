package ret.tawny.truthful.utils.world;

/**
 * ChunkSnapshot - Thread-safe block data storage using Global IDs.
 * Optimized for speed and memory by using a flat array of sections.
 * Supports modern Minecraft heights (up to Y=319 and below Y=0).
 */
public final class ChunkSnapshot {

    // 64 sections cover -512 to +511, which is more than enough for modern MC (-64 to 320)
    private final int[][] sections = new int[64][];

    public void setBlock(int x, int y, int z, int globalId) {
        int sectionIndex = (y >> 4) + 32; // Offset by 32 to handle negative Y
        if (sectionIndex < 0 || sectionIndex >= 64) return;

        int[] section = sections[sectionIndex];
        if (section == null) {
            synchronized (this) {
                section = sections[sectionIndex];
                if (section == null) {
                    section = new int[4096];
                    sections[sectionIndex] = section;
                }
            }
        }

        // Flat index: (x * 256) + (z * 16) + y
        section[((x & 15) << 8) | ((z & 15) << 4) | (y & 15)] = globalId;
    }

    public int getBlockId(int x, int y, int z) {
        int sectionIndex = (y >> 4) + 32;
        if (sectionIndex < 0 || sectionIndex >= 64) return 0;

        int[] section = sections[sectionIndex];
        if (section == null) return 0; // 0 is always Air

        return section[((x & 15) << 8) | ((z & 15) << 4) | (y & 15)];
    }
}
