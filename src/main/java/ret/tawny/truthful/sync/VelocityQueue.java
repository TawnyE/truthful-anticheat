package ret.tawny.truthful.sync;

import org.bukkit.util.Vector;
import ret.tawny.truthful.utils.tick.ITickable;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class VelocityQueue implements ITickable, Iterable<VelocityQueue.VelocityEntry> {

    private final ConcurrentLinkedDeque<VelocityEntry> queue = new ConcurrentLinkedDeque<>();
    private final int maxEntries;
    private final long ttlMillis;

    public VelocityQueue(int maxEntries, long ttlMillis) {
        this.maxEntries = Math.max(2, maxEntries);
        this.ttlMillis = Math.max(750L, ttlMillis);
    }

    public void addVelocity(final Vector velocity, final int startTransId, final int endTransId, final boolean explosion) {
        purgeExpired();
        queue.addLast(new VelocityEntry(velocity, (short) startTransId, (short) endTransId, explosion));
        while (queue.size() > maxEntries) {
            queue.pollFirst();
        }
    }

    public void confirm(short transId) {
        for (VelocityEntry entry : queue) {
            if (!entry.startAcked && entry.startId == transId) {
                entry.startAcked = true;
            }
            if (!entry.endAcked && entry.endId == transId) {
                entry.endAcked = true;
                entry.ackTick = 0;
            }
        }
    }

    public boolean hasActiveVelocity() {
        for (VelocityEntry entry : queue) {
            if (entry.isAcked() && entry.current.lengthSquared() > 1.0E-5) return true;
            if (!entry.isAcked() && !entry.isExpired(ttlMillis)) return true;
        }
        return false;
    }

    public boolean hasExplosionVelocity() {
        for (VelocityEntry entry : queue) {
            if (entry.explosion && (!entry.isExpired(ttlMillis) || entry.current.lengthSquared() > 1.0E-5)) return true;
        }
        return false;
    }

    public Vector getQueuedVelocityVector() {
        Vector total = new Vector();
        for (VelocityEntry entry : queue) {
            if (entry.isAcked() || entry.ackTick <= 1) {
                total.add(entry.current);
            }
        }
        return total;
    }


    // legacy compatibility
    public Vector getActiveVelocity() {
        return getQueuedVelocityVector();
    }

    // legacy compatibility
    public void applyFriction(double horizontalFriction) {
        applyTickFriction(horizontalFriction, 0.9800000190734863D);
    }

    public void applyTickFriction(double horizontalFriction, double verticalFriction) {
        for (VelocityEntry entry : queue) {
            if (!entry.isAcked()) continue;
            entry.current.setX(entry.current.getX() * horizontalFriction);
            entry.current.setZ(entry.current.getZ() * horizontalFriction);
            entry.current.setY(entry.current.getY() * verticalFriction);
            if (Math.abs(entry.current.getX()) < 0.003D) entry.current.setX(0.0D);
            if (Math.abs(entry.current.getY()) < 0.003D) entry.current.setY(0.0D);
            if (Math.abs(entry.current.getZ()) < 0.003D) entry.current.setZ(0.0D);
        }
    }

    @Override
    public void tick() {
        purgeExpired();
        queue.removeIf(entry -> entry.isAcked() && (entry.current.lengthSquared() <= 1.0E-5 || entry.ackTick > 25));
        for (VelocityEntry entry : queue) {
            if (entry.isAcked()) entry.ackTick++;
        }
    }

    private void purgeExpired() {
        queue.removeIf(entry -> entry.isExpired(ttlMillis));
    }

    public void clear() {
        queue.clear();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public Iterator<VelocityEntry> iterator() {
        return queue.iterator();
    }

    public static final class VelocityEntry {
        private final short startId;
        private final short endId;
        private final long createTime;
        private final boolean explosion;
        private final Vector current;
        private boolean startAcked;
        private boolean endAcked;
        private int ackTick;

        private VelocityEntry(Vector init, short startId, short endId, boolean explosion) {
            this.current = init.clone();
            this.startId = startId;
            this.endId = endId;
            this.createTime = System.currentTimeMillis();
            this.explosion = explosion;
        }

        public boolean isAcked() {
            return startAcked && endAcked;
        }

        private boolean isExpired(long ttl) {
            return System.currentTimeMillis() - createTime > ttl;
        }

        public Vector getCurrent() { return current; }
        public boolean isExplosion() { return explosion; }
        public int getAckTick() { return ackTick; }
    }
}
