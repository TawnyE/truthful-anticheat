package ret.tawny.truthful.data.world;

import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import ret.tawny.truthful.utils.world.ChunkSnapshot;
import ret.tawny.truthful.Truthful;
import java.util.UUID;

/**
 * CompensatedWorld - Manages the local block cache using Global IDs.
 * Thread-safe access for async checks to prevent main-thread deadlocks or crashes.
 */
public final class CompensatedWorld {

    // FIXED: Removed 'final' so we can update it when players change worlds (Multiverse)
    private UUID worldUid;

    // Performance: Cache the last accessed chunk to avoid map lookups and boxing
    private volatile ChunkCacheEntry lastChunkCache = new ChunkCacheEntry(Long.MAX_VALUE, null);

    private static class ChunkCacheEntry {
        final long key;
        final ChunkSnapshot value;
        ChunkCacheEntry(long key, ChunkSnapshot value) {
            this.key = key;
            this.value = value;
        }
    }

    public CompensatedWorld(UUID worldUid) {
        this.worldUid = worldUid;
    }

    // FIXED: Allows the PlayerData to re-bind the cache to the new world
    public void setWorldUid(UUID worldUid) {
        this.worldUid = worldUid;
        this.clear();
    }

    public void addChunk(int x, int z, ChunkSnapshot snapshot) {
        Truthful.getInstance().getGlobalWorldCache().addChunk(worldUid, x, z, snapshot);

        long key = getChunkKey(x, z);
        ChunkCacheEntry cache = lastChunkCache;
        if (cache.key == key) {
            lastChunkCache = new ChunkCacheEntry(key, snapshot);
        }
    }

    public void removeChunk(int x, int z) {
        long key = getChunkKey(x, z);
        ChunkCacheEntry cache = lastChunkCache;
        if (cache.key == key) {
            lastChunkCache = new ChunkCacheEntry(Long.MAX_VALUE, null);
        }
    }

    public void updateBlock(int x, int y, int z, int globalId) {
        Truthful.getInstance().getGlobalWorldCache().updateBlock(worldUid, x, y, z, globalId);
    }

    /**
     * Gets the WrappedBlockState at a location 100% Async.
     * Returns AIR (0) if chunk is not loaded/cached.
     */
    public WrappedBlockState getBlockState(int x, int y, int z) {
        long key = getChunkKey(x >> 4, z >> 4);
        ChunkSnapshot chunk;

        ChunkCacheEntry cache = lastChunkCache;
        if (cache.key == key) {
            chunk = cache.value;
        } else {
            chunk = Truthful.getInstance().getGlobalWorldCache().getChunk(worldUid, x >> 4, z >> 4);
            if (chunk != null) {
                lastChunkCache = new ChunkCacheEntry(key, chunk);
            }
        }

        // If chunk isn't cached, return AIR (0) to avoid false positives or crashes
        int id = (chunk == null) ? 0 : chunk.getBlockId(x, y, z);

        // We use PacketEvents to turn the ID back into a state object for the checks
        return WrappedBlockState.getByGlobalId(id);
    }

    /**
     * Checks if a chunk is currently cached.
     */
    public boolean isChunkLoaded(int x, int z) {
        long key = getChunkKey(x, z);
        ChunkCacheEntry cache = lastChunkCache;
        if (cache.key == key && cache.value != null) return true;
        return Truthful.getInstance().getGlobalWorldCache().getChunk(worldUid, x, z) != null;
    }

    private long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    public void clear() {
        lastChunkCache = new ChunkCacheEntry(Long.MAX_VALUE, null);
    }
}