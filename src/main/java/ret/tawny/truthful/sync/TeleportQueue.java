package ret.tawny.truthful.sync;

import org.bukkit.Location;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class TeleportQueue {

    private final ConcurrentLinkedDeque<Teleport> queue = new ConcurrentLinkedDeque<>();
    private final int maxEntries;
    private final long entryTtlMillis;

    public TeleportQueue(int maxEntries, long entryTtlMillis) {
        this.maxEntries = Math.max(1, maxEntries);
        this.entryTtlMillis = Math.max(1000L, entryTtlMillis);
    }

    public void add(int id, Location location, boolean isLagback) {
        purgeExpired();
        queue.add(new Teleport(id, location, isLagback));
        while (queue.size() > maxEntries) {
            queue.pollFirst();
        }
    }

    public Teleport confirm(int id) {
        purgeExpired();
        Iterator<Teleport> iterator = queue.iterator();
        while (iterator.hasNext()) {
            Teleport tp = iterator.next();
            if (tp.id == id) {
                iterator.remove();
                return tp;
            }
        }
        return null;
    }

    public Teleport match(double x, double y, double z) {
        purgeExpired();
        Iterator<Teleport> iterator = queue.iterator();
        while (iterator.hasNext()) {
            Teleport tp = iterator.next();

            double dx = tp.loc.getX() - x;
            double dy = tp.loc.getY() - y;
            double dz = tp.loc.getZ() - z;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq <= 2.25) {
                iterator.remove();
                return tp;
            }
        }
        return null;
    }

    public int getPendingCount() {
        return queue.size();
    }

    public void cleanup() {
        purgeExpired();
    }

    public void clear() {
        queue.clear();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        queue.removeIf(tp -> now - tp.timestamp > entryTtlMillis);
    }

    public static class Teleport {
        public final int id;
        public final Location loc;
        public final long timestamp;
        public final boolean isLagback;

        public Teleport(int id, Location loc, boolean isLagback) {
            this.id = id;
            this.loc = loc;
            this.timestamp = System.currentTimeMillis();
            this.isLagback = isLagback;
        }
    }
}