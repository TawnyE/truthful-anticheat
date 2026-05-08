package ret.tawny.truthful.compensation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import ret.tawny.truthful.utils.hitbox.SimpleHitbox;
import ret.tawny.truthful.utils.tick.ITickable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CompensationTracker implements ITickable {

    private static final int HISTORY_SIZE = 30;
    private static final int MAX_BACKTRACK_TICKS = 8;
    private static final int CLEANUP_INTERVAL = 100;
    // FIX: Increased from 40 to 1200 (60 seconds).
    // Vanilla doesn't send packets for standing-still entities.
    // They should not expire just because they haven't moved recently.
    private static final int MAX_STALE_TICKS = 1200;
    private static final int DESTROY_GRACE_TICKS = 6;
    private static final double LIVE_RESYNC_DISTANCE_SQUARED = 4.0;

    private final Map<Integer, CompensatedEntity> compensationMap = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    @Override
    public void tick() {
        tickCounter++;
        if (tickCounter % CLEANUP_INTERVAL == 0) {
            int currentTick = Bukkit.getCurrentTick();
            compensationMap.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTick));
        }
    }

    public void handleSpawn(int id, UUID uuid, double x, double y, double z, double width, double height,
                            boolean isPlayer) {
        int currentTick = Bukkit.getCurrentTick();
        CompensatedEntity data = new CompensatedEntity(id, uuid, isPlayer);
        compensationMap.put(id, data);
        data.snapshot(currentTick, x, y, z, width, height);
    }

    public void handleEntitySnapshot(Entity entity) {
        if (entity == null || !entity.isValid()) return;

        int currentTick = Bukkit.getCurrentTick();
        int entityId = entity.getEntityId();
        EntityBox box = readEntityBox(entity);
        CompensatedEntity data = compensationMap.computeIfAbsent(entityId,
                id -> new CompensatedEntity(id, entity.getUniqueId(), entity instanceof Player));

        data.snapshot(currentTick, box.x, box.y, box.z, box.width, box.height);
    }

    public void handleTeleport(int id, double x, double y, double z) {
        int currentTick = Bukkit.getCurrentTick();
        CompensatedEntity data = compensationMap.get(id);
        if (data != null) {
            data.snapshot(currentTick, x, y, z, data.lastWidth, data.lastHeight);
        }
    }

    public void handleRelMove(int id, double dx, double dy, double dz) {
        int currentTick = Bukkit.getCurrentTick();
        CompensatedEntity data = compensationMap.get(id);
        if (data != null) {
            EntitySnapshot last = data.getLatest();
            if (last != null) {
                data.snapshot(currentTick, last.x + dx, last.y + dy, last.z + dz, data.lastWidth, data.lastHeight);
            }
        }
    }

    public void handleVelocity(int id, double vx, double vy, double vz) {
        int currentTick = Bukkit.getCurrentTick();
        CompensatedEntity data = compensationMap.get(id);
        if (data != null) {
            EntitySnapshot last = data.getLatest();
            if (last != null) {
                data.snapshot(currentTick, last.x + vx, last.y + vy, last.z + vz, data.lastWidth, data.lastHeight);
            }
        }
    }

    public void handleDestroy(int id) {
        CompensatedEntity data = compensationMap.get(id);
        if (data != null) {
            data.markDestroyed(Bukkit.getCurrentTick());
        }
    }

    public CompensatedEntity getEntityData(int entityId) {
        CompensatedEntity data = compensationMap.get(entityId);
        if (data == null)
            return null;

        int currentTick = Bukkit.getCurrentTick();
        if (data.isExpired(currentTick)) {
            compensationMap.remove(entityId, data);
            return null;
        }
        syncFromLiveEntityIfNeeded(entityId, data, currentTick);
        return data;
    }

    public boolean hasEntityData(int entityId) {
        return getEntityData(entityId) != null;
    }

    public static final class CompensatedEntity {
        public final int entityId;
        public final UUID uuid;
        public final boolean isPlayer;
        private volatile int lastSeenTick;
        private volatile int destroyedTick = -1;
        private final EntitySnapshot[] snapshots = new EntitySnapshot[HISTORY_SIZE];
        private final int[] ticks = new int[HISTORY_SIZE];
        private int head = 0;
        private int count = 0;

        protected double lastWidth = 0.6;
        protected double lastHeight = 1.8;

        public CompensatedEntity(int entityId, UUID uuid, boolean isPlayer) {
            this.entityId = entityId;
            this.uuid = uuid;
            this.isPlayer = isPlayer;
        }

        public synchronized void snapshot(int tick, double x, double y, double z, double width, double height) {
            this.lastSeenTick = tick;
            this.destroyedTick = -1;
            this.lastWidth = width;
            this.lastHeight = height;
            snapshots[head] = new EntitySnapshot(x, y, z, width, height);
            ticks[head] = tick;
            head = (head + 1) % HISTORY_SIZE;
            if (count < HISTORY_SIZE)
                count++;
        }

        public synchronized EntitySnapshot getLatest() {
            if (count == 0)
                return null;
            return snapshots[(head - 1 + HISTORY_SIZE) % HISTORY_SIZE];
        }

        public void markDestroyed(int tick) {
            this.destroyedTick = tick;
        }

        public boolean isExpired(int currentTick) {
            int destroyedAt = this.destroyedTick;
            if (destroyedAt >= 0 && currentTick - destroyedAt > DESTROY_GRACE_TICKS) {
                return true;
            }
            return currentTick - this.lastSeenTick > MAX_STALE_TICKS;
        }

        public synchronized void snapshot(int tick, Location loc, double width, double height) {
            snapshot(tick, loc.getX(), loc.getY(), loc.getZ(), width, height);
        }

        public synchronized SimpleHitbox getHitboxAt(int serverTickDelay) {
            return getHitboxAt(serverTickDelay, Bukkit.getCurrentTick());
        }

        public synchronized SimpleHitbox getHitboxAt(int serverTickDelay, int currentTick) {
            if (serverTickDelay > MAX_BACKTRACK_TICKS)
                serverTickDelay = MAX_BACKTRACK_TICKS;
            if (serverTickDelay < 0)
                serverTickDelay = 0;

            int targetTick = currentTick - serverTickDelay;

            EntitySnapshot bestPrev = null;
            int bestPrevTick = Integer.MIN_VALUE;
            EntitySnapshot bestNext = null;
            int bestNextTick = Integer.MAX_VALUE;

            for (int i = 0; i < count; i++) {
                int tick = ticks[i];
                EntitySnapshot s = snapshots[i];

                if (tick == targetTick)
                    return s.toHitbox();

                if (tick < targetTick && tick > bestPrevTick) {
                    bestPrevTick = tick;
                    bestPrev = s;
                } else if (tick > targetTick && tick < bestNextTick) {
                    bestNextTick = tick;
                    bestNext = s;
                }
            }

            if (bestPrev != null && bestNext != null) {
                int tickDelta = bestNextTick - bestPrevTick;
                if (tickDelta <= 0)
                    return bestPrev.toHitbox();
                double progress = (double) (targetTick - bestPrevTick) / (double) tickDelta;
                return EntitySnapshot.interpolate(bestPrev, bestNext, progress);
            }

            if (count > 0) {
                int latestIdx = (head - 1 + HISTORY_SIZE) % HISTORY_SIZE;
                return snapshots[latestIdx].toHitbox();
            }
            return null;
        }
    }

    private void syncFromLiveEntityIfNeeded(int entityId, CompensatedEntity data, int currentTick) {
        if (!Bukkit.isPrimaryThread()) return;

        Entity entity = findEntity(entityId);
        if (entity == null || !entity.isValid()) return;

        EntitySnapshot latest = data.getLatest();
        if (latest == null) {
            handleEntitySnapshot(entity);
            return;
        }

        EntityBox live = readEntityBox(entity);
        double dx = latest.x - live.x;
        double dy = latest.y - live.y;
        double dz = latest.z - live.z;
        double distanceSquared = dx * dx + dy * dy + dz * dz;

        if (distanceSquared > LIVE_RESYNC_DISTANCE_SQUARED || currentTick - data.lastSeenTick > MAX_STALE_TICKS) {
            data.snapshot(currentTick, live.x, live.y, live.z, live.width, live.height);
        }
    }

    private Entity findEntity(int entityId) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getEntityId() == entityId) {
                    return entity;
                }
            }
        }
        return null;
    }

    private EntityBox readEntityBox(Entity entity) {
        try {
            BoundingBox box = entity.getBoundingBox();
            double width = Math.max(box.getMaxX() - box.getMinX(), box.getMaxZ() - box.getMinZ());
            double height = box.getMaxY() - box.getMinY();
            return new EntityBox(
                    (box.getMinX() + box.getMaxX()) * 0.5,
                    box.getMinY(),
                    (box.getMinZ() + box.getMaxZ()) * 0.5,
                    width,
                    height
            );
        } catch (Throwable ignored) {
            Location location = entity.getLocation();
            return new EntityBox(location.getX(), location.getY(), location.getZ(), 0.6, 1.8);
        }
    }

    private static final class EntityBox {
        final double x, y, z, width, height;

        EntityBox(double x, double y, double z, double width, double height) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
        }
    }

    public static class EntitySnapshot {
        final double x, y, z, width, height;

        EntitySnapshot(double x, double y, double z, double width, double height) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
        }

        SimpleHitbox toHitbox() {
            return new SimpleHitbox(x, y, z, width, height);
        }

        static SimpleHitbox interpolate(EntitySnapshot from, EntitySnapshot to, double t) {
            return new SimpleHitbox(
                    from.x + (to.x - from.x) * t,
                    from.y + (to.y - from.y) * t,
                    from.z + (to.z - from.z) * t,
                    from.width + (to.width - from.width) * t,
                    from.height + (to.height - from.height) * t);
        }
    }
}
