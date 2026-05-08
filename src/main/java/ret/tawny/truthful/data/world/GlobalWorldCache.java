package ret.tawny.truthful.data.world;

import ret.tawny.truthful.utils.world.ChunkSnapshot;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GlobalWorldCache - Shared block data across all players to save memory and CPU.
 */
public final class GlobalWorldCache {

    private final Map<UUID, Map<Long, ChunkSnapshot>> worldCaches = new ConcurrentHashMap<>();

    public void addChunk(UUID worldUid, int x, int z, ChunkSnapshot snapshot) {
        worldCaches.computeIfAbsent(worldUid, k -> new ConcurrentHashMap<>())
                   .put(getChunkKey(x, z), snapshot);
    }

    public void removeChunk(UUID worldUid, int x, int z) {
        Map<Long, ChunkSnapshot> cache = worldCaches.get(worldUid);
        if (cache != null) {
            cache.remove(getChunkKey(x, z));
            if (cache.isEmpty()) worldCaches.remove(worldUid);
        }
    }

    public void updateBlock(UUID worldUid, int x, int y, int z, int globalId) {
        ChunkSnapshot chunk = getChunk(worldUid, x >> 4, z >> 4);
        if (chunk != null) {
            chunk.setBlock(x, y, z, globalId);
        }
    }

    public ChunkSnapshot getChunk(UUID worldUid, int x, int z) {
        Map<Long, ChunkSnapshot> cache = worldCaches.get(worldUid);
        return (cache == null) ? null : cache.get(getChunkKey(x, z));
    }

    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    public void clearWorld(UUID worldUid) {
        worldCaches.remove(worldUid);
    }

    public void clearAll() {
        worldCaches.clear();
    }
}
