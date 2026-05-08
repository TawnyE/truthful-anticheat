package ret.tawny.truthful.checks.impl.combat.aim;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.compensation.CompensationTracker;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.utils.hitbox.SimpleHitbox;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AimH: Target Lock Detection (Oscillation Analysis)
 */
@CheckData(order = 'H', type = CheckType.AIM)
public final class AimH extends Check {

    private static final int SAMPLE_SIZE = 20;
    private static final double OSCILLATION_THRESHOLD = 0.7; // 70% sign changes = oscillating

    private final CheckBuffer buffer = new CheckBuffer(12.0);
    private final Map<UUID, LockData> dataMap = new ConcurrentHashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isRotationUpdate())
            return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isRotationExempt())
            return;


        if (data.isInsideVehicle()) {
            dataMap.remove(player.getUniqueId());
            return;
        }

        Entity target = data.getLastTarget();
        if (target == null || !target.isValid()) {
            dataMap.remove(player.getUniqueId());
            return;
        }

        // Combat context only (within 20 ticks of last hit)
        if (data.getTicksTracked() - data.getLastHitTick() > 20) {
            dataMap.remove(player.getUniqueId());
            return;
        }

        if (data.isServerFrozen() || data.getTickFreezeGraceTicks() > 0) {
            buffer.decrease(player, 0.5);
            return;
        }


        float pitch = data.getPitch();
        float yaw = data.getYaw();


        CompensationTracker.CompensatedEntity targetData = Truthful.getInstance().getCompensationTracker().getEntityData(target.getEntityId());
        if (targetData == null) return;


        SimpleHitbox targetBox = targetData.getHitboxAt(0);
        if (targetBox == null) return;

        double eyeHeight = data.getEyeHeight(false, data.isSneaking(), data.isSwimming());
        Vector playerEye = new Vector(data.getX(), data.getY() + eyeHeight, data.getZ());
        Vector targetCenter = new Vector(
                (targetBox.minX + targetBox.maxX) / 2.0,
                (targetBox.minY + targetBox.maxY) / 2.0,
                (targetBox.minZ + targetBox.maxZ) / 2.0
        );

        double dx = targetCenter.getX() - playerEye.getX();
        double dy = targetCenter.getY() - playerEye.getY();
        double dz = targetCenter.getZ() - playerEye.getZ();


        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));


        float yawOffset = normalizeAngle(yaw - idealYaw);
        float pitchOffset = pitch - idealPitch;

        LockData lockData = dataMap.computeIfAbsent(player.getUniqueId(), k -> new LockData());
        lockData.addSample(yawOffset, pitchOffset);

        if (!lockData.isReady())
            return;


        double oscillationRatio = lockData.getOscillationRatio();
        boolean oscillating = oscillationRatio > OSCILLATION_THRESHOLD;


        double convergenceScore = lockData.getConvergenceScore();
        boolean converging = convergenceScore > 0.6;


        double avgOffset = lockData.getAverageAbsOffset();
        boolean tightLock = avgOffset < 3.0;


        double offsetVariance = lockData.getOffsetVariance();
        boolean artificialVariance = offsetVariance > 0.5 && offsetVariance < 2.0;

        int suspicion = 0;
        if (oscillating && tightLock)
            suspicion += 2;
        if (converging && tightLock)
            suspicion += 2;
        if (artificialVariance && tightLock)
            suspicion++;

        if (suspicion >= 2) {
            double severity = suspicion * 0.75;
            if (buffer.increase(player, severity) > 10.0) {
                flag(data, String.format("Target Lock. oscRatio=%.2f, convScore=%.2f, avgOff=%.1f",
                        oscillationRatio, convergenceScore, avgOffset));
                buffer.reset(player, 5.0);
            }
        } else {
            buffer.decrease(player, 0.4);
        }
    }

    private float normalizeAngle(float angle) {
        angle = angle % 360.0f;
        if (angle >= 180.0f)
            angle -= 360.0f;
        if (angle < -180.0f)
            angle += 360.0f;
        return angle;
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        dataMap.remove(event.getPlayer().getUniqueId());
    }

    private static class LockData {
        private final Deque<Float> yawOffsets = new ArrayDeque<>();
        private final Deque<Float> pitchOffsets = new ArrayDeque<>();

        void addSample(float yawOffset, float pitchOffset) {
            yawOffsets.addLast(yawOffset);
            pitchOffsets.addLast(pitchOffset);

            while (yawOffsets.size() > SAMPLE_SIZE)
                yawOffsets.pollFirst();
            while (pitchOffsets.size() > SAMPLE_SIZE)
                pitchOffsets.pollFirst();
        }

        boolean isReady() {
            return yawOffsets.size() >= SAMPLE_SIZE;
        }

        double getOscillationRatio() {
            int signChanges = 0;
            Float prev = null;

            for (Float offset : yawOffsets) {
                if (prev != null && Math.signum(offset) != Math.signum(prev) && Math.abs(offset) > 0.5) {
                    signChanges++;
                }
                prev = offset;
            }

            return yawOffsets.size() <= 1 ? 0.0 : (double) signChanges / (yawOffsets.size() - 1);
        }

        double getConvergenceScore() {
            int decreasing = 0;
            Float prev = null;

            for (Float offset : yawOffsets) {
                if (prev != null && Math.abs(offset) < Math.abs(prev)) {
                    decreasing++;
                }
                prev = offset;
            }

            return yawOffsets.size() <= 1 ? 0.0 : (double) decreasing / (yawOffsets.size() - 1);
        }

        double getAverageAbsOffset() {
            double sum = 0.0;
            for (Float offset : yawOffsets) {
                sum += Math.abs(offset);
            }
            return yawOffsets.isEmpty() ? 0.0 : sum / yawOffsets.size();
        }

        double getOffsetVariance() {
            double mean = 0.0;
            for (Float o : yawOffsets) {
                mean += o;
            }
            mean = yawOffsets.isEmpty() ? 0.0 : mean / yawOffsets.size();

            double sum = 0.0;
            for (Float o : yawOffsets) {
                double diff = o - mean;
                sum += diff * diff;
            }
            return yawOffsets.isEmpty() ? 0.0 : sum / yawOffsets.size();
        }
    }
}
