package ret.tawny.truthful.checks.impl.combat.aim;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import ret.tawny.truthful.Truthful;
import ret.tawny.truthful.checks.api.Check;
import ret.tawny.truthful.checks.api.CheckBuffer;
import ret.tawny.truthful.checks.api.data.CheckData;
import ret.tawny.truthful.checks.api.data.CheckType;
import ret.tawny.truthful.data.PlayerData;
import ret.tawny.truthful.wrapper.impl.client.position.RelMovePacketWrapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AimG: Angular Velocity Derivative Analysis (Jerk Detection)
 *
 * Advanced aimbot detection based on the physics of mouse movement.
 *
 * KEY INSIGHT:
 * Human mouse movements have natural "jerk" (rate of change of acceleration).
 * - Starting a mouse movement: jerk is positive (acceleration increases)
 * - During movement: jerk oscillates slightly due to muscle control
 * - Stopping: jerk is negative (deceleration)
 *
 * Aimbots exhibit unnatural jerk patterns:
 * 1. Step changes: instant velocity change = infinite jerk
 * 2. Linear aimbot: constant velocity = zero acceleration = zero jerk
 * 3. Smoothed aimbot: constant acceleration = zero jerk
 *
 * We analyze the second derivative of rotation angle over time.
 */
@CheckData(order = 'G', type = CheckType.AIM)
public final class AimG extends Check {

    private static final int SAMPLE_SIZE = 12;

    private final CheckBuffer buffer = new CheckBuffer(15.0);
    private final Map<UUID, JerkData> dataMap = new HashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isRotationUpdate())
            return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isRotationExempt())
            return;

        // FIX: Boat steering produces constant-velocity rotations with zero acceleration
        // and zero jerk, which is exactly the signature of a linear aimbot.
        if (data.isInsideVehicle()) {
            dataMap.remove(player.getUniqueId());
            return;
        }

        // Combat context only
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

        // Filter noise and extreme flicks
        if (deltaYaw < 0.5f || deltaYaw > 40.0f || deltaPitch > 40.0f) {
            buffer.decrease(player, 0.1);
            return;
        }

        JerkData jerkData = dataMap.get(player.getUniqueId());
        if (jerkData == null) {
            jerkData = new JerkData();
            dataMap.put(player.getUniqueId(), jerkData);
        }

        // Calculate angular velocity (already have this as delta)
        float velocity = deltaYaw;

        // Calculate acceleration (change in velocity)
        float acceleration = velocity - jerkData.lastVelocity;

        // Calculate jerk (change in acceleration)
        float jerk = acceleration - jerkData.lastAcceleration;

        jerkData.addSample(velocity, acceleration, jerk);
        jerkData.lastVelocity = velocity;
        jerkData.lastAcceleration = acceleration;

        if (!jerkData.isReady())
            return;

        // === Analysis 1: Zero Jerk Detection (Linear/Smooth Aimbot) ===
        double jerkVariance = jerkData.getJerkVariance();
        boolean zeroJerk = jerkVariance < 0.001 && jerkData.getAverageJerk() < 0.01;

        // === Analysis 2: Step Function Detection (Instant Snaps) ===
        // Count samples with extremely high jerk (velocity step changes)
        int stepCount = jerkData.countHighJerkSamples(15.0f);
        boolean hasSteps = stepCount >= 2;

        // === Analysis 3: Acceleration Consistency ===
        // Linear aimbots have near-zero acceleration variance
        double accelVariance = jerkData.getAccelerationVariance();
        boolean constantAccel = accelVariance < 0.05 && jerkData.getAverageAbsAcceleration() > 0.5;

        int suspicion = 0;
        if (zeroJerk)
            suspicion++;
        if (hasSteps)
            suspicion++;
        if (constantAccel)
            suspicion++;

        if (suspicion >= 2) {
            double severity = suspicion == 3 ? 2.5 : 1.5;
            if (buffer.increase(player, severity) > 12.0) {
                flag(data, String.format("Jerk Analysis. jerkVar=%.4f, steps=%d, accelVar=%.4f",
                        jerkVariance, stepCount, accelVariance));
                buffer.reset(player, 5.0);
            }
        } else {
            buffer.decrease(player, 0.35);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        dataMap.remove(event.getPlayer().getUniqueId());
    }

    private static class JerkData {
        float lastVelocity = 0f;
        float lastAcceleration = 0f;

        private final Deque<Float> velocities = new ArrayDeque<>();
        private final Deque<Float> accelerations = new ArrayDeque<>();
        private final Deque<Float> jerks = new ArrayDeque<>();

        void addSample(float velocity, float acceleration, float jerk) {
            velocities.addLast(velocity);
            accelerations.addLast(acceleration);
            jerks.addLast(jerk);

            while (velocities.size() > SAMPLE_SIZE)
                velocities.pollFirst();
            while (accelerations.size() > SAMPLE_SIZE)
                accelerations.pollFirst();
            while (jerks.size() > SAMPLE_SIZE)
                jerks.pollFirst();
        }

        boolean isReady() {
            return jerks.size() >= SAMPLE_SIZE;
        }

        double getAverageJerk() {
            double sum = 0.0;
            for (float j : jerks) {
                sum += Math.abs(j);
            }
            return jerks.isEmpty() ? 0.0 : sum / jerks.size();
        }

        double getJerkVariance() {
            double mean = 0.0;
            for (float j : jerks) {
                mean += j;
            }
            mean = jerks.isEmpty() ? 0.0 : mean / jerks.size();

            double sum = 0.0;
            for (float j : jerks) {
                double diff = j - mean;
                sum += diff * diff;
            }
            return jerks.isEmpty() ? 0.0 : sum / jerks.size();
        }

        double getAccelerationVariance() {
            double mean = 0.0;
            for (float a : accelerations) {
                mean += a;
            }
            mean = accelerations.isEmpty() ? 0.0 : mean / accelerations.size();

            double sum = 0.0;
            for (float a : accelerations) {
                double diff = a - mean;
                sum += diff * diff;
            }
            return accelerations.isEmpty() ? 0.0 : sum / accelerations.size();
        }

        double getAverageAbsAcceleration() {
            double sum = 0.0;
            for (float a : accelerations) {
                sum += Math.abs(a);
            }
            return accelerations.isEmpty() ? 0.0 : sum / accelerations.size();
        }

        int countHighJerkSamples(float threshold) {
            int count = 0;
            for (float j : jerks) {
                if (Math.abs(j) > threshold) {
                    count++;
                }
            }
            return count;
        }
    }
}
