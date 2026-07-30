package ret.tawny.truthful.checks.impl.world.scaffold;

import com.github.retrooper.packetevents.util.Vector3i;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.action.PlayerBlockPlacePacketWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

final class ScaffoldSupport {

    private ScaffoldSupport() {}

    static PlacementContext context(final PlayerData data) {
        if (data == null) return null;

        PlayerBlockPlacePacketWrapper place = data.getCurrentBlockPlacement();
        if (place == null || place.getBlockPosition() == null) return null;

        Vector3i clicked = place.getBlockPosition();
        BlockFace face = place.getBlockFace();
        if (face == null) return null;

        int placedX = clicked.getX() + face.getModX();
        int placedY = clicked.getY() + face.getModY();
        int placedZ = clicked.getZ() + face.getModZ();

        Vector hit = place.getHitVec();
        double hitX = clicked.getX() + clamp01(hit.getX());
        double hitY = clicked.getY() + clamp01(hit.getY());
        double hitZ = clicked.getZ() + clamp01(hit.getZ());

        long ping = data.getPing();
        double pingTicks = Math.min(8.0, Math.max(0.0, ping / 50.0));

        double eyeX = data.getX() - (data.getDeltaX() * pingTicks);
        double eyeY = (data.getY() - (data.getDeltaY() * pingTicks))
                + data.getEyeHeight(false, data.isSneaking(), data.isSwimming());
        double eyeZ = data.getZ() - (data.getDeltaZ() * pingTicks);

        double dx = hitX - eyeX;
        double dy = hitY - eyeY;
        double dz = hitZ - eyeZ;
        double horizontal = Math.hypot(dx, dz);
        double reach = Math.sqrt(dx * dx + dy * dy + dz * dz);

        float requiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float requiredPitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));

        double placedCenterDx = placedX + 0.5D - eyeX;
        double placedCenterDz = placedZ + 0.5D - eyeZ;
        double placedHorizontal = Math.hypot(placedCenterDx, placedCenterDz);

        boolean belowFeet = placedY <= Math.floor(data.getY()) - 1;
        boolean nearFeet = placedY <= data.getY() + 0.25D && placedHorizontal <= 2.15D;

        boolean scaffoldLike = nearFeet;

        return new PlacementContext(
                clicked.getX(), clicked.getY(), clicked.getZ(),
                placedX, placedY, placedZ,
                requiredYaw, requiredPitch,
                yawDistance(data.getYaw(), requiredYaw),
                Math.abs(data.getPitch() - requiredPitch),
                reach,
                placedHorizontal,
                belowFeet,
                scaffoldLike,
                face
        );
    }

    static boolean shouldLookDown(final PlacementContext ctx) {
        return ctx != null && ctx.placedY <= ctx.clickedY && ctx.requiredPitch >= 45.0F;
    }

    static float yawDistance(final float a, final float b) {
        float diff = Math.abs(a - b) % 360.0F;
        return diff > 180.0F ? 360.0F - diff : diff;
    }

    private static double clamp01(final double value) {
        if (value < 0.0D) return 0.0D;
        if (value > 1.0D) return 1.0D;
        return value;
    }

    static PlacementBag bag() { return PlacementBag.INSTANCE; }

    static final class PlacementBag {
        static final PlacementBag INSTANCE = new PlacementBag();
        private final Map<UUID, Queue<PlacementSlot>> slots = new ConcurrentHashMap<>();
        private static final int MAX_SLOTS = 40;

        void record(UUID uuid, PlacementContext ctx, long timestamp) {
            slots.computeIfAbsent(uuid, k -> new ConcurrentLinkedQueue<>())
                    .offer(new PlacementSlot(ctx, timestamp));
            trim(uuid);
        }

        List<PlacementSlot> recent(UUID uuid, int n) {
            Queue<PlacementSlot> q = slots.get(uuid);
            if (q == null || q.isEmpty()) return Collections.emptyList();
            trim(uuid);
            List<PlacementSlot> list = new ArrayList<>(q);
            if (list.size() <= n) return list;
            return list.subList(list.size() - n, list.size());
        }

        private void trim(UUID uuid) {
            Queue<PlacementSlot> q = slots.get(uuid);
            if (q != null) {
                while (q.size() > MAX_SLOTS) {
                    q.poll();
                }
            }
        }

        void remove(UUID uuid) { slots.remove(uuid); }
    }

    static final class PlacementSlot {
        final PlacementContext ctx;
        final long timestamp;

        PlacementSlot(PlacementContext ctx, long timestamp) {
            this.ctx = ctx;
            this.timestamp = timestamp;
        }
    }

    static final class RotationSnapshot {
        final float yaw;
        final float pitch;
        final long tick;

        RotationSnapshot(float yaw, float pitch, long tick) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.tick = tick;
        }
    }

    static final class ReachSample {
        final double dist;
        final long timestamp;

        ReachSample(double dist, long timestamp) {
            this.dist = dist;
            this.timestamp = timestamp;
        }
    }

    static final class RotationBag {
        static final RotationBag INSTANCE = new RotationBag();
        private final Map<UUID, Queue<RotationSnapshot>> history = new ConcurrentHashMap<>();
        private static final int MAX = 80;

        void push(UUID uuid, float yaw, float pitch, long tick) {
            history.computeIfAbsent(uuid, k -> new ConcurrentLinkedQueue<>())
                    .offer(new RotationSnapshot(yaw, pitch, tick));
            trim(uuid);
        }

        List<RotationSnapshot> recent(UUID uuid, int n) {
            Queue<RotationSnapshot> q = history.get(uuid);
            if (q == null || q.isEmpty()) return Collections.emptyList();
            trim(uuid);
            List<RotationSnapshot> list = new ArrayList<>(q);
            if (list.size() <= n) return list;
            return list.subList(list.size() - n, list.size());
        }

        private void trim(UUID uuid) {
            Queue<RotationSnapshot> q = history.get(uuid);
            if (q != null) {
                while (q.size() > MAX) {
                    q.poll();
                }
            }
        }

        void remove(UUID uuid) { history.remove(uuid); }
    }

    static final class ReachBag {
        static final ReachBag INSTANCE = new ReachBag();
        private final Map<UUID, Queue<ReachSample>> samples = new ConcurrentHashMap<>();
        private static final int MAX = 60;

        void push(UUID uuid, double dist, long timestamp) {
            samples.computeIfAbsent(uuid, k -> new ConcurrentLinkedQueue<>())
                    .offer(new ReachSample(dist, timestamp));
            trim(uuid);
        }

        List<ReachSample> recent(UUID uuid, int n) {
            Queue<ReachSample> q = samples.get(uuid);
            if (q == null || q.isEmpty()) return Collections.emptyList();
            trim(uuid);
            List<ReachSample> list = new ArrayList<>(q);
            if (list.size() <= n) return list;
            return list.subList(list.size() - n, list.size());
        }

        private void trim(UUID uuid) {
            Queue<ReachSample> q = samples.get(uuid);
            if (q != null) {
                while (q.size() > MAX) {
                    q.poll();
                }
            }
        }

        void remove(UUID uuid) { samples.remove(uuid); }
    }

    static final class PlacementContext {
        final int clickedX, clickedY, clickedZ;
        final int placedX, placedY, placedZ;
        final float requiredYaw, requiredPitch;
        final float yawError, pitchError;
        final double reach, placedHorizontal;
        final boolean belowFeet, scaffoldLike;
        final BlockFace face;

        PlacementContext(int clickedX, int clickedY, int clickedZ,
                         int placedX, int placedY, int placedZ,
                         float requiredYaw, float requiredPitch,
                         float yawError, float pitchError,
                         double reach, double placedHorizontal,
                         boolean belowFeet, boolean scaffoldLike,
                         BlockFace face) {
            this.clickedX = clickedX;
            this.clickedY = clickedY;
            this.clickedZ = clickedZ;
            this.placedX = placedX;
            this.placedY = placedY;
            this.placedZ = placedZ;
            this.requiredYaw = requiredYaw;
            this.requiredPitch = requiredPitch;
            this.yawError = yawError;
            this.pitchError = pitchError;
            this.reach = reach;
            this.placedHorizontal = placedHorizontal;
            this.belowFeet = belowFeet;
            this.scaffoldLike = scaffoldLike;
            this.face = face;
        }
    }
}