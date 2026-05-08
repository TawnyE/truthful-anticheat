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
import ret.tawny.truthful.utils.math.MathHelper;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


@CheckData(order = 'I', type = CheckType.AIM)
public final class AimI extends Check {

    private static final int SAMPLE_SIZE = 25;
    private static final double SUSPICION_THRESHOLD = 0.75; // 75% synthetic probability

    private final CheckBuffer buffer = new CheckBuffer(15.0);
    private final Map<UUID, BayesData> dataMap = new ConcurrentHashMap<>();

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


        if (data.getTicksTracked() - data.getLastHitTick() > 40) {
            dataMap.remove(player.getUniqueId());
            return;
        }

        if (data.isServerFrozen() || data.getTickFreezeGraceTicks() > 0) {
            buffer.decrease(player, 0.5);
            return;
        }

        float deltaYaw = Math.abs(data.getDeltaYaw());
        float deltaPitch = Math.abs(data.getDeltaPitch());
        float lastDeltaYaw = Math.abs(data.getLastDeltaYaw());
        float lastDeltaPitch = Math.abs(data.getLastDeltaPitch());


        if (deltaYaw < 0.3f || deltaYaw > 50.0f) {
            buffer.decrease(player, 0.1);
            return;
        }

        BayesData bayesData = dataMap.computeIfAbsent(player.getUniqueId(), k -> new BayesData());


        double gcdScore = calculateGcdScore(deltaPitch, lastDeltaPitch);


        float accel = Math.abs(deltaYaw - lastDeltaYaw);
        double accelScore = calculateAccelScore(deltaYaw, accel, bayesData);


        float currentYaw = data.getYaw();


        Entity target = data.getLastTarget();
        double efficiencyScore = 0.0;
        if (target != null && target.isValid()) {
            efficiencyScore = calculateEfficiencyScore(data, target, currentYaw, deltaYaw);
        }


        double timingScore = calculateTimingScore(bayesData);


        bayesData.addSample(deltaYaw, deltaPitch, accel);

        if (!bayesData.isReady())
            return;


        double combinedScore = (gcdScore * 0.3) + (accelScore * 0.25) +
                (efficiencyScore * 0.25) + (timingScore * 0.2);

        bayesData.addProbability(combinedScore);
        double smoothedProbability = bayesData.getSmoothedProbability();

        if (smoothedProbability > SUSPICION_THRESHOLD) {
            double severity = (smoothedProbability - 0.5) * 4.0;
            if (buffer.increase(player, severity) > 12.0) {
                flag(data, String.format("Bayes Aim. prob=%.2f, gcd=%.2f, accel=%.2f, eff=%.2f",
                        smoothedProbability, gcdScore, accelScore, efficiencyScore));
                buffer.reset(player, 5.0);
            }
        } else {
            buffer.decrease(player, 0.3);
        }
    }

    private double calculateGcdScore(float deltaPitch, float lastDeltaPitch) {
        if (deltaPitch < 0.1 || lastDeltaPitch < 0.1)
            return 0.0;

        long current = (long) (deltaPitch * 16777216.0);
        long last = (long) (lastDeltaPitch * 16777216.0);
        long gcd = MathHelper.getGcd(current, last);
        double step = gcd / 16777216.0;

        // Valid mouse sensitivity produces step > 0.005
        if (step < 0.003)
            return 0.8; // Too small = synthetic
        if (step > 0.5)
            return 0.6; // Too large = suspicious

        // Check if rotation is quatized to step
        double pixels = deltaPitch / step;
        double error = Math.abs(pixels - Math.round(pixels));

        return error > 0.1 ? 0.5 : 0.0; // High error = possibly synthetic
    }

    private double calculateAccelScore(float velocity, float accel, BayesData data) {
        if (velocity < 1.0)
            return 0.0;


        if (accel < 0.01 && velocity > 3.0)
            return 0.7;


        double accelVariance = data.getAccelVariance();
        if (accelVariance < 0.01 && data.getAverageVelocity() > 2.0) {
            return 0.6; // Too consistent
        }

        return 0.0;
    }


    private double calculateEfficiencyScore(PlayerData data, Entity target, float currentYaw, float deltaYaw) {
        CompensationTracker.CompensatedEntity targetData = Truthful.getInstance().getCompensationTracker().getEntityData(target.getEntityId());
        if (targetData == null) return 0.0;

        SimpleHitbox targetBox = targetData.getHitboxAt(0);
        if (targetBox == null) return 0.0;

        double eyeHeight = data.getEyeHeight(false, data.isSneaking(), data.isSwimming());
        Vector playerEye = new Vector(data.getX(), data.getY() + eyeHeight, data.getZ());
        Vector targetCenter = new Vector(
                (targetBox.minX + targetBox.maxX) / 2.0,
                (targetBox.minY + targetBox.maxY) / 2.0,
                (targetBox.minZ + targetBox.maxZ) / 2.0
        );

        double dx = targetCenter.getX() - playerEye.getX();
        double dz = targetCenter.getZ() - playerEye.getZ();
        float idealYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;

        float yawDiff = normalizeAngle(currentYaw - idealYaw);


        if (Math.abs(yawDiff) < 5.0 && deltaYaw > 3.0) {

            float expectedMove = Math.abs(yawDiff);
            float actualMove = Math.abs(deltaYaw);

            if (actualMove > expectedMove * 0.8 && actualMove < expectedMove * 1.2) {
                return 0.5; // Suspiciously efficient
            }
        }

        return 0.0;
    }

    private double calculateTimingScore(BayesData data) {

        double velocityVariance = data.getVelocityVariance();


        if (velocityVariance < 0.5 && data.getAverageVelocity() > 2.0) {
            return 0.6;
        }

        return 0.0;
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

    private static class BayesData {
        private final Deque<Float> velocities = new ArrayDeque<>();
        private final Deque<Float> accelerations = new ArrayDeque<>();
        private final Deque<Double> probabilities = new ArrayDeque<>();

        void addSample(float deltaYaw, float deltaPitch, float accel) {
            velocities.addLast(deltaYaw);
            accelerations.addLast(accel);

            while (velocities.size() > SAMPLE_SIZE)
                velocities.pollFirst();
            while (accelerations.size() > SAMPLE_SIZE)
                accelerations.pollFirst();
        }

        void addProbability(double prob) {
            probabilities.addLast(prob);
            while (probabilities.size() > 10)
                probabilities.pollFirst();
        }

        boolean isReady() {
            return velocities.size() >= SAMPLE_SIZE;
        }

        double getSmoothedProbability() {
            if (probabilities.isEmpty())
                return 0.0;
            double sum = 0.0;
            for (Double prob : probabilities) {
                sum += prob;
            }
            return sum / probabilities.size();
        }

        double getAverageVelocity() {
            double sum = 0.0;
            for (Float v : velocities) {
                sum += v;
            }
            return velocities.isEmpty() ? 0.0 : sum / velocities.size();
        }

        double getVelocityVariance() {
            double mean = getAverageVelocity();
            double sum = 0.0;
            for (Float v : velocities) {
                double diff = v - mean;
                sum += diff * diff;
            }
            return velocities.isEmpty() ? 0.0 : sum / velocities.size();
        }

        double getAccelVariance() {
            double mean = 0.0;
            for (Float a : accelerations) {
                mean += a;
            }
            mean = accelerations.isEmpty() ? 0.0 : mean / accelerations.size();

            double sum = 0.0;
            for (Float a : accelerations) {
                double diff = a - mean;
                sum += diff * diff;
            }
            return accelerations.isEmpty() ? 0.0 : sum / accelerations.size();
        }
    }
}
