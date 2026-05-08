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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@CheckData(order = 'F', type = CheckType.AIM)
public final class AimF extends Check {

    private static final int SAMPLE_SIZE = 25;

    private final CheckBuffer buffer = new CheckBuffer(15.0);
    private final Map<UUID, AimTemporalData> dataMap = new ConcurrentHashMap<>();

    @Override
    public void handleRelMove(final RelMovePacketWrapper wrapper) {
        if (!wrapper.isRotationUpdate()) return;

        final Player player = wrapper.getPlayer();
        final PlayerData data = Truthful.getInstance().getDataManager().getPlayerData(player);
        if (data == null || data.isRotationExempt()) return;

        // FIX: Boat A/D steering produces tick-perfect rotation timing that triggers
        // the temporal consistency detection.
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

        // Ignore noise & massive flicks
        if (deltaYaw < 0.15f || deltaYaw > 25.0f || deltaPitch > 25.0f) {
            buffer.decrease(player, 0.1);
            return;
        }

        AimTemporalData temporal = dataMap.computeIfAbsent(
                player.getUniqueId(), k -> new AimTemporalData()
        );

        long now = System.nanoTime();
        if (temporal.lastRotationTime != 0L) {
            long intervalNs = now - temporal.lastRotationTime;
            temporal.intervals.add(intervalNs);
        }
        temporal.lastRotationTime = now;

        temporal.yawSamples.add(deltaYaw);
        temporal.pitchSamples.add(deltaPitch);

        if (!temporal.isReady()) return;

        // === 1. INTERVAL CONSISTENCY (Timing Quantization) ===
        double intervalVariance = temporal.getIntervalVariance();
        boolean stableTiming = intervalVariance < 1.2E10; // ~3.5ms variance

        // === 2. QUANTIZATION STABILITY ===
        double yawStepVar = temporal.getQuantizationVariance(temporal.yawSamples);
        boolean quantized = yawStepVar < 1.0E-4;

        // === 3. AXIAL COUPLING (Yaw ↔ Pitch Lock) ===
        double ratioVar = temporal.getYawPitchRatioVariance();
        boolean axialLock = ratioVar < 0.02;

        int suspicion = 0;
        if (stableTiming) suspicion++;
        if (quantized) suspicion++;
        if (axialLock) suspicion++;

        if (suspicion >= 2) {
            double increase = suspicion == 3 ? 2.0 : 1.0;
            if (buffer.increase(player, increase) > 10.0) {
                flag(data, String.format(
                        "Temporal Aim. timingVar=%.2e, stepVar=%.5f, ratioVar=%.3f",
                        intervalVariance, yawStepVar, ratioVar
                ));
                buffer.reset(player, 4.0);
            }
        } else {
            buffer.decrease(player, 0.35);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        buffer.remove(event.getPlayer());
        dataMap.remove(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------

    private static final class AimTemporalData {
        long lastRotationTime;

        final Deque<Long> intervals = new ArrayDeque<>();
        final Deque<Float> yawSamples = new ArrayDeque<>();
        final Deque<Float> pitchSamples = new ArrayDeque<>();

        boolean isReady() {
            return intervals.size() >= SAMPLE_SIZE;
        }

        double getIntervalVariance() {
            double mean = intervals.stream().mapToDouble(Long::doubleValue).average().orElse(0.0);
            double sum = 0.0;
            for (long v : intervals) {
                double d = v - mean;
                sum += d * d;
            }
            trim(intervals);
            return sum / intervals.size();
        }

        double getQuantizationVariance(Deque<Float> samples) {
            double mean = samples.stream().mapToDouble(Float::doubleValue).average().orElse(0.0);
            double sum = 0.0;
            for (float f : samples) {
                double d = f - mean;
                sum += d * d;
            }
            trim(samples);
            return sum / samples.size();
        }

        double getYawPitchRatioVariance() {
            double mean = 0.0;
            int count = 0;

            for (int i = 0; i < yawSamples.size(); i++) {
                float y = yawSamples.pollFirst();
                float p = pitchSamples.pollFirst();
                if (p > 0.01f) {
                    mean += (y / p);
                    count++;
                }
                yawSamples.addLast(y);
                pitchSamples.addLast(p);
            }

            if (count == 0) return 1.0;
            mean /= count;

            double sum = 0.0;
            for (int i = 0; i < yawSamples.size(); i++) {
                float y = yawSamples.pollFirst();
                float p = pitchSamples.pollFirst();
                if (p > 0.01f) {
                    double r = (y / p) - mean;
                    sum += r * r;
                }
                yawSamples.addLast(y);
                pitchSamples.addLast(p);
            }

            trim(yawSamples);
            trim(pitchSamples);
            return sum / count;
        }

        private static <T> void trim(Deque<T> deque) {
            while (deque.size() > SAMPLE_SIZE) deque.pollFirst();
        }
    }
}
